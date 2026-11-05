package org.caojun.autoocrtask.recorder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import org.caojun.autoocrtask.data.AutomationStep
import org.caojun.autoocrtask.data.LaunchSpec
import org.caojun.shizuku.RikkaShizukuGateway

enum class RecorderStartResult {
    OK,
    NO_OVERLAY_PERMISSION,
    SHIZUKU_NOT_RUNNING,
    SHIZUKU_NOT_GRANTED,
    PACKAGE_MISSING,
    SERVICE_FAILED
}

object RecorderStarter {

    fun isPermissionGranted(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    fun permissionIntent(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
    )

    fun start(
        context: Context,
        taskId: String?,
        name: String,
        packageName: String,
        launch: LaunchSpec,
        existingSteps: List<AutomationStep>
    ): RecorderStartResult {
        if (!isPermissionGranted(context)) {
            return RecorderStartResult.NO_OVERLAY_PERMISSION
        }
        if (packageName.isBlank()) {
            return RecorderStartResult.PACKAGE_MISSING
        }

        val gateway = RikkaShizukuGateway(context.applicationContext)
        if (!gateway.isRunning()) {
            return RecorderStartResult.SHIZUKU_NOT_RUNNING
        }
        if (!gateway.isPermissionGranted()) {
            return RecorderStartResult.SHIZUKU_NOT_GRANTED
        }

        RecorderSession.begin(
            context = context,
            taskId = taskId,
            name = name,
            packageName = packageName,
            launch = launch,
            existingSteps = existingSteps
        )

        val started = runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, RecorderOverlayService::class.java)
            )
        }.isSuccess

        return if (started) RecorderStartResult.OK else RecorderStartResult.SERVICE_FAILED
    }
}
