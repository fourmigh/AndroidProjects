package org.caojun.shotocr.parser

open class ReceiptParser : DataParser<ReceiptData> {

    // 顺序即优先级：电商订单页含"支付宝交易号/支付宝支付"等，需先于"支付宝"命中。
    private val sourceAppSignatures = listOf(
        "淘宝" to listOf("淘宝", "天猫", "支付宝交易号", "进店逛逛"),
        "京东" to listOf("京东", "京豆", "京东交易", "确认收货", "我要催单"),
        "抖音" to listOf("抖音支付", "抖音月付", "抖音"),
        "云闪付" to listOf("云闪付"),
        "微信" to listOf("微信支付", "微信零钱", "零钱通", "微信账单"),
        "支付宝" to listOf("支付宝", "花呗", "余额宝")
    )

    override fun parse(text: String): ReceiptData {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

        var originalAmount = extractOriginalAmount(lines)
        var amount = extractAmount(lines, originalAmount)
        val discount = extractDiscount(lines, originalAmount)
        if (amount == null && originalAmount != null && discount != null) {
            val inferred = originalAmount - discount
            if (inferred > 0) amount = inferred
        }
        if (originalAmount == null && amount != null) {
            originalAmount = if (discount != null && amount + discount > amount) {
                Math.round((amount + discount) * 100.0) / 100.0
            } else {
                amount
            }
        }
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
            rawText = text,
            sourceApp = extractSourceApp(lines)
        )
    }

    // ---- 数据来源（可被 ConfigurableReceiptParser 覆盖）----

    protected open fun originalAmountKeywords(): List<String> =
        listOf("订单金额", "原价", "原价金额", "商品总价", "总价", "合计金额")

    protected open fun originalAmountPattern(): Regex = Regex("""(\d+\.?\d+)""")

    protected open fun amountKeywords(): List<String> =
        listOf("实付", "实收", "应付", "支付金额")

    protected open fun amountExcludePatterns(): List<String> =
        listOf("支付时间", "支付方式")

    protected open fun amountPattern(): Regex = Regex("""(\d+\.?\d*)""")

    protected open fun discountKeywords(): List<String> =
        listOf("优惠", "尤惠", "减免", "共减", "共省", "折扣", "立减", "满减", "抵扣", "福利", "财神", "红包", "补贴", "返现")

    protected open fun discountExcludeKeywords(): List<String> =
        listOf(
            "领", "就可用", "去查看", "推荐", "服务",
            "有机会", "恭喜", "领取", "获得", "奖励", "免单", "积分", "去兑", "下次再来"
        )

    /**
     * 优惠"合计"关键词：命中即直接取该合计金额，避免其下的明细项（如"补贴抵¥3.7 平台券抵¥0.35"）
     * 被再次累加导致重复计算。
     */
    protected open fun discountTotalKeywords(): List<String> =
        listOf("共减", "共省", "共优惠", "总优惠", "优惠总额", "优惠合计", "合计优惠", "共抵扣", "共立减", "共满减")

    protected open fun storeNameExcludeKeywords(): List<String> =
        listOf(
            "账单", "详情", "支付", "订单", "交易", "时间", "方式", "说明", "奖励", "收款", "推荐", "服务", "分类", "管理", "贴纸", "解锁",
            "等待收货", "待收货", "收货", "发货", "仓库", "预计", "物流", "快递", "运单", "收货地址", "女士", "先生", "充值成功",
            "删除", "支付成功", "规则", "商品订单", "点击收起", "免单奖励"
        )

    protected open fun paymentMethodKeywords(): List<String> =
        listOf("付款方式", "寸款方式", "支付方式")

    protected open fun paymentMethodValues(): List<String> =
        listOf(
            "银行卡", "储蓄卡", "信用卡", "花呗", "余额宝", "微信", "支付宝", "网商银行", "微信支付", "白条", "京东支付",
            "抖音月付", "抖音支付", "月付"
        )

    protected open fun orderNumberKeywords(): List<String> =
        listOf("订单号", "订单编号", "订单号码", "交易号", "流水号")

    protected open fun orderNumberPattern(): Regex = Regex("""(\d{16,})""")

    protected open fun itemPattern(): Regex =
        Regex("""(.+?)\s+[¥￥]?\s*(\d+\.?\d*)\s*[xX×]\s*(\d+)""")

    // ---- 提取实现 ----

    protected open fun extractOriginalAmount(lines: List<String>): Double? {
        val amountPattern = originalAmountPattern()
        val currencyPattern = Regex("""[¥￥]\s*(\d+\.?\d*)""")
        val keywords = originalAmountKeywords()

        for (i in lines.indices) {
            val line = lines[i]
            val hasKeyword = keywords.any { keyword -> line.contains(keyword) }
            if (hasKeyword) {
                val currencyMatch = currencyPattern.find(line)
                if (currencyMatch != null) {
                    return currencyMatch.groupValues[1].toDoubleOrNull()
                }
                val match = amountPattern.find(line)
                if (match != null && !isQuantityAt(line, match.range.last + 1)) {
                    return match.groupValues[1].toDoubleOrNull()
                }
                for (offset in listOf(1, 2, -1, -2)) {
                    val j = i + offset
                    if (j < 0 || j >= lines.size) continue
                    val adjacent = lines[j].trim()
                    if (adjacent.startsWith("-")) continue
                    val currencyMatch = currencyPattern.find(adjacent) ?: continue
                    val value = currencyMatch.groupValues[1].toDoubleOrNull()
                    if (value != null && value > 0) {
                        return value
                    }
                }
                for (j in maxOf(0, i - 2)..minOf(lines.size - 1, i + 2)) {
                    if (j == i) continue
                    val adjacent = lines[j].trim()
                    if (adjacent.startsWith("-")) continue
                    val adjacentMatch = amountPattern.find(adjacent) ?: continue
                    if (isQuantityAt(adjacent, adjacentMatch.range.last + 1)) continue
                    val value = adjacentMatch.groupValues[1].toDoubleOrNull()
                    if (value != null && value > 0) {
                        return value
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
        val amountRegex = amountPattern()

        for (line in lines) {
            val trimmed = normalizeOcrAmountLine(line.trim())
            if (!trimmed.startsWith("-")) continue
            val rawValue = extractNumber(amountRegex, trimmed) ?: continue
            if (Math.abs(rawValue) <= 0) continue
            return Math.abs(applyDecimalInference(rawValue, trimmed, originalAmount))
        }

        val keywords = amountKeywords()
        val excludePatterns = amountExcludePatterns()
        for (i in lines.indices) {
            val line = lines[i]
            val hasKeyword = keywords.any { keyword -> line.contains(keyword) }
            if (!hasKeyword) continue
            if (excludePatterns.any { pattern -> line.contains(pattern) }) continue

            val rawAmountLine = if (extractNumber(amountRegex, line) != null) line
            else if (i + 1 < lines.size) lines[i + 1]
            else null
            if (rawAmountLine == null) continue
            val amountLine = normalizeOcrAmountLine(rawAmountLine)
            val rawValue = extractKeywordAmount(amountRegex, amountLine) ?: continue
            return Math.abs(applyDecimalInference(rawValue, amountLine, originalAmount))
        }

        return null
    }

    protected open fun extractNumber(regex: Regex, line: String): Double? {
        val match = regex.find(line) ?: return null
        val matched = if (match.groupValues.size > 1) match.groupValues[1] else match.value
        return matched.replace("¥", "").replace("￥", "").toDoubleOrNull()
    }

    /**
     * 提取关键字行中的金额：优先取"合计/实付/实收/应付"标签后的金额，
     * 否则取该行最后一个金额。避免"共减¥28.63 合计¥126.37"被取成优惠额 28.63。
     */
    protected open fun extractKeywordAmount(regex: Regex, line: String): Double? {
        val labeled = amountLabelPattern.find(line)
        if (labeled != null) {
            return labeled.groupValues[1].replace("¥", "").replace("￥", "").toDoubleOrNull()
        }
        return regex.findAll(line)
            .mapNotNull { match ->
                val matched = if (match.groupValues.size > 1) match.groupValues[1] else match.value
                matched.replace("¥", "").replace("￥", "").toDoubleOrNull()
            }
            .lastOrNull()
    }

    private val amountLabelPattern =
        Regex("""(?:合计|实付|实收|应付|支付金额)[^\d]*[¥￥]?\s*(\d+\.?\d*)""")

    private val shopNamePattern =
        Regex("""[\u4e00-\u9fa5A-Za-z0-9]{2,}(?:旗舰店|专卖店|专营店|官方店)""")

    private val companyNamePattern =
        Regex("""[\u4e00-\u9fa5A-Za-z0-9（）()]{2,}(?:有限公司|有限责任公司|股份有限公司)""")

    private val quantitySuffixes = setOf(
        '件', '个', '份', '张', '瓶', '盒', '支', '条', '包', '袋', '人', '天', '次', '台', '只', '套',
        '本', '杯', '组', '箱', '罐', '桶', '片', '粒', '颗', '枚', '笔', '项', '种', '款', '名', '户',
        '例', '场', '部', '首', '集', '步', '层', '间', '辆', '束', '把', '根', '串', '副', '双'
    )

    /** 判断 [index] 处是否紧跟量词，用于排除"共1件"这类数量而非金额。 */
    private fun isQuantityAt(line: String, index: Int): Boolean {
        if (index < 0 || index >= line.length) return false
        return line[index] in quantitySuffixes
    }

    protected open fun applyDecimalInference(rawValue: Double, rawLine: String, originalAmount: Double?): Double {
        val abs = Math.abs(rawValue)
        if (abs > 1 && originalAmount != null && !rawLine.contains(".")) {
            return inferDecimalPoint(abs, originalAmount)
        }
        return abs
    }

    /**
     * 修正 OCR 常见错误：金额中的小数点被识别为连字符或间隔符，如 "-9-77" -> "-9.77"。
     * 仅当形如 -数字 + 1~2 位分隔符数字且其后不再紧跟数字时才转换，避免误伤日期/大数。
     */
    protected open fun normalizeOcrAmountLine(line: String): String {
        return line.replace(Regex("""^(-\d+)[-·,](\d{1,2})([^\d].*)?$""")) { match ->
            "${match.groupValues[1]}.${match.groupValues[2]}${match.groupValues[3]}"
        }
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
        val discountKeywords = discountKeywords()
        val excludeKeywords = discountExcludeKeywords()
        val datePattern = Regex("""\d{4}[-/]\d{1,2}[-/]\d{1,2}""")
        val processedIndices = mutableSetOf<Int>()
        var totalDiscount = 0.0
        var found = false

        extractDiscountTotal(lines, discountTotalKeywords(), excludeKeywords, datePattern, originalAmount)
            ?.let { return Math.round(it * 100.0) / 100.0 }

        for (i in lines.indices) {
            if (i in processedIndices) continue
            val line = lines[i]
            if (excludeKeywords.any { keyword -> line.contains(keyword) }) continue

            val hasKeyword = discountKeywords.any { keyword -> line.contains(keyword) }
            if (!hasKeyword) continue

            val value = findDiscountAmount(line)
            if (value != null && Math.abs(value) > 0) {
                totalDiscount += Math.abs(value)
                found = true
                processedIndices.add(i)
                continue
            }

            val neighbor = selectDiscountNeighbor(
                lines, i, discountKeywords, datePattern, originalAmount, processedIndices
            )
            if (neighbor != null) {
                totalDiscount += Math.abs(neighbor.second)
                found = true
                processedIndices.add(neighbor.first)
            }
        }

        return if (found) Math.round(totalDiscount * 100.0) / 100.0 else null
    }

    /** 命中优惠"合计"关键词时返回其金额（本行优先，其次邻近行），否则返回 null。 */
    protected open fun extractDiscountTotal(
        lines: List<String>,
        totalKeywords: List<String>,
        excludeKeywords: List<String>,
        datePattern: Regex,
        originalAmount: Double?
    ): Double? {
        if (totalKeywords.isEmpty()) return null

        for (i in lines.indices) {
            val line = lines[i]
            if (excludeKeywords.any { line.contains(it) }) continue
            if (!totalKeywords.any { line.contains(it) }) continue

            val value = findDiscountAmount(line)
            if (value != null && Math.abs(value) > 0) return Math.abs(value)

            for (offset in listOf(1, -1, 2, -2)) {
                val j = i + offset
                if (j < 0 || j >= lines.size) continue
                val adjacent = lines[j]
                if (excludeKeywords.any { adjacent.contains(it) }) continue
                val adjacentValue = discountAdjacentValue(adjacent, datePattern, originalAmount) ?: continue
                return Math.abs(adjacentValue)
            }
        }
        return null
    }

    protected open fun selectDiscountNeighbor(
        lines: List<String>,
        index: Int,
        discountKeywords: List<String>,
        datePattern: Regex,
        originalAmount: Double?,
        processedIndices: Set<Int>
    ): Pair<Int, Double>? {
        val offsets = mutableListOf<Int>()
        for (distance in 1..3) {
            offsets.add(distance)
            offsets.add(-distance)
        }

        for (offset in offsets) {
            val j = index + offset
            if (j < 0 || j >= lines.size || j in processedIndices) continue
            val adjacentLine = lines[j]
            if (!discountKeywords.any { adjacentLine.contains(it) }) continue
            val value = discountAdjacentValue(adjacentLine, datePattern, originalAmount) ?: continue
            return j to value
        }

        var best: Pair<Int, Double>? = null
        for (offset in offsets) {
            val j = index + offset
            if (j < 0 || j >= lines.size || j in processedIndices) continue
            val adjacentLine = lines[j]
            if (discountKeywords.any { adjacentLine.contains(it) }) continue
            val value = discountAdjacentValue(adjacentLine, datePattern, originalAmount) ?: continue
            if (best == null || Math.abs(value) < Math.abs(best.second)) {
                best = j to value
            }
        }
        return best
    }

    protected open fun discountAdjacentValue(
        line: String,
        datePattern: Regex,
        originalAmount: Double?
    ): Double? {
        if (datePattern.containsMatchIn(line)) return null
        if (containsYearValue(line)) return null
        val value = findDiscountAmount(line) ?: return null
        if (Math.abs(value) <= 0) return null
        if (originalAmount != null && Math.abs(value) >= originalAmount) return null
        return value
    }

    /**
     * 提取优惠相关行中的金额：优先匹配带小数点的金额（如 "-0.50"），
     * 避免 "支付18VIP到店-0.50" 被左起优先匹配成 "18"。
     */
    protected open fun findDiscountAmount(line: String): Double? {
        val normalized = normalizeOcrAmountLine(line)
        val decimalMatch = Regex("""(-?\d+\.\d+)""").find(normalized)
        val match = decimalMatch ?: Regex("""(-?\d+)""").find(normalized)
        return match?.groupValues?.get(1)?.toDoubleOrNull()
    }

    protected open fun containsYearValue(line: String): Boolean =
        Regex("""\d{4}\s*年""").containsMatchIn(line) || Regex("""\d{4}\s*[-/]\s*\d{4}""").containsMatchIn(line)

    protected open fun extractSourceApp(lines: List<String>): String? {
        val joined = lines.joinToString("\n")
        for ((appName, signatures) in sourceAppSignatures) {
            if (signatures.any { signature -> joined.contains(signature) }) {
                return appName
            }
        }
        return null
    }

    protected open fun extractStoreName(lines: List<String>): String? {
        val excludeKeywords = storeNameExcludeKeywords()

        for (line in lines) {
            if (excludeKeywords.any { keyword -> line.contains(keyword) }) continue
            val companyMatch = companyNamePattern.find(line)
            if (companyMatch != null) return companyMatch.value
        }

        for (line in lines) {
            if (excludeKeywords.any { keyword -> line.contains(keyword) }) continue
            val shopMatch = shopNamePattern.find(line)
            if (shopMatch != null) return shopMatch.value
        }

        for (i in lines.indices) {
            if (!lines[i].contains("进店")) continue
            for (offset in listOf(-1, 1, -2, 2)) {
                val j = i + offset
                if (j !in lines.indices) continue
                val candidate = lines[j].trim()
                if (isCandidateStoreName(candidate, excludeKeywords)) return candidate
            }
        }

        for (line in lines) {
            if (isCandidateStoreName(line, excludeKeywords)) {
                return line
            }
        }
        return null
    }

    private fun isCandidateStoreName(line: String, excludeKeywords: List<String>): Boolean {
        if (line.length < 2) return false
        if (excludeKeywords.any { keyword -> line.contains(keyword) }) return false
        if (line.all { it.isDigit() || it == '.' || it == '-' || it == '¥' || it == '￥' || it == ' ' }) return false
        if (line.matches(Regex("""\d{4}[-/]\d{1,2}[-/]\d{1,2}.*"""))) return false
        if (line.matches(Regex("""[A-Za-z0-9\s.:-]+""")) && line.length < 15) return false
        if (line.contains("√") || line.contains(">") || line.contains("¥")) return false

        return line.contains(Regex("""[\u4e00-\u9fa5]{2,}""")) && !line.contains(Regex("""\d"""))
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
        val values = paymentMethodValues()
        if (values.isEmpty()) return null
        val valuesPattern = Regex(values.joinToString("|") { Regex.escape(it) })
        val keywords = paymentMethodKeywords()

        for (i in lines.indices) {
            if (keywords.any { lines[i].contains(it) }) {
                for (j in maxOf(0, i - 3)..minOf(lines.size - 1, i + 3)) {
                    if (j == i) continue
                    val line = lines[j]
                    if (line.contains(valuesPattern)) {
                        return cleanPaymentMethod(line)
                    }
                }
            }
        }

        for (line in lines) {
            if (line.contains(valuesPattern)) {
                return cleanPaymentMethod(line)
            }
        }

        return null
    }

    protected open fun extractOrderNumber(lines: List<String>): String? {
        val orderRegex = orderNumberPattern()
        val keywords = orderNumberKeywords()

        for (i in lines.indices) {
            if (keywords.any { lines[i].contains(it) }) {
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
            val match = orderRegex.find(trimmed)
            if (match != null) {
                return match.groupValues[1]
            }
        }

        return null
    }

    protected open fun extractItems(lines: List<String>): List<ReceiptItem> {
        val items = mutableListOf<ReceiptItem>()
        val itemPattern = itemPattern()

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
