package org.caojun.shotocr.parser

class ReceiptParser : DataParser<ReceiptData> {

    override fun parse(text: String): ReceiptData {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

        val originalAmount = extractOriginalAmount(lines)
        val amount = extractAmount(lines, originalAmount)
        val discount = extractDiscount(lines)
        val storeName = extractStoreName(lines)
        val paymentTime = extractPaymentTime(lines)
        val paymentMethod = extractPaymentMethod(lines)
        val orderNumber = extractOrderNumber(lines)
        val items = extractItems(lines)

        return ReceiptData(
            amount = amount,
            discount = discount,
            originalAmount = originalAmount,
            storeName = storeName,
            paymentTime = paymentTime,
            paymentMethod = paymentMethod,
            orderNumber = orderNumber,
            items = items,
            rawText = text
        )
    }

    private fun extractOriginalAmount(lines: List<String>): Double? {
        val amountPattern = Regex("""(\d+\.?\d+)""")
        val keywords = listOf("订单金额", "原价", "原价金额", "商品总价", "总价", "合计金额")

        for (i in lines.indices) {
            val line = lines[i]
            val hasKeyword = keywords.any { keyword -> line.contains(keyword) }
            if (hasKeyword) {
                val match = amountPattern.find(line)
                if (match != null) {
                    return match.groupValues[1].toDoubleOrNull()
                }
                for (j in maxOf(0, i - 2)..minOf(lines.size - 1, i + 2)) {
                    if (j == i) continue
                    val adjacentMatch = amountPattern.find(lines[j])
                    if (adjacentMatch != null) {
                        val value = adjacentMatch.groupValues[1].toDoubleOrNull()
                        if (value != null && value > 0) {
                            return value
                        }
                    }
                }
            }
        }

        for (i in lines.indices) {
            if (lines[i].contains("交易成功") && i + 2 < lines.size) {
                val nextLine = lines[i + 1]
                if (nextLine.matches(Regex("""\d+\.?\d+"""))) {
                    return nextLine.toDoubleOrNull()
                }
            }
        }

        return null
    }

