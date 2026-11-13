package org.caojun.hanzi.strokes

/**
 * 极简 SVG path 解析器，移植自小程序 lib/svgpath.js。
 * hanzi-writer-data 实际只用到 M/L/Q/C/Z，其余命令按标准支持。
 */
sealed interface PathCmd {
    data class MoveTo(val x: Float, val y: Float) : PathCmd
    data class LineTo(val x: Float, val y: Float) : PathCmd
    data class QuadTo(val cx: Float, val cy: Float, val x: Float, val y: Float) : PathCmd
    data class CubicTo(
        val c1x: Float, val c1y: Float,
        val c2x: Float, val c2y: Float,
        val x: Float, val y: Float,
    ) : PathCmd
    data object Close : PathCmd
}

object SvgPath {
    private val tokenRegex =
        Regex("([a-zA-Z])|(-?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?)")

    private fun tokenize(d: String): List<Any> {
        val out = ArrayList<Any>(64)
        for (m in tokenRegex.findAll(d)) {
            val letter = m.groupValues[1]
            if (letter.isNotEmpty()) out.add(letter) else out.add(m.groupValues[2].toFloat())
        }
        return out
    }

    private fun argCount(cmd: String): Int = when (cmd) {
        "M", "L" -> 2
        "H", "V" -> 1
        "C" -> 6
        "S", "Q" -> 4
        "T" -> 2
        "A" -> 7
        "Z" -> 0
        else -> -1
    }

    fun parse(d: String?): List<PathCmd> {
        if (d.isNullOrEmpty()) return emptyList()
        val tokens = tokenize(d)
        val out = ArrayList<PathCmd>(32)
        var i = 0
        var cmd: String? = null
        var x = 0f
        var y = 0f
        var startX = 0f
        var startY = 0f
        var cubicCx = 0f
        var cubicCy = 0f
        var cubicSet = false
        var quadCx = 0f
        var quadCy = 0f
        var quadSet = false

        while (i < tokens.size) {
            val t = tokens[i]
            if (t is String) {
                cmd = t
                i++
            }
            val current = cmd ?: break
            val upper = current.uppercase()
            val rel = current != upper

            if (upper == "Z") {
                out.add(PathCmd.Close)
                x = startX
                y = startY
                cubicSet = false
                quadSet = false
                cmd = null
                continue
            }

            val need = argCount(upper)
            if (need < 0) {
                i++
                cmd = null
                continue
            }

            if (i + need > tokens.size) break
            val args = FloatArray(need)
            var ok = true
            for (k in 0 until need) {
                val v = tokens[i + k]
                if (v !is Float) {
                    ok = false
                    break
                }
                args[k] = v
            }
            if (!ok) break
            i += need

            val ax = args.copyOf()
            if (upper == "A") {
                if (rel) {
                    ax[5] += x
                    ax[6] += y
                }
            } else if (rel) {
                var k = 0
                while (k < need) {
                    ax[k] += x
                    if (k + 1 < need) ax[k + 1] += y
                    k += 2
                }
            }

            when (upper) {
                "M" -> {
                    x = ax[0]; y = ax[1]
                    startX = x; startY = y
                    out.add(PathCmd.MoveTo(x, y))
                    cubicSet = false; quadSet = false
                    cmd = if (rel) "l" else "L"
                }
                "L" -> {
                    x = ax[0]; y = ax[1]
                    out.add(PathCmd.LineTo(x, y))
                    cubicSet = false; quadSet = false
                }
                "H" -> {
                    x = if (rel) x + args[0] else args[0]
                    out.add(PathCmd.LineTo(x, y))
                    cubicSet = false; quadSet = false
                }
                "V" -> {
                    y = if (rel) y + args[0] else args[0]
                    out.add(PathCmd.LineTo(x, y))
                    cubicSet = false; quadSet = false
                }
                "C" -> {
                    out.add(PathCmd.CubicTo(ax[0], ax[1], ax[2], ax[3], ax[4], ax[5]))
                    cubicCx = ax[2]; cubicCy = ax[3]; cubicSet = true
                    quadSet = false
                    x = ax[4]; y = ax[5]
                }
                "S" -> {
                    val c1x = if (cubicSet) 2 * x - cubicCx else x
                    val c1y = if (cubicSet) 2 * y - cubicCy else y
                    out.add(PathCmd.CubicTo(c1x, c1y, ax[0], ax[1], ax[2], ax[3]))
                    cubicCx = ax[0]; cubicCy = ax[1]; cubicSet = true
                    quadSet = false
                    x = ax[2]; y = ax[3]
                }
                "Q" -> {
                    out.add(PathCmd.QuadTo(ax[0], ax[1], ax[2], ax[3]))
                    quadCx = ax[0]; quadCy = ax[1]; quadSet = true
                    cubicSet = false
                    x = ax[2]; y = ax[3]
                }
                "T" -> {
                    val cx = if (quadSet) 2 * x - quadCx else x
                    val cy = if (quadSet) 2 * y - quadCy else y
                    out.add(PathCmd.QuadTo(cx, cy, ax[0], ax[1]))
                    quadCx = cx; quadCy = cy; quadSet = true
                    cubicSet = false
                    x = ax[0]; y = ax[1]
                }
                "A" -> {
                    // 数据中未出现圆弧，退化为直线到终点
                    x = ax[5]; y = ax[6]
                    out.add(PathCmd.LineTo(x, y))
                    cubicSet = false; quadSet = false
                }
            }
        }
        return out
    }
}
