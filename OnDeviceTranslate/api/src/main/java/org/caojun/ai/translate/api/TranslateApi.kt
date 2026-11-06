package org.caojun.ai.translate.api

/** 翻译 provider 的公开契约常量。 */
object TranslateApi {

    const val API_VERSION = 1
    const val MIN_SUPPORTED_API_VERSION = 1

    const val PACKAGE = "org.caojun.ai.translate"
    const val SERVICE_CLASS = "org.caojun.ai.translate.TranslateService"
    const val ACTIVITY_CLASS = "org.caojun.ai.translate.MainActivity"

    const val PERMISSION = "org.caojun.ai.translate.permission.USE_SERVICE"

    const val LANG_ZH = "zh"
    const val LANG_EN = "en"

    /** provider 支持的完整方向，格式如 "zh-en"。 */
    const val DIRECTION_ZH_EN = "zh-en"
    const val DIRECTION_EN_ZH = "en-zh"

    const val ERROR_OK = 0
    const val ERROR_UNAUTHORIZED = 1
    const val ERROR_NOT_READY = 2
    const val ERROR_INVALID_INPUT = 3
    const val ERROR_BUSY = 4
    const val ERROR_INFERENCE = 5
    const val ERROR_UNSUPPORTED = 6

    fun directionOf(from: String, to: String): String? = when {
        from == LANG_ZH && to == LANG_EN -> DIRECTION_ZH_EN
        from == LANG_EN && to == LANG_ZH -> DIRECTION_EN_ZH
        else -> null
    }
}

class TranslateException(val code: Int, message: String? = null) :
    Exception(message ?: "translate error ($code)")
