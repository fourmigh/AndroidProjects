package org.caojun.hanzi

import org.caojun.hanzi.strokes.MedianPoint
import org.caojun.hanzi.strokes.PathCmd
import org.caojun.hanzi.strokes.StrokeCalc
import org.junit.Assert.assertEquals
import org.junit.Test

class StrokeCalcTest {
    @Test
    fun `polyline length sums segments`() {
        val pts = listOf(MedianPoint(0f, 0f), MedianPoint(3f, 0f), MedianPoint(3f, 4f))
        assertEquals(7f, StrokeCalc.polylineLength(pts), 0.001f)
    }

    @Test
    fun `stroke width is area over length`() {
        // 10 x 2 的矩形轮廓：面积 20，中心线长 10 → 平均粗细 2。
        val cmds = listOf(
            PathCmd.MoveTo(0f, 0f),
            PathCmd.LineTo(10f, 0f),
            PathCmd.LineTo(10f, 2f),
            PathCmd.LineTo(0f, 2f),
            PathCmd.Close,
        )
        assertEquals(2f, StrokeCalc.estimateWidth(cmds, 10f), 0.01f)
        assertEquals(0f, StrokeCalc.estimateWidth(cmds, 0f), 0.001f)
    }

    @Test
    fun `partial polyline stops at progress`() {
        val pts = listOf(MedianPoint(0f, 0f), MedianPoint(10f, 0f))
        val half = StrokeCalc.partialPolyline(pts, 0.5f)
        assertEquals(1, half.size)
        assertEquals(5f, half[0].second.x, 0.001f)
        val full = StrokeCalc.partialPolyline(pts, 1f)
        assertEquals(10f, full[0].second.x, 0.001f)
        assertEquals(0, StrokeCalc.partialPolyline(pts, 0f).size)
    }

    @Test
    fun `hit test finds nearest stroke and rejects far taps`() {
        val medians = listOf(
            listOf(MedianPoint(0f, 0f), MedianPoint(10f, 0f)),
            listOf(MedianPoint(0f, 10f), MedianPoint(10f, 10f)),
        )
        val map = { x: Float, y: Float -> MedianPoint(x, y) }
        assertEquals(0, StrokeCalc.hitTest(medians, map, 5f, 1f, 3f))
        assertEquals(1, StrokeCalc.hitTest(medians, map, 5f, 9f, 3f))
        assertEquals(-1, StrokeCalc.hitTest(medians, map, 5f, 30f, 3f))
    }
}
