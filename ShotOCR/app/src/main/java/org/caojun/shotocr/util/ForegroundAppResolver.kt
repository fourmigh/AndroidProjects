package org.caojun.shotocr.util

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.util.Log

object ForegroundAppResolver {

    private const val TAG = "ShotOCR/ForegroundApp"

    private val ignoredPackages = setOf(
        "com.android.systemui",
        "android"
    )

    private val packageAppNames = mapOf(
        "com.eg.android.AlipayGphone" to "支付宝",
        "com.tencent.mm" to "微信",
        "com.unionpay" to "云闪付",
        "com.unionpay.tsmservice" to "云闪付",
        "com.unionpay.mobilepayment" to "云闪付"
    )

    fun isUsageAccessGranted(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
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

    fun getForegroundPackage(context: Context, aroundTime: Long): String? {
        if (!isUsageAccessGranted(context)) return null

        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val begin = aroundTime - 10 * 60_000L
        val end = minOf(aroundTime + 2_000L, System.currentTimeMillis())
        if (begin >= end) {
            Log.w(TAG, "getForegroundPackage invalid window: aroundTime=$aroundTime, begin=$begin, end=$end")
            return null
        }
        val events = usageStatsManager.queryEvents(begin, end) ?: return null

        val event = UsageEvents.Event()
        var packageName: String? = null
        var lastTime = 0L
        var foregroundEventCount = 0

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val isForeground = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            } else {
                @Suppress("DEPRECATION")
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            }
            if (!isForeground) continue
            foregroundEventCount++

            val pkg = event.packageName ?: continue
            if (pkg == context.packageName) continue
            if (pkg in ignoredPackages) continue

            if (event.timeStamp >= lastTime) {
                lastTime = event.timeStamp
                packageName = pkg
            }
        }

        Log.d(TAG, "getForegroundPackage around=$aroundTime window=[$begin, $end] foregroundEvents=$foregroundEventCount -> $packageName")
        return packageName
    }

    fun getAppLabel(context: Context, packageName: String): String? {
        packageAppNames[packageName]?.let { name ->
            Log.d(TAG, "getAppLabel $packageName -> $name (builtin)")
            return name
        }
        return try {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            val label = pm.getApplicationLabel(info).toString()
            Log.d(TAG, "getAppLabel $packageName -> $label (resolved)")
            label
        } catch (e: Exception) {
            Log.w(TAG, "getAppLabel failed for $packageName, fallback to package name", e)
            packageName
        }
    }
}