    private fun extractAmount(lines: List<String>, originalAmount: Double?): Double? {
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("-") && trimmed.matches(Regex("""^-?\d+\.?\d*$"""))) {
                var value = trimmed.toDoubleOrNull()
                if (value != null && Math.abs(value) > 1) {
                    if (originalAmount != null && !trimmed.contains(".")) {
                        value = inferDecimalPoint(Math.abs(value), originalAmount)
                    }
                    return Math.abs(value)
                }
            }
        }

        val keywords = listOf("实付", "实收", "应付", "支付金额")
        for (i in lines.indices) {
            val line = lines[i]
            val hasKeyword = keywords.any { keyword -> line.contains(keyword) }
            if (hasKeyword) {
                if (line.contains("支付时间") || line.contains("支付方式")) continue
                val match = Regex("""(-?\d+\.?\d+)""").find(line)
                if (match != null) {
                    return Math.abs(match.groupValues[1].toDoubleOrNull() ?: 0.0)
                }
                if (i + 1 < lines.size) {
                    val nextMatch = Regex("""(-?\d+\.?\d+)""").find(lines[i + 1])
                    if (nextMatch != null) {
                        return Math.abs(nextMatch.groupValues[1].toDoubleOrNull() ?: 0.0)
                    }
                }
            }
        }

        return null
    }

    private fun inferDecimalPoint(rawAmount: Double, originalAmount: Double): Double {
        val absAmount = Math.abs(rawAmount)
        if (absAmount > originalAmount * 10 && absAmount < originalAmount * 1000) {
            var divisor = 10.0
            while (divisor <= 1000) {
                val inferred = absAmount / divisor
                if (inferred >= originalAmount * 0.5 && inferred <= originalAmount * 1.5) {
                    return inferred
                }
                divisor *= 10
            }
        }
        return absAmount
    }

    private fun extractDiscount(lines: List<String>): Double? {
        val discountKeywords = listOf("优惠", "减免", "折扣", "立减", "满减", "抵扣", "福利", "财神", "红包", "补贴", "返现")
        val excludeKeywords = listOf("领", "就可用", "去查看", "推荐", "服务")
        val amountPattern = Regex("""(-?\d+\.?\d+)""")
        val datePattern = Regex("""\d{4}[-/]\d{1,2}[-/]\d{1,2}""")
        val processedIndices = mutableSetOf<Int>()
        var totalDiscount = 0.0
        var found = false

        for (i in lines.indices) {
            if (i in processedIndices) continue
            val line = lines[i]
            if (excludeKeywords.any { keyword -> line.contains(keyword) }) continue

            val hasKeyword = discountKeywords.any { keyword -> line.contains(keyword) }
            if (!hasKeyword) continue

            val match = amountPattern.find(line)
            if (match != null) {
                val value = match.groupValues[1].toDoubleOrNull()
                if (value != null && Math.abs(value) > 0) {
                    totalDiscount += Math.abs(value)
                    found = true
                    processedIndices.add(i)
                }
                continue
            }

            for (offset in listOf(-1, 1)) {
                val j = i + offset
                if (j < 0 || j >= lines.size || j in processedIndices) continue
                val adjacentLine = lines[j]
                if (discountKeywords.any { kw -> adjacentLine.contains(kw) }) continue
                if (datePattern.containsMatchIn(adjacentLine)) continue
                val adjMatch = amountPattern.find(adjacentLine)
                if (adjMatch != null) {
                    val value = adjMatch.groupValues[1].toDoubleOrNull()
                    if (value != null && Math.abs(value) > 0) {
                        totalDiscount += Math.abs(value)
                        found = true
                        processedIndices.add(j)
                        break
                    }
                }
            }
        }

        return if (found) Math.round(totalDiscount * 100.0) / 100.0 else null
    }

    private fun extractStoreName(lines: List<String>): String? {
        val excludeKeywords = listOf("账单", "详情", "支付", "订单", "交易", "时间", "方式", "说明", "奖励", "收款", "推荐", "服务", "分类", "管理", "贴纸", "解锁")

        for (line in lines) {
            if (line.length < 2) continue
            if (excludeKeywords.any { keyword -> line.contains(keyword) }) continue
            if (line.all { it.isDigit() || it == '.' || it == '-' || it == '¥' || it == '￥' || it == ' ' }) continue
            if (line.matches(Regex("""\d{4}[-/]\d{1,2}[-/]\d{1,2}.*"""))) continue
            if (line.matches(Regex("""[A-Za-z0-9\s.:-]+""")) && line.length < 15) continue
            if (line.contains("√") || line.contains(">") || line.contains("¥")) continue

            if (line.contains(Regex("""[\u4e00-\u9fa5]{2,}""")) && !line.contains(Regex("""\d"""))) {
                return line
            }
        }
        return null
    }

    private fun extractPaymentTime(lines: List<String>): String? {
        val timePattern = Regex("""(\d{4}[-/]\d{1,2}[-/]\d{1,2}\s*\d{1,2}:\d{2}:\d{2})""")

        for (line in lines) {
            val match = timePattern.find(line)
            if (match != null) {
                return formatPaymentTime(match.groupValues[1])
            }
        }

        for (i in lines.indices) {
            if (lines[i].contains("支付时间")) {
                for (j in maxOf(0, i - 3)..minOf(lines.size - 1, i + 3)) {
                    val match = timePattern.find(lines[j])
                    if (match != null) {
                        return formatPaymentTime(match.groupValues[1])
                    }
                }
            }
        }

        return null
    }

    private fun formatPaymentTime(raw: String): String {
        val p = Regex("""(\d{4}[-/]\d{1,2}[-/]\d{1,2})(\d{1,2}:\d{2}:\d{2})""")
        val m = p.matchEntire(raw)
        return if (m != null) "${m.groupValues[1]} ${m.groupValues[2]}" else raw
    }

    private fun cleanPaymentMethod(raw: String): String {
        var result = raw.trimEnd(' ', '>')
        val openCount = result.count { it == '(' || it == '（' }
        val closeCount = result.count { it == ')' || it == '）' }
        if (closeCount > openCount) {
            val excess = closeCount - openCount
            var removed = 0
            while (removed < excess && result.isNotEmpty() && (result.last() == ')' || result.last() == '）')) {
                result = result.dropLast(1)
                removed++
            }
        }
        return result
    }

    private fun extractPaymentMethod(lines: List<String>): String? {
        for (i in lines.indices) {
            if (lines[i].contains("付款方式") || lines[i].contains("寸款方式") || lines[i].contains("支付方式")) {
                for (j in maxOf(0, i - 3)..minOf(lines.size - 1, i + 3)) {
                    if (j == i) continue
                    val line = lines[j]
                    if (line.contains(Regex("""(银行卡|储蓄卡|信用卡|花呗|余额宝|微信|支付宝|网商银行)"""))) {
                        return cleanPaymentMethod(line)
                    }
                }
            }
        }

        for (line in lines) {
            if (line.contains(Regex("""(储蓄卡|信用卡|花呗|余额宝)"""))) {
                return cleanPaymentMethod(line)
            }
        }

        for (line in lines) {
            if (line.contains(Regex("""(银行卡|微信支付|支付宝|网商银行)"""))) {
                return cleanPaymentMethod(line)
            }
        }

        return null
    }

    private fun extractOrderNumber(lines: List<String>): String? {
        for (i in lines.indices) {
            if (lines[i].contains("订单号") || lines[i].contains("交易号") || lines[i].contains("流水号")) {
                for (j in maxOf(0, i - 3)..minOf(lines.size - 1, i + 3)) {
                    if (j == i) continue
                    val line = lines[j].trim()
                    val match = Regex("""(\d{16,})""").find(line)
                    if (match != null) {
                        return match.groupValues[1]
                    }
                }
            }
        }

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.all { it.isDigit() } && trimmed.length >= 16) {
                return trimmed
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
