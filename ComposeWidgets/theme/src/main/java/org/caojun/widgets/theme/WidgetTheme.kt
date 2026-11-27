package org.caojun.widgets.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/** 控件共用的配色，集中在这里维护。 */
data class WidgetColors(
    val accent: Color,
    val onAccent: Color,
    val container: Color,
    val onContainer: Color,
    val muted: Color,
    val bar: Color,
    val barSelected: Color,
    val barUnselected: Color,
    val barIndicator: Color,
)

/** 没有显式提供 WidgetTheme 时的兜底深色配色。 */
val LocalWidgetColors = compositionLocalOf {
    WidgetColors(
        accent = Color(0xFFFF7A45),
        onAccent = Color(0xFF241009),
        container = Color(0xFF23232B),
        onContainer = Color(0xFFA9ADB8),
        muted = Color(0xFF3A3A44),
        bar = Color(0xD916161C),
        barSelected = Color(0xFFFF7A45),
        barUnselected = Color(0xFFA9ADB8),
        barIndicator = Color(0xFF23232B),
    )
}

/** 默认跟随宿主的 MaterialTheme。 */
@Composable
fun widgetColorsFromMaterial(): WidgetColors = WidgetColors(
    accent = MaterialTheme.colorScheme.primary,
    onAccent = MaterialTheme.colorScheme.onPrimary,
    container = MaterialTheme.colorScheme.surfaceVariant,
    onContainer = MaterialTheme.colorScheme.onSurfaceVariant,
    muted = MaterialTheme.colorScheme.outline,
    bar = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
    barSelected = MaterialTheme.colorScheme.primary,
    barUnselected = MaterialTheme.colorScheme.onSurfaceVariant,
    barIndicator = MaterialTheme.colorScheme.surfaceVariant,
)

/** 宿主在根主题里包一层，让所有控件使用同一套配色。 */
@Composable
fun WidgetTheme(
    colors: WidgetColors = widgetColorsFromMaterial(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalWidgetColors provides colors) {
        content()
    }
}
