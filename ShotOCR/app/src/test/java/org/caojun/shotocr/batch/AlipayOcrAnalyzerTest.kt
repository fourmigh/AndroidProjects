package org.caojun.shotocr.batch

import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlipayOcrAnalyzerTest {

    private fun box(
        text: String,
        top: Int,
        left: Int = 0,
        right: Int = 1000,
        height: Int = 40
    ) = OcrTextBox(text, left, top, right, top + height)

    @Test
    fun analyze_findsMonthAndAmountRows() {
        val boxes = listOf(
            box("2026年10月", 100, height = 50),
            box("肯德基 -12.34", 500),
            box("地铁 3.00", 620)
        )

        val snapshot = AlipayOcrAnalyzer.analyze(boxes, screenHeight = 2400)

        assertEquals(YearMonth.of(2026, 10), snapshot.month)
        assertEquals("2026年10月", snapshot.monthText)
        assertEquals(2, snapshot.rows.size)
        assertEquals(1, snapshot.monthLikeTextCount)
        assertEquals("肯德基 -12.34", snapshot.rows[0].text)
    }

    @Test
    fun analyze_parsesMonthHeaderWithArrow() {
        val boxes = listOf(
            box("10月 ▾", 100, height = 50),
            box("上海地铁 -3.70", 700)
        )

        val snapshot = AlipayOcrAnalyzer.analyze(boxes, screenHeight = 2400)

        assertEquals(YearMonth.of(YearMonth.now().year, 10), snapshot.month)
    }

    @Test
    fun analyze_ignoresRowDateAsMonthHeader() {
        val boxes = listOf(
            box("2026-10-20 12:30:00", 1800),
            box("-12.34", 1850)
        )

        val snapshot = AlipayOcrAnalyzer.analyze(boxes, screenHeight = 2400)

        assertNull(snapshot.month)
    }

    @Test
    fun analyze_ignoresHeaderWithTopBarMoreOnly() {
        val boxes = listOf(
            box("账单详情", 100),
            box("更多", 120),
            box("-12.34", 500)
        )

        val snapshot = AlipayOcrAnalyzer.analyze(boxes, screenHeight = 2400)

        assertNull(snapshot.moreTarget)
    }

    @Test
    fun analyze_selectsMoreNearestOrderSection() {
        val boxes = listOf(
            box("订单信息", 1000),
            box("更多", 1100, left = 800, right = 1000),
            box("商户信息", 1400),
            box("更多", 1500, left = 800, right = 1000)
        )

        val snapshot = AlipayOcrAnalyzer.analyze(boxes, screenHeight = 2400)

        assertNotNull(snapshot.moreTarget)
        assertEquals(1100, snapshot.moreTarget!!.bounds.top)
    }

    @Test
    fun findOrderTab_prefersFilterRowOverHeaderShortcut() {
        val boxes = listOf(
            box("订单", 200, left = 900, right = 1000),
            box("全部", 500, left = 100, right = 180),
            box("订单", 500, left = 650, right = 730),
            box("订单号", 900, left = 100, right = 220)
        )

        val tab = AlipayOcrAnalyzer.findOrderTab(boxes, screenHeight = 2400)

        assertNotNull(tab)
        assertEquals(500, tab!!.top)
    }

    @Test
    fun findSearchEntry_prefersTopSearchBox() {
        val boxes = listOf(
            box("搜索", 120, left = 140, right = 260),
            box("搜索", 120, left = 700, right = 820),
            box("兰州纯汤牛肉面", 900, left = 100, right = 500)
        )

        val entry = AlipayOcrAnalyzer.findSearchEntry(boxes, screenHeight = 2400)

        assertNotNull(entry)
        assertEquals(140, entry!!.left)
    }

    @Test
    fun findMineNavigationEntries() {
        val boxes = listOf(
            box("账单", 740, left = 130, right = 230),
            box("总资产", 1000, left = 130, right = 280),
            box("我的", 2550, left = 1000, right = 1110)
        )

        assertEquals(
            740,
            AlipayOcrAnalyzer.findBillEntry(boxes, screenHeight = 2688)!!.top
        )
        assertEquals(
            2550,
            AlipayOcrAnalyzer.findBottomTab(
                boxes,
                screenHeight = 2688,
                label = "我的"
            )!!.top
        )
    }

    @Test
    fun analyze_detectsDetailAndOrderText() {
        val boxes = listOf(
            box("账单详情", 100),
            box("订单号 2026100823001465011447014045", 900)
        )

        val snapshot = AlipayOcrAnalyzer.analyze(boxes, screenHeight = 2400)

        assertTrue(snapshot.isDetail)
        assertTrue(snapshot.rawText.contains("2026100823001465011447014045"))
    }

    @Test
    fun analyze_skipsSummaryAmountsAboveAnalysisAnchor() {
        val boxes = listOf(
            box("2026年10月", 100, height = 50),
            box("支出 ¥10,368.27", 260),
            box("收入 ¥21.94", 260, left = 500),
            box("收支分析", 340),
            box("蚂蚁财富-帮你投 400.00", 700)
        )

        val snapshot = AlipayOcrAnalyzer.analyze(boxes, screenHeight = 2400)

        assertEquals(1, snapshot.rows.size)
        assertEquals("蚂蚁财富-帮你投 400.00", snapshot.rows[0].text)
    }

    @Test
    fun analyze_detectsFundDetailAnchors() {
        val boxes = listOf(
            box("蚂蚁财富-蚂蚁（杭州）基金销售有限公司", 100),
            box("400.00", 260),
            box("账单管理", 600),
            box("创建时间", 720),
            box("2026-10-26 14:32:15", 760),
            box("商品说明", 840)
        )

        val snapshot = AlipayOcrAnalyzer.analyze(boxes, screenHeight = 2400)

        assertTrue(snapshot.isDetail)
        val signals = AlipayOcrAnalyzer.signals(snapshot.rawText)
        assertTrue(signals.amount)
        assertTrue(signals.title)
        assertTrue(signals.time)
    }

    @Test
    fun analyze_deduplicatesIdenticalRows() {
        val boxes = listOf(
            box("2026年10月", 100),
            box("肯德基 -12.34", 500),
            box("肯德基 -12.34", 620)
        )

        val snapshot = AlipayOcrAnalyzer.analyze(boxes, screenHeight = 2400)

        assertEquals(1, snapshot.rows.size)
    }

    @Test
    fun analyze_listPageIsNotDetail() {
        val boxes = listOf(
            box("2026年10月", 100),
            box("账单", 300),
            box("肯德基 -12.34", 500)
        )

        val snapshot = AlipayOcrAnalyzer.analyze(boxes, screenHeight = 2400)

        assertFalse(snapshot.isDetail)
    }

    @Test
    fun analyze_summaryPageIsNotDetailAndHasNoRows() {
        val boxes = listOf(
            box("2026年9月", 100, height = 50),
            box("支出", 240),
            box("¥7271.02", 300),
            box("共176笔 查看统计设置", 360),
            box("月度小结", 700),
            box("支出分类", 800),
            box("省钱建议", 800, left = 700),
            box("¥2.4万", 1200)
        )

        val snapshot = AlipayOcrAnalyzer.analyze(boxes, screenHeight = 2400)

        assertTrue(AlipayBatchProfile.isSummaryPage(snapshot.rawText))
        assertFalse(snapshot.isDetail)
        assertTrue(snapshot.rows.isEmpty())
    }

    @Test
    fun analyze_skipsSummaryCardAboveOrderRows() {
        val boxes = listOf(
            box("2026年10月", 100, height = 50),
            box("支出", 220),
            box("¥7271.02", 280),
            box("共176笔 查看统计设置", 340),
            box("兰州纯汤牛肉面 -25.49", 700),
            box("地铁 -3.00", 900)
        )

        val snapshot = AlipayOcrAnalyzer.analyze(boxes, screenHeight = 2400)

        assertEquals(2, snapshot.rows.size)
        assertEquals("兰州纯汤牛肉面 -25.49", snapshot.rows[0].text)
    }

    @Test
    fun analyze_exposesMonthHeaderAnchor() {
        val boxes = listOf(
            box("10月 ▾", 100, left = 100, right = 260, height = 50),
            box("兰州纯汤牛肉面 -25.49", 700)
        )

        val snapshot = AlipayOcrAnalyzer.analyze(boxes, screenHeight = 2400)

        assertNotNull(snapshot.monthAnchor)
        assertEquals(100, snapshot.monthAnchor!!.top)
    }

    @Test
    fun analyze_parsesMonthPicker() {
        val boxes = listOf(
            box("月份选择", 50),
            box("2025", 1000, left = 300),
            box("2026", 1100, left = 300),
            box("9", 1000, left = 700),
            box("10", 1100, left = 700),
            box("确定", 2500)
        )

        val snapshot = AlipayOcrAnalyzer.analyze(boxes, screenHeight = 2688)

        val picker = snapshot.monthPicker
        assertNotNull(picker)
        assertEquals(2, picker!!.years.size)
        assertEquals(2, picker.months.size)
        assertEquals("确定", picker.confirm!!.text)
    }
}
