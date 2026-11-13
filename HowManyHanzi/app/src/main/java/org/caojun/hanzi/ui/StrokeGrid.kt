package org.caojun.hanzi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.caojun.hanzi.strokes.PathCmd
import org.caojun.hanzi.strokes.SvgPath

/** 4 列逐笔网格，对应小程序 lib/strokes.js 的 canvas 绘制。 */
@Composable
fun StrokeGrid(
    strokes: List<String>,
    names: List<String>,
    modifier: Modifier = Modifier,
    onCellClick: ((Int) -> Unit)? = null,
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
                            onClick = onCellClick?.let { cb -> { cb(index) } },
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
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .width(cellW)
            .height(cellH)
            .clip(RoundedCornerShape(10.dp))
            .background(StrokeCellBg)
            .border(1.dp, StrokeCellBorder, RoundedCornerShape(10.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
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
            val paths = parsed.map { buildStrokePath(it) }
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
                        j < index -> StrokeDone
                        j == index -> StrokeCurrent
                        else -> StrokePending
                    }
                    drawPath(paths[j], color)
                }
            }
        }
        Text(
            "${index + 1}",
            fontSize = 9.sp,
            color = StrokeNumber,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 5.dp, top = 3.dp),
        )
        Text(
            name,
            fontSize = 10.sp,
            color = StrokeName,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp, start = 2.dp, end = 2.dp),
        )
    }
}
