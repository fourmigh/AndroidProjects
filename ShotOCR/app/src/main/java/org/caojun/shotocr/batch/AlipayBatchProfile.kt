package org.caojun.shotocr.batch

import java.time.YearMonth
import kotlin.math.abs
import org.caojun.shotocr.llm.OrderNumberResolver

data class BatchRow(
    val bounds: UiBounds,
    val signature: String,
    val text: String
)

data class MoreTarget(
    val bounds: UiBounds,
    val context: String
)

data class MonthCandidate(
    val month: YearMonth,
    val text: String,
    val top: Int
)

/** 诊断用 UI 快照；不包含商户、金额、订单号等文本。 */
data class AlipayUiSnapshot(
    val packageName: String?,
    val month: YearMonth?,
    val monthText: String?,
    val nodeCount: Int,
    val clickableCount: Int,
    val scrollableCount: Int,
    val moneyTextCount: Int,
    val monthLikeTextCount: Int,
    val rowCount: Int
)

/**
 * 支付宝账单列表的 UI 结构启发式。
 * 只依赖 bill list 的通用特征：可滚动容器、包含金额的可点击行、详情页锚点。
 */
object AlipayBatchProfile {

    const val ALIPAY_PACKAGE = "com.eg.android.AlipayGphone"

    private const val MAX_ROWS = 40
    private val DETAIL_ANCHORS = listOf(
        "账单详情",
        "账单管理",
        "创建时间",
        "商品说明",
        "查看往来记录",
        "对此订单有疑问",
        "订单号",
        "交易成功",
        "付款方式"
    )
    private val SUMMARY_MARKERS = listOf(
        "月度小结",
        "支出分类",
        "省钱建议",
        "月支出均值",
        "本月已解锁",
        "有投资理财交易"
    )
    private val SUMMARY_ROW_MARKERS = listOf(
        "查看统计",
        "收支分析",
        "月度小结",
        "支出分类",
        "省钱建议",
        "月支出均值"
    )
    private val SUMMARY_COUNT = Regex("""共\s*\d+\s*笔""")
    private val MONEY = Regex(
        """[+-]\s*[¥￥]?\s*\d+(?:,\d{3})*\.\d{1,2}""" +
            """|[¥￥]\s*\d+(?:,\d{3})*(?:\.\d{1,2})?""" +
            """|\d+(?:,\d{3})*\.\d{1,2}"""
    )
    private val YEAR_MONTH = Regex("""(\d{4})\s*年\s*(\d{1,2})\s*月""")
    private val YEAR_MONTH_DASH = Regex("""(\d{4})[-/.](\d{1,2})(?!\d)""")
    private val MONTH_ONLY = Regex("""^(\d{1,2})\s*月(?:份)?(?:账单)?$""")
    private val MONTH_HEADING = Regex("""^(\d{1,2})\s*月(?:份)?(?:账单)?""")
    private val MONTH_LABEL = Regex(
        """\d{4}\s*年\s*\d{1,2}\s*月|\d{4}[-/.]\d{1,2}|\d{1,2}\s*月(?:份)?|本月|当月"""
    )
    private val MORE_CONTEXT = listOf("订单信息", "账单信息", "交易信息", "订单详情")

    fun findRows(root: UiNode, density: Float): List<BatchRow> {
        val listBounds = findScrollable(root) ?: root.bounds ?: return emptyList()
        if (listBounds.width <= 0 || listBounds.height <= 0) return emptyList()
        if (isSummaryPage(root.allTexts().joinToString(" "))) return emptyList()

        val minHeight = 40f * density
        val maxHeight = 320f * density
        val rows = mutableListOf<BatchRow>()
        val seen = mutableSetOf<String>()

        fun add(bounds: UiBounds, text: String) {
            val signature = normalize(text)
            if (signature.isBlank() || !seen.add(signature)) return
            rows.add(BatchRow(bounds, signature, text))
        }

        val clickableNodes = mutableListOf<UiNode>()
        fun visit(node: UiNode) {
            val bounds = node.bounds
            if (bounds != null && bounds.width > 0 && bounds.height > 0 &&
                listBounds.contains(bounds) && node.clickable
            ) {
                clickableNodes.add(node)
            }
            node.children.forEach(::visit)
        }
        visit(root)

        for (node in clickableNodes) {
            val bounds = node.bounds ?: continue
            if (bounds.height < minHeight || bounds.height > maxHeight) continue
            if (bounds.width < listBounds.width * 0.5f) continue
            val text = node.allTexts().joinToString(" ")
            if (!MONEY.containsMatchIn(text)) continue
            add(bounds, text)
        }

        if (rows.isEmpty()) {
            addRowFromLeafText(root, listBounds, density, ::add)
        }

        return rows.sortedBy { it.bounds.top }.take(MAX_ROWS)
    }

