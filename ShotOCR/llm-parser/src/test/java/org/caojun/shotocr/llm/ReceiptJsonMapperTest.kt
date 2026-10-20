package org.caojun.shotocr.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptJsonMapperTest {

    @Test
    fun map_fullJson_mapsAllFields() {
        val json = """
            {"amount":8.29,"discount":0.01,"originalAmount":8.30,
             "storeName":"库迪咖啡（北京）有限公司","paymentTime":"2026-10-13 06:33:22",
             "paymentMethod":"抖音月付","orderNumber":"3648468013999049","sourceApp":"抖音"}
        """.trimIndent()

        val data = ReceiptJsonMapper.map(json, "raw")

        assertEquals(8.29, data.amount!!, 0.001)
        assertEquals(0.01, data.discount!!, 0.001)
        assertEquals(8.30, data.originalAmount!!, 0.001)
        assertEquals("库迪咖啡（北京）有限公司", data.storeName)
        assertEquals("2026-10-13 06:33:22", data.paymentTime)
        assertEquals("抖音月付", data.paymentMethod)
        assertEquals("3648468013999049", data.orderNumber)
        assertEquals("抖音", data.sourceApp)
        assertEquals("raw", data.rawText)
    }

    @Test
    fun map_jsonWrappedInCodeFence_isExtracted() {
        val output = "```json\n{\"amount\":21.8,\"discount\":null}\n```"

        val data = ReceiptJsonMapper.map(output, "raw")

        assertEquals(21.8, data.amount!!, 0.001)
        assertNull(data.discount)
    }

    @Test
    fun map_missingFields_returnsNulls() {
        val data = ReceiptJsonMapper.map("{}", "raw")

        assertNull(data.amount)
        assertNull(data.discount)
        assertNull(data.originalAmount)
        assertNull(data.storeName)
        assertNull(data.paymentMethod)
    }

    @Test
    fun map_negativeAmounts_takenAsAbsoluteValues() {
        val data = ReceiptJsonMapper.map(
            "{\"amount\":-15.97,\"discount\":-0.03,\"originalAmount\":-16.0}",
            "raw"
        )

        assertEquals(15.97, data.amount!!, 0.001)
        assertEquals(0.03, data.discount!!, 0.001)
        assertEquals(16.0, data.originalAmount!!, 0.001)
    }

    @Test
    fun map_stringNumbers_areParsed() {
        val data = ReceiptJsonMapper.map("{\"amount\":\"¥16.95\",\"quantity\":\"2\"}", "raw")

        assertEquals(16.95, data.amount!!, 0.001)
    }

    @Test
    fun map_paymentTimeWithoutSeparator_isNormalized() {
        val data = ReceiptJsonMapper.map(
            "{\"paymentTime\":\"2026-09-1410:00:00\"}",
            "raw"
        )

        assertEquals("2026-09-14 10:00:00", data.paymentTime)
    }

    @Test
    fun map_paymentTimeWithIsoSeparator_isNormalized() {
        val data = ReceiptJsonMapper.map(
            "{\"paymentTime\":\"2026-09-14T10:00:00\"}",
            "raw"
        )

        assertEquals("2026-09-14 10:00:00", data.paymentTime)
    }

    @Test
    fun map_chinesePaymentTimeWithoutSeparator_isNormalized() {
        val data = ReceiptJsonMapper.map(
            "{\"paymentTime\":\"2026年9月5日10:00:00\"}",
            "raw"
        )

        assertEquals("2026年9月5日 10:00:00", data.paymentTime)
    }

    @Test
    fun extractJsonObject_noBraces_returnsNull() {
        assertNull(ReceiptJsonMapper.extractJsonObject("no json here"))
    }
}
