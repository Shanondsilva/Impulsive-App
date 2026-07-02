package com.impulsive.app.frontend.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.impulsive.app.frontend.theme.ImpulsivePsychological
import com.impulsive.app.frontend.utils.rememberImpulsiveHaptics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

enum class BottomNavItem {
    Home,
    Progress,
    Trigger,
    Focus,
    Settings,
}

class BottomNavIndicatorState(
    val leftIndex: Animatable<Float, AnimationVector1D>,
    val rightIndex: Animatable<Float, AnimationVector1D>,
)

@Composable
fun rememberBottomNavIndicatorState(initialIndex: Int = 0): BottomNavIndicatorState {
    val left = remember { Animatable(initialIndex.toFloat()) }
    val right = remember { Animatable(initialIndex.toFloat()) }
    return remember(left, right) { BottomNavIndicatorState(left, right) }
}

@Composable
fun BottomNavBar(
    selected: BottomNavItem,
    onSelect: (BottomNavItem) -> Unit,
    onLongSelect: (BottomNavItem) -> Unit = {},
    hapticsEnabled: Boolean? = null,
    settingsBadgeVisible: Boolean = false,
    modeSelectorOpen: Boolean = false,
    indicatorState: BottomNavIndicatorState = rememberBottomNavIndicatorState(),
    isActive: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberImpulsiveHaptics(enabled = hapticsEnabled)
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val navGlow = ImpulsivePsychological
    val selectedGlow = ImpulsivePsychological
    val navShape = RoundedCornerShape(50)
    val selectedIndex = selected.navIndex()
    val navHorizontalPadding = 10.dp
    val selectedIndicatorSize = 40.dp
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
            glowColor = selectedGlow,
            fallbackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f),
        ),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            val itemSlotWidth = (maxWidth - (navHorizontalPadding * 2)) / BottomNavItemCount
            fun indicatorLeftFor(index: Float): Dp = navHorizontalPadding +
                (itemSlotWidth * index) +
                ((itemSlotWidth - selectedIndicatorSize) / 2)

            LaunchedEffect(selectedIndex, itemSlotWidth, isActive) {
                if (!isActive) return@LaunchedEffect
                val target = selectedIndex.toFloat()
                val movingRight = target > indicatorState.leftIndex.value
                // The edge facing the travel direction moves on a fast spring while
                // the trailing edge follows on a soft one, so the pill stretches in
                // flight and settles back to its normal width on arrival.
                val fastSpring = spring<Float>(dampingRatio = 0.78f, stiffness = 420f)
                val softSpring = spring<Float>(dampingRatio = 0.92f, stiffness = 170f)
                launch { indicatorState.leftIndex.animateTo(target, if (movingRight) softSpring else fastSpring) }
                launch { indicatorState.rightIndex.animateTo(target, if (movingRight) fastSpring else softSpring) }
            }
            val leftEdge = indicatorLeftFor(indicatorState.leftIndex.value)
            val rightEdge = indicatorLeftFor(indicatorState.rightIndex.value) + selectedIndicatorSize
            BottomNavSelectedIndicator(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = leftEdge)
                    .width((rightEdge - leftEdge).coerceAtLeast(0.dp)),
                isDark = isDark,
                selectedGlow = selectedGlow,
                size = selectedIndicatorSize,
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = navHorizontalPadding),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    BottomNavButton(
                        item = BottomNavItem.Home,
                        icon = Icons.Filled.Home,
                        selected = selected == BottomNavItem.Home,
                        onSelect = onSelect,
                        onLongSelect = onLongSelect,
                        haptics = haptics,
                        badgeVisible = false,
                    )
                }
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    BottomNavButton(
                        item = BottomNavItem.Progress,
                        icon = Icons.Filled.ShowChart,
                        selected = selected == BottomNavItem.Progress,
                        onSelect = onSelect,
                        onLongSelect = onLongSelect,
                        haptics = haptics,
                        badgeVisible = false,
                    )
                }
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    BottomNavButton(
                        item = BottomNavItem.Trigger,
                        symbol = "\u2726",
                        selected = selected == BottomNavItem.Trigger || modeSelectorOpen,
                        modeSelectorOpen = modeSelectorOpen,
                        onSelect = onSelect,
                        onLongSelect = onLongSelect,
                        haptics = haptics,
                        badgeVisible = false,
                    )
                }
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    BottomNavButton(
                        item = BottomNavItem.Focus,
                        icon = Icons.Filled.CenterFocusStrong,
                        selected = selected == BottomNavItem.Focus,
                        onSelect = onSelect,
                        onLongSelect = onLongSelect,
                        haptics = haptics,
                        badgeVisible = false,
                    )
                }
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    BottomNavButton(
                        item = BottomNavItem.Settings,
                        icon = Icons.Filled.Settings,
                        selected = selected == BottomNavItem.Settings,
                        onSelect = onSelect,
                        onLongSelect = onLongSelect,
                        haptics = haptics,
                        badgeVisible = settingsBadgeVisible,
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun BottomNavButton(
    item: BottomNavItem,
    icon: ImageVector? = null,
    symbol: String? = null,
    selected: Boolean,
    modeSelectorOpen: Boolean = false,
    onSelect: (BottomNavItem) -> Unit,
    onLongSelect: (BottomNavItem) -> Unit,
    haptics: com.impulsive.app.frontend.utils.ImpulsiveHaptics,
    badgeVisible: Boolean = false,
) {
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.18f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
        label = "BottomNavIconPop",
    )

    Box(
        modifier = Modifier
            .size(48.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    haptics.light()
                    onSelect(item)
                },
                onLongClick = {
                    haptics.confirm()
                    onLongSelect(item)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        BottomNavButtonIcon(
            item = item,
            icon = icon,
            symbol = symbol,
            selected = selected,
            scale = iconScale,
            modeSelectorOpen = modeSelectorOpen,
        )
        if (badgeVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-6).dp, y = 6.dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE5484D))
                    .border(2.dp, Color.White, CircleShape),
            )
        }
    }
}

