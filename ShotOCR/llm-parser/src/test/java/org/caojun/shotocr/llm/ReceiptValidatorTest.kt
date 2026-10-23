package org.caojun.shotocr.llm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptValidatorTest {

    private fun data(
        storeName: String? = null,
        orderNumber: String? = null
    ) = ReceiptData(
        amount = null,
        discount = null,
        originalAmount = null,
        storeName = storeName,
        paymentTime = null,
        paymentMethod = null,
        orderNumber = orderNumber,
        rawText = ""
    )

    @Test
    fun alipayReceipt_isValid() {
        val text = """
            13:00:59144H支天福
            日为90
            账单详情
            胡记锅贴
            -6-97
            交易成功
            订单金额
            7.00
            支付遇财神√-0.03
            支付时间
            2026-10-2007:07:57
            付款方式
            花呗>
            订单号
            2026102023001465011448654929
            商家订单号
            17924512735002374665014
        """.trimIndent()

        assertTrue(ReceiptValidator.isReceipt(text, data()))
    }

    @Test
    fun wechatReceipt_isValid() {
        val text = """
            全部账单
            上海市闵行中学
            -461.38
            原价
            ¥462.00
            银行卡立减金优惠¥0.62
            支付时间
            2026-10-1317:11:55
            兴业银行储蓄卡(5719)
            支付方式
            交易单号
            4500000426202610139083205733
        """.trimIndent()

        assertTrue(ReceiptValidator.isReceipt(text, data()))
    }

    @Test
    fun unionPayReceipt_isValid() {
        val text = """
            云闪付交易详情
            上海公共交通卡股份有限公司
            -￥2.50
            尤惠信息
            动-¥0.50
            ¥3.00
            订单金额
            上海银行银联信用卡9320
            付款方式
            交易流水号
            334425102113135896808
        """.trimIndent()

        assertTrue(ReceiptValidator.isReceipt(text, data()))
    }

    @Test
    fun taobaoReceipt_isValid() {
        val text = """
            充值成功
            茉莉的卡券快充
            进店逛逛>
            哈啰单车月卡
            实付价¥21.8
            订单信息共6项
            支付宝交易号
            2026100823001165011453404071
            抖音月付
            支付方式
        """.trimIndent()

        assertTrue(ReceiptValidator.isReceipt(text, data()))
    }

    @Test
    fun jdReceipt_isValid() {
        val text = """
            郑远元旗舰店
            实付款
            共减¥28.63合计¥126.37
            订单编号
            3648468013999049
            支付方式
            白条账单
        """.trimIndent()

        assertTrue(ReceiptValidator.isReceipt(text, data()))
    }

    @Test
    fun douyinReceipt_isValid() {
        val text = """
            库迪咖啡（北京）有限公司
            -8.29
            支付成功
            ¥0.01
            抖音支付优惠
            抖音月付
            支付方式
            交易单号
            2001022610130706741247834599
        """.trimIndent()

        assertTrue(ReceiptValidator.isReceipt(text, data()))
    }

    @Test
    fun minimalPaymentResult_isValid() {
        val text = """
            支付成功
            ¥8.88
            星巴克咖啡
            微信支付
        """.trimIndent()

        assertTrue(
            ReceiptValidator.isReceipt(
                text,
                data(storeName = "星巴克咖啡")
            )
        )
    }

    @Test
    fun homeScreen_isInvalid() {
        val text = """
            设置
            WLAN
            蓝牙
            显示
            声音
            电池
            存储
            关于手机
        """.trimIndent()

        assertFalse(ReceiptValidator.isReceipt(text, data()))
    }

    @Test
    fun chatScreen_isInvalid() {
        val text = """
            微信
            张三
            晚上一起吃饭吗？
            好的
            明天见
        """.trimIndent()

        assertFalse(ReceiptValidator.isReceipt(text, data()))
    }

    @Test
    fun dateOnlyScreen_isInvalid() {
        val text = """
            2026-10-23 10:00:00
            2026-10-22 09:30:00
            -10
            -23
        """.trimIndent()

        assertFalse(ReceiptValidator.isReceipt(text, data()))
    }

    @Test
    fun productPage_isInvalid() {
        val text = """
            商品详情
            ¥99.00
            原价 ¥129.00
            优惠 ¥30.00
            立即购买
        """.trimIndent()

        assertFalse(ReceiptValidator.isReceipt(text, data()))
    }

    @Test
    fun blankText_isInvalid() {
        assertFalse(ReceiptValidator.isReceipt("", data()))
    }
}
