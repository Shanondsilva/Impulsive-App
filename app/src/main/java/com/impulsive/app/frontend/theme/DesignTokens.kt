package com.impulsive.app.frontend.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val ImpulsiveOverallTheme = Color(0xFF93E9BE)
val ImpulsivePsychological = Color(0xFFD0C3F1)
val ImpulsivePhysical = Color(0xFFBDE0FE)
val ImpulsiveSpiritual = Color(0xFFFEF1AB)
val ImpulsiveFocusMode = Color(0xFFF5A7A6)

val ImpulsiveBackground = Color(0xFFF3FBF6)
val ImpulsiveSurface = Color(0xFFFFFEFC)
val ImpulsiveText = Color(0xFF25362D)
val ImpulsiveMutedText = Color(0xFF637369)

val ImpulsiveBackgroundDark = Color(0xFF11161A)
val ImpulsiveSurfaceDark = Color(0xFF1A2026)
val ImpulsiveTextDark = Color(0xFFE8EAEC)
val ImpulsiveMutedTextDark = Color(0xFF9BA4AC)
val ImpulsivePsychologicalDark = Color(0xFF6B5BA3)
val ImpulsivePhysicalDark = Color(0xFF4A6B8A)
val ImpulsiveSpiritualDark = Color(0xFFB39833)

private val ImpulsiveColorScheme = lightColorScheme(
    primary = ImpulsiveOverallTheme,
    onPrimary = ImpulsiveText,
    secondary = ImpulsivePsychological,
    onSecondary = ImpulsiveText,
    tertiary = ImpulsivePhysical,
    onTertiary = ImpulsiveText,
    background = ImpulsiveBackground,
    onBackground = ImpulsiveText,
    surface = ImpulsiveSurface,
    onSurface = ImpulsiveText,
    surfaceVariant = Color(0xFFF8F5FE),
    onSurfaceVariant = ImpulsiveMutedText,
)

private val ImpulsiveDarkColorScheme = darkColorScheme(
    primary = ImpulsiveOverallTheme,
    onPrimary = Color.Black,
    secondary = ImpulsivePsychologicalDark,
    onSecondary = ImpulsiveTextDark,
    tertiary = ImpulsivePhysicalDark,
    onTertiary = ImpulsiveTextDark,
    background = ImpulsiveBackgroundDark,
    onBackground = ImpulsiveTextDark,
    surface = ImpulsiveSurfaceDark,
    onSurface = ImpulsiveTextDark,
    surfaceVariant = Color(0xFF20262D),
    onSurfaceVariant = ImpulsiveMutedTextDark,
)

@Composable
fun ImpulsiveTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) ImpulsiveDarkColorScheme else ImpulsiveColorScheme,
        content = content,
    )
}
