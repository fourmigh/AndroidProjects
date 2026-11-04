package org.caojun.shotocr.batch

import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlipayBatchProfileTest {

    private fun node(
        text: String? = null,
        bounds: UiBounds? = null,
        clickable: Boolean = false,
        scrollable: Boolean = false,
        children: List<UiNode> = emptyList()
    ) = UiNode(
        text = text,
        contentDesc = null,
        className = null,
        packageName = AlipayBatchProfile.ALIPAY_PACKAGE,
        bounds = bounds,
        clickable = clickable,
        scrollable = scrollable,
        children = children.toMutableList()
    )

    private fun row(merchant: String, amount: String, top: Int): UiNode {
        val rowBounds = UiBounds(40, top, 1040, top + 120)
        return node(
            bounds = rowBounds,
            clickable = true,
            children = listOf(
                node(text = merchant, bounds = UiBounds(60, top + 10, 600, top + 50)),
                node(text = "2026-10-20 12:30:00", bounds = UiBounds(60, top + 60, 600, top + 100)),
                node(text = amount, bounds = UiBounds(800, top + 10, 1000, top + 50))
            )
        )
    }

    @Test
    fun findRows_clickableRowsWithMoney() {
        val list = node(
            bounds = UiBounds(0, 400, 1080, 2200),
            scrollable = true,
            children = listOf(
                row("肯德基", "-12.34", 600),
                row("地铁", "¥ 3.00", 760)
            )
        )
        val root = node(bounds = UiBounds(0, 0, 1080, 2400), children = listOf(list))

        val rows = AlipayBatchProfile.findRows(root, density = 2f)

        assertEquals(2, rows.size)
        assertTrue(rows[0].text.contains("肯德基"))
        assertTrue(rows[1].text.contains("地铁"))
    }

    @Test
    fun findRows_fallsBackToLeafAmounts() {
        val list = node(
            bounds = UiBounds(0, 400, 1080, 2200),
            scrollable = true,
            children = listOf(
                node(text = "肯德基", bounds = UiBounds(60, 600, 600, 640)),
                node(text = "-12.34", bounds = UiBounds(800, 600, 1000, 640))
            )
        )
        val root = node(bounds = UiBounds(0, 0, 1080, 2400), children = listOf(list))

        val rows = AlipayBatchProfile.findRows(root, density = 2f)

        assertEquals(1, rows.size)
        assertEquals("-12.34", rows[0].text)
    }

    @Test
    fun findScrollable_picksLargestScrollable() {
        val small = node(bounds = UiBounds(0, 500, 1080, 800), scrollable = true)
        val large = node(bounds = UiBounds(0, 400, 1080, 2200), scrollable = true)
        val root = node(
            bounds = UiBounds(0, 0, 1080, 2400),
            children = listOf(small, large)
        )

        assertEquals(UiBounds(0, 400, 1080, 2200), AlipayBatchProfile.findScrollable(root))
    }

    @Test
    fun monthFromRoot_prefersHeaderOverRowDate() {
        val header = node(text = "2026年9月", bounds = UiBounds(0, 100, 1080, 200))
        val date = node(text = "2026-10-20 12:30:00", bounds = UiBounds(60, 500, 600, 540))
        val root = node(
            bounds = UiBounds(0, 0, 1080, 2400),
            children = listOf(header, date)
        )

        assertEquals(YearMonth.of(2026, 9), AlipayBatchProfile.monthFromRoot(root, 2400))
    }

    @Test
    fun monthFromRoot_ignoresHeaderBelowTopArea() {
        val header = node(text = "2026年9月", bounds = UiBounds(0, 1800, 1080, 1900))
        val root = node(bounds = UiBounds(0, 0, 1080, 2400), children = listOf(header))

        assertEquals(null, AlipayBatchProfile.monthFromRoot(root, 2400))
    }

    @Test
    fun isDetailPage_detectsAnchors() {
        val detail = node(
            text = "账单详情",
            bounds = UiBounds(0, 100, 1080, 200)
        )
        val list = node(
            text = "账单",
            bounds = UiBounds(0, 100, 1080, 200)
        )

        assertTrue(AlipayBatchProfile.isDetailPage(detail))
        assertFalse(AlipayBatchProfile.isDetailPage(list))
    }

    @Test
    fun summaryPageIsNotDetail() {
        val summary = node(
            text = "月度小结 默认不计入收支统计 为您推荐 家人保险",
            bounds = UiBounds(0, 100, 1080, 200)
        )

        assertTrue(AlipayBatchProfile.isSummaryPage("月度小结 支出分类 省钱建议"))
        assertFalse(AlipayBatchProfile.isDetailPage(summary))
    }

    @Test
    fun summaryCardRowIsRejected() {
        assertTrue(
            AlipayBatchProfile.isSummaryRow("支出 ¥7271.02 共176笔 查看统计设置")
        )
        assertTrue(
            AlipayBatchProfile.isSummaryRow("￥172.96 ￥0.00 收支分析")
        )
        assertFalse(
            AlipayBatchProfile.isSummaryRow("兰州纯汤牛肉面 -25.49 2026-10-07 12:46:07")
        )
    }

    @Test
    fun snapshot_reportsMonthAndRowStructure() {
        val header = node(
            text = "2026年10月",
            bounds = UiBounds(0, 100, 1080, 180)
        )
        val list = node(
            bounds = UiBounds(0, 400, 1080, 2200),
            scrollable = true,
            children = listOf(row("肯德基", "-12.34", 600))
        )
        val root = node(
            bounds = UiBounds(0, 0, 1080, 2400),
            children = listOf(header, list)
        )

        val snapshot = AlipayBatchProfile.snapshot(root, screenHeight = 2400, density = 2f)

        assertEquals(YearMonth.of(2026, 10), snapshot.month)
        assertEquals("2026年10月", snapshot.monthText)
        assertEquals(1, snapshot.rowCount)
        assertEquals(1, snapshot.monthLikeTextCount)
        assertTrue(snapshot.nodeCount >= 4)
        assertTrue(snapshot.clickableCount >= 1)
        assertTrue(snapshot.scrollableCount >= 1)
    }

    @Test
    fun findOrderNumber_readsVisiblePaymentOrder() {
        val root = node(
            bounds = UiBounds(0, 0, 1080, 2400),
            children = listOf(
                node(text = "订单号", bounds = UiBounds(60, 1000, 300, 1050)),
                node(
                    text = "2026100823001465011447014045",
                    bounds = UiBounds(300, 1000, 1000, 1050)
                )
            )
        )

        assertEquals(
            "2026100823001465011447014045",
            AlipayBatchProfile.findOrderNumber(root)
        )
    }

    @Test
    fun findOrderNumber_ignoresMerchantOrder() {
        val root = node(
            bounds = UiBounds(0, 0, 1080, 2400),
            children = listOf(
                node(text = "商家订单号", bounds = UiBounds(60, 1000, 300, 1050)),
                node(
                    text = "2026100823001465011447014045",
                    bounds = UiBounds(300, 1000, 1000, 1050)
                )
            )
        )

        assertNull(AlipayBatchProfile.findOrderNumber(root))
    }

    @Test
    fun findMoreButton_singleCandidate() {
        val bounds = UiBounds(900, 1200, 1000, 1260)
        val root = node(
            bounds = UiBounds(0, 0, 1080, 2400),
            children = listOf(
                node(text = "更多", bounds = bounds, clickable = true)
            )
        )

        val target = AlipayBatchProfile.findMoreButton(root)

        assertNotNull(target)
        assertEquals(bounds, target!!.bounds)
    }

    @Test
    fun findMoreButton_prefersOrderInformationContext() {
        val orderRow = node(
            bounds = UiBounds(40, 900, 1040, 1000),
            clickable = true,
            children = listOf(
                node(text = "订单信息", bounds = UiBounds(60, 910, 400, 950)),
                node(text = "更多", bounds = UiBounds(900, 910, 1000, 950))
            )
        )
        val merchantRow = node(
            bounds = UiBounds(40, 1100, 1040, 1200),
            clickable = true,
            children = listOf(
                node(text = "商户信息", bounds = UiBounds(60, 1110, 400, 1150)),
                node(text = "更多", bounds = UiBounds(900, 1110, 1000, 1150))
            )
        )
        val root = node(
            bounds = UiBounds(0, 0, 1080, 2400),
            children = listOf(orderRow, merchantRow)
        )

        val target = AlipayBatchProfile.findMoreButton(root)

        assertNotNull(target)
        assertEquals(UiBounds(900, 910, 1000, 950), target!!.bounds)
    }

    @Test
    fun findMoreButton_ignoresTopBarMenu() {
        val root = node(
            bounds = UiBounds(0, 0, 1080, 2400),
            children = listOf(
                node(text = "更多", bounds = UiBounds(900, 80, 1000, 140), clickable = true)
            )
        )

        assertNull(AlipayBatchProfile.findMoreButton(root))
    }

    @Test
    fun findMoreButton_ambiguousCandidatesReturnsNull() {
        val root = node(
            bounds = UiBounds(0, 0, 1080, 2400),
            children = listOf(
                node(text = "更多", bounds = UiBounds(900, 900, 1000, 950), clickable = true),
                node(text = "更多", bounds = UiBounds(900, 1200, 1000, 1250), clickable = true)
            )
        )

        assertNull(AlipayBatchProfile.findMoreButton(root))
    }
}
