package org.caojun.ai.ocr.api

import android.app.Activity
import android.content.Context
import android.content.Intent

/** 查询/打开 OCR provider。 */
object OcrProviderInfo {

    fun isInstalled(context: Context): Boolean = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(OcrApi.PACKAGE, 0)
    }.isSuccess

    fun openSettings(context: Context): Boolean = runCatching {
        val intent = Intent().setClassName(OcrApi.PACKAGE, OcrApi.ACTIVITY_CLASS)
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}
