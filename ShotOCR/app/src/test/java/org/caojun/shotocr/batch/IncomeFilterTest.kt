package org.caojun.shotocr.batch

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomeFilterTest {

    @Test
    fun positiveAmountIsIncome() {
        assertTrue(IncomeFilter.isIncome("转账-北京某公司 +0.02"))
        assertTrue(IncomeFilter.isIncome("+0.03\n2026-10-27 07:10:40"))
        assertTrue(IncomeFilter.isIncome("＋0.02"))
    }

    @Test
    fun refundIsIncome() {
        assertTrue(IncomeFilter.isIncome("退款成功\n原路退回"))
    }

    @Test
    fun normalExpenseIsNotIncome() {
        assertFalse(IncomeFilter.isIncome("上海接地气餐饮管理有限公司 -18.62"))
        assertFalse(IncomeFilter.isIncome("订单金额 19.50\n400.00"))
        assertFalse(IncomeFilter.isIncome("商家可扫码退款或查询交易"))
    }

    @Test
    fun dateAndOrderNumberAreNotIncome() {
        assertFalse(IncomeFilter.isIncome("2026-10-26 18:35:27\n订单号 20261026"))
        assertFalse(IncomeFilter.isIncome("订单号 202610260191301759900100449810"))
    }

    @Test
    fun floatingStatusIncomeLabelIsNotIncome() {
        assertFalse(
            IncomeFilter.isIncome(
                "批量识别 · 已处理 2 · 成功 0 · 重复 0 · 收入 2 · 跳过 0 · 失败 0"
            )
        )
    }
}
