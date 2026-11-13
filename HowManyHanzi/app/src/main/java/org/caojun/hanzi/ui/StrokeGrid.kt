package org.caojun.hanzi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.caojun.hanzi.strokes.PathCmd
import org.caojun.hanzi.strokes.SvgPath

private val COLOR_DONE = Color(0xFF22333B)
private val COLOR_CURRENT = Color(0xFFC8553D)
private val COLOR_PENDING = Color(0xFFE7DFD1)
private val COLOR_CELL = Color(0xFFFBF7EF)
private val COLOR_CELL_BORDER = Color(0xFFEDE5D8)
private val COLOR_NUMBER = Color(0xFFA29A8E)
private val COLOR_NAME = Color(0xFF55636B)

/** 4 列逐笔网格，对应小程序 lib/strokes.js 的 canvas 绘制。 */
@Composable
fun StrokeGrid(
    strokes: List<String>,
    names: List<String>,
    modifier: Modifier = Modifier,
) {
    val parsed = remember(strokes) { strokes.map { SvgPath.parse(it) } }
    if (parsed.isEmpty()) return

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val gap = 10.dp
        val cellW = (maxWidth - gap * 3) / 4
        val cellH = cellW + 34.dp
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            parsed.indices.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { index ->
                        StrokeCell(
                            index = index,
                            parsed = parsed,
                            name = names.getOrElse(index) { "" },
                            cellW = cellW,
                            cellH = cellH,
                        )
                    }
                    repeat(4 - row.size) { Spacer(Modifier.width(cellW)) }
                }
            }
        }
    }
}

@Composable
private fun StrokeCell(
    index: Int,
    parsed: List<List<PathCmd>>,
    name: String,
    cellW: androidx.compose.ui.unit.Dp,
    cellH: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier = Modifier
            .width(cellW)
            .height(cellH)
            .clip(RoundedCornerShape(10.dp))
            .background(COLOR_CELL)
            .border(1.dp, COLOR_CELL_BORDER, RoundedCornerShape(10.dp)),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(cellW),
        ) {
            val gs = (size.width * 0.9f) / 1024f
            val boxOffset = 512f * gs
            val bx = size.width / 2f
            val by = size.height / 2f
            val paths = parsed.map { buildPath(it) }
            // 未写笔画放最底层，已完成居中，当前笔最上层，避免未写笔画压住已写笔画。
            val order = parsed.indices.sortedBy { j ->
                when {
                    j > index -> 0
                    j < index -> 1
                    else -> 2
                }
            }
            withTransform({
                translate(bx - boxOffset, by - boxOffset + 900f * gs)
                scale(gs, -gs, pivot = Offset.Zero)
            }) {
                for (j in order) {
                    val color = when {
                        j < index -> COLOR_DONE
                        j == index -> COLOR_CURRENT
                        else -> COLOR_PENDING
                    }
                    drawPath(paths[j], color)
                }
            }
        }
        Text(
            "${index + 1}",
            fontSize = 9.sp,
            color = COLOR_NUMBER,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 5.dp, top = 3.dp),
        )
        Text(
            name,
            fontSize = 10.sp,
            color = COLOR_NAME,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp, start = 2.dp, end = 2.dp),
        )
    }
}

private fun buildPath(cmds: List<PathCmd>): Path {
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