    /** 部分列表没有可点击行节点，只暴露金额文本；按纵坐标把相邻文本聚成一行。 */
    private fun addRowFromLeafText(
        root: UiNode,
        listBounds: UiBounds,
        density: Float,
        add: (UiBounds, String) -> Unit
    ) {
        data class Group(var bounds: UiBounds, val texts: MutableList<String>)

        val groups = mutableListOf<Group>()
        val tolerance = 24f * density

        fun collect(node: UiNode) {
            val bounds = node.bounds
            val text = node.text?.trim()
            if (bounds != null && !text.isNullOrEmpty() && listBounds.contains(bounds) &&
                MONEY.containsMatchIn(text)
            ) {
                val group = groups.firstOrNull { abs(it.bounds.centerY - bounds.centerY) <= tolerance }
                if (group == null) {
                    groups.add(Group(bounds, mutableListOf(text)))
                } else {
                    group.bounds = group.bounds.union(bounds)
                    group.texts.add(text)
                }
            }
            node.children.forEach(::collect)
        }
        collect(root)

        groups.forEach { add(it.bounds, it.texts.joinToString(" ")) }
    }

    fun findScrollable(root: UiNode): UiBounds? {
        var best: UiBounds? = null
        fun visit(node: UiNode) {
            val bounds = node.bounds
            if (node.scrollable && bounds != null && bounds.area > (best?.area ?: 0L)) {
                best = bounds
            }
            node.children.forEach(::visit)
        }
        visit(root)
        return best
    }

    fun isDetailPage(root: UiNode): Boolean {
        val texts = root.allTexts().joinToString(" ")
        return !isSummaryPage(texts) && containsDetailText(texts)
    }

    fun containsDetailText(text: String): Boolean =
        DETAIL_ANCHORS.any { text.contains(it) }

    fun isSummaryPage(text: String): Boolean =
        SUMMARY_MARKERS.any { text.contains(it) }

    fun isSummaryRow(text: String): Boolean =
        SUMMARY_ROW_MARKERS.any { text.contains(it) } ||
            SUMMARY_COUNT.containsMatchIn(text)

    /** 从详情页可见文本里读取支付订单号（排除商家单号）。 */
    fun findOrderNumber(root: UiNode): String? {
        val entries = mutableListOf<Triple<Int, Int, String>>()

        fun collect(node: UiNode) {
            val bounds = node.bounds
            val text = node.text?.trim()?.takeIf { it.isNotEmpty() }
                ?: node.contentDesc?.trim()?.takeIf { it.isNotEmpty() }
            if (bounds != null && text != null) {
                entries.add(Triple(bounds.top, bounds.left, text))
            }
            node.children.forEach(::collect)
        }
        collect(root)

        if (entries.isEmpty()) return null
        val visualText = entries
            .sortedWith(compareBy({ it.first }, { it.second }))
            .joinToString("\n") { it.third }
        return OrderNumberResolver.resolve(visualText)
    }

