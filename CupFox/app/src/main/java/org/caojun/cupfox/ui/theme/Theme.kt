package org.caojun.cupfox.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CupFoxColors = darkColorScheme(
    primary = Color(0xFFFF7A45),
    onPrimary = Color(0xFF241009),
    primaryContainer = Color(0xFF5A2A16),
    onPrimaryContainer = Color(0xFFFFD9C9),
    secondary = Color(0xFFB9BCC8),
    onSecondary = Color(0xFF1B1B20),
    background = Color(0xFF0E0E12),
    onBackground = Color(0xFFE8E8EE),
    surface = Color(0xFF16161C),
    onSurface = Color(0xFFE8E8EE),
    surfaceVariant = Color(0xFF23232B),
    onSurfaceVariant = Color(0xFFA9ADB8),
    outline = Color(0xFF3A3A44),
    error = Color(0xFFFF6B6B),
)

@Composable
fun CupFoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CupFoxColors,
        content = content,
    )
}