@Composable
private fun BottomNavSelectedIndicator(
    modifier: Modifier,
    isDark: Boolean,
    selectedGlow: Color,
    size: androidx.compose.ui.unit.Dp,
) {
    val selectedShape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .height(size)
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
    )
}

@Composable
private fun BottomNavButtonIcon(
    item: BottomNavItem,
    icon: ImageVector?,
    symbol: String?,
    selected: Boolean,
    scale: Float,
    modeSelectorOpen: Boolean = false,
) {
    val tint = if (selected) {
        MaterialTheme.colorScheme.onSecondary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    if (item == BottomNavItem.Trigger) {
        BottomNavTriggerIcon(
            symbol = symbol,
            tint = tint,
            scale = scale,
            modeSelectorOpen = modeSelectorOpen,
        )
    } else if (symbol != null) {
        Text(
            text = symbol,
            color = tint,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        )
    } else if (icon != null) {
        Icon(
            imageVector = icon,
            contentDescription = item.name,
            tint = tint,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        )
    }
}

@Composable
private fun BottomNavTriggerIcon(
    symbol: String?,
    tint: Color,
    scale: Float,
    modeSelectorOpen: Boolean,
) {
    val openRotation by animateFloatAsState(
        targetValue = if (modeSelectorOpen) 45f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "BottomNavTriggerOpenRotation",
    )
    Text(
        text = symbol ?: "\u2726",
        color = tint,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            rotationZ = openRotation
        },
    )
}

private const val BottomNavItemCount = 5

private fun BottomNavItem.navIndex(): Int = when (this) {
    BottomNavItem.Home -> 0
    BottomNavItem.Progress -> 1
    BottomNavItem.Trigger -> 2
    BottomNavItem.Focus -> 3
    BottomNavItem.Settings -> 4
}
