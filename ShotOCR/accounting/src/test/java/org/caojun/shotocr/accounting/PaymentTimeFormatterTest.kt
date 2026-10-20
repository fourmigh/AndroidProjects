package org.caojun.shotocr.accounting

import org.junit.Assert.assertEquals
import org.junit.Test

class PaymentTimeFormatterTest {

    @Test
    fun formatsDateAndTimeWithoutSeparator() {
        assertEquals("2026-09-14 10:00:00", formatPaymentTime("2026-09-1410:00:00"))
        assertEquals("2026-09-14 10:00:00", formatPaymentTime("2026-09-14T10:00:00"))
    }

    @Test
    fun formatsChineseDateTime() {
        assertEquals("2026-09-05 10:00:00", formatPaymentTime("2026年9月5日10:00:00"))
    }

    @Test
    fun padsMissingSeconds() {
        assertEquals("2026-09-14 10:00:00", formatPaymentTime("2026/9/14 10:00"))
    }

    @Test
    fun formatsDateOnly() {
        assertEquals("2026-09-14", formatPaymentTime("2026-09-14"))
        assertEquals("2026-09-05", formatPaymentTime("2026年9月5日"))
    }

    @Test
    fun unparseable_returnsRawText() {
        assertEquals("not-a-time", formatPaymentTime("not-a-time"))
        assertEquals("", formatPaymentTime(""))
    }
}
