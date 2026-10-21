package org.caojun.shotocr.shizuku

import android.content.Context
import android.content.Intent
import org.caojun.floatingbutton.FloatingButtonController
import org.caojun.shizuku.RikkaShizukuGateway
import org.caojun.shizuku.autoreturn.ReturnNotificationConfig
import org.caojun.shizuku.autoreturn.ShizukuReturnConfig
import org.caojun.shizuku.autoreturn.ShizukuReturnHandler
import org.caojun.shotocr.MainActivity
import org.caojun.shotocr.R

/**
 * App 侧对 :shizuku-return 的接线：目标 Activity、通知文案、等待悬浮点、权限请求。
 */
object ShizukuReturn {

    private var handler: ShizukuReturnHandler? = null
    private var pendingPermissionRequest = false

    fun init(context: Context) {
        if (handler != null) return
        val app = context.applicationContext
        handler = ShizukuReturnHandler(
            context = app,
            gateway = RikkaShizukuGateway(app),
            config = ShizukuReturnConfig(
                targetIntent = Intent(app, MainActivity::class.java),
                isTargetForeground = { MainActivity.isResumed },
                showWaitingOverlay = { FloatingButtonController.showWaitingOverlay(app) },
                hideWaitingOverlay = { FloatingButtonController.hideWaitingOverlay(app) },
                notification = ReturnNotificationConfig(
                    channelId = "shizuku_return_channel",
                    channelName = app.getString(R.string.shizuku_return_channel_name),
                    channelDescription = app.getString(R.string.shizuku_return_channel_desc),
                    title = app.getString(R.string.shizuku_returned_title),
                    text = app.getString(R.string.shizuku_returned_message),
                    smallIconRes = android.R.drawable.ic_menu_camera
                ),
                onReturned = { pendingPermissionRequest = true }
            )
        )
    }

    fun markLaunched() {
        handler?.markLaunched()
    }

    fun cancel() {
        handler?.cancel()
    }

    fun onAppResumed() {
        handler?.onAppResumed()
    }

    fun consumeAutoRequestPermission(): Boolean {
        val value = pendingPermissionRequest
        pendingPermissionRequest = false
        return value
    }
}
