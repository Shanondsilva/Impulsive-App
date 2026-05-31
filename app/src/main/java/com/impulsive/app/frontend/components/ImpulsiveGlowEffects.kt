package com.impulsive.app.frontend.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared dark-mode glow helpers for Impulsive cards and floating navigation.
 *
 * Keep card fills unchanged. These helpers only add a soft website-style glow
 * and a pastel border when dark mode is active.
 */
fun Modifier.impulsiveGlowShadow(
    enabled: Boolean,
    shape: Shape,
    glowColor: Color,
    elevation: Dp = 14.dp,
    ambientAlpha: Float = 0.16f,
    spotAlpha: Float = 0.20f,
): Modifier = if (!enabled) {
    this
} else {
    shadow(
        elevation = elevation,
        shape = shape,
        clip = false,
        ambientColor = glowColor.copy(alpha = ambientAlpha),
        spotColor = glowColor.copy(alpha = spotAlpha),
    )
}

fun impulsiveGlowBorderStroke(
    enabled: Boolean,
    glowColor: Color,
    fallbackColor: Color,
    width: Dp = 1.dp,
    darkAlpha: Float = 0.56f,
): BorderStroke = BorderStroke(
    width = width,
    color = if (enabled) glowColor.copy(alpha = darkAlpha) else fallbackColor,
)
