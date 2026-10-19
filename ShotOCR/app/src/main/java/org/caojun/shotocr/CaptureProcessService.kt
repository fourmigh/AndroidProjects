package org.caojun.shotocr

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.caojun.floatingbutton.FloatingButtonController
import org.caojun.screen.capture.ShizukuScreenCapture
import org.caojun.shizuku.RikkaShizukuUserServiceHost
import org.caojun.shotocr.accounting.CorrectionHooks
import org.caojun.shotocr.accounting.ui.ConfirmActivity
import org.caojun.shotocr.llm.Correction
import org.caojun.shotocr.llm.LlmStores
import org.caojun.shotocr.llm.PlatformDetector
import org.caojun.shotocr.llm.ReceiptData
import org.caojun.shotocr.processing.ProcessStep
import org.caojun.shotocr.processing.ProcessingProgress
import org.caojun.shotocr.processing.ReceiptProcessor
import org.caojun.shotocr.processing.RunState

/**
 * SHIZUKU 模式：以 shell 身份截屏并识别。
 * 仅在处理期间作为前台服务运行（短暂通知），完成后立即结束。
 */
class CaptureProcessService : LifecycleService(), ReceiptProcessor.Notifier {

    companion object {
        private const val TAG = "ShotOCR/Capture"
        private const val CHANNEL_ID = "screenshot_monitor_channel"
        private const val CHANNEL_ID_HIGH = "screenshot_confirm_channel"
        private const val ID_FOREGROUND = 1006
        private const val ID_CONFIRM = 1002
        private const val ID_DUPLICATE = 1003
        private const val ID_RESULT = 1004
    }

    private var processor: ReceiptProcessor? = null
    private var screenCapture: ShizukuScreenCapture? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        createHighPriorityNotificationChannel()
        startForeground(ID_FOREGROUND, buildProgressNotification(getString(R.string.step_idle)))

        processor = ReceiptProcessor(applicationContext)
        screenCapture = ShizukuScreenCapture(
            applicationContext,
            RikkaShizukuUserServiceHost(applicationContext)
        )

        CorrectionHooks.remember = { rawText, correctedJson ->
            val platformId = PlatformDetector.detect(null, rawText)?.id ?: "generic"
            LlmStores.correctionStore(applicationContext).add(Correction(rawText, correctedJson, platformId))
        }

        lifecycleScope.launch {
            // 无论磁贴还是悬浮按钮触发，截屏前先隐藏悬浮按钮并留出渲染时间。
            FloatingButtonController.hide(applicationContext)
            delay(300)
            val bitmap = screenCapture?.capture()
            FloatingButtonController.show(applicationContext)
            if (bitmap == null) {
                Log.e(TAG, "Shizuku capture failed, fallback to monitor mode")
                notifyFailure(ProcessStep.FAILED, getString(R.string.capture_failed_fallback))
                fallbackToMonitor()
                stopSelfSafely()
                return@launch
            }
            processor?.process(bitmap, System.currentTimeMillis(), null, this@CaptureProcessService)
            stopSelfSafely()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        processor?.release()
        processor = null
        super.onDestroy()
    }

    private fun fallbackToMonitor() {
        try {
            ContextCompat.startForegroundService(
                this,
                Intent(this, ScreenshotService::class.java)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start fallback monitor", e)
        }
    }

    private fun stopSelfSafely() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun mainPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE
    )

    // ---- ReceiptProcessor.Notifier ----

    override fun updateProgress(step: ProcessStep, detail: String?) {
        ProcessingProgress.update(step, RunState.RUNNING, detail)
        val text = detail ?: getString(step.labelRes)
        getSystemService(NotificationManager::class.java)
            .notify(ID_FOREGROUND, buildProgressNotification(text))
    }

    override fun clearProgress() {
        ProcessingProgress.reset()
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
        getSystemService(NotificationManager::class.java).notify(ID_RESULT, notification)
    }

    override fun notifyDuplicate(duplicateId: Long, screenshotUri: String?) {
        val intent = Intent(this, ConfirmActivity::class.java).apply {
            putExtra("receipt_id", duplicateId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            ID_DUPLICATE,
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
        getSystemService(NotificationManager::class.java).notify(ID_DUPLICATE, notification)
    }

    override fun notifyConfirm(
        receiptData: ReceiptData,
        rawText: String,
        sourceApp: String?,
        screenshotUri: String?
    ) {
        val intent = Intent(this, ConfirmActivity::class.java).apply {
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
        val pendingIntent = PendingIntent.getActivity(
            this,
            ID_CONFIRM,
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
        getSystemService(NotificationManager::class.java).notify(ID_CONFIRM, notification)
    }

    private fun buildProgressNotification(text: String): android.app.Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(mainPendingIntent())
            .setOnlyAlertOnce(true)
            .setProgress(ProcessStep.TOTAL, 0, true)
            .build()

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
}
