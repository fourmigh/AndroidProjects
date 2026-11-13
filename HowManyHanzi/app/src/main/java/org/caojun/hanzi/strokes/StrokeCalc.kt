package org.caojun.hanzi.strokes

import kotlin.math.abs

/** 书写动画用的几何计算，移植自小程序 lib/strokes.js。 */
object StrokeCalc {
    /** 中心线总长（原始字形坐标）。 */
    fun polylineLength(points: List<MedianPoint>): Float {
        var total = 0f
        for (i in 0 until points.size - 1) {
            val dx = points[i + 1].x - points[i].x
            val dy = points[i + 1].y - points[i].y
            total += kotlin.math.sqrt(dx * dx + dy * dy)
        }
        return total
    }

    /**
     * 估算笔画平均粗细：轮廓面积 ÷ 中心线长度。
     * 轮廓由外圈与内孔组成，按子路径求有符号面积再相加，即为笔画实际覆盖面积。
     */
    fun estimateWidth(commands: List<PathCmd>, medianLength: Float): Float {
        if (commands.isEmpty() || medianLength <= 0f) return 0f

        val points = ArrayList<Float>(64)
        var area = 0f
        var cursorX = 0f
        var cursorY = 0f

        fun flush() {
            val n = points.size / 2
            if (n >= 3) {
                var sum = 0f
                for (i in 0 until n) {
                    val px = points[i * 2]
                    val py = points[i * 2 + 1]
                    val qx = points[((i + 1) % n) * 2]
                    val qy = points[((i + 1) % n) * 2 + 1]
                    sum += px * qy - qx * py
                }
                area += sum / 2f
            }
            points.clear()
        }

        fun add(x: Float, y: Float) {
            points.add(x)
            points.add(y)
        }

        fun sampleQuad(c: PathCmd.QuadTo) {
            val x0 = cursorX
            val y0 = cursorY
            for (s in 1..8) {
                val t = s / 8f
                val mt = 1f - t
                add(
                    mt * mt * x0 + 2f * mt * t * c.cx + t * t * c.x,
                    mt * mt * y0 + 2f * mt * t * c.cy + t * t * c.y,
                )
            }
            cursorX = c.x
            cursorY = c.y
        }

        fun sampleCubic(c: PathCmd.CubicTo) {
            val x0 = cursorX
            val y0 = cursorY
            for (s in 1..10) {
                val t = s / 10f
                val mt = 1f - t
                val a = mt * mt * mt
                val b = 3f * mt * mt * t
                val d = 3f * mt * t * t
                val e = t * t * t
                add(
                    a * x0 + b * c.c1x + d * c.c2x + e * c.x,
                    a * y0 + b * c.c1y + d * c.c2y + e * c.y,
                )
            }
            cursorX = c.x
            cursorY = c.y
        }

        for (c in commands) {
            when (c) {
                is PathCmd.MoveTo -> {
                    flush()
                    cursorX = c.x
                    cursorY = c.y
                    add(c.x, c.y)
                }
                is PathCmd.LineTo -> {
                    add(c.x, c.y)
                    cursorX = c.x
                    cursorY = c.y
                }
                is PathCmd.QuadTo -> sampleQuad(c)
                is PathCmd.CubicTo -> sampleCubic(c)
                PathCmd.Close -> flush()
            }
        }
        flush()

        return abs(area) / medianLength
    }

    /**
     * 点击命中：返回离点击点最近的笔画索引，超出 maxDist 返回 -1。
     * map 把原始字形坐标映射到画布坐标。
     */
    fun hitTest(
        medians: List<List<MedianPoint>>,
        map: (Float, Float) -> MedianPoint,
        x: Float,
        y: Float,
        maxDist: Float,
    ): Int {
        var best = -1
        var bestDist = maxDist * maxDist

        fun test(index: Int, px: Float, py: Float) {
            val dx = px - x
            val dy = py - y
            val dist = dx * dx + dy * dy
            if (dist <= bestDist) {
                bestDist = dist
                best = index
            }
        }

        for (i in medians.indices) {
            val pts = medians[i]
            if (pts.isEmpty()) continue
            if (pts.size == 1) {
                val p = map(pts[0].x, pts[0].y)
                test(i, p.x, p.y)
                continue
            }
            var prev = map(pts[0].x, pts[0].y)
            for (j in 1 until pts.size) {
                val next = map(pts[j].x, pts[j].y)
                val vx = next.x - prev.x
                val vy = next.y - prev.y
                val len2 = vx * vx + vy * vy
                var t = 0f
                if (len2 > 0f) {
                    t = ((x - prev.x) * vx + (y - prev.y) * vy) / len2
                    t = t.coerceIn(0f, 1f)
                }
                test(i, prev.x + vx * t, prev.y + vy * t)
                prev = next
            }
        }
        return best
    }

    /**
     * 沿中心线取前 [progress]（0..1）比例的点，返回每段端点对，
     * 最后一段可能是不完整段，用 endPartial 标记。
     */
    fun partialPolyline(
        points: List<MedianPoint>,
        progress: Float,
    ): List<Pair<MedianPoint, MedianPoint>> {
        if (points.size < 2) return emptyList()
        val total = polylineLength(points)
        if (total <= 0f) return emptyList()
        val target = total * progress.coerceIn(0f, 1f)
        if (target <= 0f) return emptyList()
        val out = ArrayList<Pair<MedianPoint, MedianPoint>>(points.size)
        var walked = 0f
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            val dx = b.x - a.x
            val dy = b.y - a.y
            val len = kotlin.math.sqrt(dx * dx + dy * dy)
            if (len <= 0f) continue
            if (walked + len <= target) {
                out.add(a to b)
                walked += len
            } else {
                val ratio = ((target - walked) / len).coerceIn(0f, 1f)
                out.add(a to MedianPoint(a.x + dx * ratio, a.y + dy * ratio))
                break
            }
        }
        return out
    }
}
