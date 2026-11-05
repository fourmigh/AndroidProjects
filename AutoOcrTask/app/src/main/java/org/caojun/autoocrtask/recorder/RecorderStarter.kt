package org.caojun.autoocrtask.recorder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import org.caojun.autoocrtask.data.AutomationStep
import org.caojun.autoocrtask.data.LaunchSpec

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
    ): Boolean {
        if (!isPermissionGranted(context)) return false
        if (packageName.isBlank()) return false

        RecorderSession.begin(
            context = context,
            taskId = taskId,
            name = name,
            packageName = packageName,
            launch = launch,
            existingSteps = existingSteps
        )

        return runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, RecorderOverlayService::class.java)
            )
        }.isSuccess
    }
}
