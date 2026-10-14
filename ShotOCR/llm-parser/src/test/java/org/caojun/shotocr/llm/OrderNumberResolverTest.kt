package org.caojun.shotocr.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OrderNumberResolverTest {

    @Test
    fun wechat_valueAboveTransactionLabel() {
        val text = """
            支付方式
            4500000453202610059952337463
            交易单号
            商户单号
            POCC202610051629340913218207
        """.trimIndent()

        assertEquals("4500000453202610059952337463", OrderNumberResolver.resolve(text))
    }

    @Test
    fun labelAndValueOnSameLine() {
        assertEquals(
            "2026100823001465011447014045",
            OrderNumberResolver.resolve("订单号 2026100823001465011447014045")
        )
    }

    @Test
    fun unionPay_serialNumber() {
        assertEquals(
            "334425102113135896808",
            OrderNumberResolver.resolve("交易流水号\n334425102113135896808")
        )
    }

    @Test
    fun merchantOnlyNumber_returnsNull() {
        assertNull(OrderNumberResolver.resolve("商户单号\nPOCC202610051629340913218207"))
    }

    @Test
    fun noLabels_returnsNull() {
        assertNull(OrderNumberResolver.resolve("毫无标签 123456789012345678901234"))
    }
}
