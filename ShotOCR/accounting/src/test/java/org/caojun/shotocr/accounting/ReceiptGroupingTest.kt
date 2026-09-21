package org.caojun.shotocr.accounting

import android.net.Uri
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class ReceiptGroupingTest {

    private val testUri: Uri = mock(Uri::class.java)

    private fun receipt(
        paymentTime: String = "",
        amount: String = "",
        discount: String = "",
        originalAmount: String = "",
        id: Long = 0
    ): EditableReceipt {
        return EditableReceipt(
            id = id,
            screenshotUri = testUri,
            rawText = "",
            paymentTime = paymentTime,
            amount = amount,
            discount = discount,
            originalAmount = originalAmount
        )
    }

    // ---------- parseAmount ----------

    @Test
    fun parseAmount_plainNumber() {
        assertEquals(BigDecimal("12.50"), parseAmount("12.50"))
    }

    @Test
    fun parseAmount_withCurrencyAndComma() {
        assertEquals(BigDecimal("1234.56"), parseAmount("¥1,234.56"))
    }

    @Test
    fun parseAmount_blankOrInvalid_returnsZero() {
        assertEquals(BigDecimal.ZERO, parseAmount(""))
        assertEquals(BigDecimal.ZERO, parseAmount("  "))
        assertEquals(BigDecimal.ZERO, parseAmount("abc"))
        assertEquals(BigDecimal.ZERO, parseAmount(null))
    }

    @Test
    fun parseAmount_negative() {
        assertEquals(BigDecimal("-5"), parseAmount("-5"))
    }

    // ---------- parsePaymentDate ----------

    @Test
    fun parsePaymentDate_formats() {
        assertEquals(LocalDate.of(2026, 9, 11), parsePaymentDate("2026-09-11 09:40:18"))
        assertEquals(LocalDate.of(2026, 9, 11), parsePaymentDate("2026/09/11"))
        assertEquals(LocalDate.of(2026, 9, 5), parsePaymentDate("2026年9月5日"))
        assertEquals(LocalDate.of(2026, 1, 5), parsePaymentDate("2026.1.5"))
    }

    @Test
    fun parsePaymentDate_invalid_returnsNull() {
        assertEquals(null, parsePaymentDate(""))
        assertEquals(null, parsePaymentDate("not-a-date"))
    }

    // ---------- buildGroupedData ----------

    @Test
    fun grouping_sameWeek_andMonth() {
        // 2026-09-11 是周五，2026-09-14 是周一，2026-09-20 是周日
        val mon = receipt(paymentTime = "2026-09-14 10:00:00", amount = "10", id = 1)
        val sun = receipt(paymentTime = "2026-09-20 20:00:00", amount = "20", id = 2)

        val data = buildGroupedData(listOf(mon, sun), DayOfWeek.MONDAY)

        assertEquals(1, data.months.size)
        assertEquals("2026-09", data.months[0].key)
        assertEquals(1, data.months[0].weeks.size)
        assertEquals("2026-09-14", data.months[0].weeks[0].key)
        assertEquals(2, data.months[0].weeks[0].receipts.size)
        assertTrue(data.unclassified.isEmpty())
    }

    @Test
    fun grouping_crossMonthWeekFollowsWeekStart() {
        // 2026-09-29 是周二（周一起始归入 09-28 那一周，属于 9 月）
        val tue = receipt(paymentTime = "2026-09-29 12:00:00", id = 1)
        // 2026-10-01 是周四，周一起始为 09-28，仍归属 9 月
        val thu = receipt(paymentTime = "2026-10-01 12:00:00", id = 2)
        // 2026-10-05 是周一，归于 10 月
        val nextMon = receipt(paymentTime = "2026-10-05 12:00:00", id = 3)

        val data = buildGroupedData(listOf(tue, thu, nextMon), DayOfWeek.MONDAY)

        assertEquals(listOf("2026-10", "2026-09"), data.months.map { it.key })
        assertEquals(1, data.months[1].weeks.size)
        assertEquals("2026-09-28", data.months[1].weeks[0].key)
        assertEquals(2, data.months[1].weeks[0].receipts.size)
        assertEquals(1, data.months[0].weeks.size)
        assertEquals("2026-10-05", data.months[0].weeks[0].key)
    }

    @Test
    fun grouping_weekStartSunday() {
        // 2026-09-13 是周日。
        // 周日为起始日时，2026-09-16（周三）所在周起点为 09-13。
        val wed = receipt(paymentTime = "2026-09-16 12:00:00", id = 1)
        // 2026-10-01（周四）周日起点为 09-27，仍归 9 月
        val thu = receipt(paymentTime = "2026-10-01 12:00:00", id = 2)

        val data = buildGroupedData(listOf(wed, thu), DayOfWeek.SUNDAY)

        assertEquals(1, data.months.size)
        assertEquals("2026-09", data.months[0].key)
        val weekKeys = data.months[0].weeks.map { it.key }
        assertTrue(weekKeys.contains("2026-09-27"))
        assertTrue(weekKeys.contains("2026-09-13"))
        assertEquals(
            listOf(1L),
            data.months[0].weeks.first { it.key == "2026-09-13" }.receipts.map { it.id }
        )
        assertEquals(
            listOf(2L),
            data.months[0].weeks.first { it.key == "2026-09-27" }.receipts.map { it.id }
        )
    }

    @Test
    fun grouping_unclassifiedKeepsInputOrder() {
        val a = receipt(paymentTime = "2026-09-11 09:00:00", id = 1)
        val noTime1 = receipt(amount = "1", id = 2)
        val noTime2 = receipt(amount = "2", id = 3)
        val b = receipt(paymentTime = "2026-09-12 09:00:00", id = 4)

        val data = buildGroupedData(listOf(a, noTime1, noTime2, b), DayOfWeek.MONDAY)

        assertEquals(listOf(noTime1, noTime2).map { it.id }, data.unclassified.map { it.id })
        assertEquals(listOf(2L, 3L), data.unclassified.map { it.id })
        assertEquals(1, data.months.size)
    }

    // ---------- totalsOf ----------

    @Test
    fun totals_usesOriginalAmountFirst() {
        val list = listOf(
            receipt(amount = "100", discount = "10", originalAmount = "110", id = 1),
            receipt(amount = "50", discount = "5", originalAmount = "", id = 2)
        )
        val totals = totalsOf(list)

        assertEquals(2, totals.count)
        assertEquals("150", totals.totalAmount.toPlainString())
        assertEquals("15", totals.totalDiscount.toPlainString())
        // 第二条 originalAmount 为空，用 amount + discount = 55
        assertEquals("165", totals.totalOrderAmount.toPlainString())
        assertEquals("0.0909", totals.discountRate.toPlainString())
    }

    @Test
    fun totals_empty() {
        val totals = totalsOf(emptyList())
        assertEquals(0, totals.count)
        assertEquals(BigDecimal.ZERO, totals.totalAmount)
        assertEquals(BigDecimal.ZERO, totals.discountRate)
    }
}