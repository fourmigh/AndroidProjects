package org.caojun.shotocr.batch

/** 批量识别时过滤收入/退款；没有正号且没有收入标签的正数金额不直接判定为收入。 */
object IncomeFilter {

    private val POSITIVE_MONEY = Regex(
        """(?:^|[^\d])[+＋]\s*[¥￥]?\s*\d+(?:,\d{3})*\.\d{1,2}(?!\d)"""
    )
    private val INCOME_LABELS = listOf(
        "收入金额",
        "收款成功",
        "到账",
        "退款成功",
        "退款到账",
        "已退款",
        "退款金额"
    )

    fun isIncome(text: String): Boolean {
        if (text.isBlank()) return false
        // 浮动栏状态里也有“收入”字样，不能参与详情页收入判断。
        val content = text.lines()
            .filterNot { it.contains("批量识别") || it.contains("已处理") }
            .joinToString("\n")
        if (POSITIVE_MONEY.containsMatchIn(content)) return true
        return INCOME_LABELS.any(content::contains)
    }
}
