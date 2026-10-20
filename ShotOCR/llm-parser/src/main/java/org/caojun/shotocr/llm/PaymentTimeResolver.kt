package org.caojun.shotocr.llm

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 支付时间确定性解析：优先取"支付时间"标签附近的完整日期时间，
 * 支持日期与时间之间没有空格、用 T 分隔等 OCR 变体。
 */
object PaymentTimeResolver {

    private val DATE_TIME = Regex(
        """(\d{4})[-/.年](\d{1,2})[-/.月](\d{1,2}?)\s*日?\s*(\d{1,2}):(\d{2})(?::(\d{2}))?"""
    )
    private val DATE_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)
    private val TIME_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT)

    fun resolve(ocrText: String): String? {
        val matches = DATE_TIME.findAll(ocrText).toList()
        if (matches.isEmpty()) return null

        val labelIndex = ocrText.indexOf("支付时间")
        val match = if (labelIndex >= 0) {
            matches.firstOrNull { it.range.first >= labelIndex } ?: matches.first()
        } else {
            matches.first()
        }

        val year = match.groupValues[1].toIntOrNull() ?: return null
        val month = match.groupValues[2].toIntOrNull() ?: return null
        val day = match.groupValues[3].toIntOrNull() ?: return null
        val hour = match.groupValues[4].toIntOrNull() ?: return null
        val minute = match.groupValues[5].toIntOrNull() ?: return null
        val second = match.groupValues[6].toIntOrNull() ?: 0

        return runCatching {
            LocalDate.of(year, month, day).format(DATE_FORMAT) + " " +
                LocalTime.of(hour, minute, second).format(TIME_FORMAT)
        }.getOrNull()
    }
}
