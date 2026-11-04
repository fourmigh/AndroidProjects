package org.caojun.shotocr.batch

import java.time.YearMonth
import org.caojun.shotocr.llm.OrderNumberResolver

data class OcrTextBox(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
}

data class OcrRow(
    val text: String,
    val signature: String,
    val bounds: OcrTextBox
)

data class MoreOcrTarget(
    val bounds: OcrTextBox
)

data class MonthPickerOcr(
    val years: List<OcrTextBox>,
    val months: List<OcrTextBox>,
    val confirm: OcrTextBox?
)

data class AlipayOcrSnapshot(
    val month: YearMonth?,
    val monthText: String?,
    val rawText: String,
    val rows: List<OcrRow>,
    val isDetail: Boolean,
    val moreTarget: MoreOcrTarget?,
    val monthLikeTextCount: Int,
    val textCount: Int,
    val orderTab: OcrTextBox? = null,
    val orderTabSelected: Boolean = false,
    val searchEntry: OcrTextBox? = null,
    val mineTab: OcrTextBox? = null,
    val billEntry: OcrTextBox? = null,
    val monthAnchor: OcrTextBox? = null,
    val monthPicker: MonthPickerOcr? = null
)

data class OcrTextSignals(
    val chars: Int,
    val amount: Boolean,
    val title: Boolean,
    val payment: Boolean,
    val order: Boolean,
    val time: Boolean
)

/**
 * 支付宝 XRiver 页面无法稳定 uiautomator dump，使用截图 OCR 的坐标做页面判断与点击。
 * 这里只处理纯文本/坐标，便于单元测试。
 */
object AlipayOcrAnalyzer {

    private const val MAX_ROWS = 40
    private val MONEY = Regex(
        """[+-]\s*[¥￥]?\s*\d+(?:,\d{3})*\.\d{1,2}""" +
            """|[¥￥]\s*\d+(?:,\d{3})*(?:\.\d{1,2})?""" +
            """|\d+(?:,\d{3})*\.\d{1,2}"""
    )
    private val MONTH_LABEL = Regex(
        """\d{4}\s*年\s*\d{1,2}\s*月|\d{4}[-/.]\d{1,2}|\d{1,2}\s*月(?:份)?|本月|当月"""
    )
    private val MORE_CONTEXT = listOf("订单信息", "账单信息", "交易信息", "订单详情")

    fun analyze(boxes: List<OcrTextBox>, screenHeight: Int): AlipayOcrSnapshot {
        val sorted = boxes.sortedWith(compareBy({ it.top }, { it.left }))
        val rawText = sorted.joinToString("\n") { it.text }
        val topLimit = screenHeight * 0.5f
        val topBoxes = sorted.filter { it.top <= topLimit }

        var month: YearMonth? = null
        var monthText: String? = null
        var monthBox: OcrTextBox? = null
        for (box in topBoxes) {
            val parsed = AlipayBatchProfile.parseMonth(box.text) ?: continue
            month = parsed
            monthText = MONTH_LABEL.find(box.text)?.value
            monthBox = box
            break
        }

        val monthLikeTextCount = topBoxes.count { it.text.contains("月") }
        val monthPicker = findMonthPicker(boxes, screenHeight)
        val summaryPage = AlipayBatchProfile.isSummaryPage(rawText)
        val summaryAnchor = boxes
            .filter { it.text.contains("收支分析") }
            .minByOrNull { it.top }
        val listHeaderBottom = boxes
            .filter { AlipayBatchProfile.isSummaryRow(it.text) }
            .maxOfOrNull { it.bottom }
        val rowTop = when {
            summaryAnchor != null &&
                (monthBox == null || summaryAnchor.top >= monthBox.top) ->
                maxOf(summaryAnchor.bottom + 8, listHeaderBottom?.plus(8) ?: 0)

            monthBox != null -> maxOf(monthBox.bottom + 8, listHeaderBottom?.plus(8) ?: 0)
            else -> (screenHeight * 0.12f).toInt()
        }
        val seen = mutableSetOf<String>()
        val rows = mutableListOf<OcrRow>()
        if (!summaryPage) {
            for (box in sorted) {
                if (box.top < rowTop || box.height < 8 || box.width < 20) continue
                if (!MONEY.containsMatchIn(box.text)) continue
                if (AlipayBatchProfile.isSummaryRow(box.text)) continue
                val signature = normalize(box.text)
                if (signature.isBlank() || !seen.add(signature)) continue
                rows.add(OcrRow(box.text, signature, box))
                if (rows.size >= MAX_ROWS) break
            }
        }

        val moreTarget = findMoreTarget(boxes, screenHeight)
        return AlipayOcrSnapshot(
            month = month,
            monthText = monthText,
            rawText = rawText,
            rows = rows,
            isDetail = !summaryPage && AlipayBatchProfile.containsDetailText(rawText),
            moreTarget = moreTarget,
            monthLikeTextCount = monthLikeTextCount,
            textCount = boxes.size,
            orderTab = findOrderTab(boxes, screenHeight),
            searchEntry = findSearchEntry(boxes, screenHeight),
            mineTab = findBottomTab(boxes, screenHeight, "我的"),
            billEntry = findBillEntry(boxes, screenHeight),
            monthAnchor = monthBox,
            monthPicker = monthPicker
        )
    }

