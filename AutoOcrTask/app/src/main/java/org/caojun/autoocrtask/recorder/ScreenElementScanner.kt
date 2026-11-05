package org.caojun.autoocrtask.recorder

import kotlin.math.abs
import kotlin.math.roundToInt
import org.caojun.autoocrtask.automation.UiBounds
import org.caojun.autoocrtask.automation.UiNode
import org.caojun.autoocrtask.automation.UiTreeParser
import org.caojun.shizuku.automation.ShizukuUiAutomationSession
import org.caojun.shotocr.ocr.OcrEngine

data class ScreenElement(
    val text: String,
    val x: Int,
    val y: Int,
    val clickable: Boolean,
    val fromOcr: Boolean
)

/**
 * 读取当前屏幕可点击元素：优先 UI 树，OCR 作为补充。
 * 返回按从上到下、从左到右排序的候选列表。
 */
class ScreenElementScanner(
    private val session: ShizukuUiAutomationSession,
    private val ocrProvider: suspend () -> OcrEngine?,
    private val minTopPx: Int,
    private val density: Float,
    private val appPackage: String
) {

    suspend fun scan(): List<ScreenElement> {
        val all = mutableListOf<ScreenElement>()

        runCatching { collectUiTree(all) }
        val uiClickable = dedupe(all).count { it.clickable }
        if (uiClickable < MIN_CLICKABLE_HINTS) {
            runCatching { collectOcr(all) }
        }

        val deduped = dedupe(all)
        val clickable = deduped.filter { it.clickable }
        val result = if (clickable.size >= MIN_CLICKABLE_HINTS) clickable else deduped
        return result.take(MAX_ITEMS)
    }

    private suspend fun collectUiTree(result: MutableList<ScreenElement>) {
        val xml = session.dumpUi() ?: return
        val root = UiTreeParser.parse(xml) ?: return

        fun visit(node: UiNode, clickableAncestor: UiBounds?) {
            if (node.packageName == appPackage) return
            val bounds = node.bounds
            val label = node.text?.trim()?.takeIf { it.isNotEmpty() }
                ?: node.contentDesc?.trim()?.takeIf { it.isNotEmpty() }
            if (bounds != null &&
                label != null &&
                label.length <= MAX_TEXT_LENGTH &&
                bounds.width > 0 &&
                bounds.height > 0 &&
                bounds.top >= minTopPx
            ) {
                val clickable = node.clickable || clickableAncestor != null
                val target = if (node.clickable || clickableAncestor == null) {
                    bounds
                } else {
                    clickableAncestor
                }
                result.add(
                    ScreenElement(
                        text = label,
                        x = target.centerX,
                        y = target.centerY,
                        clickable = clickable,
                        fromOcr = false
                    )
                )
            }

            val nextAncestor = if (node.clickable && bounds != null) bounds else clickableAncestor
            node.children.forEach { visit(it, nextAncestor) }
        }

        visit(root, null)
    }

    private suspend fun collectOcr(result: MutableList<ScreenElement>) {
        val bitmap = session.screenshot() ?: return
        try {
            val engine = ocrProvider() ?: return
            val results = runCatching { engine.detect(bitmap) }.getOrNull() ?: return
            for (item in results) {
                val text = item.text.trim()
                if (text.isEmpty() || text.length > MAX_TEXT_LENGTH) continue
                val x = item.center.x.roundToInt()
                val y = item.center.y.roundToInt()
                if (y < minTopPx) continue
                result.add(
                    ScreenElement(
                        text = text,
                        x = x,
                        y = y,
                        clickable = true,
                        fromOcr = true
                    )
                )
            }
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun dedupe(elements: List<ScreenElement>): List<ScreenElement> {
        val toleranceX = (60 * density).toInt()
        val toleranceY = (24 * density).toInt()
        val result = mutableListOf<ScreenElement>()

        for (element in elements) {
            val duplicate = result.any { existing ->
                normalize(existing.text) == normalize(element.text) &&
                    abs(existing.x - element.x) <= toleranceX &&
                    abs(existing.y - element.y) <= toleranceY
            }
            if (!duplicate) result.add(element)
        }

        return result.sortedWith(compareBy({ it.y }, { it.x }))
    }

    private fun normalize(text: String): String =
        text.replace(Regex("\\s+"), "")

    companion object {
        private const val MAX_ITEMS = 80
        private const val MAX_TEXT_LENGTH = 60
        private const val MIN_CLICKABLE_HINTS = 3
    }
}
