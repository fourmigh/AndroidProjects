package org.caojun.floatingbutton

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import android.provider.Settings

/**
 * 悬浮按钮的进程级控制器。
 * 仅使用普通绑定服务，不启动前台服务、不发送常驻通知；
 * 进程被系统回收时按钮随之消失。
 */
object FloatingButtonController {

    private var options: FloatingButtonOptions? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            bound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
        }
    }

    fun isPermissionGranted(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun permissionIntent(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
    )

    fun isEnabled(context: Context): Boolean = FloatingButtonSettings(context).enabled

    fun setEnabled(context: Context, enabled: Boolean) {
        FloatingButtonSettings(context).enabled = enabled
    }

    /** 根据开关与悬浮窗权限绑定/解绑服务；[options] 在进程内保持有效。 */
    fun sync(context: Context, options: FloatingButtonOptions) {
        this.options = options
        val app = context.applicationContext
        val shouldShow = FloatingButtonSettings(app).enabled && Settings.canDrawOverlays(app)
        if (shouldShow) {
            bind(app)
        } else {
            stop(app)
        }
    }

    /** 截屏完成后可主动恢复按钮。 */
    fun show(context: Context) {
        send(context, FloatingButtonService.ACTION_SHOW)
    }

    fun hide(context: Context) {
        send(context, FloatingButtonService.ACTION_HIDE)
    }

    /**
     * 显示悬浮进度列表；[steps] 为全部步骤名称，[currentIndex] 为当前步骤（从 1 开始）。
     */
    fun showProgress(
        context: Context,
        steps: List<String>,
        currentIndex: Int,
        fraction: Float? = null
    ) {
        sendProgress(
            context,
            FloatingButtonService.ACTION_PROGRESS_SHOW,
            ArrayList(steps),
            currentIndex,
            fraction
        )
    }

    fun updateProgress(context: Context, currentIndex: Int, fraction: Float? = null) {
        sendProgress(
            context,
            FloatingButtonService.ACTION_PROGRESS_UPDATE,
            null,
            currentIndex,
            fraction
        )
    }

    /** 处理完成：冻结耗时并短暂停留后自动隐藏。 */
    fun finishProgress(context: Context) {
        send(context, FloatingButtonService.ACTION_PROGRESS_FINISH)
    }

    /** 立即移除进度列表（新截屏开始前调用，避免出现在截图里）。 */
    fun clearProgress(context: Context) {
        send(context, FloatingButtonService.ACTION_PROGRESS_CLEAR)
    }

    fun stop(context: Context) {
        val app = context.applicationContext
        if (bound) {
            runCatching { app.unbindService(connection) }
            bound = false
        }
        runCatching { app.stopService(Intent(app, FloatingButtonService::class.java)) }
    }

    internal fun currentOptions(): FloatingButtonOptions? = options

    private fun bind(context: Context) {
        if (bound) return
        bound = runCatching {
            context.bindService(
                Intent(context, FloatingButtonService::class.java),
                connection,
                Context.BIND_AUTO_CREATE
            )
        }.getOrDefault(false)
    }

    private fun send(context: Context, action: String) {
        runCatching {
            context.sendBroadcast(Intent(action).setPackage(context.packageName))
        }
    }

    private fun sendProgress(
        context: Context,
        action: String,
        steps: ArrayList<String>?,
        currentIndex: Int,
        fraction: Float?
    ) {
        runCatching {
            val intent = Intent(action)
                .setPackage(context.packageName)
                .putExtra(FloatingButtonService.EXTRA_CURRENT_INDEX, currentIndex)
                .putExtra(FloatingButtonService.EXTRA_FRACTION, fraction ?: -1f)
            if (steps != null) {
                intent.putStringArrayListExtra(FloatingButtonService.EXTRA_STEPS, steps)
            }
            context.sendBroadcast(intent)
        }
    }
}
