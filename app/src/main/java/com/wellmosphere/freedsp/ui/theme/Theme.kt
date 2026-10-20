package com.wellmosphere.freedsp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** One accent, quiet surfaces: the curve is the hero. */
object Palette {
    val Ink = Color(0xFF0D0F13)
    val Panel = Color(0xFF161A21)
    val Raised = Color(0xFF1F242D)
    val Line = Color(0xFF2A303B)
    val Text = Color(0xFFE9EDF2)
    val Muted = Color(0xFF8B94A3)
    val Signal = Color(0xFF7CE7C4)
    val Warm = Color(0xFFFFB86B)
    val Alert = Color(0xFFFF7A7A)
}

private val scheme = darkColorScheme(
    primary = Palette.Signal, onPrimary = Palette.Ink,
    secondary = Palette.Warm, onSecondary = Palette.Ink,
    background = Palette.Ink, onBackground = Palette.Text,
    surface = Palette.Panel, onSurface = Palette.Text,
    surfaceVariant = Palette.Raised, onSurfaceVariant = Palette.Muted,
    surfaceContainerHigh = Palette.Raised, surfaceContainerLow = Palette.Panel,
    outline = Palette.Line, outlineVariant = Palette.Line,
    error = Palette.Alert, onError = Palette.Ink,
    errorContainer = Color(0xFF3A1E22), onErrorContainer = Color(0xFFFFD9D9),
)

private val type = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 28.sp, letterSpacing = (-0.5).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.4.sp),
)

/** Tabular figures so values don't jitter while dragging. */
val Numbers = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 15.sp)

@Composable
fun FreeDspTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, typography = type, content = content)
