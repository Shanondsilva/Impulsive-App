package com.impulsive.app.frontend.screens.games

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveGameLayoutTest {

    @Test
    fun compactPortraitPhoneKeepsPortraitAndFullWidth() {
        val metrics =
            adaptiveGameMetrics(
                availableWidth = 412.dp,
                availableHeight = 915.dp,
            )

        assertTrue(metrics.requestPortrait)
        assertFalse(metrics.constrainContentWidth)
        assertEquals(
            412f,
            metrics.contentWidth.value,
            0.01f,
        )
    }

    @Test
    fun transientCompactPhoneLandscapeStaysPortraitProtectedAndBounded() {
        val metrics =
            adaptiveGameMetrics(
                availableWidth = 915.dp,
                availableHeight = 412.dp,
            )

        assertTrue(metrics.requestPortrait)
        assertTrue(metrics.constrainContentWidth)
        assertEquals(
            329.6f,
            metrics.contentWidth.value,
            0.01f,
        )
    }

    @Test
    fun sevenInchPortraitTabletDoesNotRequestPortrait() {
        val metrics =
            adaptiveGameMetrics(
                availableWidth = 600.dp,
                availableHeight = 960.dp,
            )

        assertFalse(metrics.requestPortrait)
        assertTrue(metrics.constrainContentWidth)
        assertEquals(
            480f,
            metrics.contentWidth.value,
            0.01f,
        )
    }

    @Test
    fun landscapeTabletUsesCentredMaximumWidth() {
        val metrics =
            adaptiveGameMetrics(
                availableWidth = 1280.dp,
                availableHeight = 800.dp,
            )

        assertFalse(metrics.requestPortrait)
        assertTrue(metrics.constrainContentWidth)
        assertEquals(
            480f,
            metrics.contentWidth.value,
            0.01f,
        )
    }

    @Test
    fun unfoldedFoldableUsesCentredMaximumWidth() {
        val metrics =
            adaptiveGameMetrics(
                availableWidth = 800.dp,
                availableHeight = 1280.dp,
            )

        assertFalse(metrics.requestPortrait)
        assertTrue(metrics.constrainContentWidth)
        assertEquals(
            480f,
            metrics.contentWidth.value,
            0.01f,
        )
    }

    @Test
    fun shortResizableLandscapeWindowUsesHeightBoundedWidth() {
        val metrics =
            adaptiveGameMetrics(
                availableWidth = 900.dp,
                availableHeight = 360.dp,
            )

        assertTrue(metrics.requestPortrait)
        assertTrue(metrics.constrainContentWidth)
        assertEquals(
            288f,
            metrics.contentWidth.value,
            0.01f,
        )
    }
}
