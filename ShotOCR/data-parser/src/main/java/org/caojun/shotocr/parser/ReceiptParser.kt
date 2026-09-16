package org.caojun.shotocr.parser

open class ReceiptParser : DataParser<ReceiptData> {

    override fun parse(text: String): ReceiptData {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

        val originalAmount = extractOriginalAmount(lines)
        val amount = extractAmount(lines, originalAmount)
        val discount = extractDiscount(lines, originalAmount)
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

    protected open fun extractOriginalAmount(lines: List<String>): Double? {
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

    protected open fun extractAmount(lines: List<String>, originalAmount: Double?): Double? {
        for (line in lines) {
            val trimmed = line.trim()
            if (!trimmed.startsWith("-")) continue
            val cleaned = trimmed.replace("¥", "").replace("￥", "")
            if (cleaned.matches(Regex("""^-?\d+\.?\d*$"""))) {
                var value = cleaned.toDoubleOrNull()
                if (value != null && Math.abs(value) > 1) {
                    if (originalAmount != null && !cleaned.contains(".")) {
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

    protected open fun inferDecimalPoint(rawAmount: Double, originalAmount: Double): Double {
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

    protected open fun extractDiscount(lines: List<String>, originalAmount: Double?): Double? {
        val discountKeywords = listOf("优惠", "尤惠", "减免", "折扣", "立减", "满减", "抵扣", "福利", "财神", "红包", "补贴", "返现")
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

            var bestValue: Double? = null
            var bestIndex = -1
            for (offset in -3..3) {
                if (offset == 0) continue
                val j = i + offset
                if (j < 0 || j >= lines.size || j in processedIndices) continue
                val adjacentLine = lines[j]
                if (discountKeywords.any { kw -> adjacentLine.contains(kw) }) continue
                if (datePattern.containsMatchIn(adjacentLine)) continue
                if (containsYearValue(adjacentLine)) continue
                val adjMatch = amountPattern.find(adjacentLine)
                if (adjMatch != null) {
                    val value = adjMatch.groupValues[1].toDoubleOrNull()
                    if (value == null || Math.abs(value) <= 0) continue
                    if (originalAmount != null && Math.abs(value) > originalAmount) continue
                    if (bestValue == null || Math.abs(value) < Math.abs(bestValue)) {
                        bestValue = value
                        bestIndex = j
                    }
                }
            }
            if (bestValue != null) {
                totalDiscount += Math.abs(bestValue)
                found = true
                processedIndices.add(bestIndex)
            }
        }

        return if (found) Math.round(totalDiscount * 100.0) / 100.0 else null
    }

    protected open fun containsYearValue(line: String): Boolean =
        Regex("""\d{4}\s*年""").containsMatchIn(line) || Regex("""\d{4}\s*[-/]\s*\d{4}""").containsMatchIn(line)

    protected open fun extractStoreName(lines: List<String>): String? {
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

    protected open fun extractPaymentTime(lines: List<String>): String? {
        val timeRegex = paymentTimeRegex()
        val timeKeywords = paymentTimeKeywords()

        for (i in lines.indices) {
            if (timeKeywords.isEmpty()) break
            if (timeKeywords.any { lines[i].contains(it) }) {
                val result = findNeighborhoodTime(lines, i)
                if (result != null) return result
            }
        }

        for (line in lines) {
            val match = timeRegex.find(line)
            if (match != null) {
                return normalizeDateTime(match.groupValues[1])
            }
        }

        return null
    }

    protected open fun paymentTimeRegex(): Regex =
        Regex("""(\d{3,4}[-/年]\d{1,2}[-/月]\d{1,2}[日]?\s*\d{1,2}:\d{2}:\d{2})""")

    protected open fun paymentTimeKeywords(): List<String> =
        listOf("支付时间", "扣款时间", "付款时间", "交易时间", "成交时间")

    private fun findNeighborhoodTime(lines: List<String>, index: Int): String? {
        val dateRegex = Regex("""(\d{3,4}[-/年]\d{1,2}[-/月]\d{1,2}日?)""")
        val timeRegex = Regex("""(\d{1,2}:\d{2}:\d{2})""")
        var date: String? = null
        var time: String? = null

        for (j in maxOf(0, index - 3)..minOf(lines.size - 1, index + 3)) {
            val line = lines[j]
            if (date == null) {
                dateRegex.find(line)?.let { date = it.groupValues[1] }
            }
            if (time == null) {
                timeRegex.find(line)?.let { time = it.groupValues[1] }
            }
            if (date != null && time != null) break
        }

        return when {
            date != null && time != null -> normalizeDateTime("$date $time")
            date != null -> normalizeDateTime(date)
            time != null -> time
            else -> null
        }
    }

    protected open fun normalizeDateTime(raw: String): String {
        val filled = raw.replace("年", "-").replace("月", "-").replace("日", " ").replace("：", ":")
        val full = Regex("""(\d{3,4})\s*[-/]\s*(\d{1,2})\s*[-/]\s*(\d{1,2})[\s-]*(\d{1,2}):(\d{2}):(\d{2})""").find(filled)
        if (full != null) {
            val g = full.groupValues
            return "${normalizeYear(g[1])}-${pad2(g[2])}-${pad2(g[3])} ${pad2(g[4])}:${g[5]}:${g[6]}"
        }
        val dateOnly = Regex("""(\d{3,4})\s*[-/]\s*(\d{1,2})\s*[-/]\s*(\d{1,2})""").find(filled)
        if (dateOnly != null) {
            val g = dateOnly.groupValues
            return "${normalizeYear(g[1])}-${pad2(g[2])}-${pad2(g[3])}"
        }
        return raw.trim()
    }

    private fun normalizeYear(year: String): String = if (year.length == 3) "2$year" else year

    private fun pad2(value: String): String = value.padStart(2, '0')

    protected open fun cleanPaymentMethod(raw: String): String {
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

    protected open fun extractPaymentMethod(lines: List<String>): String? {
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

    protected open fun extractOrderNumber(lines: List<String>): String? {
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

    protected open fun extractItems(lines: List<String>): List<ReceiptItem> {
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
