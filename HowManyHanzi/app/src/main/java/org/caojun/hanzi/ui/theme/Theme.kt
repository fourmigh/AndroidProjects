package org.caojun.hanzi.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF22333B)
val Accent = Color(0xFFC8553D)
val Paper = Color(0xFFF7F3EC)
val Muted = Color(0xFF7A858C)
val Soft = Color(0xFF55636B)
val ChipBg = Color(0xFFF0EBE2)
val Border = Color(0xFFD8D0C4)
val CardBg = Color(0xFFFFFFFF)
val SoftLine = Color(0xFFEDE5D8)

fun levelColor(level: Int): Color = when (level) {
    0 -> Color(0xFFA8A29A)
    1 -> Color(0xFF7FA87F)
    2 -> Color(0xFF5F9B7F)
    3 -> Color(0xFF2F6B4F)
    4 -> Color(0xFF4A7FA5)
    5 -> Color(0xFF8A6FAE)
    6 -> Color(0xFFC87D3D)
    else -> Accent
}

private val scheme = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF2DED8),
    onPrimaryContainer = Ink,
    secondary = Ink,
    onSecondary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = CardBg,
    onSurface = Ink,
    surfaceVariant = ChipBg,
    onSurfaceVariant = Soft,
    outline = Border,
    error = Color(0xFFB3402F),
)

private val typography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Ink),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 26.sp, color = Ink),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp, color = Ink),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, color = Muted),
)

@Composable
fun HanziTheme(content: @Composable () -> Unit) {
    // 原版只有浅色主题，这里保持单一配色。
    @Suppress("UNUSED_VARIABLE")
    val dark = isSystemInDarkTheme()
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
