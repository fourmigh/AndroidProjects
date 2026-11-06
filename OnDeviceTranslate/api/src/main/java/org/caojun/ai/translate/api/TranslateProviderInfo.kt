package org.caojun.ai.translate.api

import android.app.Activity
import android.content.Context
import android.content.Intent

/** 查询/打开翻译 provider。 */
object TranslateProviderInfo {

    fun isInstalled(context: Context): Boolean = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(TranslateApi.PACKAGE, 0)
    }.isSuccess

    fun openSettings(context: Context): Boolean = runCatching {
        val intent = Intent().setClassName(TranslateApi.PACKAGE, TranslateApi.ACTIVITY_CLASS)
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}
