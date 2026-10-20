package org.caojun.shotocr

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        private const val MAX_IMAGE_BYTES = 400_000
        private const val MAX_IMAGE_BYTES_HARD = 600_000
    }

    private var processor: ReceiptProcessor? = null
    private var screenCapture: ShizukuScreenCapture? = null
    private var pendingScreenshotImage: ByteArray? = null
    private var progressFinished = false
    private val progressStepLabels: List<String> by lazy {
        ProcessStep.entries
            .filter { it.weight in 1..ProcessStep.TOTAL && it != ProcessStep.FAILED }
            .sortedBy { it.weight }
            .map { getString(it.labelRes) }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        createHighPriorityNotificationChannel()
        startForeground(
            ID_FOREGROUND,
            buildProgressNotification(getString(R.string.step_idle), 0, indeterminate = true)
        )

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
            // 无论磁贴还是悬浮按钮触发，截屏前先清理进度并隐藏悬浮按钮，留出渲染时间。
            progressFinished = false
            FloatingButtonController.clearProgress(applicationContext)
            FloatingButtonController.hide(applicationContext)
            delay(300)
            val bitmap = screenCapture?.capture()
            if (bitmap == null) {
                FloatingButtonController.show(applicationContext)
                Log.e(TAG, "Shizuku capture failed")
                notifyFailure(
                    ProcessStep.FAILED,
                    getString(
                        R.string.capture_failed_shizuku,
                        getString(R.string.capture_mode_shizuku)
                    )
                )
                stopSelfSafely()
                return@launch
            }
            try {
                pendingScreenshotImage = encodeScreenshotForStorage(bitmap)
                FloatingButtonController.showProgress(
                    applicationContext,
                    progressStepLabels,
                    ProcessStep.RESOLVING_SOURCE.weight,
                    ProcessStep.RESOLVING_SOURCE.weight / ProcessStep.TOTAL.toFloat()
                )
                processor?.process(
                    bitmap,
                    System.currentTimeMillis(),
                    null,
                    this@CaptureProcessService
                )
            } finally {
                FloatingButtonController.finishProgress(applicationContext)
                progressFinished = true
                stopSelfSafely()
            }
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
        if (!progressFinished) {
            FloatingButtonController.clearProgress(applicationContext)
        }
        pendingScreenshotImage = null
        processor?.release()
        processor = null
        super.onDestroy()
    }

    /** 压缩一张用于确认页/列表缩略图的预览图；OCR 仍使用原始位图。 */
    private suspend fun encodeScreenshotForStorage(bitmap: Bitmap): ByteArray? =
        withContext(Dispatchers.Default) {
            val attempts = listOf(1280 to 75, 960 to 65)
            var last: ByteArray? = null
            for ((maxSide, quality) in attempts) {
                val bytes = compressScreenshot(bitmap, maxSide, quality) ?: continue
                last = bytes
                if (bytes.size <= MAX_IMAGE_BYTES) return@withContext bytes
            }
            last?.takeIf { it.size <= MAX_IMAGE_BYTES_HARD }
        }

    private fun compressScreenshot(bitmap: Bitmap, maxSide: Int, quality: Int): ByteArray? {
        val longestSide = max(bitmap.width, bitmap.height)
        val scale = if (longestSide > maxSide) {
            maxSide.toFloat() / longestSide
        } else {
            1f
        }
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                (bitmap.height * scale).roundToInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }
        return try {
            ByteArrayOutputStream().use { out ->
                if (scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
                    out.toByteArray()
                } else {
                    null
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "encode screenshot failed", t)
            null
        } finally {
            if (scaled !== bitmap) scaled.recycle()
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
        val text = progressText(step, detail)

        FloatingButtonController.updateProgress(
            applicationContext,
            step.weight,
            step.weight / ProcessStep.TOTAL.toFloat()
        )

        getSystemService(NotificationManager::class.java)
            .notify(ID_FOREGROUND, buildProgressNotification(text, step.weight))
    }

    private fun progressText(step: ProcessStep, detail: String?): String = getString(
        R.string.progress_step_format,
        step.weight,
        ProcessStep.TOTAL,
        detail ?: getString(step.labelRes)
    )

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
            pendingScreenshotImage?.let { putExtra("screenshot_image", it) }
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

    private fun buildProgressNotification(
        text: String,
        weight: Int,
        indeterminate: Boolean = false
    ): android.app.Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(mainPendingIntent())
            .setOnlyAlertOnce(true)
            .setProgress(ProcessStep.TOTAL, weight, indeterminate)
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
