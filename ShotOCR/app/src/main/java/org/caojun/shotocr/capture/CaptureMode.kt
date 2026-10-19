package org.caojun.shotocr.capture

/** 当前实际使用的截屏方式。 */
enum class CaptureMode { SHIZUKU, FALLBACK }

/** 用户偏好：自动（优先 Shizuku）/ 仅 Shizuku / 仅现有。 */
enum class CapturePreference(val id: String) {
    AUTO("auto"),
    SHIZUKU("shizuku"),
    FALLBACK("fallback");

    companion object {
        fun fromId(id: String?): CapturePreference = entries.firstOrNull { it.id == id } ?: AUTO
    }
}
