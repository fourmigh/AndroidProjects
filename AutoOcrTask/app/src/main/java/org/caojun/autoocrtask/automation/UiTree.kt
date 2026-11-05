package org.caojun.autoocrtask.automation

import android.util.Xml
import java.io.StringReader
import org.xmlpull.v1.XmlPullParser

data class UiBounds(
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

data class UiNode(
    val text: String?,
    val contentDesc: String?,
    val className: String?,
    val packageName: String?,
    val bounds: UiBounds?,
    val clickable: Boolean,
    val scrollable: Boolean,
    val children: MutableList<UiNode> = mutableListOf()
)

object UiTreeParser {

    private val BOUNDS = Regex("""\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]""")

    fun parse(xml: String): UiNode? = runCatching { parseInternal(xml) }.getOrNull()

    private fun parseInternal(xml: String): UiNode? {
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        var event = parser.eventType
        var root: UiNode? = null
        val stack = ArrayDeque<UiNode>()

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> if (parser.name == "node") {
                    val node = UiNode(
                        text = parser.getAttributeValue(null, "text"),
                        contentDesc = parser.getAttributeValue(null, "content-desc"),
                        className = parser.getAttributeValue(null, "class"),
                        packageName = parser.getAttributeValue(null, "package"),
                        bounds = parseBounds(parser.getAttributeValue(null, "bounds")),
                        clickable = parser.getAttributeValue(null, "clickable") == "true",
                        scrollable = parser.getAttributeValue(null, "scrollable") == "true"
                    )
                    if (stack.isEmpty()) {
                        root = node
                    } else {
                        stack.last().children.add(node)
                    }
                    stack.addLast(node)
                }

                XmlPullParser.END_TAG -> if (parser.name == "node" && stack.isNotEmpty()) {
                    stack.removeLast()
                }
            }
            event = parser.next()
        }
        return root
    }

    private fun parseBounds(value: String?): UiBounds? {
        val match = BOUNDS.matchEntire(value ?: return null) ?: return null
        val values = match.groupValues
        val left = values[1].toIntOrNull() ?: return null
        val top = values[2].toIntOrNull() ?: return null
        val right = values[3].toIntOrNull() ?: return null
        val bottom = values[4].toIntOrNull() ?: return null
        return UiBounds(left, top, right, bottom)
    }
}
