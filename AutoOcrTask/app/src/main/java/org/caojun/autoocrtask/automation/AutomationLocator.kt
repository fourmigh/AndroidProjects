package org.caojun.autoocrtask.automation

import kotlin.math.roundToInt
import org.caojun.shizuku.automation.ShizukuUiAutomationSession
import org.caojun.ai.ocr.api.OcrEngine

data class UiPoint(val x: Int, val y: Int)

/**
 * 文本定位：优先读取 UI 树，找不到时截图 OCR 兜底。
 */
class AutomationLocator(
    private val session: ShizukuUiAutomationSession,
    private val ocrProvider: suspend () -> OcrEngine?
) {

    suspend fun findText(text: String, exact: Boolean, useOcr: Boolean): UiPoint? {
        val normalized = normalize(text)
        if (normalized.isEmpty()) return null

        findInUiTree(normalized, exact)?.let { return it }
        if (!useOcr) return null
        return findInOcr(normalized, exact)
    }

    private suspend fun findInUiTree(normalized: String, exact: Boolean): UiPoint? {
        val xml = session.dumpUi() ?: return null
        val root = UiTreeParser.parse(xml) ?: return null

        var exactHit: UiPoint? = null
        var containsHit: UiPoint? = null

        fun visit(node: UiNode, clickableAncestor: UiPoint?) {
            val bounds = node.bounds
            val label = node.text?.trim()?.takeIf { it.isNotEmpty() }
                ?: node.contentDesc?.trim()?.takeIf { it.isNotEmpty() }
            if (bounds != null && label != null) {
                val candidate = normalize(label)
                val isExact = candidate == normalized
                val contains = candidate.contains(normalized)
                if (isExact || (contains && !exact)) {
                    val target = if (node.clickable || clickableAncestor == null) {
                        UiPoint(bounds.centerX, bounds.centerY)
                    } else {
                        clickableAncestor
                    }
                    if (isExact) {
                        if (exactHit == null) exactHit = target
                    } else if (containsHit == null) {
                        containsHit = target
                    }
                }
            }
            val next = if (node.clickable && bounds != null) {
                UiPoint(bounds.centerX, bounds.centerY)
            } else {
                clickableAncestor
            }
            node.children.forEach { visit(it, next) }
        }

        visit(root, null)
        return exactHit ?: containsHit
    }

    private suspend fun findInOcr(normalized: String, exact: Boolean): UiPoint? {
        val bitmap = session.screenshot() ?: return null
        try {
            val engine = ocrProvider() ?: return null
            val results = runCatching { engine.detect(bitmap) }.getOrNull() ?: return null
            var containsHit: UiPoint? = null
            for (result in results) {
                val candidate = normalize(result.text)
                val isExact = candidate == normalized
                val contains = candidate.contains(normalized)
                if (!isExact && !(contains && !exact)) continue
                val point = UiPoint(
                    result.center.x.roundToInt(),
                    result.center.y.roundToInt()
                )
                if (isExact) return point
                if (containsHit == null) containsHit = point
            }
            return containsHit
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun normalize(text: String): String =
        text.replace(Regex("\\s+"), "").lowercase()
}
