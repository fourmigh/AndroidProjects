package org.caojun.autoocrtask.automation

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import org.caojun.autoocrtask.data.AutomationTask
import org.caojun.autoocrtask.data.LaunchType

object AutomationLauncher {

    fun isInstalled(context: Context, packageName: String): Boolean = runCatching {
        context.packageManager.getPackageInfo(packageName, 0)
    }.isSuccess

    fun launch(context: Context, task: AutomationTask): Boolean {
        val pm = context.packageManager
        val intent = when (task.launch.type) {
            LaunchType.HOME -> pm.getLaunchIntentForPackage(task.packageName)

            LaunchType.ACTIVITY -> {
                val activity = task.launch.activity.trim()
                if (activity.isEmpty()) return false
                val className = if (activity.startsWith(".")) {
                    task.packageName + activity
                } else {
                    activity
                }
                Intent().setComponent(ComponentName(task.packageName, className))
            }

            LaunchType.URI -> {
                val uri = task.launch.uri.trim()
                if (uri.isEmpty()) return false
                Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage(task.packageName)
            }
        } ?: return false

        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        )
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    fun resolveLaunchActivity(context: Context, packageName: String): Intent? =
        context.packageManager.getLaunchIntentForPackage(packageName)

    fun queryLaunchablePackages(context: Context): List<Pair<String, String>> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val infos = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(
                mainIntent,
                PackageManager.ResolveInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }
        return infos
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                val label = info.loadLabel(pm).toString()
                pkg to label
            }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
    }
}
