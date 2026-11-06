package org.caojun.ai.llm.api

/** LLM provider 的公开契约常量。 */
object LlmApi {

    const val API_VERSION = 1
    const val MIN_SUPPORTED_API_VERSION = 1

    const val PACKAGE = "org.caojun.ai.llm"
    const val SERVICE_CLASS = "org.caojun.ai.llm.LlmService"
    const val ACTIVITY_CLASS = "org.caojun.ai.llm.MainActivity"

    const val PERMISSION = "org.caojun.ai.llm.permission.USE_SERVICE"

    const val ERROR_OK = 0
    const val ERROR_UNAUTHORIZED = 1
    const val ERROR_NOT_READY = 2
    const val ERROR_INVALID_INPUT = 3
    const val ERROR_BUSY = 4
    const val ERROR_INFERENCE = 5
    const val ERROR_UNSUPPORTED = 6
}

class LlmException(val code: Int, message: String? = null) :
    Exception(message ?: "llm error ($code)")
