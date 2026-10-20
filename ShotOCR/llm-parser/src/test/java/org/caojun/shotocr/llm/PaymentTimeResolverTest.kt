package org.caojun.shotocr.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentTimeResolverTest {

    @Test
    fun resolvesDateTimeWithoutSeparator() {
        val text = "支付时间\n2026-10-2007:07:57\n付款方式"

        assertEquals("2026-10-20 07:07:57", PaymentTimeResolver.resolve(text))
    }

    @Test
    fun prefersDateTimeAfterPaymentLabel() {
        val text = "订单创建时间 2026-10-19 08:00:00\n支付时间\n2026-10-20 07:07:57"

        assertEquals("2026-10-20 07:07:57", PaymentTimeResolver.resolve(text))
    }

    @Test
    fun returnsNullWithoutFullDateTime() {
        assertNull(PaymentTimeResolver.resolve("13:00:59\n支付时间"))
        assertNull(PaymentTimeResolver.resolve("2026-10-20"))
    }
}
