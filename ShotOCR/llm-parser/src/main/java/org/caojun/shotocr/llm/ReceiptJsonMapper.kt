package org.caojun.shotocr.llm

import org.json.JSONObject

/** 把大模型输出的 JSON 映射为 [ReceiptData]。 */
object ReceiptJsonMapper {

    fun map(rawModelOutput: String, rawText: String): ReceiptData {
        val json = extractJsonObject(rawModelOutput) ?: "{}"
        val obj = runCatching { JSONObject(json) }.getOrElse { JSONObject() }

        return ReceiptData(
            amount = absDouble(obj.opt("amount")),
            discount = absDouble(obj.opt("discount")),
            originalAmount = absDouble(obj.opt("originalAmount")),
            storeName = toStringOrNull(obj.opt("storeName")),
            paymentTime = toStringOrNull(obj.opt("paymentTime")),
            paymentMethod = toStringOrNull(obj.opt("paymentMethod")),
            orderNumber = toStringOrNull(obj.opt("orderNumber")),
            rawText = rawText,
            sourceApp = toStringOrNull(obj.opt("sourceApp"))
        )
    }

    /** 从可能包含 markdown 围栏或多余文字的输出中截取首个 JSON 对象。 */
    fun extractJsonObject(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return text.substring(start, end + 1)
    }

    /** 金额统一取绝对值（避免模型把"-0.03"这类负号带进来）。 */
    private fun absDouble(value: Any?): Double? = toDouble(value)?.let { kotlin.math.abs(it) }

    private fun toDouble(value: Any?): Double? = when (value) {
        null, JSONObject.NULL -> null
        is Number -> value.toDouble()
        is String -> value.trim().replace("¥", "").replace("￥", "").toDoubleOrNull()
        else -> null
    }

    private fun toStringOrNull(value: Any?): String? {
        if (value == null || value == JSONObject.NULL) return null
        val s = value.toString().trim()
        return s.ifEmpty { null }
    }
}
