package org.caojun.shotocr.llm

/** 内置账单平台。GENERIC 为无法判定时的通用提示词。 */
enum class Platform(val id: String) {
    ALIPAY("alipay"),
    WECHAT("wechat"),
    UNIONPAY("unionpay"),
    TAOBAO("taobao"),
    JD("jd"),
    DOUYIN("douyin"),
    GENERIC("generic");

    companion object {
        fun fromId(id: String): Platform? = entries.firstOrNull { it.id == id }
    }
}
