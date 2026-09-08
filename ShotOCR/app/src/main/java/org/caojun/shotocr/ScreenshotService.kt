package org.caojun.shotocr

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.IBinder
import android.util.Log
import android.widget.Toast
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
import org.caojun.shotocr.parser.ReceiptParser
import org.caojun.shotocr.accounting.ui.ConfirmActivity

class ScreenshotService : LifecycleService() {

    companion object {
        private const val TAG = "ShotOCR/Service"
        private const val CHANNEL_ID = "screenshot_monitor_channel"
        private const val CHANNEL_ID_HIGH = "screenshot_confirm_channel"
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_ID_CONFIRM = 1002
    }

    private var monitor: ScreenshotMonitor? = null
    private var ocrEngine: OcrEngine? = null
    private val parser = ReceiptParser()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate - Service creating")
        createNotificationChannel()
        createHighPriorityNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        Log.d(TAG, "onCreate - Foreground service started")

        ocrEngine = OcrEngine(applicationContext)
        Log.d(TAG, "onCreate - OcrEngine created")

        monitor = ScreenshotMonitor(applicationContext).also {
            it.start()
            Log.d(TAG, "onCreate - ScreenshotMonitor started")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(TAG, "onStartCommand: flags=$flags, startId=$startId")
        
        lifecycleScope.launch {
            Log.d(TAG, "onStartCommand - Starting to collect screenshots flow")
            monitor?.screenshots?.collect { screenshot ->
                Log.d(TAG, "Screenshot event received: ${screenshot.displayName}, uri=${screenshot.uri}")
                processScreenshot(screenshot.uri.toString())
            }
        }
        
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

    private suspend fun processScreenshot(uriString: String) {
        try {
            Log.d(TAG, "processScreenshot: Starting processing for $uriString")
            val uri = android.net.Uri.parse(uriString)

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
                    delay(retryDelayMs)
                }
            }

            if (bitmap == null) {
                Log.e(TAG, "processScreenshot: Failed to load bitmap after $maxRetries attempts for $uriString")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ScreenshotService, "Failed to load screenshot (item may still be pending)", Toast.LENGTH_LONG).show()
                }
                return
            }
            Log.d(TAG, "processScreenshot: Bitmap loaded: ${bitmap.width}x${bitmap.height}")

            Log.d(TAG, "processScreenshot: Initializing OCR engine")
            ocrEngine?.initialize()
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

            val receiptData = parser.parse(fullText)
            Log.d(TAG, "processScreenshot: Parsed receipt: amount=${receiptData.amount}, discount=${receiptData.discount}, items=${receiptData.items.size}")

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

                val names = ArrayList(receiptData.items.map { it.name })
                val prices = ArrayList(receiptData.items.map { it.price?.toString() ?: "" })
                val quantities = ArrayList(receiptData.items.map { it.quantity?.toString() ?: "" })
                putStringArrayListExtra("item_names", names)
                putStringArrayListExtra("item_prices", prices)
                putStringArrayListExtra("item_quantities", quantities)
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
                .setContentText(getString(R.string.screenshot_detected))
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setFullScreenIntent(pendingIntent, true)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID_CONFIRM, confirmNotification)
            Log.d(TAG, "processScreenshot: Confirm notification sent")
        } catch (e: Exception) {
            Log.e(TAG, "processScreenshot: Error processing screenshot", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@ScreenshotService, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
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
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_listening))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
