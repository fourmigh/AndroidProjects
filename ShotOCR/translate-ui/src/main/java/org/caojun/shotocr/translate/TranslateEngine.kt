package org.caojun.shotocr.translate

import android.content.Context
import org.caojun.ai.translate.api.TranslateClient

/**
 * 业务侧的翻译入口。
 *
 * 真正的离线翻译模型在独立的「离线翻译服务」App 里，这里保持最小兼容门面，
 * 让 translate-ui 等既有调用方不需要感知跨进程细节。
 */
object TranslateEngine {

    const val LANG_ZH = "zh"
    const val LANG_EN = "en"

    @Volatile
    private var appContext: Context? = null

    /** 可在任意位置重复调用；只需在首次翻译前调用一次即可。 */
    fun initialize(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    suspend fun translate(text: String, from: String, to: String): String {
        if (text.isBlank()) return ""
        val context = appContext
            ?: error("TranslateEngine.initialize(context) has not been called")
        return TranslateClient(context).translate(text, from, to)
    }
}
