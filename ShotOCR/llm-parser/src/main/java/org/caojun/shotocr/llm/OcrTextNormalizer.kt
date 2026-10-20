package org.caojun.shotocr.llm

/**
 * 送给大模型前的 OCR 文本清洗：
 * - 去掉账单锚点之前的无关噪声（状态栏、其他浮层等）；
 * - 把日期与时间之间缺失的空格补上，便于模型识别支付时间。
 */
object OcrTextNormalizer {

    private val ANCHORS = listOf(
        "账单详情",
        "全部账单",
        "云闪付交易详情",
        "交易详情",
        "订单详情",
        "订单信息",
        "充值成功",
        "支付成功",
        "支付结果"
    )

    private val DATE_TIME_GAP = Regex(
        """(\d{4}\s*[-/.]\s*\d{1,2}\s*[-/.]\s*)(\d{1,2}?)\s*(\d{1,2}:\d{2}(?::\d{2})?)"""
    )
    private val CN_DATE_TIME_GAP = Regex(
        """(\d{4}\s*年\s*\d{1,2}\s*月\s*)(\d{1,2}?)\s*日\s*(\d{1,2}:\d{2}(?::\d{2})?)"""
    )

    fun normalize(raw: String): String {
        var text = trimBeforeAnchor(raw).replace('T', ' ')
        text = DATE_TIME_GAP.replace(text) { match ->
            "${match.groupValues[1]}${match.groupValues[2]} ${match.groupValues[3]}"
        }
        text = CN_DATE_TIME_GAP.replace(text) { match ->
            "${match.groupValues[1]}${match.groupValues[2]}日 ${match.groupValues[3]}"
        }
        return text
    }

    private fun trimBeforeAnchor(text: String): String {
        val index = ANCHORS
            .map { text.indexOf(it) }
            .filter { it >= 0 }
            .minOrNull()
            ?: return text
        return text.substring(index)
    }
}
