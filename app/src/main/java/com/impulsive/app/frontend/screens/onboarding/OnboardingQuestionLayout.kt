package com.impulsive.app.frontend.screens.onboarding

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class OnboardingQuestionMetrics(
    val titleFontSize: TextUnit,
    val titleLineHeight: TextUnit,
    val subtitleFontSize: TextUnit,
    val subtitleLineHeight: TextUnit,
    val headerToIconSpacing: Dp,
    val iconToTitleSpacing: Dp,
    val titleToSubtitleSpacing: Dp,
    val subtitleToOptionsSpacing: Dp,
    val optionAreaMinHeight: Dp,
)

internal fun onboardingQuestionMetrics(
    viewport: OnboardingViewport,
): OnboardingQuestionMetrics {
    val compactHeight = viewport.compactHeight

    return when {
        viewport.width < 380.dp -> OnboardingQuestionMetrics(
            titleFontSize = 25.sp,
            titleLineHeight = 31.sp,
            subtitleFontSize = 15.sp,
            subtitleLineHeight = 22.sp,
            headerToIconSpacing = if (compactHeight) 12.dp else 20.dp,
            iconToTitleSpacing = 16.dp,
            titleToSubtitleSpacing = 10.dp,
            subtitleToOptionsSpacing = if (compactHeight) 18.dp else 22.dp,
            optionAreaMinHeight = if (compactHeight) 320.dp else 350.dp,
        )

        viewport.width < 430.dp -> OnboardingQuestionMetrics(
            titleFontSize = 27.sp,
            titleLineHeight = 34.sp,
            subtitleFontSize = 15.sp,
            subtitleLineHeight = 23.sp,
            headerToIconSpacing = if (compactHeight) 14.dp else 24.dp,
            iconToTitleSpacing = 18.dp,
            titleToSubtitleSpacing = 12.dp,
            subtitleToOptionsSpacing = if (compactHeight) 20.dp else 26.dp,
            optionAreaMinHeight = if (compactHeight) 330.dp else 360.dp,
        )

        viewport.width < 600.dp -> OnboardingQuestionMetrics(
            titleFontSize = 29.sp,
            titleLineHeight = 36.sp,
            subtitleFontSize = 16.sp,
            subtitleLineHeight = 24.sp,
            headerToIconSpacing = if (compactHeight) 18.dp else 30.dp,
            iconToTitleSpacing = 22.dp,
            titleToSubtitleSpacing = 12.dp,
            subtitleToOptionsSpacing = if (compactHeight) 22.dp else 30.dp,
            optionAreaMinHeight = if (compactHeight) 340.dp else 370.dp,
        )

        else -> OnboardingQuestionMetrics(
            titleFontSize = 32.sp,
            titleLineHeight = 40.sp,
            subtitleFontSize = 16.sp,
            subtitleLineHeight = 24.sp,
            headerToIconSpacing = if (compactHeight) 22.dp else 42.dp,
            iconToTitleSpacing = 28.dp,
            titleToSubtitleSpacing = 14.dp,
            subtitleToOptionsSpacing = if (compactHeight) 28.dp else 38.dp,
            optionAreaMinHeight = if (compactHeight) 360.dp else 390.dp,
        )
    }
}
