package org.caojun.shotocr.translate

import android.content.Context
import org.caojun.ai.translate.api.TranslateClient
import org.caojun.ai.translate.provider.LanguageCatalog
import org.caojun.ai.translate.provider.TranslateProviderApi
import org.caojun.ai.translate.provider.TranslateRouter

/**
 * 业务侧的翻译入口。
 *
 * 路由策略：
 * 1. 中英（简体）优先走内置的 `OnDeviceTranslate`（Marian）：零下载、几十毫秒完成
 * 2. 其他语种 / 繁体 / 粤语 / 民汉 → 走多模型 provider（`OnDeviceTranslateHub`）
 * 3. Hub 不可用时，中英回退到 Marian
 *
 * 真正的模型都在独立 App 里，这里只做选择，不感知跨进程细节。
 */
object TranslateEngine {

    const val LANG_ZH = "zh"
    const val LANG_EN = "en"

    /** 内置 Marian provider 能处理的规范语种码。 */
    private val BUILTIN_LANGS = setOf("zh-Hans", "en")

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
        val context = appContext ?: error("TranslateEngine.initialize(context) has not been called")

        val src = LanguageCatalog.normalize(from) ?: from
        val dst = LanguageCatalog.normalize(to) ?: to

        val builtinCapable = src in BUILTIN_LANGS && dst in BUILTIN_LANGS && src != dst

        if (builtinCapable) {
            try {
                return TranslateClient(context).translate(text, legacyCode(src), legacyCode(dst))
            } catch (_: Throwable) {
                // 内置 provider 不可用，继续尝试 Hub
            }
        }

        return try {
            TranslateRouter(context).translate(text, src, dst).text
        } catch (error: Throwable) {
            // Hub 未安装 / 没有可用模型时，中英仍回退到内置 Marian
            if (builtinCapable) {
                TranslateClient(context).translate(text, legacyCode(src), legacyCode(dst))
            } else {
                throw error
            }
        }
    }

    /** Hub 已安装且支持某方向（供 UI 判断是否提示「去下载模型」）。 */
    suspend fun canTranslate(from: String, to: String): Boolean {
        val context = appContext ?: return false
        val src = LanguageCatalog.normalize(from) ?: return false
        val dst = LanguageCatalog.normalize(to) ?: return false
        if (src in BUILTIN_LANGS && dst in BUILTIN_LANGS && src != dst) return true
        return TranslateRouter(context).canTranslate(src, dst)
    }

    /** 打开模型管理页（模型缺失时引导用户下载）。 */
    fun openModelManager(context: Context): Boolean = runCatching {
        val intent = context.packageManager
            .getLaunchIntentForPackage(TranslateProviderApi.PACKAGE)
            ?: return false
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    private fun legacyCode(normalized: String): String = when (normalized) {
        "zh-Hans", "zh-Hant" -> LANG_ZH
        "en" -> LANG_EN
        else -> normalized
    }
}
