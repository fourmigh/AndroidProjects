package org.caojun.player

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PlayerColors = darkColorScheme(
    primary = Color(0xFFFF7A45),
    onPrimary = Color(0xFF241009),
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF16161C),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF23232B),
    onSurfaceVariant = Color(0xFFA9ADB8),
)

@Composable
internal fun PlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PlayerColors,
        content = content,
    )
}
