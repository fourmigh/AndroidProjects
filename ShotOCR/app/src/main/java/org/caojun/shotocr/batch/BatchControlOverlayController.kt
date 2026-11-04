package org.caojun.shotocr.batch

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** 批量识别顶部控制条的进程内控制器。 */
object BatchControlOverlayController {

    internal const val ACTION_START = "org.caojun.shotocr.batch.control.START"
    internal const val ACTION_UPDATE = "org.caojun.shotocr.batch.control.UPDATE"
    internal const val ACTION_HIDE_FOR_CAPTURE = "org.caojun.shotocr.batch.control.HIDE_CAPTURE"
    internal const val ACTION_SHOW_AFTER_CAPTURE = "org.caojun.shotocr.batch.control.SHOW_CAPTURE"
    internal const val ACTION_REMOVE = "org.caojun.shotocr.batch.control.REMOVE"

    internal const val EXTRA_CURRENT = "current"
    internal const val EXTRA_SUCCESS = "success"
    internal const val EXTRA_FAILED = "failed"
    internal const val EXTRA_DUPLICATE = "duplicate"
    internal const val EXTRA_INCOME = "income"
    internal const val EXTRA_SKIPPED = "skipped"
    internal const val EXTRA_PAUSED = "paused"
    internal const val EXTRA_ORDER_WAITING = "order_waiting"

    fun isPermissionGranted(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun permissionIntent(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
    )

    fun start(
        context: Context,
        current: Int,
        success: Int,
        failed: Int,
        paused: Boolean,
        orderWaiting: Boolean = false,
        duplicate: Int = 0,
        income: Int = 0,
        skipped: Int = 0
    ): Boolean {
        if (!isPermissionGranted(context)) return false
        val intent = Intent(context, BatchControlOverlayService::class.java)
            .setAction(ACTION_START)
            .putExtra(EXTRA_CURRENT, current)
            .putExtra(EXTRA_SUCCESS, success)
            .putExtra(EXTRA_FAILED, failed)
            .putExtra(EXTRA_DUPLICATE, duplicate)
            .putExtra(EXTRA_INCOME, income)
            .putExtra(EXTRA_SKIPPED, skipped)
            .putExtra(EXTRA_PAUSED, paused)
            .putExtra(EXTRA_ORDER_WAITING, orderWaiting)
        return runCatching { context.startService(intent) }.isSuccess
    }

    fun update(
        context: Context,
        current: Int,
        success: Int,
        failed: Int,
        paused: Boolean,
        orderWaiting: Boolean,
        duplicate: Int,
        income: Int,
        skipped: Int
    ) {
        send(
            context,
            Intent(ACTION_UPDATE)
                .putExtra(EXTRA_CURRENT, current)
                .putExtra(EXTRA_SUCCESS, success)
                .putExtra(EXTRA_FAILED, failed)
                .putExtra(EXTRA_DUPLICATE, duplicate)
                .putExtra(EXTRA_INCOME, income)
                .putExtra(EXTRA_SKIPPED, skipped)
                .putExtra(EXTRA_PAUSED, paused)
                .putExtra(EXTRA_ORDER_WAITING, orderWaiting)
        )
    }

    fun hideForCapture(context: Context) {
        send(context, Intent(ACTION_HIDE_FOR_CAPTURE))
    }

    fun showAfterCapture(context: Context) {
        send(context, Intent(ACTION_SHOW_AFTER_CAPTURE))
    }

    fun stop(context: Context) {
        runCatching {
            context.stopService(Intent(context, BatchControlOverlayService::class.java))
        }
    }

    private fun send(context: Context, intent: Intent) {
        runCatching {
            context.sendBroadcast(intent.setPackage(context.packageName))
        }
    }
}
