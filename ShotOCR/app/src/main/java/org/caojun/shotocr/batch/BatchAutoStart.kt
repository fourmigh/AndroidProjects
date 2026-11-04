package org.caojun.shotocr.batch

import android.content.Context
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.time.YearMonth
import org.caojun.shotocr.R
import org.caojun.shotocr.capture.applyCaptureMode

object BatchAutoStart {

    /** 启动批量服务后立即打开支付宝账单页；失败时不留下待命状态。 */
    fun start(context: Context, yearMonth: YearMonth): Boolean {
        if (!AlipayLauncher.isInstalled(context)) {
            Toast.makeText(context, R.string.batch_capture_alipay_missing, Toast.LENGTH_LONG).show()
            return false
        }
        if (!BatchControlOverlayController.isPermissionGranted(context)) {
            BatchDiagnostics.warn("auto_start_failed", "reason" to "NO_OVERLAY_PERMISSION")
            Toast.makeText(context, R.string.batch_control_permission, Toast.LENGTH_LONG).show()
            runCatching {
                context.startActivity(
                    BatchControlOverlayController.permissionIntent(context)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return false
        }

        BatchModeController.arm(yearMonth)
        applyCaptureMode(context)
        val started = runCatching {
            ContextCompat.startForegroundService(
                context,
                android.content.Intent(context, BatchCaptureService::class.java)
            )
        }.isSuccess
        if (!started) {
            BatchModeController.cancel()
            BatchDiagnostics.warn("auto_start_failed", "reason" to "FOREGROUND_SERVICE")
            Toast.makeText(context, R.string.batch_capture_launch_failed, Toast.LENGTH_LONG).show()
            return false
        }

        BatchDiagnostics.log("auto_start", "target" to yearMonth)
        if (!AlipayLauncher.launch(context)) {
            context.stopService(android.content.Intent(context, BatchCaptureService::class.java))
            BatchModeController.cancel()
            Toast.makeText(context, R.string.batch_capture_launch_failed, Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }
}
