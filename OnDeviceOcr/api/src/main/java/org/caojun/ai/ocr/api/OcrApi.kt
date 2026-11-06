package org.caojun.ai.ocr.api

/** OCR provider 的公开契约常量。 */
object OcrApi {

    /** API 版本；provider 升级不兼容时递增。 */
    const val API_VERSION = 1

    /** 客户端支持的最低 provider API 版本。 */
    const val MIN_SUPPORTED_API_VERSION = 1

    const val PACKAGE = "org.caojun.ai.ocr"
    const val SERVICE_CLASS = "org.caojun.ai.ocr.OcrService"
    const val ACTIVITY_CLASS = "org.caojun.ai.ocr.MainActivity"

    /** normal 权限：客户端在 manifest 中声明后自动授予，不校验签名。 */
    const val PERMISSION = "org.caojun.ai.ocr.permission.USE_SERVICE"

    const val ERROR_OK = 0
    const val ERROR_UNAUTHORIZED = 1
    const val ERROR_NOT_READY = 2
    const val ERROR_INVALID_INPUT = 3
    const val ERROR_BUSY = 4
    const val ERROR_INFERENCE = 5
    const val ERROR_UNSUPPORTED = 6

    const val FORMAT_PNG = "png"
    const val FORMAT_JPEG = "jpeg"
}
