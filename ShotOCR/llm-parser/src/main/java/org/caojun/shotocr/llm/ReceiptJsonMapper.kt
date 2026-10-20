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
            paymentTime = normalizePaymentTime(toStringOrNull(obj.opt("paymentTime"))),
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

    /**
     * 统一支付时间格式：把日期与时间之间缺失的空格补上。
     * 例如 2026-09-1410:00:00 / 2026-09-14T10:00:00 -> 2026-09-14 10:00:00。
     */
    internal fun normalizePaymentTime(raw: String?): String? {
        val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        var normalized = text.replace('T', ' ')
        normalized = DATE_TIME_GAP.replace(normalized) { match ->
            "${match.groupValues[1]}${match.groupValues[2]} ${match.groupValues[3]}"
        }
        normalized = CN_DATE_TIME_GAP.replace(normalized) { match ->
            "${match.groupValues[1]}${match.groupValues[2]}日 ${match.groupValues[3]}"
        }
        return normalized.replace(WHITESPACE, " ").trim()
    }

    private val DATE_TIME_GAP = Regex(
        """(\d{4}\s*[-/.]\s*\d{1,2}\s*[-/.]\s*)(\d{1,2}?)\s*(\d{1,2}:\d{2})"""
    )
    private val CN_DATE_TIME_GAP = Regex(
        """(\d{4}\s*年\s*\d{1,2}\s*月\s*)(\d{1,2}?)\s*日\s*(\d{1,2}:\d{2})"""
    )
    private val WHITESPACE = Regex("""\s+""")
}
