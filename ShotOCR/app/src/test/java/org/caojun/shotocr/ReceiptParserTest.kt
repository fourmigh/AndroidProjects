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

    private val unionPayTextAmountBelowOrder = """
        日名引{841414:00:41144支
        马
        云闪付交易详情
        上海公共交通卡股份有限公司
        -¥4.50
        优惠信息
        上海地铁出行优惠卡-¥0.50
        交易成功
        当前状态
        ¥5.00
        订单金额
        上海银行银联信用卡[9320]
        付款方式
        延迟扣费|2026年9月30日
        扣款时间
        08:59:27
        莘庄2026年9月30日06:16:50
        乘车信息
        塘桥2026年9月30日06:59:19
        801342595704904359387
        交易流水号
        1422508132364468224
        商户订单号
        在此商户的交易
    """.trimIndent()

    @Test
    fun basicParser_amountBelowOrderAmount_parsesDiscountCorrectly() {
        val result = ReceiptParser().parse(unionPayTextAmountBelowOrder)

        assertEquals(5.0, result.originalAmount!!, 0.001)
        assertEquals(4.5, result.amount!!, 0.001)
        assertEquals(0.5, result.discount!!, 0.001)
    }

    @Test
    fun configurableParser_amountBelowOrderAmount_parsesDiscountCorrectly() {
        val result = ConfigurableReceiptParser(ReceiptParseConfig.default()).parse(unionPayTextAmountBelowOrder)

        assertEquals(5.0, result.originalAmount!!, 0.001)
        assertEquals(4.5, result.amount!!, 0.001)
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

    @Test
    fun parser_detectsSourceAppFromUnionPay() {
        val result = ReceiptParser().parse(unionPayText)

        assertEquals("云闪付", result.sourceApp)
    }

    @Test
    fun parser_detectsSourceAppFromUnionPayAmountBelowOrder() {
        val result = ReceiptParser().parse(unionPayTextAmountBelowOrder)

        assertEquals("云闪付", result.sourceApp)
    }

    @Test
    fun parser_detectsSourceAppFromWeChat() {
        val text = """
            微信支付
            商户A
            交易成功
            ¥30.00
            订单金额
            微信支付-零钱
        """.trimIndent()
        val result = ReceiptParser().parse(text)

        assertEquals("微信", result.sourceApp)
    }

    @Test
    fun configurableParser_detectsSourceAppFromUnionPay() {
        val result = ConfigurableReceiptParser(ReceiptParseConfig.default()).parse(unionPayText)

        assertEquals("云闪付", result.sourceApp)
    }

    private val alipayOcrDashAmountText = """
        日为7414
        15:58:39144支
        账单详情
        肯德基(上海塘桥餐厅
        -9-77
        交易成功
        11.20
        订单金额
        网商银行福利-0.90
        金抵扣
        支付18VIP到店-0.50
        折扣券
        百次立减
        -0.03>
        2026-10-0807:06:50
        支付时间
        网商银行储蓄卡(3841)>
        付款方式
        商品说明
        KFC_PREAF100126832247827649007
        查看购物详情
        支付奖励
        已领取2积分>
        推荐服务
        领银行卡立减金，支付就可用
        去查看>
        订单号
        2026100823001465011447014045
        商家可扫码退款或查询交易
        商家订单号
    """.trimIndent()

    @Test
    fun basicParser_ocrDashAsDecimalPoint_parsesAmount() {
        val result = ReceiptParser().parse(alipayOcrDashAmountText)

        assertEquals(11.2, result.originalAmount!!, 0.001)
        assertEquals(9.77, result.amount!!, 0.001)
        assertEquals(1.43, result.discount!!, 0.001)
    }

    @Test
    fun configurableParser_ocrDashAsDecimalPoint_parsesAmount() {
        val result = ConfigurableReceiptParser(ReceiptParseConfig.default()).parse(alipayOcrDashAmountText)

        assertEquals(11.2, result.originalAmount!!, 0.001)
        assertEquals(9.77, result.amount!!, 0.001)
        assertEquals(1.43, result.discount!!, 0.001)
    }

    private val meiliOriginalAboveActualText = """
        日8614
        13:13:09144
        X
        梅里古镇
        -9.60
        原价
        ¥10.00
        优惠
        摇优惠·银行卡多笔立减优惠¥0.40
        支付成功
        当前状态
        支付时间
        2026年10月5日16:31:13
        亭车费用-梅里古镇-沪MZ0081-10元
        商品
        商户全称
        无锡劲里商业管理有限公司
        收单机构
        财付通支付科技有限公司
        兴业银行储蓄卡(5719)
        支付方式
        4500000453202610059952337463
        交易单号
        商户单号
        POCC202610051629340913218207
        梅里古镇>
        商家小程序
        账单服务
        发起群收款
        ？对订单有疑惑
        目在此商户的交易
        ☆申请电子凭证
    """.trimIndent()

    @Test
    fun basicParser_originalAmountBelowKeyword_parsesOriginal() {
        val result = ReceiptParser().parse(meiliOriginalAboveActualText)

        assertEquals(10.0, result.originalAmount!!, 0.001)
        assertEquals(9.6, result.amount!!, 0.001)
        assertEquals(0.4, result.discount!!, 0.001)
    }

    @Test
    fun configurableParser_originalAmountBelowKeyword_parsesOriginal() {
        val result = ConfigurableReceiptParser(ReceiptParseConfig.default()).parse(meiliOriginalAboveActualText)

        assertEquals(10.0, result.originalAmount!!, 0.001)
        assertEquals(9.6, result.amount!!, 0.001)
        assertEquals(0.4, result.discount!!, 0.001)
    }

    private val smallAmountOcrDashText = """
        日泪64
        14:19:42144
        账单详情
        车迪管理(苏州)有限公司
        -0-99
        交易成功
        1.00
        订单金额
        -0.01
        网商银行福利
        金抵扣
        2026-10-0907:04:06
        支付时间
        付款方式
        网商银行储蓄卡(3841)>
        小黄油美式（共1件）
        商品说明
        已领取2积分>
        支付奖励
        库迪管理(苏州)有限公司
        收款方全称
        推荐服务
        领银行卡立减金，支付就可用
        去查看>
        订单号
        2026100923001465011450406928
        48874106644463617
        商家订单号
        账单管理
        已解锁"咖啡奶茶"贴纸，去兑换好礼
        餐饮美食>
        账单分类
        标签
        请选择>
    """.trimIndent()

    @Test
    fun basicParser_smallAmountWithOcrDash_parsesAmount() {
        val result = ReceiptParser().parse(smallAmountOcrDashText)

        assertEquals(1.0, result.originalAmount!!, 0.001)
        assertEquals(0.01, result.discount!!, 0.001)
        assertEquals(0.99, result.amount!!, 0.001)
    }

    @Test
    fun configurableParser_smallAmountWithOcrDash_parsesAmount() {
        val result = ConfigurableReceiptParser(ReceiptParseConfig.default()).parse(smallAmountOcrDashText)

        assertEquals(1.0, result.originalAmount!!, 0.001)
        assertEquals(0.01, result.discount!!, 0.001)
        assertEquals(0.99, result.amount!!, 0.001)
    }
}