package com.impulsive.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Design system: "The Stoic Sanctuary"
private val ImpulsiveColorScheme = darkColorScheme(
    background          = Color(0xFF061423),
    surface             = Color(0xFF061423),
    surfaceContainerLow = Color(0xFF0F1C2C),
    surfaceContainer    = Color(0xFF132030),
    surfaceContainerHigh    = Color(0xFF1E2B3B),
    surfaceContainerHighest = Color(0xFF283646),
    surfaceBright       = Color(0xFF2D3A4A),

    primary             = Color(0xFF7CD6CD),
    primaryContainer    = Color(0xFF4DA8A0),
    onPrimary           = Color(0xFF003733),

    secondary           = Color(0xFFCDBEF8),

    tertiary            = Color(0xFF6B9F78),   // Walk Away / success

    onSurface           = Color(0xFFE8E8E8),
    onSurfaceVariant    = Color(0xFF8899AA),
    outline             = Color(0xFF889391),
    outlineVariant      = Color(0xFF3E4947),

    error               = Color(0xFFC4873B),   // Amber, never red
)

@Composable
fun ImpulsiveTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ImpulsiveColorScheme,
        typography  = ImpulsiveTypography,
        content     = content
    )
}
