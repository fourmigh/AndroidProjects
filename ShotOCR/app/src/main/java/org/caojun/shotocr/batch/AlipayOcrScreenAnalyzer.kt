package org.caojun.shotocr.batch

import android.graphics.Bitmap
import kotlin.math.roundToInt
import org.caojun.ai.ocr.api.OcrEngine

/** 把 OcrEngine 的结果转换为支付宝批量导航使用的纯坐标模型。 */
class AlipayOcrScreenAnalyzer(private val ocr: OcrEngine) {

    suspend fun analyze(bitmap: Bitmap): AlipayOcrSnapshot {
        val results = ocr.detect(bitmap)
        val boxes = results.map { result ->
            OcrTextBox(
                text = result.text,
                left = result.box.left.roundToInt(),
                top = result.box.top.roundToInt(),
                right = result.box.right.roundToInt(),
                bottom = result.box.bottom.roundToInt()
            )
        }
        val snapshot = AlipayOcrAnalyzer.analyze(boxes, bitmap.height)
        val orderTab = snapshot.orderTab
        return snapshot.copy(
            orderTabSelected = orderTab != null && hasBlueText(bitmap, orderTab)
        )
    }

    private fun hasBlueText(bitmap: Bitmap, box: OcrTextBox): Boolean {
        val left = box.left.coerceIn(0, bitmap.width - 1)
        val right = box.right.coerceIn(0, bitmap.width)
        val top = box.top.coerceIn(0, bitmap.height - 1)
        val bottom = box.bottom.coerceIn(0, bitmap.height)
        var bluePixels = 0
        var y = top
        while (y < bottom) {
            var x = left
            while (x < right) {
                val color = bitmap.getPixel(x, y)
                val r = (color shr 16) and 0xFF
                val g = (color shr 8) and 0xFF
                val b = color and 0xFF
                if (b >= 140 && b > r + 25 && b > g + 5) bluePixels++
                x += 2
            }
            y += 2
        }
        return bluePixels >= 8
    }
}
