package org.caojun.shizuku.autoreturn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.caojun.shizuku.ShizukuGateway

/**
 * 监听 Shizuku 启动完成，并尽量把宿主 App 带回前台。
 * 所有方法应在主线程调用。
 */
class ShizukuReturnHandler(
    context: Context,
    gateway: ShizukuGateway,
    private val config: ShizukuReturnConfig
) {

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val pending = PendingLaunchTracker(config.timeoutMs)
    private var waitingOverlayShown = false

    private val timeoutRunnable = Runnable { clearPending() }
    private val fallbackRunnable = Runnable { postReturnNotification() }
    private val removeBinderListener: () -> Unit = gateway.addBinderReceivedListenerSticky {
        onBinderReceived()
    }

    /** 用户点击“启动 Shizuku”时调用。 */
    fun markLaunched() {
        pending.mark(SystemClock.elapsedRealtime())
        waitingOverlayShown = runCatching { config.showWaitingOverlay() }.getOrDefault(false)
        handler.removeCallbacks(timeoutRunnable)
        handler.postDelayed(timeoutRunnable, config.timeoutMs)
    }

    /** 启动 Shizuku 失败时调用。 */
    fun cancel() {
        clearPending()
    }

    /** 宿主回到前台时调用。 */
    fun onAppResumed() {
        clearPending()
    }

    /** 移除 binder 监听并清理状态（通常进程生命周期结束时无需调用）。 */
    fun release() {
        runCatching { removeBinderListener() }
        clearPending()
    }

    private fun onBinderReceived() {
        val now = SystemClock.elapsedRealtime()
        if (!pending.consume(now)) return
        handler.removeCallbacks(timeoutRunnable)

        if (config.isTargetForeground()) {
            hideWaitingOverlay()
            return
        }

        config.onReturned()

        val intent = Intent(config.targetIntent).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        val started = runCatching { appContext.startActivity(intent) }.isSuccess
        // 后台启动 Activity 的可见悬浮窗豁免在 startActivity 时检查，之后可以移除。
        hideWaitingOverlay()

        if (!started) {
            postReturnNotification()
            return
        }

        handler.removeCallbacks(fallbackRunnable)
        handler.postDelayed(fallbackRunnable, config.fallbackDelayMs)
    }

    private fun clearPending() {
        pending.clear()
        handler.removeCallbacks(timeoutRunnable)
        handler.removeCallbacks(fallbackRunnable)
        hideWaitingOverlay()
        cancelNotification()
    }

    private fun hideWaitingOverlay() {
        if (!waitingOverlayShown) return
        config.hideWaitingOverlay()
        waitingOverlayShown = false
    }

    private fun postReturnNotification() {
        val notification = config.notification ?: return
        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) return

        createNotificationChannel(notification)

        val intent = Intent(config.targetIntent).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            notification.notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(appContext, notification.channelId)
            .setContentTitle(notification.title)
            .setContentText(notification.text)
            .setSmallIcon(notification.smallIconRes)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (notification.allowFullScreenIntent && canUseFullScreenIntent()) {
            builder.setFullScreenIntent(pendingIntent, true)
        }

        runCatching {
            NotificationManagerCompat.from(appContext).notify(
                notification.notificationId,
                builder.build()
            )
        }
    }

    private fun canUseFullScreenIntent(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.canUseFullScreenIntent()
    }

    private fun createNotificationChannel(notification: ReturnNotificationConfig) {
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            notification.channelId,
            notification.channelName,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = notification.channelDescription.toString()
        }
        manager.createNotificationChannel(channel)
    }

    private fun cancelNotification() {
        val notification = config.notification ?: return
        runCatching {
            NotificationManagerCompat.from(appContext).cancel(notification.notificationId)
        }
    }
}
