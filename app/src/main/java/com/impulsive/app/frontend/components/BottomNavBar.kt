package com.impulsive.app.frontend.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.impulsive.app.frontend.utils.rememberImpulsiveHaptics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class BottomNavItem {
    Home,
    Progress,
    Trigger,
    Focus,
    Settings,
}

@Composable
fun BottomNavBar(
    selected: BottomNavItem,
    onSelect: (BottomNavItem) -> Unit,
    hapticsEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberImpulsiveHaptics(hapticsEnabled)
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val navGlow = Color(0xFF93E9BE)
    val selectedGlow = Color(0xFFD0C3F1)
    val navShape = RoundedCornerShape(50)
    Surface(
        modifier = modifier
            .impulsiveGlowShadow(
                enabled = isDark,
                shape = navShape,
                glowColor = navGlow,
                elevation = 18.dp,
                ambientAlpha = 0.18f,
                spotAlpha = 0.24f,
            )
            .height(64.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = navShape,
        tonalElevation = 4.dp,
        border = impulsiveGlowBorderStroke(
            enabled = isDark,
            glowColor = navGlow,
            fallbackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomNavButton(
                item = BottomNavItem.Home,
                icon = Icons.Filled.Home,
                selected = selected == BottomNavItem.Home,
                onSelect = onSelect,
                haptics = haptics,
                isDark = isDark,
                selectedGlow = selectedGlow,
            )
            BottomNavButton(
                item = BottomNavItem.Progress,
                icon = Icons.Filled.ShowChart,
                selected = selected == BottomNavItem.Progress,
                onSelect = onSelect,
                haptics = haptics,
                isDark = isDark,
                selectedGlow = selectedGlow,
            )
            BottomNavButton(
                item = BottomNavItem.Trigger,
                icon = Icons.Filled.TrackChanges,
                selected = selected == BottomNavItem.Trigger,
                onSelect = onSelect,
                haptics = haptics,
                isDark = isDark,
                selectedGlow = selectedGlow,
            )
            BottomNavButton(
                item = BottomNavItem.Focus,
                icon = Icons.Filled.CenterFocusStrong,
                selected = selected == BottomNavItem.Focus,
                onSelect = onSelect,
                haptics = haptics,
                isDark = isDark,
                selectedGlow = selectedGlow,
            )
            BottomNavButton(
                item = BottomNavItem.Settings,
                icon = Icons.Filled.Settings,
                selected = selected == BottomNavItem.Settings,
                onSelect = onSelect,
                haptics = haptics,
                isDark = isDark,
                selectedGlow = selectedGlow,
            )
        }
    }
}

@Composable
private fun BottomNavButton(
    item: BottomNavItem,
    icon: ImageVector,
    selected: Boolean,
    onSelect: (BottomNavItem) -> Unit,
    haptics: com.impulsive.app.frontend.utils.ImpulsiveHaptics,
    isDark: Boolean,
    selectedGlow: Color,
) {
    IconButton(onClick = {
        haptics.light()
        onSelect(item)
    }) {
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                val selectedShape = RoundedCornerShape(50)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .impulsiveGlowShadow(
                            enabled = isDark,
                            shape = selectedShape,
                            glowColor = selectedGlow,
                            elevation = 10.dp,
                            ambientAlpha = 0.18f,
                            spotAlpha = 0.24f,
                        )
                        .clip(selectedShape)
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.42f))
                        .border(
                            width = 1.dp,
                            color = if (isDark) selectedGlow.copy(alpha = 0.62f) else Color.Transparent,
                            shape = selectedShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = item.name,
                        tint = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = item.name,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
