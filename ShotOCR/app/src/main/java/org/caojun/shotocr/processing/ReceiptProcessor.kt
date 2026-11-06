package org.caojun.shotocr.processing

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.caojun.shotocr.R
import org.caojun.shotocr.batch.BatchDiagnostics
import org.caojun.shotocr.database.data.AppDatabase
import org.caojun.ai.llm.api.LlmApi
import org.caojun.ai.llm.api.LlmEngineApi
import org.caojun.ai.llm.api.LlmException
import org.caojun.ai.ocr.api.OcrEngine
import org.caojun.shotocr.llm.LlmReceiptEngine
import org.caojun.shotocr.llm.LlmReceiptParser
import org.caojun.shotocr.llm.LlmStores
import org.caojun.shotocr.llm.ReceiptData
import org.caojun.shotocr.llm.ReceiptValidator
import org.caojun.shotocr.llm.SystemPromptProvider
import org.caojun.shotocr.util.ForegroundAppResolver

/**
 * 账单识别流水线：来源解析 → OCR → 本地大模型抽取 → 重复检测 → 通知确认。
 * 与"如何截图"无关，供"监听截屏（FALLBACK）"与"Shizuku 截屏"两种入口复用。
 */
class ReceiptProcessor(
    context: Context,
    private val sharedOcrEngine: OcrEngine? = null
) {

    interface Notifier {
        fun updateProgress(step: ProcessStep, detail: String?)
        fun clearProgress()
        fun notifyFailure(step: ProcessStep, message: String?)
        fun notifyNotReceipt()
        fun notifyDuplicate(duplicateId: Long, screenshotUri: String?)
        fun notifyConfirm(
            receiptData: ReceiptData,
            rawText: String,
            sourceApp: String?,
            screenshotUri: String?
        )
    }

    private val appContext = context.applicationContext
    private val timeComponentRegex = Regex("""\d{1,2}:\d{2}:\d{2}""")

    private val ownsOcrEngine = sharedOcrEngine == null
    private var ocrEngine: OcrEngine? = sharedOcrEngine
    private var parser: LlmReceiptParser? = null
    private var promptProvider: SystemPromptProvider? = null

    suspend fun process(
        bitmap: Bitmap,
        timestamp: Long,
        screenshotUri: String?,
        notifier: Notifier,
        precomputedText: String? = null,
        orderNumberHint: String? = null,
        allowMissingPaymentTime: Boolean = false
    ) {
        try {
            notifier.updateProgress(ProcessStep.RESOLVING_SOURCE, null)
            val foregroundPackage = withContext(Dispatchers.IO) {
                ForegroundAppResolver.getForegroundPackage(appContext, timestamp)
            }
            val foregroundLabel = foregroundPackage?.let { pkg ->
                withContext(Dispatchers.IO) { ForegroundAppResolver.getAppLabel(appContext, pkg) }
            }
            notifier.updateProgress(
                ProcessStep.LOADING_IMAGE,
                foregroundLabel?.let { appContext.getString(R.string.progress_detail_source, it) }
            )

            val fullText: String
            val ocrCount: Int
            if (precomputedText != null) {
                fullText = precomputedText
                ocrCount = precomputedText.lines().size
                bitmap.recycle()
            } else {
                notifier.updateProgress(ProcessStep.OCR_INIT, null)
                val ocr = ocrEngine ?: OcrEngine(appContext).also {
                    it.initialize()
                    ocrEngine = it
                }
                notifier.updateProgress(ProcessStep.OCR_RECOGNIZE, null)
                val ocrResults = try {
                    ocr.detect(bitmap)
                } catch (e: Exception) {
                    null
                }
                ocrCount = ocrResults?.size ?: 0
                bitmap.recycle()
                fullText = ocrResults?.joinToString("\n") { it.text } ?: ""
            }

            notifier.updateProgress(
                ProcessStep.AI_LOADING,
                appContext.getString(R.string.progress_detail_ai_loading)
            )
            val engine = LlmReceiptEngine.get(appContext)
            try {
                engine.initialize()
            } catch (e: Exception) {
                notifier.notifyFailure(ProcessStep.AI_LOADING, llmErrorMessage(e))
                return
            }

            notifier.updateProgress(
                ProcessStep.AI_PARSING,
                appContext.getString(R.string.progress_detail_ocr, ocrCount)
            )
            var receiptData = try {
                withContext(Dispatchers.Default) { resolveParser(engine).parse(fullText, foregroundLabel) }
            } catch (e: Exception) {
                notifier.notifyFailure(ProcessStep.AI_PARSING, e.message ?: e.javaClass.simpleName)
                return
            }
            if (!orderNumberHint.isNullOrBlank()) {
                receiptData = receiptData.copy(orderNumber = orderNumberHint)
            }
            val sourceApp = foregroundLabel ?: receiptData.sourceApp

            val validation = ReceiptValidator.validate(fullText, receiptData)
            val hasAmount = (receiptData.amount ?: 0.0) > 0.0
            val canSaveMissingTime = allowMissingPaymentTime &&
                receiptData.paymentTime.isNullOrBlank() &&
                hasAmount
            // 批量详情即使缺少付款方式/时间，也先保存为待审核，避免直接丢掉订单。
            val canSaveIncompleteBatch = allowMissingPaymentTime &&
                validation.amountSignal &&
                validation.title &&
                hasAmount
            if (!validation.valid || canSaveMissingTime || canSaveIncompleteBatch) {
                BatchDiagnostics.log(
                    "validation",
                    "valid" to validation.valid,
                    "reason" to validation.reason,
                    "amountSignal" to validation.amountSignal,
                    "groups" to validation.groups,
                    "title" to validation.title,
                    "payment" to validation.payment,
                    "order" to validation.order,
                    "time" to validation.time,
                    "merchant" to validation.merchant,
                    "missingTimeBypass" to canSaveMissingTime,
                    "incompleteBatchBypass" to canSaveIncompleteBatch
                )
            }
            if (!validation.valid && !canSaveMissingTime && !canSaveIncompleteBatch) {
                notifier.notifyNotReceipt()
                notifier.clearProgress()
                return
            }

            notifier.updateProgress(ProcessStep.DEDUP, null)
            val duplicateId = findDuplicate(receiptData, sourceApp)
            if (duplicateId != null) {
                notifier.updateProgress(
                    ProcessStep.DONE,
                    appContext.getString(R.string.progress_duplicate)
                )
                notifier.notifyDuplicate(duplicateId, screenshotUri)
                notifier.clearProgress()
                return
            }

            notifier.updateProgress(ProcessStep.DONE, appContext.getString(R.string.progress_done))
            notifier.notifyConfirm(receiptData, fullText, sourceApp, screenshotUri)
            notifier.clearProgress()
        } catch (e: Exception) {
            val step = ProcessingProgress.state.value.step
                .takeIf { it != ProcessStep.IDLE } ?: ProcessStep.FAILED
            notifier.notifyFailure(step, e.message ?: e.javaClass.simpleName)
        }
    }

    fun release() {
        if (ownsOcrEngine) {
            ocrEngine?.release()
        }
        ocrEngine = null
    }

    private fun resolveParser(engine: LlmEngineApi): LlmReceiptParser {
        val provider = promptProvider ?: SystemPromptProvider(
            LlmStores.promptStore(appContext),
            LlmStores.customPlatformStore(appContext),
            LlmStores.correctionStore(appContext)
        ).also { promptProvider = it }
        return parser ?: LlmReceiptParser(engine, provider).also { parser = it }
    }

    private suspend fun findDuplicate(receiptData: ReceiptData, sourceApp: String?): Long? {
        val paymentTime = receiptData.paymentTime
        if (paymentTime.isNullOrEmpty() || !timeComponentRegex.containsMatchIn(paymentTime)) {
            return null
        }
        val appKey = sourceApp?.takeIf { it.isNotBlank() }
            ?: receiptData.sourceApp?.takeIf { it.isNotBlank() }
            ?: ""
        val dao = AppDatabase.getInstance(appContext).receiptDao()
        return withContext(Dispatchers.IO) {
            dao.getByPaymentTime(paymentTime, appKey)?.id
        }
    }

    private fun llmErrorMessage(e: Exception): String =
        if (e is LlmException && e.code == LlmApi.ERROR_NOT_READY) {
            "本地大模型服务未就绪，请确认已安装并打开「本地大模型服务」App"
        } else {
            e.message ?: e.javaClass.simpleName
        }
}
