package org.caojun.shotocr.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 右侧悬浮滚动条：内容溢出时显示，贴右侧边缘绘制。
 */
@Composable
fun VerticalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    width: Dp = 4.dp,
    minThumbHeight: Dp = 32.dp,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
) {
    if (scrollState.maxValue <= 0) return

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(width)
    ) {
        val viewportHeight = constraints.maxHeight
        if (viewportHeight == Constraints.Infinity || viewportHeight <= 0) return@BoxWithConstraints

        val viewport = viewportHeight.toFloat()
        val contentHeight = (viewport + scrollState.maxValue).coerceAtLeast(1f)
        val minThumbPx = with(LocalDensity.current) { minThumbHeight.toPx() }
            .coerceAtMost(viewport)
        val thumbHeight = (viewport * viewport / contentHeight)
            .coerceIn(minThumbPx, viewport)
        val maxOffset = (viewport - thumbHeight).coerceAtLeast(0f)
        val offset = if (scrollState.maxValue > 0) {
            maxOffset * scrollState.value.toFloat() / scrollState.maxValue
        } else {
            0f
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRoundRect(
                color = color,
                topLeft = Offset(0f, offset),
                size = Size(size.width, thumbHeight),
                cornerRadius = CornerRadius(size.width / 2f)
            )
        }
    }
}
