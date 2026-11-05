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

data class ScanResult(
    val elements: List<ScreenElement>,
    val uiCount: Int,
    val ocrCount: Int,
    val totalCount: Int,
    val ocrUsed: Boolean,
    val ocrError: String?
)

/**
 * 读取当前屏幕文字：优先 UI 树，按需合并 OCR。
 * 默认只读 UI 树；[scan] 的 includeOcr=true 时执行截图 OCR。
 */
class ScreenElementScanner(
    private val session: ShizukuUiAutomationSession,
    private val ocrProvider: suspend () -> OcrEngine?,
    private val minTopPx: Int,
    private val density: Float,
    private val appPackage: String
) {

    suspend fun scan(includeOcr: Boolean = false): ScanResult {
        val ui = mutableListOf<ScreenElement>()
        runCatching { collectUiTree(ui) }
        val uiElements = dedupe(ui)

        var ocrElements: List<ScreenElement> = emptyList()
        var ocrError: String? = null
        if (includeOcr) {
            val ocr = mutableListOf<ScreenElement>()
            try {
                collectOcr(ocr)
                ocrElements = dedupe(ocr)
            } catch (t: Throwable) {
                ocrError = t.message ?: t.javaClass.simpleName
            }
        }

        val merged = dedupe(uiElements + ocrElements)
            .sortedWith(compareBy({ it.y }, { it.x }))
        return ScanResult(
            elements = merged.take(MAX_ITEMS),
            uiCount = uiElements.size,
            ocrCount = ocrElements.size,
            totalCount = merged.size,
            ocrUsed = includeOcr,
            ocrError = ocrError
        )
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
        val bitmap = session.screenshot()
            ?: throw IllegalStateException("SCREENSHOT_FAILED")
        try {
            val engine = ocrProvider()
                ?: throw IllegalStateException("OCR_UNAVAILABLE")
            val results = engine.detect(bitmap)
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

        return result
    }

    private fun normalize(text: String): String =
        text.replace(Regex("\\s+"), "")

    companion object {
        const val MAX_ITEMS = 200
        private const val MAX_TEXT_LENGTH = 60
    }
}
