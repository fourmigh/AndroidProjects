package org.caojun.autoocrtask.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

object AutomationStarter {

    fun start(context: Context, taskId: String): Boolean = runCatching {
        ContextCompat.startForegroundService(
            context,
            Intent(context, AutomationService::class.java)
                .putExtra(AutomationService.EXTRA_TASK_ID, taskId)
        )
    }.isSuccess
}
