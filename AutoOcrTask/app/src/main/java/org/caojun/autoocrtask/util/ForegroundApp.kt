package org.caojun.autoocrtask.util

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process

object ForegroundApp {

    private val ignoredPackages = setOf(
        "com.android.systemui",
        "android"
    )

    fun isUsageAccessGranted(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun currentPackage(context: Context): String? {
        if (!isUsageAccessGranted(context)) return null

        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE)
            as? UsageStatsManager ?: return null
        val now = System.currentTimeMillis()
        val begin = now - 10 * 60_000L
        val events = manager.queryEvents(begin, now) ?: return null

        val event = UsageEvents.Event()
        var packageName: String? = null
        var lastTime = 0L

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val isForeground = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            } else {
                @Suppress("DEPRECATION")
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            }
            if (!isForeground) continue

            val pkg = event.packageName ?: continue
            if (pkg == context.packageName) continue
            if (pkg in ignoredPackages) continue

            if (event.timeStamp >= lastTime) {
                lastTime = event.timeStamp
                packageName = pkg
            }
        }
        return packageName
    }
}
