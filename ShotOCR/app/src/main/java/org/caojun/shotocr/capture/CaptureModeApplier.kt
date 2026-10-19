package org.caojun.shotocr.capture

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import org.caojun.floatingbutton.FloatingButtonController
import org.caojun.floatingbutton.FloatingButtonOptions
import org.caojun.shotocr.CaptureProcessService
import org.caojun.shotocr.R
import org.caojun.shotocr.ScreenshotService

/** 按当前解析出的模式启动或停止监听服务/悬浮按钮。Shizuku 模式不保留常驻前台通知。 */
fun applyCaptureMode(context: Context) {
    when (CaptureModeStore.create(context).resolve()) {
        CaptureMode.SHIZUKU -> {
            context.stopService(Intent(context, ScreenshotService::class.java))
            syncFloatingButton(context)
            Log.d(TAG, "Shizuku mode: listening service stopped")
        }
        CaptureMode.FALLBACK -> {
            FloatingButtonController.stop(context)
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, ScreenshotService::class.java)
                )
            }.onFailure { Log.w(TAG, "start listening service failed", it) }
        }
    }
}

private fun syncFloatingButton(context: Context) {
    FloatingButtonController.sync(
        context,
        FloatingButtonOptions(
            action = Intent(context, CaptureProcessService::class.java),
            contentDescription = context.getString(R.string.floating_button_content_desc)
        )
    )
}

private const val TAG = "ShotOCR/CaptureMode"