    /**
     * 找“更多”按钮。只有一个候选时直接返回；多个候选仅接受上下文
     * 明确属于订单/账单/交易信息的那个，否则返回 null，避免误点。
     */
    fun findMoreButton(root: UiNode): MoreTarget? {
        val candidates = mutableListOf<MoreTarget>()
        val rootBounds = root.bounds
        val minMoreTop = rootBounds?.let { it.top + (it.height * 0.12f).toInt() } ?: 0

        fun visit(node: UiNode, clickableAncestor: UiNode?, ancestorContext: String) {
            val bounds = node.bounds
            val label = node.text?.trim()?.takeIf { it.isNotEmpty() }
                ?: node.contentDesc?.trim()?.takeIf { it.isNotEmpty() }
            val action = if (node.clickable) node else clickableAncestor

            if (label == "更多" && bounds != null && action != null && bounds.top >= minMoreTop) {
                val context = (action.allTexts().joinToString(" ") + " " + ancestorContext).trim()
                candidates.add(MoreTarget(bounds, context))
            }

            val nextClickable = if (node.clickable) node else clickableAncestor
            val nextContext = listOf(
                ancestorContext,
                node.text?.trim().orEmpty(),
                node.contentDesc?.trim().orEmpty()
            ).filter { it.isNotEmpty() }.joinToString(" ")
            node.children.forEach { visit(it, nextClickable, nextContext) }
        }
        visit(root, null, "")

        val distinct = candidates.distinctBy { "${it.bounds}|${it.context}" }
        if (distinct.size == 1) return distinct[0]

        return distinct
            .filter { candidate -> MORE_CONTEXT.any { candidate.context.contains(it) } }
            .singleOrNull()
    }

    /** 只在屏幕上部找月份标题，避免把列表中的日期误当成月份。 */
    fun monthFromRoot(root: UiNode, screenHeight: Int): YearMonth? =
        findMonthCandidate(root, screenHeight)?.month

    fun findMonthCandidate(root: UiNode, screenHeight: Int): MonthCandidate? {
        var result: MonthCandidate? = null
        val maxTop = screenHeight * 0.5f

        fun visit(node: UiNode) {
            if (result != null) return
            val bounds = node.bounds
            val text = node.text?.trim()?.takeIf { it.isNotEmpty() }
                ?: node.contentDesc?.trim()?.takeIf { it.isNotEmpty() }
            if (bounds != null && text != null && text.length <= 32 && bounds.top <= maxTop) {
                parseMonth(text)?.let {
                    result = MonthCandidate(it, text, bounds.top)
                    return
                }
            }
            node.children.forEach(::visit)
        }
        visit(root)
        return result
    }

    /** 统计 UI 结构，供批量诊断日志使用；不读取业务文本内容。 */
    fun snapshot(root: UiNode, screenHeight: Int, density: Float): AlipayUiSnapshot {
        var nodes = 0
        var clickable = 0
        var scrollable = 0
        var moneyTexts = 0
        var monthLikeTexts = 0
        val monthTop = screenHeight * 0.5f

        fun visit(node: UiNode) {
            nodes++
            if (node.clickable) clickable++
            if (node.scrollable) scrollable++
            val text = node.text ?: node.contentDesc
            if (!text.isNullOrBlank()) {
                if (MONEY.containsMatchIn(text)) moneyTexts++
                if (node.bounds != null && node.bounds.top <= monthTop && text.contains("月")) {
                    monthLikeTexts++
                }
            }
            node.children.forEach(::visit)
        }
        visit(root)

        val monthCandidate = findMonthCandidate(root, screenHeight)
        return AlipayUiSnapshot(
            packageName = root.packageName,
            month = monthCandidate?.month,
            monthText = monthCandidate?.text?.let { MONTH_LABEL.find(it)?.value },
            nodeCount = nodes,
            clickableCount = clickable,
            scrollableCount = scrollable,
            moneyTextCount = moneyTexts,
            monthLikeTextCount = monthLikeTexts,
            rowCount = findRows(root, density).size
        )
    }

    internal fun parseMonth(text: String): YearMonth? {
        YEAR_MONTH.find(text)?.let {
            val year = it.groupValues[1].toIntOrNull() ?: return null
            val month = it.groupValues[2].toIntOrNull() ?: return null
            if (year in 2000..2100 && month in 1..12) return YearMonth.of(year, month)
        }
        YEAR_MONTH_DASH.find(text)?.let {
            val year = it.groupValues[1].toIntOrNull() ?: return null
            val month = it.groupValues[2].toIntOrNull() ?: return null
            if (year in 2000..2100 && month in 1..12) return YearMonth.of(year, month)
        }
        if (text.contains("本月") || text.contains("当月")) return YearMonth.now()
        val monthMatch = MONTH_ONLY.matchEntire(text) ?: MONTH_HEADING.find(text)
        monthMatch?.let {
            val month = it.groupValues[1].toIntOrNull() ?: return null
            if (month in 1..12) return YearMonth.of(YearMonth.now().year, month)
        }
        return null
    }

    private fun normalize(text: String): String = text.replace(Regex("\\s+"), " ").trim()
}
