package org.caojun.shotocr.capture

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import org.caojun.floatingbutton.FloatingButtonController
import org.caojun.floatingbutton.FloatingButtonOptions
import org.caojun.shotocr.CaptureProcessService
import org.caojun.shotocr.R
import org.caojun.shotocr.ScreenshotService

/** 按用户选择的模式启停监听服务/悬浮按钮；Shizuku 模式绝不启动监听。 */
fun applyCaptureMode(context: Context) {
    when (CaptureModeStore.create(context).resolve()) {
        CaptureMode.SHIZUKU -> {
            context.stopService(Intent(context, ScreenshotService::class.java))
            syncFloatingButton(context)
            Log.d(TAG, "Shizuku mode: listening service stopped")
        }
        CaptureMode.FALLBACK -> {
            FloatingButtonController.stop(context)
            if (hasStoragePermission(context)) {
                runCatching {
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, ScreenshotService::class.java)
                    )
                }.onFailure { Log.w(TAG, "start listening service failed", it) }
            } else {
                context.stopService(Intent(context, ScreenshotService::class.java))
                Log.d(TAG, "Fallback mode: storage permission missing")
            }
        }
        CaptureMode.UNAVAILABLE -> {
            context.stopService(Intent(context, ScreenshotService::class.java))
            FloatingButtonController.stop(context)
            Log.d(TAG, "Shizuku unavailable: no capture service started")
        }
    }
}

fun hasStoragePermission(context: Context): Boolean {
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    return ContextCompat.checkSelfPermission(context, permission) ==
        PackageManager.PERMISSION_GRANTED
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
