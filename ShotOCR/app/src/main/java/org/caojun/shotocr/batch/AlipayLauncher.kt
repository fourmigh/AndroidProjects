package org.caojun.shotocr.batch

import android.content.Context
import android.content.Intent

object AlipayLauncher {

    fun isInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(AlipayBatchProfile.ALIPAY_PACKAGE, 0)
    }.isSuccess

    /** 批量固定从支付宝首页开始，再走“我的 -> 账单 -> 订单”。 */
    fun launch(context: Context): Boolean {
        val home = context.packageManager
            .getLaunchIntentForPackage(AlipayBatchProfile.ALIPAY_PACKAGE)
            ?.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            )
            ?: run {
                BatchDiagnostics.warn("alipay_launch_failed", "reason" to "NO_LAUNCH_INTENT")
                return false
            }
        val result = runCatching { context.startActivity(home) }
        result.exceptionOrNull()?.let {
            BatchDiagnostics.error("alipay_home_failed", it)
        }
        BatchDiagnostics.log("alipay_home", "ok" to result.isSuccess)
        return result.isSuccess
    }
}
