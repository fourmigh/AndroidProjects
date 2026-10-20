package org.caojun.shotocr.llm

import java.math.BigDecimal

/**
 * 用 OCR 中明确出现的数值校验模型输出，避免小模型照抄 few-shot 示例。
 * 仅当模型值在 OCR 中找不到对应数值时，才用确定性解析结果纠正。
 */
object ReceiptAmountResolver {

    private val NUMBER = Regex("""\d+(?:\.\d{1,2})?""")
    private val ORDER_AMOUNT_AFTER = Regex("""订单金额\s*[¥￥]?\s*(\d+(?:\.\d{1,2})?)""")
    private val ORDER_AMOUNT_BEFORE = Regex("""[¥￥]?\s*(\d+(?:\.\d{1,2})?)\s*订单金额""")
    private val ORIGINAL_AFTER = Regex(
        """(?:原价|商品金额|订单总额)\s*[：:]?\s*[¥￥]?\s*(\d+(?:\.\d{1,2})?)"""
    )
    private val NEGATIVE = Regex("""-\s*[¥￥]?\s*(\d+(?:\.\d{1,2})?)""")
    private val GARBLED_AMOUNT = Regex("""-\s*\d+\s*-\s*\d{2}""")

    private val DISCOUNT_KEYWORDS = listOf(
        "优惠", "立减", "红包", "补贴", "支付遇财神", "福利", "奖励", "抵扣", "满减", "共减", "共省"
    )

    fun resolve(data: ReceiptData, ocrText: String): ReceiptData {
        val tokens = NUMBER.findAll(ocrText)
            .mapNotNull { parse(it.value) }
            .map { it.stripTrailingZeros() }
            .toSet()

        val originalFromText = extractOriginal(ocrText)
        val discountFromLines = extractDiscount(ocrText)
        val paidFromText = extractPaid(ocrText)

        val resultOriginal = when {
            data.originalAmount != null && isGrounded(data.originalAmount, tokens) ->
                data.originalAmount
            originalFromText != null -> originalFromText.toDouble()
            else -> data.originalAmount
        }

        val resultPaid = when {
            data.amount != null && isGrounded(data.amount, tokens) -> data.amount
            paidFromText != null -> paidFromText.toDouble()
            resultOriginal != null && discountFromLines != null ->
                (BigDecimal.valueOf(resultOriginal) - discountFromLines).toDouble()
            else -> data.amount
        }

        val resultDiscount = when {
            data.discount != null && isGrounded(data.discount, tokens) -> data.discount
            resultOriginal != null && resultPaid != null ->
                (BigDecimal.valueOf(resultOriginal) - BigDecimal.valueOf(resultPaid))
                    .abs()
                    .toDouble()
            discountFromLines != null -> discountFromLines.toDouble()
            else -> data.discount
        }

        return data.copy(
            amount = resultPaid,
            discount = resultDiscount,
            originalAmount = resultOriginal
        )
    }

    private fun extractOriginal(text: String): BigDecimal? =
        parseMatch(ORDER_AMOUNT_AFTER, text)
            ?: parseMatch(ORDER_AMOUNT_BEFORE, text)
            ?: parseMatch(ORIGINAL_AFTER, text)

    private fun extractDiscount(text: String): BigDecimal? {
        var total: BigDecimal? = null
        for (line in text.lines()) {
            if (DISCOUNT_KEYWORDS.none { line.contains(it) }) continue
            val values = NEGATIVE.findAll(line)
                .mapNotNull { parse(it.groupValues[1]) }
                .toList()
            if (values.isEmpty()) continue
            val lineTotal = values.fold(BigDecimal.ZERO, BigDecimal::add)
            total = (total ?: BigDecimal.ZERO).add(lineTotal)
        }
        return total
    }

    private fun extractPaid(text: String): BigDecimal? {
        for (line in text.lines()) {
            if (DISCOUNT_KEYWORDS.any { line.contains(it) }) continue
            if (GARBLED_AMOUNT.containsMatchIn(line)) continue
            val match = NEGATIVE.find(line) ?: continue
            return parse(match.groupValues[1])
        }
        return null
    }

    private fun parseMatch(regex: Regex, text: String): BigDecimal? =
        regex.find(text)?.groupValues?.getOrNull(1)?.let { parse(it) }

    private fun parse(value: String): BigDecimal? =
        value.trim().replace(",", "").toBigDecimalOrNull()

    private fun isGrounded(value: Double, tokens: Set<BigDecimal>): Boolean {
        val target = BigDecimal.valueOf(value).stripTrailingZeros()
        return tokens.any { it.compareTo(target) == 0 }
    }
}
