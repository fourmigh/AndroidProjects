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
import org.caojun.shotocr.accounting.CorrectionHooks
import org.caojun.shotocr.accounting.ui.ConfirmActivity
import org.caojun.shotocr.llm.Correction
import org.caojun.shotocr.llm.LlmStores
import org.caojun.shotocr.llm.PlatformDetector
import org.caojun.shotocr.llm.ReceiptData
import org.caojun.shotocr.monitor.ScreenshotMonitor
import org.caojun.shotocr.processing.ProcessStep
import org.caojun.shotocr.processing.ProcessingProgress
import org.caojun.shotocr.processing.ReceiptProcessor
import org.caojun.shotocr.processing.RunState

/**
 * FALLBACK 模式的截图监听服务：前台服务 + MediaStore 监听（硬件截屏后自动识别）。
 */
class ScreenshotService : LifecycleService(), ReceiptProcessor.Notifier {

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
    }

    private var monitor: ScreenshotMonitor? = null
    private var processor: ReceiptProcessor? = null

    private val inFlightUris = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    )

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

        processor = ReceiptProcessor(applicationContext)

        CorrectionHooks.remember = { rawText, correctedJson ->
            val platformId = PlatformDetector.detect(null, rawText)?.id ?: "generic"
            LlmStores.correctionStore(applicationContext).add(Correction(rawText, correctedJson, platformId))
        }

        monitor = ScreenshotMonitor(applicationContext).also { it.start() }

        lifecycleScope.launch {
            monitor?.screenshots?.collect { screenshot ->
                Log.d(TAG, "Screenshot event received: ${screenshot.displayName}, uri=${screenshot.uri}")
                processScreenshot(screenshot.uri.toString(), screenshot.timestamp)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy - Service destroying")
        monitor?.destroy()
        monitor = null
        processor?.release()
        processor = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun mainPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE
    )

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
            val uri = android.net.Uri.parse(uriString)
            updateProgress(ProcessStep.LOADING_IMAGE, null)

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
                        null
                    }
                }
                if (bitmap != null) break
                if (attempt < maxRetries) {
                    updateProgress(
                        ProcessStep.LOADING_IMAGE,
                        getString(R.string.step_loading_image_retry, attempt, maxRetries)
                    )
                    delay(retryDelayMs)
                }
            }

            if (bitmap == null) {
                notifyFailure(ProcessStep.LOADING_IMAGE, getString(R.string.error_load_screenshot))
                return
            }

            val activeProcessor = processor ?: ReceiptProcessor(applicationContext).also { processor = it }
            activeProcessor.process(bitmap, timestamp, uriString, this)
        } catch (e: Exception) {
            Log.e(TAG, "processScreenshot: Error processing screenshot", e)
            notifyFailure(ProcessStep.FAILED, e.message ?: e.javaClass.simpleName)
        } finally {
            inFlightUris.remove(uriString)
            recentlyProcessedUris[uriString] = System.currentTimeMillis()
        }
    }

    // ---- ReceiptProcessor.Notifier ----

    override fun updateProgress(step: ProcessStep, detail: String?) {
        ProcessingProgress.update(step, RunState.RUNNING, detail)
        val text = getString(
            R.string.progress_step_format,
            step.weight,
            ProcessStep.TOTAL,
            detail ?: getString(step.labelRes)
        )
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

    override fun clearProgress() {
        ProcessingProgress.reset()
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID_PROGRESS)
    }

    override fun notifyFailure(step: ProcessStep, message: String?) {
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

    override fun notifyDuplicate(duplicateId: Long, screenshotUri: String?) {
        val intent = Intent(this, ConfirmActivity::class.java).apply {
            putExtra("receipt_id", duplicateId)
            if (!screenshotUri.isNullOrEmpty()) putExtra("delete_screenshot_uri", screenshotUri)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        postDuplicateNotification(intent)
    }

    override fun notifyConfirm(
        receiptData: ReceiptData,
        rawText: String,
        sourceApp: String?,
        screenshotUri: String?
    ) {
        val intent = Intent(this, ConfirmActivity::class.java).apply {
            putExtra("screenshot_uri", screenshotUri ?: "")
            putExtra("raw_text", rawText)
            putExtra("amount", receiptData.amount?.toString() ?: "")
            putExtra("discount", receiptData.discount?.toString() ?: "")
            putExtra("original_amount", receiptData.originalAmount?.toString() ?: "")
            putExtra("store_name", receiptData.storeName ?: "")
            putExtra("payment_time", receiptData.paymentTime ?: "")
            putExtra("payment_method", receiptData.paymentMethod ?: "")
            putExtra("order_number", receiptData.orderNumber ?: "")
            putExtra("source_app", sourceApp ?: "")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        postConfirmNotification(intent)
    }

    private fun postConfirmNotification(intent: Intent) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID_CONFIRM,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID_HIGH)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.screenshot_recognized))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID_CONFIRM, notification)
    }

    private fun postDuplicateNotification(intent: Intent) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID_DUPLICATE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.duplicate_receipt_notification))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID_DUPLICATE, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
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
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
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
