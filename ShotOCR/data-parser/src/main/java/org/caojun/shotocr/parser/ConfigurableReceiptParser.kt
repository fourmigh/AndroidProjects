package org.caojun.shotocr.parser

import org.caojun.shotocr.patternpicker.PresetType
import org.caojun.shotocr.patternpicker.RegexPreset

class ConfigurableReceiptParser(
    private val config: ReceiptParseConfig
) : ReceiptParser() {

    private val actualOriginalAmountRegex: String
        get() = RegexPreset.resolveRegex(PresetType.AMOUNT, config.originalAmountPatternPreset, config.originalAmountRegexCustom)

    private val actualPaymentTimeRegex: String
        get() = RegexPreset.resolveRegex(PresetType.TIME, config.paymentTimePatternPreset, config.paymentTimeRegexCustom)

    private val actualOrderNumberRegex: String
        get() = RegexPreset.resolveRegex(PresetType.ORDER_NUMBER, config.orderNumberPatternPreset, config.orderNumberRegexCustom)

    private val actualAmountRegex: String
        get() = RegexPreset.resolveRegex(PresetType.AMOUNT, config.amountPatternPreset, config.amountRegexCustom)

    private val actualItemRegex: String
        get() = RegexPreset.resolveRegex(PresetType.ITEM, config.itemPatternPreset, config.itemRegexCustom)

    private val effectiveDiscountKeywords: List<String> by lazy {
        (ReceiptParseConfig.default().discountKeywords + config.discountKeywords).distinct()
    }

    override fun extractOriginalAmount(lines: List<String>): Double? {
        val amountPattern = Regex(actualOriginalAmountRegex)

        for (i in lines.indices) {
            val line = lines[i]
            val hasKeyword = config.originalAmountKeywords.any { keyword -> line.contains(keyword) }
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

    override fun extractAmount(lines: List<String>, originalAmount: Double?): Double? {
        val amountRegex = Regex(actualAmountRegex)

        for (line in lines) {
            val trimmed = line.trim()
            if (!trimmed.startsWith("-")) continue
            val rawValue = extractNumber(amountRegex, trimmed) ?: continue
            if (Math.abs(rawValue) <= 1) continue
            return Math.abs(applyDecimalInference(rawValue, trimmed, originalAmount))
        }

        for (i in lines.indices) {
            val line = lines[i]
            val hasKeyword = config.amountKeywords.any { keyword -> line.contains(keyword) }
            if (!hasKeyword) continue
            if (config.amountExcludePatterns.any { pattern -> line.contains(pattern) }) continue

            val amountLine = if (extractNumber(amountRegex, line) != null) line
            else if (i + 1 < lines.size) lines[i + 1]
            else null
            if (amountLine == null) continue
            val rawValue = extractNumber(amountRegex, amountLine) ?: continue
            return Math.abs(applyDecimalInference(rawValue, amountLine, originalAmount))
        }

        return null
    }

    private fun extractNumber(regex: Regex, line: String): Double? {
        val match = regex.find(line) ?: return null
        val matched = if (match.groupValues.size > 1) match.groupValues[1] else match.value
        return matched.replace("¥", "").replace("￥", "").toDoubleOrNull()
    }

    private fun applyDecimalInference(rawValue: Double, rawLine: String, originalAmount: Double?): Double {
        val abs = Math.abs(rawValue)
        if (abs > 1 && originalAmount != null && !rawLine.contains(".")) {
            return inferDecimalPoint(abs, originalAmount)
        }
        return abs
    }

    override fun extractDiscount(lines: List<String>, originalAmount: Double?): Double? {
        val amountPattern = Regex("""(-?\d+\.?\d+)""")
        val datePattern = Regex("""\d{4}[-/]\d{1,2}[-/]\d{1,2}""")
        val processedIndices = mutableSetOf<Int>()
        var totalDiscount = 0.0
        var found = false

        for (i in lines.indices) {
            if (i in processedIndices) continue
            val line = lines[i]
            if (config.discountExcludeKeywords.any { keyword -> line.contains(keyword) }) continue

            val hasKeyword = effectiveDiscountKeywords.any { keyword -> line.contains(keyword) }
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
                if (effectiveDiscountKeywords.any { kw -> adjacentLine.contains(kw) }) continue
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

    override fun extractStoreName(lines: List<String>): String? {
        for (line in lines) {
            if (line.length < 2) continue
            if (config.storeNameExcludeKeywords.any { keyword -> line.contains(keyword) }) continue
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

    override fun paymentTimeRegex(): Regex = Regex(actualPaymentTimeRegex)

    override fun paymentTimeKeywords(): List<String> =
        (super.paymentTimeKeywords() + config.paymentTimeKeywords).distinct()

    override fun extractPaymentMethod(lines: List<String>): String? {
        for (i in lines.indices) {
            if (config.paymentMethodKeywords.any { keyword -> lines[i].contains(keyword) }) {
                for (j in maxOf(0, i - 3)..minOf(lines.size - 1, i + 3)) {
                    if (j == i) continue
                    val line = lines[j]
                    if (line.contains(Regex(config.paymentMethodValues.joinToString("|") { Regex.escape(it) }))) {
                        return cleanPaymentMethod(line)
                    }
                }
            }
        }

        val paymentPattern = Regex(config.paymentMethodValues.joinToString("|") { Regex.escape(it) })
        for (line in lines) {
            if (line.contains(paymentPattern)) {
                return cleanPaymentMethod(line)
            }
        }

        return null
    }

    override fun extractOrderNumber(lines: List<String>): String? {
        val orderRegex = Regex(actualOrderNumberRegex)

        for (i in lines.indices) {
            if (config.orderNumberKeywords.any { keyword -> lines[i].contains(keyword) }) {
                for (j in maxOf(0, i - 3)..minOf(lines.size - 1, i + 3)) {
                    if (j == i) continue
                    val line = lines[j].trim()
                    val match = orderRegex.find(line)
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

    override fun extractItems(lines: List<String>): List<ReceiptItem> {
        val items = mutableListOf<ReceiptItem>()
        val itemPattern = Regex(actualItemRegex)

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