    private fun findMonthPicker(
        boxes: List<OcrTextBox>,
        screenHeight: Int
    ): MonthPickerOcr? {
        val raw = boxes.joinToString(" ") { it.text }
        if (!raw.contains("确定") ||
            (!raw.contains("月份选择") && !raw.contains("自定义时间"))
        ) {
            return null
        }
        val minTop = (screenHeight * 0.25f).toInt()
        val maxTop = (screenHeight * 0.95f).toInt()
        val years = boxes.filter { box ->
            box.top in minTop..maxTop &&
                box.text.trim().matches(Regex("""\d{4}""")) &&
                box.text.trim().toIntOrNull()?.let { it in 2000..2100 } == true
        }
        val months = boxes.filter { box ->
            box.top in minTop..maxTop &&
                box.text.trim().matches(Regex("""\d{1,2}""")) &&
                box.text.trim().toIntOrNull()?.let { it in 1..12 } == true
        }
        val confirm = boxes
            .filter {
                it.text.trim() == "确定" || it.text.trim() == "完成"
            }
            .maxByOrNull { it.top }
            ?: return null
        if (months.isEmpty()) return null
        return MonthPickerOcr(years = years, months = months, confirm = confirm)
    }

    fun findBottomTab(
        boxes: List<OcrTextBox>,
        screenHeight: Int,
        label: String
    ): OcrTextBox? {
        val minTop = (screenHeight * 0.78f).toInt()
        return boxes
            .filter { it.text.trim() == label && it.top >= minTop && it.width <= 240 }
            .maxByOrNull { it.top }
    }

    fun findBillEntry(boxes: List<OcrTextBox>, screenHeight: Int): OcrTextBox? {
        val minTop = (screenHeight * 0.10f).toInt()
        val maxTop = (screenHeight * 0.45f).toInt()
        return boxes
            .filter {
                it.text.trim() == "账单" &&
                    it.top in minTop..maxTop &&
                    it.width <= 240
            }
            .minByOrNull { it.top }
    }

    /** 账单页顶部的搜索入口；进入后会显示“全部/支出/转账/退款/订单”分类标签。 */
    fun findSearchEntry(boxes: List<OcrTextBox>, screenHeight: Int): OcrTextBox? {
        val maxTop = (screenHeight * 0.12f).toInt()
        return boxes
            .filter { box ->
                box.text.contains("搜索") &&
                    box.top <= maxTop &&
                    box.width >= 40 &&
                    box.height >= 8
            }
            .minByOrNull { it.left }
    }

    /**
     * 顶部筛选栏里的“订单”标签。右上角也可能有一个“订单”快捷入口，
     * 因此取筛选栏范围内最靠下的“订单”，并排除详情页的“订单号”。
     */
    fun findOrderTab(boxes: List<OcrTextBox>, screenHeight: Int): OcrTextBox? {
        val minTop = (screenHeight * 0.08f).toInt()
        val maxTop = (screenHeight * 0.30f).toInt()
        return boxes
            .filter { box ->
                box.text.trim() == "订单" &&
                    box.top in minTop..maxTop &&
                    box.height >= 8 &&
                    box.width <= 240
            }
            .maxByOrNull { it.top }
    }

    private fun findMoreTarget(boxes: List<OcrTextBox>, screenHeight: Int): MoreOcrTarget? {
        val topLimit = screenHeight * 0.12f
        val candidates = boxes.filter {
            it.text.trim() == "更多" && it.top >= topLimit
        }
        if (candidates.size == 1) return MoreOcrTarget(candidates.first())

        val sections = boxes.filter { box ->
            MORE_CONTEXT.any { box.text.contains(it) }
        }
        if (sections.isEmpty()) return null

        val ranked = candidates.map { candidate ->
            val nearest = sections
                .filter { it.bottom <= candidate.top }
                .minByOrNull { candidate.top - it.bottom }
            candidate to (nearest?.let { candidate.top - it.bottom })
        }.filter { it.second != null }
        if (ranked.isEmpty()) return null
        val minDistance = ranked.minOf { it.second!! }
        val nearest = ranked.filter { it.second == minDistance }
        return if (nearest.size == 1) MoreOcrTarget(nearest.first().first) else null
    }

    fun findOrderNumber(text: String): String? =
        OrderNumberResolver.resolve(text)

    fun containsOrderNumber(text: String): Boolean =
        findOrderNumber(text) != null

    fun signals(text: String): OcrTextSignals = OcrTextSignals(
        chars = text.length,
        amount = MONEY.containsMatchIn(text) ||
            listOf("实付", "订单金额", "支付金额", "优惠", "立减").any(text::contains),
        title = listOf(
            "账单详情",
            "账单管理",
            "创建时间",
            "商品说明",
            "全部账单",
            "交易成功",
            "支付成功",
            "支付结果"
        ).any(text::contains),
        payment = listOf("付款方式", "支付方式").any(text::contains),
        order = listOf("订单号", "交易单号", "订单编号", "交易流水号")
            .any(text::contains),
        time = listOf("支付时间", "付款时间", "交易时间", "创建时间")
            .any(text::contains)
    )

    private fun normalize(text: String): String =
        text.replace(Regex("\\s+"), " ").trim()
}
