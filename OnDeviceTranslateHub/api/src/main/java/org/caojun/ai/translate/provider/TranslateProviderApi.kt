package org.caojun.ai.translate.provider

/** 翻译 provider 契约常量。 */
object TranslateProviderApi {

    const val API_VERSION = 1
    const val MIN_SUPPORTED_API_VERSION = 1

    /** provider 服务统一声明的 action，客户端据此枚举（无需知道包名）。 */
    const val ACTION_PROVIDER = "org.caojun.ai.translate.provider.TRANSLATE"

    /** 本工程（模型商店 / 多模型 provider）。 */
    const val PACKAGE = "org.caojun.ai.translate.hub"
    const val SERVICE_CLASS = "org.caojun.ai.translate.hub.HubTranslateService"

    const val ERROR_OK = 0
    const val ERROR_UNAUTHORIZED = 1
    const val ERROR_NOT_READY = 2
    const val ERROR_INVALID_INPUT = 3
    const val ERROR_BUSY = 4
    const val ERROR_INFERENCE = 5
    const val ERROR_UNSUPPORTED = 6
    const val ERROR_MODEL_MISSING = 7

    /** 路由策略。 */
    enum class Policy { AUTO, QUALITY, SPEED }
}

class TranslateProviderException(
    val code: Int,
    message: String? = null,
) : Exception(message ?: "translate provider error ($code)")
