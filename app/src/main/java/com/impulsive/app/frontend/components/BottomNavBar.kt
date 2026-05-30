package com.impulsive.app.frontend.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.impulsive.app.frontend.theme.ImpulsiveText

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
    Surface(
        modifier = modifier
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(50),
                clip = false,
                ambientColor = ImpulsiveText.copy(alpha = 0.10f),
                spotColor = ImpulsiveText.copy(alpha = 0.14f),
            )
            .height(64.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(50),
        tonalElevation = 4.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f),
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
            )
            BottomNavButton(
                item = BottomNavItem.Progress,
                icon = Icons.Filled.ShowChart,
                selected = selected == BottomNavItem.Progress,
                onSelect = onSelect,
                haptics = haptics,
            )
            BottomNavButton(
                item = BottomNavItem.Trigger,
                icon = Icons.Filled.TrackChanges,
                selected = selected == BottomNavItem.Trigger,
                onSelect = onSelect,
                haptics = haptics,
            )
            BottomNavButton(
                item = BottomNavItem.Focus,
                icon = Icons.Filled.CenterFocusStrong,
                selected = selected == BottomNavItem.Focus,
                onSelect = onSelect,
                haptics = haptics,
            )
            BottomNavButton(
                item = BottomNavItem.Settings,
                icon = Icons.Filled.Settings,
                selected = selected == BottomNavItem.Settings,
                onSelect = onSelect,
                haptics = haptics,
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
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.42f)),
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
