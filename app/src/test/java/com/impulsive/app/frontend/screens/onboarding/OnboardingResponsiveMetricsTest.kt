package com.impulsive.app.frontend.screens.onboarding

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingResponsiveMetricsTest {

    @Test
    fun compactHeightUsesAvailableContentHeightBoundary() {
        assertTrue(
            OnboardingViewport(
                width = 480.dp,
                height = 719.dp,
            ).compactHeight,
        )

        assertFalse(
            OnboardingViewport(
                width = 480.dp,
                height = 720.dp,
            ).compactHeight,
        )
    }

    @Test
    fun constrainedTabletColumnUsesUnder600QuestionMetrics() {
        val metrics = onboardingQuestionMetrics(
            OnboardingViewport(
                width = 480.dp,
                height = 650.dp,
            ),
        )

        assertEquals(29.sp, metrics.titleFontSize)
        assertEquals(18.dp, metrics.headerToIconSpacing)
        assertEquals(340.dp, metrics.optionAreaMinHeight)
    }

    @Test
    fun compactPhoneRetainsSmallPhoneQuestionMetrics() {
        val metrics = onboardingQuestionMetrics(
            OnboardingViewport(
                width = 360.dp,
                height = 800.dp,
            ),
        )

        assertEquals(25.sp, metrics.titleFontSize)
        assertEquals(20.dp, metrics.headerToIconSpacing)
        assertEquals(350.dp, metrics.optionAreaMinHeight)
    }

    @Test
    fun constrainedTabletWelcomeUsesCompactVerticalSpacing() {
        val metrics = welcomeResponsiveMetrics(
            OnboardingViewport(
                width = 480.dp,
                height = 650.dp,
            ),
        )

        assertEquals(18.dp, metrics.topSpacing)
        assertEquals(24.dp, metrics.privacyToTitleSpacing)
        assertEquals(24.dp, metrics.inputToAvatarTitleSpacing)
    }
}
