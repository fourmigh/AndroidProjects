package org.caojun.shotocr

import org.caojun.shotocr.parser.ConfigurableReceiptParser
import org.caojun.shotocr.parser.ReceiptParseConfig
import org.caojun.shotocr.parser.ReceiptParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptParserTest {

    private val unionPayText = """
        N为之{885
        5:58:05144
        1
        A
        云闪付交易详情
        上海公共交通卡股份有限公司
        -￥250
        尤惠信息
        2026年--2027年出行省心卡活
        动-¥0.50
        当前状态
        交易成功
        ¥3.00
        订单金额
        上海银行银联信用卡I9320
        付款方式
        征迟扣费12026年9月11日
        扣款时间
        09:40:18
        唐桥2026年9月11日07:24:22
        乘车信息
        源深路2026年9月11日
        07:40:09
        334425102113135896808
        交易流水号
        1415633041227788288
        商户订单号
        在此商户的交易
    """.trimIndent()

    @Test
    fun basicParser_parsesUnionPayScreenshot() {
        val result = ReceiptParser().parse(unionPayText)

        assertNotNull(result.originalAmount)
        assertEquals(3.0, result.originalAmount!!, 0.001)
        assertNotNull(result.amount)
        assertEquals(2.5, result.amount!!, 0.001)
        assertNotNull(result.discount)
        assertEquals(0.5, result.discount!!, 0.001)
        assertEquals("2026-09-11 09:40:18", result.paymentTime)
    }

    @Test
    fun configurableParser_defaultConfig_parsesUnionPayScreenshot() {
        val result = ConfigurableReceiptParser(ReceiptParseConfig.default()).parse(unionPayText)

        assertNotNull(result.originalAmount)
        assertEquals(3.0, result.originalAmount!!, 0.001)
        assertNotNull(result.amount)
        assertEquals(2.5, result.amount!!, 0.001)
        assertNotNull(result.discount)
        assertEquals(0.5, result.discount!!, 0.001)
        assertEquals("2026-09-11 09:40:18", result.paymentTime)
    }

    @Test
    fun configurableParser_currencySymbolPreset_parsesUnionPayScreenshot() {
        val config = ReceiptParseConfig.default().copy(
            amountPatternPreset = "currency_symbol",
            amountRegexCustom = ""
        )
        val result = ConfigurableReceiptParser(config).parse(unionPayText)

        assertNotNull(result.amount)
        assertEquals(2.5, result.amount!!, 0.001)
    }

    @Test
    fun configurableParser_legacyDbConfig_withoutYouHui_stillParsesDiscount() {
        val legacyConfig = ReceiptParseConfig.default().copy(
            discountKeywords = listOf("优惠", "减免", "折扣", "立减", "满减", "抵扣", "福利", "红包", "补贴", "返现")
        )
        val result = ConfigurableReceiptParser(legacyConfig).parse(unionPayText)

        assertNotNull(result.discount)
        assertEquals(0.5, result.discount!!, 0.001)
    }

    @Test
    fun basicParser_noDiscount_returnsNullDiscount() {
        val text = """
            微信支付
            商户A
            交易成功
            ¥30.00
            订单金额
            微信支付-零钱
        """.trimIndent()
        val result = ReceiptParser().parse(text)

        assertNull(result.discount)
        assertNotNull(result.originalAmount)
        assertEquals(30.0, result.originalAmount!!, 0.001)
    }

    @Test
    fun configurableParser_legacyDbConfig_withoutKuanKuanTime_stillParsesPaymentTime() {
        val legacyConfig = ReceiptParseConfig.default().copy(
            paymentTimeKeywords = listOf("支付时间", "付款时间", "交易时间", "成交时间")
        )
        val result = ConfigurableReceiptParser(legacyConfig).parse(unionPayText)

        assertEquals("2026-09-11 09:40:18", result.paymentTime)
    }

    @Test
    fun parser_inlineDateTime_fallbackExtractsTime() {
        val text = """
            微信支付
            商户A
            交易成功
            支付时间 2024-01-15 14:30:00
            实付金额 ¥30.00
        """.trimIndent()
        val result = ReceiptParser().parse(text)

        assertEquals("2024-01-15 14:30:00", result.paymentTime)
    }

    @Test
    fun parser_dateOnly_returnsNormalizedDate() {
        val text = """
            云闪付
            某商户
            扣款时间
            2026年9月11日 09:40
        """.trimIndent()
        val result = ReceiptParser().parse(text)

        assertNotNull(result.paymentTime)
    }

    @Test
    fun parser_truncatedYearInDateTime_recoversNormalizedDate() {
        val text = """
            账单详情
            上海银行股份有限公司
            -17942
            交易成功
            180.00
            订单金额
            -0.58
            中国银行立减
            026-09-1609:48:20
            支付时间
            付款方式
            中国银行信用卡(7957))
            公共支付
            商品说明
        """.trimIndent()
        val result = ReceiptParser().parse(text)

        assertEquals("2026-09-16 09:48:20", result.paymentTime)
    }
}