package org.caojun.shotocr.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptAmountResolverTest {

    private val ocr = """
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
        商品说明
        收钱码收款
        支付奖励
        已领取2积分>
        收款方全称
        *通(个人)
        订单号
        2026102023001465011448654929
        商家订单号
        17924512735002374665014
    """.trimIndent()

    private fun data(
        amount: Double?,
        discount: Double?,
        originalAmount: Double?
    ) = ReceiptData(
        amount = amount,
        discount = discount,
        originalAmount = originalAmount,
        storeName = "肯德基",
        paymentTime = null,
        paymentMethod = "网商银行储蓄卡(3841)",
        orderNumber = "2026100823001465011447014045",
        rawText = ocr
    )

    @Test
    fun correctsValuesCopiedFromExample() {
        val result = ReceiptAmountResolver.resolve(data(9.77, 1.43, 11.2), ocr)

        assertEquals(6.97, result.amount!!, 0.001)
        assertEquals(0.03, result.discount!!, 0.001)
        assertEquals(7.0, result.originalAmount!!, 0.001)
    }

    @Test
    fun keepsGroundedModelValues() {
        val text = "-9.77\n11.20\n订单金额\n网商银行福利-0.90\n支付18VIP到店-0.50"
        val result = ReceiptAmountResolver.resolve(data(9.77, 1.43, 11.2), text)

        assertEquals(9.77, result.amount!!, 0.001)
        assertEquals(1.43, result.discount!!, 0.001)
        assertEquals(11.2, result.originalAmount!!, 0.001)
    }

    @Test
    fun dateOnlyText_doesNotBecomePaidAmount() {
        val result = ReceiptAmountResolver.resolve(
            data(null, null, null),
            "2026-10-23 10:00:00"
        )

        assertNull(result.amount)
    }

    @Test
    fun dateAndAmountOnSameLine_extractsAmount() {
        val result = ReceiptAmountResolver.resolve(
            data(null, null, null),
            "2026-10-23 10:00:00 -8.88"
        )

        assertEquals(8.88, result.amount!!, 0.001)
    }
}
