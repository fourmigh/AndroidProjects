package org.caojun.ai.ocr.api

import org.json.JSONArray
import org.json.JSONObject

/** OCR 识别出的一行文本及其在图片中的像素坐标。 */
data class OcrLine(
    val text: String,
    val score: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun toJson(): JSONObject = JSONObject().apply {
        put("text", text)
        put("score", score.toDouble())
        put("left", left.toDouble())
        put("top", top.toDouble())
        put("right", right.toDouble())
        put("bottom", bottom.toDouble())
    }

    companion object {

        fun listToJson(lines: List<OcrLine>): String {
            val array = JSONArray()
            lines.forEach { array.put(it.toJson()) }
            return array.toString()
        }

        fun listFromJson(json: String): List<OcrLine> {
            if (json.isBlank()) return emptyList()
            val array = JSONArray(json)
            return buildList(array.length()) {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(
                        OcrLine(
                            text = o.optString("text"),
                            score = o.optDouble("score", 0.0).toFloat(),
                            left = o.optDouble("left", 0.0).toFloat(),
                            top = o.optDouble("top", 0.0).toFloat(),
                            right = o.optDouble("right", 0.0).toFloat(),
                            bottom = o.optDouble("bottom", 0.0).toFloat()
                        )
                    )
                }
            }
        }
    }
}

/** OCR 调用失败。code 见 [OcrApi] 的 ERROR_* 常量。 */
class OcrException(val code: Int, message: String? = null) :
    Exception(message ?: "OCR error ($code)")
