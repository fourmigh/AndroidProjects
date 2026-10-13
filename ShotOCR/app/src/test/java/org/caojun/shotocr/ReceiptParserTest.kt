package org.caojun.shotocr

import org.caojun.shotocr.parser.ConfigurableReceiptParser
import org.caojun.shotocr.parser.ReceiptParseConfig
import org.caojun.shotocr.parser.ReceiptParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    private val jdOrderDetailText = """
        日90-09:19:56144
        .
        等待收货
        仓库处理中预计今天发货
        圆通快递·您的订单由第三方卖家拣货完成，待出
        库交付圆通快递，运单号为Y73763750952615
        晚发赔服务生效中，送达超承诺时效最高赔30元
        莘松路958弄上海康城山林道66号301室
        朱女士139****0564
        进店>
        郑远元旗舰店
        郑远元旗舰店三黄抑...
        ①到手¥8.76
        数量×2.【单瓶装】巧万√
        ¥10
        无理由退货政策
        加购物车
        退款/售后
        郑远元官方旗舰店授..
        i到手¥118.2
        ¥135
        数量×1,太诺+净舒康
        无理由退货政策
        加购物车
        退款/售后
        实付款
        共减¥28.63合计¥126.37
        订单编号
        3648468013999049复制
        交易快照
        发生交易争议时，可作为判断依据
        支付方式
        白条账单白条>
        发票类型
        不开发票>
        支付时间
        2026-10-1121:09:48
        下单时间
        2026.10.11
        评价得40京豆
        更多
        确认收货
        我要催单
        再次购买
    """.trimIndent()

    @Test
    fun basicParser_parsesJdOrderDetail() {
        val result = ReceiptParser().parse(jdOrderDetailText)

        assertEquals(126.37, result.amount!!, 0.001)
        assertEquals(28.63, result.discount!!, 0.001)
        assertEquals(155.0, result.originalAmount!!, 0.001)
        assertEquals("郑远元旗舰店", result.storeName)
        assertTrue(result.paymentMethod?.contains("白条") == true)
        assertEquals("3648468013999049", result.orderNumber)
    }

    @Test
    fun configurableParser_parsesJdOrderDetail() {
        val result = ConfigurableReceiptParser(ReceiptParseConfig.default()).parse(jdOrderDetailText)

        assertEquals(126.37, result.amount!!, 0.001)
        assertEquals(28.63, result.discount!!, 0.001)
        assertEquals(155.0, result.originalAmount!!, 0.001)
        assertEquals("郑远元旗舰店", result.storeName)
        assertTrue(result.paymentMethod?.contains("白条") == true)
        assertEquals("3648468013999049", result.orderNumber)
    }

    private val taobaoOrderDetailText = """
        09:52:35144N90-
        充值成功
        茉莉的卡券快充
        进店逛逛〉
        88VIP好评率98%，客服平均22秒回复
        哈啰单车月卡不限次1个月哈啰骑
        单车|24小时自动发货
        ¥21.8
        行卡30天一个月三个月季卡
        月卡
        哈啰单车骑行30天「仅限单车」充
        乐
        直号码：13817066976
        全国通
        实付价¥21.8
        申请售后
        ¥21.8
        实付款
        订单信息共6项
        5127314763021013439复制
        支付宝支付
        支付方式
        不***************
        收货信息
        2026100823001165011453404071
        支付宝交易号
        创建时间
        2026-10-0817:42:42
        2026-10-0817:42:59
        付款时间
        2026-10-0817:47:05
        成交时间
        订单保障
        凭据：10月8日下单交易快照>
        JISSBON
        水润呵护女性
        访生Pr
        8
        删除订单
        评价
        再买一笔
    """.trimIndent()

    @Test
    fun basicParser_parsesTaobaoOrderDetail() {
        val result = ReceiptParser().parse(taobaoOrderDetailText)

        assertEquals("茉莉的卡券快充", result.storeName)
        assertEquals("淘宝", result.sourceApp)
        assertEquals(21.8, result.amount!!, 0.001)
    }

    @Test
    fun configurableParser_parsesTaobaoOrderDetail() {
        val result = ConfigurableReceiptParser(ReceiptParseConfig.default()).parse(taobaoOrderDetailText)

        assertEquals("茉莉的卡券快充", result.storeName)
        assertEquals("淘宝", result.sourceApp)
        assertEquals(21.8, result.amount!!, 0.001)
    }

    private val taobaoOrderWithTotalText = """
        日90--
        10:15:24144
        充值成功
        茉莉的卡券快充
        8
        进店逛逛〉
        88VIP好评率98%，客服平均22秒回复
        哈啰单车月卡不限次1个月哈啰骑
        罗单车|24小时自动发货
        ¥21.8
        行卡30天一个月三个月季卡
        月卡
        哈啰单车骑行30天「仅限单车」充
        店
        直号码：13817066976
        全国通
        实付价¥21.8
        申请售后
        ¥21.8
        商品总价共1件
        ¥21.8^
        实付款
        订单信息共6项
        5127314763021013439复制
        支付方式
        支付宝支付
        不****************
        收货信息
        2026100823001165011453404071
        支付宝交易号
        2026-10-0817:42:42
        创建时间
        2026-10-0817:42:59
        付款时间
        2026-10-0817:47:05
        成交时间
        凭据：10月8日下单交易快照>
        订单保障
        JISSBON
        水润呵护女性
        删除订单
        评价
        再买一笔
        客服
    """.trimIndent()

    @Test
    fun basicParser_parsesTaobaoOrderDetailWithTotal() {
        val result = ReceiptParser().parse(taobaoOrderWithTotalText)

        assertEquals("茉莉的卡券快充", result.storeName)
        assertEquals("淘宝", result.sourceApp)
        assertEquals(21.8, result.amount!!, 0.001)
        assertEquals(21.8, result.originalAmount!!, 0.001)
    }

    @Test
    fun configurableParser_parsesTaobaoOrderDetailWithTotal() {
        val result = ConfigurableReceiptParser(ReceiptParseConfig.default()).parse(taobaoOrderWithTotalText)

        assertEquals("茉莉的卡券快充", result.storeName)
        assertEquals("淘宝", result.sourceApp)
        assertEquals(21.8, result.amount!!, 0.001)
        assertEquals(21.8, result.originalAmount!!, 0.001)
    }

    @Test
    fun configurableParser_dbConfigMissingOriginalKeyword_stillParsesOriginal() {
        val config = ReceiptParseConfig.default().copy(
            originalAmountKeywords = listOf("订单金额")
        )
        val result = ConfigurableReceiptParser(config).parse(taobaoOrderWithTotalText)

        assertEquals(21.8, result.originalAmount!!, 0.001)
    }

    private val taobaoBikeNoOriginalText = """
        日86
        11:31:06144支
        充值成功
        茉莉的卡券快充
        进店逛逛>
        88VIP好评率98%，客服平均22秒回复
        单车|24小时自动发货
        哈啰单车月卡不限次1个月哈哆...¥21.8
        哈啰单车骑行30天「仅限单车」
        月卡
        福
        实付价¥21.8
        申请售后
        ¥21.8
        实付款
        订单信息共6项
        5127314763021013439复制
        支付宝支付
        支付方式
        不★****************
        收货信息
        2026100823001165011453404071
        支付宝交易号
        2026-10-0817:42:42
        创建时间
        2026-10-0817:42:59
        付款时间
        2026-10-0817:47:05
        成交时间
        凭据：10月8日下单交易快照>
        订单保障
        豆包
        专业版会员
        AI智能助手高效创作
        再买一笔
        删除订单
        评价
    """.trimIndent()

    @Test
    fun basicParser_originalAmountMissing_fallsBackToAmount() {
        val result = ReceiptParser().parse(taobaoBikeNoOriginalText)

        assertEquals("茉莉的卡券快充", result.storeName)
        assertEquals("淘宝", result.sourceApp)
        assertEquals(21.8, result.amount!!, 0.001)
        assertNull(result.discount)
        assertEquals(21.8, result.originalAmount!!, 0.001)
    }

    @Test
    fun configurableParser_originalAmountMissing_fallsBackToAmount() {
        val result = ConfigurableReceiptParser(ReceiptParseConfig.default()).parse(taobaoBikeNoOriginalText)

        assertEquals(21.8, result.amount!!, 0.001)
        assertEquals(21.8, result.originalAmount!!, 0.001)
    }

    private val noOriginalWithDiscountText = """
        微信支付
        某商户
        交易成功
        实付金额 ¥100.00
        立减优惠¥10.00
    """.trimIndent()

    @Test
    fun basicParser_originalMissingWithDiscount_fallsBackToAmountPlusDiscount() {
        val result = ReceiptParser().parse(noOriginalWithDiscountText)

        assertEquals(100.0, result.amount!!, 0.001)
        assertEquals(10.0, result.discount!!, 0.001)
        assertEquals(110.0, result.originalAmount!!, 0.001)
    }

    private val taobaoDiscountBreakdownText = """
        日必80
        12:00:28144
        交易成功
        已签收新沪路198弄19号1101室
        曹先生86-135****6246
        FDD国际电工旗舰店
        进店逛逛〉
        国际电工
        88VIP好评率98%，客服满意度94%
        门铃家用无线呼叫器入户门...
        【白色】1个按键+1个响铃【家庭..
        退货宝15天价保假一赔四>
        1按键+1响铃
        实付价¥16.95价格明细>
        更多
        去看看>
        门铃按键装门外还是门内
        加入购物车
        申请售后
        闲鱼转卖
        ¥16.95
        买付款共减¥4.05优惠解析>
        3官方限时补贴抵¥3.7平台券抵¥0.35
        订单信息共8项
        5127478346253013439|复制
        支付宝支付
        支付方式
        曹**，86-135***6246,，上海上海市宝山
        收货信息
        X大场**
        天猫积分
        获得8点积分>
        2026092623001165011418065344
        支付宝交易号
        2026-09-2610:43:22
        创建时间
        2026-09-2610:43:45
        付款时间
        8
        再买一单
        加入购物车
        评价
        更多
        客服
    """.trimIndent()

    @Test
    fun basicParser_discountBreakdown_notDoubleCounted() {
        val result = ReceiptParser().parse(taobaoDiscountBreakdownText)

        assertEquals(16.95, result.amount!!, 0.001)
        assertEquals(4.05, result.discount!!, 0.001)
        assertEquals(21.0, result.originalAmount!!, 0.001)
        assertEquals("FDD国际电工旗舰店", result.storeName)
        assertEquals("淘宝", result.sourceApp)
    }

    @Test
    fun configurableParser_discountBreakdown_notDoubleCounted() {
        val result = ConfigurableReceiptParser(ReceiptParseConfig.default()).parse(taobaoDiscountBreakdownText)

        assertEquals(16.95, result.amount!!, 0.001)
        assertEquals(4.05, result.discount!!, 0.001)
        assertEquals(21.0, result.originalAmount!!, 0.001)
    }

    private val douyinOrderText = """
        日5414
        08:55:33144
        删除
        JO
        库迪咖啡（北京）有限公司
        -8.29
        支付成功
        ¥0.01
        抖音支付优惠
        2026-10-1306:33:22
        支付时间
        抖音月付
        支付方式
        目付账单信息占用月付额度，使用/核销后计入账单
        下单后抽免单
        免单奖励
        规则
        未抽中，下次再来抽免单
        S
        恭喜你，有机会获得20元支付红包
        已领取600积分，去兑好礼，
        抖音支付积分
        1114190942877815731
        商户单号
        2001022610130706741247834599
        交易单号
        点击收起へ
        商品订单
        冷热都好喝】小黄油拿铁
    """.trimIndent()

    @Test
    fun basicParser_parsesDouyinOrder() {
        val result = ReceiptParser().parse(douyinOrderText)

        assertEquals(8.29, result.amount!!, 0.001)
        assertEquals(0.01, result.discount!!, 0.001)
        assertEquals(8.30, result.originalAmount!!, 0.001)
        assertEquals("库迪咖啡（北京）有限公司", result.storeName)
        assertEquals("抖音月付", result.paymentMethod)
        assertEquals("抖音", result.sourceApp)
    }

    @Test
    fun configurableParser_parsesDouyinOrder() {
        val result = ConfigurableReceiptParser(ReceiptParseConfig.default()).parse(douyinOrderText)

        assertEquals(8.29, result.amount!!, 0.001)
        assertEquals(0.01, result.discount!!, 0.001)
        assertEquals(8.30, result.originalAmount!!, 0.001)
        assertEquals("库迪咖啡（北京）有限公司", result.storeName)
        assertEquals("抖音月付", result.paymentMethod)
        assertEquals("抖音", result.sourceApp)
    }
}