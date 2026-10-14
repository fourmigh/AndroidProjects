package org.caojun.shotocr

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.caojun.shotocr.monitor.ScreenshotMonitor
import org.caojun.shotocr.ocr.OcrEngine
import org.caojun.shotocr.llm.Correction
import org.caojun.shotocr.llm.LlmModelMissingException
import org.caojun.shotocr.llm.LlmReceiptEngine
import org.caojun.shotocr.llm.LlmReceiptParser
import org.caojun.shotocr.llm.LlmStores
import org.caojun.shotocr.llm.PlatformDetector
import org.caojun.shotocr.llm.SystemPromptProvider
import org.caojun.shotocr.database.data.AppDatabase
import org.caojun.shotocr.accounting.CorrectionHooks
import org.caojun.shotocr.accounting.ui.ConfirmActivity
import org.caojun.shotocr.processing.ProcessStep
import org.caojun.shotocr.processing.ProcessingProgress
import org.caojun.shotocr.processing.RunState
import org.caojun.shotocr.util.ForegroundAppResolver

class ScreenshotService : LifecycleService() {

    companion object {
        private const val TAG = "ShotOCR/Service"
        private const val CHANNEL_ID = "screenshot_monitor_channel"
        private const val CHANNEL_ID_HIGH = "screenshot_confirm_channel"
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_ID_CONFIRM = 1002
        private const val NOTIFICATION_ID_DUPLICATE = 1003
        private const val NOTIFICATION_ID_RESULT = 1004
        private const val NOTIFICATION_ID_PROGRESS = 1005
        private const val DEDUPE_WINDOW_MS = 10_000L
        private const val DEDUPE_CACHE_SIZE = 50
        private val TIME_COMPONENT_REGEX = Regex("""\d{1,2}:\d{2}:\d{2}""")
    }

    private var monitor: ScreenshotMonitor? = null
    private var ocrEngine: OcrEngine? = null
    private var parser: LlmReceiptParser? = null
    private var promptProvider: SystemPromptProvider? = null

