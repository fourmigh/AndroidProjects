package org.caojun.shotocr.accounting

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)
private val TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT)
private val TIME_REGEX = Regex("""(\d{1,2}):(\d{2})(?::(\d{2}))?""")

/**
 * 统一支付时间显示格式：
 * - 有日期和时间：yyyy-MM-dd HH:mm:ss（缺秒补 :00）
 * - 只有日期：yyyy-MM-dd
 * - 无法解析：原样返回
 */
fun formatPaymentTime(raw: String?): String {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return ""

    val date = parsePaymentDate(text) ?: return text
    val match = TIME_REGEX.find(text) ?: return date.format(DATE_FORMAT)

    val hour = match.groupValues[1].toIntOrNull() ?: return text
    val minute = match.groupValues[2].toIntOrNull() ?: return text
    val second = match.groupValues[3].toIntOrNull() ?: 0
    if (hour !in 0..23 || minute !in 0..59 || second !in 0..59) return text

    return date.format(DATE_FORMAT) + " " + LocalTime.of(hour, minute, second).format(TIME_FORMAT)
}
