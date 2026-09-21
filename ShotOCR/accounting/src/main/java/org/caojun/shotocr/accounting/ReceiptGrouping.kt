package org.caojun.shotocr.accounting

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

data class ReceiptTotals(
    val count: Int,
    val totalAmount: BigDecimal,
    val totalDiscount: BigDecimal,
    val totalOrderAmount: BigDecimal
) {
    /** 优惠率 = 总优惠 / 总订单金额（0~1 的小数，总订单金额为 0 时为 0） */
    val discountRate: BigDecimal
        get() = if (totalOrderAmount.signum() != 0) {
            totalDiscount.divide(totalOrderAmount, 4, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

    companion object {
        val EMPTY = ReceiptTotals(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
    }
}

fun totalsOf(receipts: List<EditableReceipt>): ReceiptTotals {
    var amount = BigDecimal.ZERO
    var discount = BigDecimal.ZERO
    var order = BigDecimal.ZERO
    receipts.forEach { receipt ->
        val a = parseAmount(receipt.amount)
        val d = parseAmount(receipt.discount)
        amount += a
        discount += d
        order += if (receipt.originalAmount.isNotBlank()) {
            parseAmount(receipt.originalAmount)
        } else {
            a + d
        }
    }
    return ReceiptTotals(receipts.size, amount, discount, order)
}

/** 解析金额字符串，清理货币符号、千分位等非数字字符；无法解析返回 0 */
fun parseAmount(raw: String?): BigDecimal {
    if (raw.isNullOrBlank()) return BigDecimal.ZERO
    val cleaned = raw.trim().replace(Regex("[^0-9.\\-]"), "")
    if (cleaned.isEmpty() || cleaned == "-" || cleaned == ".") return BigDecimal.ZERO
    return runCatching { BigDecimal(cleaned) }.getOrDefault(BigDecimal.ZERO)
}

fun formatAmount(value: BigDecimal): String {
    return value.setScale(2, RoundingMode.HALF_UP).toPlainString()
}

private val DATE_FORMATS = listOf(
    "yyyy-MM-dd HH:mm:ss",
    "yyyy-MM-dd HH:mm",
    "yyyy-MM-dd",
    "yyyy/MM/dd HH:mm:ss",
    "yyyy/MM/dd HH:mm",
    "yyyy/MM/dd",
    "yyyy.MM.dd HH:mm:ss",
    "yyyy.MM.dd HH:mm",
    "yyyy.MM.dd",
    "yyyy-M-d HH:mm:ss",
    "yyyy-M-d HH:mm",
    "yyyy-M-d",
    "yyyy/M/d HH:mm:ss",
    "yyyy/M/d HH:mm",
    "yyyy/M/d",
    "yyyy.M.d HH:mm:ss",
    "yyyy.M.d HH:mm",
    "yyyy.M.d",
    "yyyy年M月d日 HH:mm:ss",
    "yyyy年M月d日 HH:mm",
    "yyyy年M月d日"
)

/** 解析支付时间字符串为日期；无法解析返回 null */
fun parsePaymentDate(raw: String?): LocalDate? {
    if (raw.isNullOrBlank()) return null
    val text = raw.trim()
    for (pattern in DATE_FORMATS) {
        val formatter = DateTimeFormatter.ofPattern(pattern, Locale.CHINA)
            .withResolverStyle(ResolverStyle.LENIENT)
        val byDate = runCatching { LocalDate.parse(text, formatter) }.getOrNull()
        if (byDate != null) return byDate
        val byDateTime = runCatching {
            java.time.LocalDateTime.parse(text, formatter).toLocalDate()
        }.getOrNull()
        if (byDateTime != null) return byDateTime
    }
    return null
}

data class WeekSummary(
    val key: String,
    val label: String,
    val rangeLabel: String,
    val receipts: List<EditableReceipt>
) {
    val totals: ReceiptTotals by lazy { totalsOf(receipts) }
}

data class MonthSummary(
    val key: String,
    val label: String,
    val rangeLabel: String,
    val weeks: List<WeekSummary>
) {
    val totals: ReceiptTotals by lazy { totalsOf(weeks.flatMap { it.receipts }) }
}

data class GroupedData(
    val months: List<MonthSummary>,
    val unclassified: List<EditableReceipt>
) {
    val unclassifiedTotals: ReceiptTotals by lazy { totalsOf(unclassified) }
}

/**
 * 将记录按「月 > 周」分组。
 * - 分组依据：仅使用支付时间（paymentTime），无法解析出日期的记录进入 [GroupedData.unclassified]；
 * - 周按 [weekStart] 为每周起始日计算，跨月周按其起始日所在月份归属；
 * - 月、周、周内记录均按日期倒序，同日期保持输入顺序；未分类记录保持输入顺序。
 */
fun buildGroupedData(
    records: List<EditableReceipt>,
    weekStart: DayOfWeek = DayOfWeek.MONDAY
): GroupedData {
    data class Dated(val date: LocalDate, val weekStartDate: LocalDate)

    val dated = mutableListOf<Pair<Dated, EditableReceipt>>()
    val unclassified = mutableListOf<EditableReceipt>()

    records.forEach { receipt ->
        val date = parsePaymentDate(receipt.paymentTime)
        if (date == null) {
            unclassified.add(receipt)
        } else {
            val weekStartDate = date.with(TemporalAdjusters.previousOrSame(weekStart))
            dated.add(Pair(Dated(date, weekStartDate), receipt))
        }
    }

    val months = dated.groupBy { it.first.weekStartDate.toMonthKey() }
        .map { (monthKey, entries) ->
            val weeks = entries.groupBy { it.first.weekStartDate }
                .map { (weekStartDate, weekEntries) ->
                    val receipts = weekEntries.map { it.second }.sortedByDescending { datedDate(it) }
                    WeekSummary(
                        key = weekStartDate.toString(),
                        label = weekStartDate.format(DATE_SHORT) +
                            " - " +
                            weekStartDate.plusDays(6).format(DATE_SHORT),
                        rangeLabel = weekStartDate.format(DATE_SHORT) +
                            " - " +
                            weekStartDate.plusDays(6).format(DATE_SHORT),
                        receipts = receipts
                    )
                }
                .sortedByDescending { it.key }
            val first = LocalDate.of(
                monthKey.substring(0, 4).toInt(),
                monthKey.substring(5, 7).toInt(),
                1
            )
            val last = first.withDayOfMonth(first.lengthOfMonth())
            MonthSummary(
                key = monthKey,
                label = first.format(MONTH_SHORT),
                rangeLabel = first.format(DATE_SHORT) + " - " + last.format(DATE_SHORT),
                weeks = weeks
            )
        }
        .sortedByDescending { it.key }

    return GroupedData(months = months, unclassified = unclassified)
}

private fun datedDate(receipt: EditableReceipt): LocalDate {
    return parsePaymentDate(receipt.paymentTime) ?: LocalDate.MIN
}

private fun LocalDate.toMonthKey(): String {
    return String.format(Locale.ROOT, "%04d-%02d", year, monthValue)
}

private val DATE_SHORT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
private val MONTH_SHORT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月")