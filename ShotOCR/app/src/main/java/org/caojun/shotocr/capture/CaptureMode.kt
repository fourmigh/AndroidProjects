package org.caojun.shotocr.capture

/** 当前实际使用的截屏方式；Shizuku 不可用时不再自动回退到监听。 */
enum class CaptureMode { SHIZUKU, FALLBACK, UNAVAILABLE }

/** 用户偏好：Shizuku 截屏 / 仅监听。旧版 auto 迁移为 shizuku。 */
enum class CapturePreference(val id: String) {
    SHIZUKU("shizuku"),
    FALLBACK("fallback");

    companion object {
        fun fromId(id: String?): CapturePreference =
            if (id == FALLBACK.id) FALLBACK else SHIZUKU
    }
}
