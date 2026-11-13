package org.caojun.hanzi.ui

import androidx.compose.ui.graphics.Color
import org.caojun.hanzi.strokes.PathCmd
import androidx.compose.ui.graphics.Path

/** 笔顺/书写动画共用的颜色，对应小程序 lib/strokes.js 的 COLORS。 */
internal val StrokeDone = Color(0xFF22333B)
internal val StrokeCurrent = Color(0xFFC8553D)
internal val StrokePending = Color(0xFFE7DFD1)
internal val StrokeCellBg = Color(0xFFFBF7EF)
internal val StrokeCellBorder = Color(0xFFEDE5D8)
internal val StrokeNumber = Color(0xFFA29A8E)
internal val StrokeName = Color(0xFF55636B)

internal val LetterPaper = Color(0xFFFBF7EF)
internal val LetterRule = Color(0xFFEDE5D8)
internal val LetterInk = StrokeDone

/** 把解析后的 SVG 命令转成 Compose Path。 */
internal fun buildStrokePath(cmds: List<PathCmd>): Path {
    val path = Path()
    for (c in cmds) {
        when (c) {
            is PathCmd.MoveTo -> path.moveTo(c.x, c.y)
            is PathCmd.LineTo -> path.lineTo(c.x, c.y)
            is PathCmd.QuadTo -> path.quadraticTo(c.cx, c.cy, c.x, c.y)
            is PathCmd.CubicTo -> path.cubicTo(c.c1x, c.c1y, c.c2x, c.c2y, c.x, c.y)
            PathCmd.Close -> path.close()
        }
    }
    return path
}
