package org.caojun.shotocr.processing

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.caojun.shotocr.R
import org.caojun.shotocr.database.data.AppDatabase
import org.caojun.shotocr.llm.LlmModelMissingException
import org.caojun.shotocr.llm.LlmReceiptEngine
import org.caojun.shotocr.llm.LlmReceiptParser
import org.caojun.shotocr.llm.LlmStores
import org.caojun.shotocr.llm.ReceiptData
import org.caojun.shotocr.llm.ReceiptValidator
import org.caojun.shotocr.llm.SystemPromptProvider
import org.caojun.shotocr.ocr.OcrEngine
import org.caojun.shotocr.util.ForegroundAppResolver

/**
 * 账单识别流水线：来源解析 → OCR → 本地大模型抽取 → 重复检测 → 通知确认。
 * 与"如何截图"无关，供"监听截屏（FALLBACK）"与"Shizuku 截屏"两种入口复用。
 */
class ReceiptProcessor(context: Context) {

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

    private var ocrEngine: OcrEngine? = null
    private var parser: LlmReceiptParser? = null
    private var promptProvider: SystemPromptProvider? = null

    suspend fun process(
        bitmap: Bitmap,
        timestamp: Long,
        screenshotUri: String?,
        notifier: Notifier
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
            bitmap.recycle()
            val fullText = ocrResults?.joinToString("\n") { it.text } ?: ""

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
                appContext.getString(R.string.progress_detail_ocr, ocrResults?.size ?: 0)
            )
            val receiptData = try {
                withContext(Dispatchers.Default) { resolveParser(engine).parse(fullText, foregroundLabel) }
            } catch (e: Exception) {
                notifier.notifyFailure(ProcessStep.AI_PARSING, e.message ?: e.javaClass.simpleName)
                return
            }
            val sourceApp = foregroundLabel ?: receiptData.sourceApp

            if (!ReceiptValidator.isReceipt(fullText, receiptData)) {
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
        ocrEngine?.release()
        ocrEngine = null
    }

    private fun resolveParser(engine: org.caojun.shotocr.llm.LlmEngineApi): LlmReceiptParser {
        val provider = promptProvider ?: SystemPromptProvider(
            LlmStores.promptStore(appContext),
            LlmStores.customPlatformStore(appContext),
            LlmStores.correctionStore(appContext)
        ).also { promptProvider = it }
        return parser ?: LlmReceiptParser(engine, provider).also { parser = it }
    }

    private suspend fun findDuplicate(receiptData: ReceiptData, sourceApp: String?): Long? {
        val orderNumber = receiptData.orderNumber
        val paymentTime = receiptData.paymentTime
        val appKey = sourceApp ?: ""
        val dao = AppDatabase.getInstance(appContext).receiptDao()
        return when {
            !orderNumber.isNullOrEmpty() ->
                withContext(Dispatchers.IO) { dao.getByOrderNumber(orderNumber, appKey)?.id }
            !paymentTime.isNullOrEmpty() && timeComponentRegex.containsMatchIn(paymentTime) ->
                withContext(Dispatchers.IO) { dao.getByPaymentTime(paymentTime, appKey)?.id }
            else -> null
        }
    }

    private fun llmErrorMessage(e: Exception): String =
        if (e is LlmModelMissingException) {
            appContext.getString(R.string.error_model_missing, e.assetName.substringAfterLast('/'))
        } else {
            e.message ?: e.javaClass.simpleName
        }
}
