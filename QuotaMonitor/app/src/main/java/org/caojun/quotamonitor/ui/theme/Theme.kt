package org.caojun.quotamonitor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF1F2933)
val Accent = Color(0xFF2F6BE6)
val Paper = Color(0xFFF5F6F8)
val CardBg = Color(0xFFFFFFFF)
val Muted = Color(0xFF6B7280)
val Border = Color(0xFFE3E6EB)

val OkGreen = Color(0xFF1E9E64)
val WarnAmber = Color(0xFFD9822B)
val ErrRed = Color(0xFFC64545)

/** 进度条配色：按使用率分档。 */
fun progressColor(fraction: Float?): Color = when {
    fraction == null -> Accent
    fraction >= 0.9f -> ErrRed
    fraction >= 0.7f -> WarnAmber
    else -> OkGreen
}

private val lightScheme = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1EAFD),
    onPrimaryContainer = Ink,
    secondary = Ink,
    onSecondary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = CardBg,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEEF0F4),
    onSurfaceVariant = Muted,
    outline = Border,
    error = ErrRed,
)

private val darkScheme = darkColorScheme(
    primary = Color(0xFF7AA2FF),
    onPrimary = Color(0xFF0B1B3A),
    primaryContainer = Color(0xFF1E3A6E),
    onPrimaryContainer = Color(0xFFDCE7FF),
    secondary = Color(0xFFE5E7EB),
    onSecondary = Ink,
    background = Color(0xFF12161C),
    onBackground = Color(0xFFE5E7EB),
    surface = Color(0xFF1A1F27),
    onSurface = Color(0xFFE5E7EB),
    surfaceVariant = Color(0xFF242B35),
    onSurfaceVariant = Color(0xFF9CA3AF),
    outline = Color(0xFF303844),
    error = Color(0xFFF08A8A),
)

private val typography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, color = Ink),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, color = Ink),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, color = Muted),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun QuotaTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) darkScheme else lightScheme,
        typography = typography,
        content = content,
    )
}
