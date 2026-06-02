package com.impulsive.app.frontend.screens.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.impulsive.app.backend.session.onboarding.OnboardingState
import com.impulsive.app.frontend.theme.ImpulsiveOverallTheme
import com.impulsive.app.frontend.theme.ImpulsivePsychological
import com.impulsive.app.frontend.theme.ImpulsiveSurface
import com.impulsive.app.frontend.utils.rememberImpulsiveHaptics
import kotlin.math.abs
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun OnboardingDailyRelapseCountScreen(
    state: OnboardingState,
    initialCount: Int,
    onBack: () -> Unit,
    onContinue: (Int) -> Unit,
) {
    val reducedMotion = rememberReducedMotion()
    var selectedCount by remember(initialCount) { mutableStateOf(initialCount.coerceIn(1, 10)) }

    OnboardingScreenShell(
        backgroundColors = listOf(Color(0xFFFFFEFC), Color(0xFFFBF8FE), Color(0xFFF5F2FB)),
        stepUi = OnboardingFlowStep.DailyRelapseCount.toStepUi(),
        onBack = onBack,
        onSkip = null,
        bottomBar = { ContinueButton(enabled = true, label = "Continue", onClick = { onContinue(selectedCount) }) },
    ) { compactHeight ->
        val metrics = rememberQuestionResponsiveMetrics(compactHeight = compactHeight)

        Spacer(modifier = Modifier.height(metrics.headerToIconSpacing))
        OnboardingLogoVisual(reducedMotion = reducedMotion, scale = OnboardingLogoScale.Compact)
        Spacer(modifier = Modifier.height(metrics.iconToTitleSpacing))

        Text(
            text = "How many difficult habit moments do you want support with each day?",
            color = OnboardingPrimary,
            fontSize = metrics.titleFontSize,
            lineHeight = metrics.titleLineHeight,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(metrics.titleToSubtitleSpacing))

        Column(
            modifier = Modifier.widthIn(max = 340.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DailyRelapseCountWheelPicker(
                selectedCount = selectedCount,
                onSelectedCountChange = { selectedCount = it },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "We'll use this as your starting point, then help you return to plan one moment at a time.",
                color = OnboardingMutedText,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.92f),
            )
        }

        Spacer(modifier = Modifier.height(metrics.subtitleToOptionsSpacing))
    }
}

@Composable
private fun DailyRelapseCountWheelPicker(
    selectedCount: Int,
    onSelectedCountChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val values = remember { (1..10).toList() }
    val rowHeight = 54.dp
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedCount.coerceIn(1, 10) - 1)
    val haptics = rememberImpulsiveHaptics(enabled = true)
    var lastHapticCount by remember { mutableStateOf(selectedCount.coerceIn(1, 10)) }
    val centeredIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
            layoutInfo.visibleItemsInfo
                .minByOrNull { item -> abs((item.offset + item.size / 2) - viewportCenter) }
                ?.index
                ?.coerceIn(0, values.lastIndex)
                ?: listState.firstVisibleItemIndex.coerceIn(0, values.lastIndex)
        }
    }

    LaunchedEffect(centeredIndex) {
        val nextCount = values[centeredIndex]
        if (nextCount != selectedCount) onSelectedCountChange(nextCount)
        if (nextCount != lastHapticCount) { haptics.light(); lastHapticCount = nextCount }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { isScrolling: Boolean -> if (!isScrolling) listState.animateScrollToItem(centeredIndex) }
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .widthIn(max = 220.dp)
                .fillMaxWidth()
                .height(rowHeight * 5)
                .background(ImpulsiveSurface, RoundedCornerShape(30.dp))
                .border(BorderStroke(1.dp, ImpulsivePsychological.copy(alpha = 0.58f)), RoundedCornerShape(30.dp)),
            contentAlignment = Alignment.Center,
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = rowHeight * 2),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(values.size) { index ->
                    val value = values[index]
                    val selected = value == selectedCount
                    val distanceFromSelected = abs(value - selectedCount).coerceAtMost(2)
                    val itemAlpha = when { selected -> 1f; distanceFromSelected == 1 -> 0.56f; else -> 0.26f }
                    val itemScale = when { selected -> 1f; distanceFromSelected == 1 -> 0.90f; else -> 0.82f }

                    Box(
                        modifier = Modifier
                            .height(rowHeight)
                            .widthIn(min = 132.dp)
                            .graphicsLayer { alpha = itemAlpha; scaleX = itemScale; scaleY = itemScale }
                            .then(
                                if (selected) Modifier
                                    .background(ImpulsivePsychological.copy(alpha = 0.34f), RoundedCornerShape(24.dp))
                                    .border(BorderStroke(1.dp, ImpulsiveOverallTheme.copy(alpha = 0.42f)), RoundedCornerShape(24.dp))
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = value.toString(),
                            color = if (selected) OnboardingPrimary else OnboardingMutedText,
                            fontSize = if (selected) 36.sp else 22.sp,
                            lineHeight = if (selected) 40.sp else 28.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = if (selectedCount == 1) "time per day" else "times per day",
            color = OnboardingMutedText,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}
