package org.caojun.shotocr.parser

class ReceiptParser : DataParser<ReceiptData> {

    override fun parse(text: String): ReceiptData {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

        val amount = extractAmount(lines)
        val discount = extractDiscount(lines)
        val originalAmount = if (amount != null && discount != null) amount + discount else null
        val items = extractItems(lines)

        return ReceiptData(
            amount = amount,
            discount = discount,
            originalAmount = originalAmount,
            items = items,
            rawText = text
        )
    }

    private fun extractAmount(lines: List<String>): Double? {
        val patterns = listOf(
            Regex("""(?:实付|实收|应付|合计|总计|总额|金额|支付)[：:\s]*[¥￥]?\s*(\d+\.?\d*)"""),
            Regex("""[¥￥]\s*(\d+\.?\d*)"""),
            Regex("""(\d+\.?\d*)\s*元"""),
        )
        for (line in lines) {
            for (pattern in patterns) {
                val match = pattern.find(line)
                if (match != null) {
                    return match.groupValues[1].toDoubleOrNull()
                }
            }
        }
        return null
    }

    private fun extractDiscount(lines: List<String>): Double? {
        val patterns = listOf(
            Regex("""(?:优惠|减免|折扣|立减|满减)[：:\s]*[¥￥]?\s*(\d+\.?\d*)"""),
            Regex("""-[¥￥]?\s*(\d+\.?\d*)"""),
            Regex("""(?:节省|省)[：:\s]*[¥￥]?\s*(\d+\.?\d*)"""),
        )
        for (line in lines) {
            for (pattern in patterns) {
                val match = pattern.find(line)
                if (match != null) {
                    return match.groupValues[1].toDoubleOrNull()
                }
            }
        }
        return null
    }

    private fun extractItems(lines: List<String>): List<ReceiptItem> {
        val items = mutableListOf<ReceiptItem>()
        val itemPattern = Regex("""(.+?)\s+[¥￥]?\s*(\d+\.?\d*)\s*[xX×]\s*(\d+)""")

        for (line in lines) {
            val match = itemPattern.find(line)
            if (match != null) {
                val name = match.groupValues[1].trim()
                val price = match.groupValues[2].toDoubleOrNull()
                val quantity = match.groupValues[3].toIntOrNull()
                val subtotal = if (price != null && quantity != null) price * quantity else null

                items.add(
                    ReceiptItem(
                        name = name,
                        price = price,
                        quantity = quantity,
                        subtotal = subtotal
                    )
                )
            }
        }

        return items
    }
}