    /** 正在处理的截图 uri（防止同一张被并发处理）。 */
    private val inFlightUris = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    )

    /** 最近处理过的截图 uri → 完成时间（防止同一张被重复处理）。 */
    private val recentlyProcessedUris = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, Long>(DEDUPE_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean =
                size > DEDUPE_CACHE_SIZE
        }
    )

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate - Service creating")
        createNotificationChannel()
        createHighPriorityNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        Log.d(TAG, "onCreate - Foreground service started")

        ocrEngine = OcrEngine(applicationContext)
        Log.d(TAG, "onCreate - OcrEngine created")

        CorrectionHooks.remember = { rawText, correctedJson ->
            val platformId = PlatformDetector.detect(null, rawText)?.id ?: "generic"
            LlmStores.correctionStore(applicationContext).add(Correction(rawText, correctedJson, platformId))
        }

        lifecycleScope.launch {
            val engine = LlmReceiptEngine.get(applicationContext)
            val provider = SystemPromptProvider(
                LlmStores.promptStore(applicationContext),
                LlmStores.customPlatformStore(applicationContext),
                LlmStores.correctionStore(applicationContext)
            )
            promptProvider = provider
            parser = LlmReceiptParser(engine, provider)
            try {
                withContext(Dispatchers.Default) { engine.initialize() }
                Log.d(TAG, "onCreate - LLM parser ready")
            } catch (e: Exception) {
                Log.e(TAG, "onCreate - LLM engine init failed", e)
            }
        }
        Log.d(TAG, "onCreate - LLM parser loader started")

        monitor = ScreenshotMonitor(applicationContext).also {
            it.start()
            Log.d(TAG, "onCreate - ScreenshotMonitor started")
        }

        // 只收集一次，避免 onStartCommand 被多次调用时产生多个收集者并发处理同一截图。
        lifecycleScope.launch {
            Log.d(TAG, "onCreate - Starting to collect screenshots flow")
            monitor?.screenshots?.collect { screenshot ->
                Log.d(TAG, "Screenshot event received: ${screenshot.displayName}, uri=${screenshot.uri}")
                processScreenshot(screenshot.uri.toString(), screenshot.timestamp)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(TAG, "onStartCommand: flags=$flags, startId=$startId")
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy - Service destroying")
        monitor?.destroy()
        monitor = null
        ocrEngine?.release()
        ocrEngine = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun hasTimeComponent(paymentTime: String?): Boolean =
        !paymentTime.isNullOrEmpty() && TIME_COMPONENT_REGEX.containsMatchIn(paymentTime)

    private fun mainPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE
    )

    /** 更新独立的"进度"通知，并同步进度状态（常驻监听通知 1001 不受影响）。 */
    private fun updateProgress(step: ProcessStep, detail: String? = null) {
        ProcessingProgress.update(step, RunState.RUNNING, detail)
        val text = detail ?: getString(step.labelRes)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(mainPendingIntent())
            .setOnlyAlertOnce(true)
            .setProgress(ProcessStep.TOTAL, step.weight, false)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID_PROGRESS, notification)
    }

    /** 处理结束：清除进度状态并取消进度通知（常驻监听通知 1001 始终保留）。 */
    private fun clearProgress() {
        ProcessingProgress.reset()
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID_PROGRESS)
    }

    private fun llmErrorMessage(e: Exception): String =
        if (e is LlmModelMissingException) {
            getString(R.string.error_model_missing, e.assetName.substringAfterLast('/'))
        } else {
            e.message ?: e.javaClass.simpleName
        }

    /** 处理失败：发结果通知并清除进度。 */
    private fun notifyFailure(step: ProcessStep, message: String?) {
        ProcessingProgress.update(step, RunState.FAILED, error = message)
        val text = getString(R.string.progress_failed, getString(step.labelRes), message ?: "")
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(mainPendingIntent())
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID_RESULT, notification)
        clearProgress()
    }

    private suspend fun processScreenshot(uriString: String, timestamp: Long) {
        val now = System.currentTimeMillis()
        recentlyProcessedUris[uriString]?.let { finishedAt ->
            if (now - finishedAt < DEDUPE_WINDOW_MS) {
                Log.d(TAG, "processScreenshot: Skip recently processed uri=$uriString")
                return
            }
        }
        if (!inFlightUris.add(uriString)) {
            Log.d(TAG, "processScreenshot: Skip already processing uri=$uriString")
            return
        }
        try {
            Log.d(TAG, "processScreenshot: Starting processing for $uriString")
            val uri = android.net.Uri.parse(uriString)
            updateProgress(ProcessStep.RESOLVING_SOURCE)

            val foregroundPackage = withContext(Dispatchers.IO) {
                ForegroundAppResolver.getForegroundPackage(applicationContext, timestamp)
            }
            val foregroundLabel = foregroundPackage?.let { pkg ->
                withContext(Dispatchers.IO) {
                    ForegroundAppResolver.getAppLabel(applicationContext, pkg)
                }
            }
            Log.d(TAG, "processScreenshot: foregroundPackage=$foregroundPackage, foregroundLabel=$foregroundLabel")
            updateProgress(
                ProcessStep.LOADING_IMAGE,
                foregroundLabel?.let { getString(R.string.progress_detail_source, it) }
            )

            Log.d(TAG, "processScreenshot: Loading bitmap from URI (with retry)")
            var bitmap: android.graphics.Bitmap? = null
            val maxRetries = 10
            val retryDelayMs = 1000L

            for (attempt in 1..maxRetries) {
                bitmap = withContext(Dispatchers.IO) {
                    try {
                        contentResolver.openInputStream(uri)?.use { stream ->
                            BitmapFactory.decodeStream(stream)
                        }
                    } catch (e: Exception) {
                        val msg = e.message ?: ""
                        if (msg.contains("pending", ignoreCase = true) || msg.contains("trashed", ignoreCase = true)) {
                            Log.w(TAG, "processScreenshot: Attempt $attempt/$maxRetries - Item still pending, waiting ${retryDelayMs}ms...")
                        } else {
                            Log.e(TAG, "processScreenshot: Attempt $attempt/$maxRetries - OpenInputStream error", e)
                        }
                        null
                    }
                }
                if (bitmap != null) {
                    Log.d(TAG, "processScreenshot: Bitmap loaded successfully on attempt $attempt")
                    break
                }
                if (attempt < maxRetries) {
                    Log.d(TAG, "processScreenshot: Retrying in ${retryDelayMs}ms (attempt $attempt/$maxRetries)")
                    updateProgress(
                        ProcessStep.LOADING_IMAGE,
                        getString(R.string.step_loading_image_retry, attempt, maxRetries)
                    )
                    delay(retryDelayMs)
                }
            }

            if (bitmap == null) {
                Log.e(TAG, "processScreenshot: Failed to load bitmap after $maxRetries attempts for $uriString")
                notifyFailure(ProcessStep.LOADING_IMAGE, getString(R.string.error_load_screenshot))
                return
            }
            Log.d(TAG, "processScreenshot: Bitmap loaded: ${bitmap.width}x${bitmap.height}")

            Log.d(TAG, "processScreenshot: Initializing OCR engine")
            updateProgress(ProcessStep.OCR_INIT)
            ocrEngine?.initialize()
            updateProgress(ProcessStep.OCR_RECOGNIZE)
            Log.d(TAG, "processScreenshot: Running OCR detection")
            val ocrResults = try {
                ocrEngine?.detect(bitmap)
            } catch (e: Exception) {
                Log.e(TAG, "processScreenshot: OCR detection failed", e)
                null
            }
            bitmap.recycle()

            Log.d(TAG, "processScreenshot: OCR found ${ocrResults?.size ?: 0} text blocks")

            val fullText = ocrResults?.joinToString("\n") { it.text } ?: ""
            Log.d(TAG, "processScreenshot: OCR text: ${fullText.take(200)}...")

            updateProgress(ProcessStep.AI_LOADING, getString(R.string.progress_detail_ai_loading))
            val activeParser = parser
            val engine = LlmReceiptEngine.get(applicationContext)
            try {
                withContext(Dispatchers.Default) { engine.initialize() }
            } catch (e: Exception) {
                Log.e(TAG, "processScreenshot: LLM load failed", e)
                notifyFailure(ProcessStep.AI_LOADING, llmErrorMessage(e))
                return
            }
            updateProgress(
                ProcessStep.AI_PARSING,
                getString(R.string.progress_detail_ocr, ocrResults?.size ?: 0)
            )
            val receiptData = try {
                withContext(Dispatchers.Default) {
                    val provider = promptProvider ?: SystemPromptProvider(
                        LlmStores.promptStore(applicationContext),
                        LlmStores.customPlatformStore(applicationContext),
                        LlmStores.correctionStore(applicationContext)
                    ).also { promptProvider = it }
                    (activeParser ?: LlmReceiptParser(engine, provider)).parse(fullText, foregroundLabel)
                }
            } catch (e: Exception) {
                Log.e(TAG, "processScreenshot: LLM parse failed", e)
                notifyFailure(ProcessStep.AI_PARSING, e.message ?: e.javaClass.simpleName)
                return
            }
            val sourceApp = foregroundLabel ?: receiptData.sourceApp
            Log.d(TAG, "processScreenshot: Parsed receipt: amount=${receiptData.amount}, discount=${receiptData.discount}, sourceApp=$sourceApp")

            updateProgress(ProcessStep.DEDUP)
            val orderNumber = receiptData.orderNumber
            val paymentTime = receiptData.paymentTime
            val appKey = sourceApp ?: ""
            val receiptDao = AppDatabase.getInstance(applicationContext).receiptDao()
            var duplicateId: Long? = null
            if (!orderNumber.isNullOrEmpty()) {
                duplicateId = withContext(Dispatchers.IO) { receiptDao.getByOrderNumber(orderNumber, appKey)?.id }
            } else if (hasTimeComponent(paymentTime)) {
                duplicateId = withContext(Dispatchers.IO) { receiptDao.getByPaymentTime(paymentTime!!, appKey)?.id }
            }
            Log.d(TAG, "processScreenshot: orderNumber=$orderNumber, paymentTime=$paymentTime, duplicateId=$duplicateId")

            if (duplicateId != null) {
                updateProgress(ProcessStep.DONE, getString(R.string.progress_duplicate))
                notifyDuplicateReceipt(uriString, duplicateId)
                clearProgress()
                return
            }

            val intent = Intent(this, ConfirmActivity::class.java).apply {
                putExtra("screenshot_uri", uriString)
                putExtra("raw_text", fullText)
                putExtra("amount", receiptData.amount?.toString() ?: "")
                putExtra("discount", receiptData.discount?.toString() ?: "")
                putExtra("original_amount", receiptData.originalAmount?.toString() ?: "")
                putExtra("store_name", receiptData.storeName ?: "")
                putExtra("payment_time", receiptData.paymentTime ?: "")
                putExtra("payment_method", receiptData.paymentMethod ?: "")
                putExtra("order_number", receiptData.orderNumber ?: "")
                putExtra("source_app", sourceApp ?: "")
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

            val pendingIntent = PendingIntent.getActivity(
                this,
                NOTIFICATION_ID_CONFIRM,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val confirmNotification = NotificationCompat.Builder(this, CHANNEL_ID_HIGH)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.screenshot_recognized))
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setFullScreenIntent(pendingIntent, true)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()

            updateProgress(ProcessStep.DONE, getString(R.string.progress_done))
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID_CONFIRM, confirmNotification)
            clearProgress()
            Log.d(TAG, "processScreenshot: Confirm notification sent")
        } catch (e: Exception) {
            Log.e(TAG, "processScreenshot: Error processing screenshot", e)
            val step = ProcessingProgress.state.value.step
                .takeIf { it != ProcessStep.IDLE } ?: ProcessStep.FAILED
            notifyFailure(step, e.message ?: e.javaClass.simpleName)
        } finally {
            inFlightUris.remove(uriString)
            recentlyProcessedUris[uriString] = System.currentTimeMillis()
        }
    }

    private fun notifyDuplicateReceipt(uriString: String, duplicateId: Long) {
        val deleteIntent = Intent(this, ConfirmActivity::class.java).apply {
            putExtra("delete_screenshot_uri", uriString)
            putExtra("receipt_id", duplicateId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val deletePendingIntent = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID_DUPLICATE,
            deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val duplicateNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.duplicate_receipt_notification))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(deletePendingIntent)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID_DUPLICATE, duplicateNotification)
        Log.d(TAG, "notifyDuplicateReceipt: Duplicate notification sent for $uriString")
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
        Log.d(TAG, "Notification channel created")
    }

    private fun createHighPriorityNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID_HIGH,
            getString(R.string.confirm_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.confirm_channel_desc)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
        Log.d(TAG, "High priority notification channel created")
    }

    private fun buildNotification(): android.app.Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_listening))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(mainPendingIntent())
            .setOngoing(true)
            .build()
    }
}
