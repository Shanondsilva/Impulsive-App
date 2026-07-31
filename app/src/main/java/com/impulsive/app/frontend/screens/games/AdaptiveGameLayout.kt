package com.impulsive.app.frontend.screens.games

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val AdaptiveGameLargeWindowThreshold: Dp =
    600.dp

internal val AdaptiveGameMaximumContentWidth: Dp =
    480.dp

internal data class AdaptiveGameMetrics(
    val requestPortrait: Boolean,
    val constrainContentWidth: Boolean,
    val contentWidth: Dp,
)

internal fun adaptiveGameMetrics(
    availableWidth: Dp,
    availableHeight: Dp,
): AdaptiveGameMetrics {
    val smallestWindowDimension =
        minOf(
            availableWidth,
            availableHeight,
        )

    val isLargeWindow =
        smallestWindowDimension >=
            AdaptiveGameLargeWindowThreshold

    val isLandscape =
        availableWidth >
            availableHeight

    val constrainContentWidth =
        isLargeWindow ||
            isLandscape

    if (!constrainContentWidth) {
        return AdaptiveGameMetrics(
            requestPortrait = true,
            constrainContentWidth = false,
            contentWidth = availableWidth,
        )
    }

    val heightBoundedWidth =
        (
            availableHeight.value *
                0.80f
        ).dp

    val boundedWidth =
        minOf(
            availableWidth,
            AdaptiveGameMaximumContentWidth,
            heightBoundedWidth,
        )

    return AdaptiveGameMetrics(
        requestPortrait = !isLargeWindow,
        constrainContentWidth = true,
        contentWidth = boundedWidth,
    )
}

@Composable
internal fun AdaptiveGameContainer(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val metrics =
            adaptiveGameMetrics(
                availableWidth = maxWidth,
                availableHeight = maxHeight,
            )

        LockPortraitOrientation(
            enabled = metrics.requestPortrait,
        )

        val contentModifier =
            if (metrics.constrainContentWidth) {
                Modifier
                    .width(metrics.contentWidth)
                    .fillMaxHeight()
            } else {
                Modifier.fillMaxSize()
            }

        Box(
            modifier = contentModifier,
            content = content,
        )
    }
}
