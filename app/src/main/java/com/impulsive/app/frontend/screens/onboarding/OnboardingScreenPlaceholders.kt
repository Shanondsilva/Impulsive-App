package com.impulsive.app.frontend.screens.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.delay
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.impulsive.app.R
import com.impulsive.app.backend.domain.model.onboarding.OnboardingQuestionId
import com.impulsive.app.backend.session.onboarding.OnboardingState
import com.impulsive.app.frontend.theme.ImpulsiveOverallTheme
import com.impulsive.app.frontend.theme.ImpulsivePsychological
import com.impulsive.app.frontend.theme.ImpulsiveSurface
import com.impulsive.app.frontend.utils.rememberImpulsiveHaptics
import kotlin.math.abs
import kotlinx.coroutines.flow.distinctUntilChanged

// NOTE: LoginSignupGuestScreen used to live here as a placeholder. It moved to
// LoginScreen.kt and is now backed by AuthViewModel + FirebaseAuthRepository.

@Composable
fun OnboardingQuestionScreen(
    questionId: OnboardingQuestionId,
    state: OnboardingState,
    onMultiSelectAnswerChanged: (OnboardingQuestionId, List<String>) -> Unit,
    onSingleSelectAnswerChanged: (OnboardingQuestionId, String?) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onSkip: (() -> Unit)?,
) {
    when (questionId) {
        OnboardingQuestionId.Interrupting -> ReduceQuestionScreen(
            selectedOptionIds = state.answers.interrupting,
            onSelectionChanged = { selectedOptionIds ->
                onMultiSelectAnswerChanged(questionId, selectedOptionIds)
            },
            stepUi = OnboardingFlowStep.Reduction.toStepUi(showSkip = onSkip != null),
            onBack = onBack,
            onContinue = onContinue,
            onSkip = onSkip,
        )
        OnboardingQuestionId.Triggers -> TriggerQuestionScreen(
            selectedOptionIds = state.answers.triggers,
            onSelectionChanged = { selectedOptionIds ->
                onMultiSelectAnswerChanged(questionId, selectedOptionIds)
            },
            stepUi = OnboardingFlowStep.Triggers.toStepUi(showSkip = onSkip != null),
            onBack = onBack,
            onContinue = onContinue,
            onSkip = onSkip,
        )
        OnboardingQuestionId.Timing -> TimingQuestionScreen(
            selectedOptionIds = state.answers.timing,
            onSelectionChanged = { selectedOptionIds ->
                onMultiSelectAnswerChanged(questionId, selectedOptionIds)
            },
            stepUi = OnboardingFlowStep.Timing.toStepUi(showSkip = onSkip != null),
            onBack = onBack,
            onContinue = onContinue,
            onSkip = onSkip,
        )
        OnboardingQuestionId.WeekOneGoal -> WeekOneQuestionScreen(
            selectedOptionId = state.answers.weekOneGoal,
            onSelectionChanged = { selectedOptionId ->
                onSingleSelectAnswerChanged(questionId, selectedOptionId)
            },
            stepUi = OnboardingFlowStep.WeekOne.toStepUi(showSkip = onSkip != null),
            onBack = onBack,
            onContinue = onContinue,
            onSkip = onSkip,
        )
    }
}

@Composable
fun LevelOneRevealScreen(
    state: OnboardingState,
    onBack: () -> Unit,
    onFinish: () -> Unit,
) {
}

@Composable
private fun ReduceQuestionScreen(
    selectedOptionIds: List<String>,
    onSelectionChanged: (List<String>) -> Unit,
    stepUi: OnboardingStepUi,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onSkip: (() -> Unit)?,
) {
    val reducedMotion = rememberReducedMotion()
    val absorbState = rememberAbsorbAnimationState()
    var somethingElseText by remember { mutableStateOf("") }
    val somethingElseFocusRequester = remember { FocusRequester() }
    val somethingElseSelected = "something_else" in selectedOptionIds

    LaunchedEffect(somethingElseSelected) {
        if (somethingElseSelected) {
            delay(150)
            somethingElseFocusRequester.requestFocus()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        OnboardingScreenShell(
            backgroundColors = listOf(
                Color(0xFFFFFEFC),
                Color(0xFFFCF8FD),
                Color(0xFFF6F2FA),
            ),
            stepUi = stepUi,
            useImePadding = true,
            onBack = onBack,
            onSkip = onSkip,
            bottomBar = {
                ContinueButton(
                    enabled = selectedOptionIds.isNotEmpty(),
                    onClick = onContinue,
                )
            },
        ) { compactHeight ->
            val metrics = rememberQuestionResponsiveMetrics(compactHeight = compactHeight)

            Spacer(modifier = Modifier.height(metrics.headerToIconSpacing))

            OnboardingLogoVisual(
                reducedMotion = reducedMotion,
                scale = OnboardingLogoScale.Compact,
                absorbTrigger = absorbState.absorbTrigger,
                modifier = Modifier.onGloballyPositioned { coords ->
                    absorbState.logoCenter = coords.boundsInRoot().center
                },
            )

            Spacer(modifier = Modifier.height(metrics.iconToTitleSpacing))

            Text(
                text = "What do you want help interrupting?",
                color = OnboardingPrimary,
                fontSize = metrics.titleFontSize,
                lineHeight = metrics.titleLineHeight,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(metrics.titleToSubtitleSpacing))

            Text(
                text = "Select as many as you like. We'll tailor your experience to focus on these areas.",
                color = OnboardingMutedText,
                fontSize = metrics.subtitleFontSize,
                lineHeight = metrics.subtitleLineHeight,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.92f),
            )

            Spacer(modifier = Modifier.height(metrics.subtitleToOptionsSpacing))

            QuestionOptionGroup(areaMinHeight = metrics.optionAreaMinHeight) {
                ReduceOptions.forEach { option ->
                    ReduceOptionChip(
                        option = option,
                        selected = option.id in selectedOptionIds,
                        onClick = {
                            val updatedSelection = if (option.id in selectedOptionIds) {
                                selectedOptionIds - option.id
                            } else {
                                selectedOptionIds + option.id
                            }
                            onSelectionChanged(updatedSelection)
                        },
                        onTokenLaunch = if (reducedMotion) {
                            null
                        } else {
                            { center -> absorbState.launchToken(center) }
                        },
                    )
                }
            }

            AnimatedVisibility(
                visible = somethingElseSelected,
                enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(150)),
            ) {
                SomethingElseInput(
                    value = somethingElseText,
                    onValueChange = { somethingElseText = it },
                    focusRequester = somethingElseFocusRequester,
                )
            }
        }

        OnboardingAbsorbOverlay(state = absorbState)
    }
}

@Composable
private fun TriggerQuestionScreen(
    selectedOptionIds: List<String>,
    onSelectionChanged: (List<String>) -> Unit,
    stepUi: OnboardingStepUi,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onSkip: (() -> Unit)?,
) {
    val reducedMotion = rememberReducedMotion()
    val absorbState = rememberAbsorbAnimationState()

    Box(modifier = Modifier.fillMaxSize()) {
        OnboardingScreenShell(
            backgroundColors = listOf(
                Color(0xFFFFFEFC),
                Color(0xFFFCF8FD),
                Color(0xFFF6F2FA),
            ),
            stepUi = stepUi,
            onBack = onBack,
            onSkip = onSkip,
            bottomBar = {
                ContinueButton(
                    enabled = selectedOptionIds.isNotEmpty(),
                    onClick = onContinue,
                )
            },
        ) { compactHeight ->
            val metrics = rememberQuestionResponsiveMetrics(compactHeight = compactHeight)

            Spacer(modifier = Modifier.height(metrics.headerToIconSpacing))

            OnboardingLogoVisual(
                reducedMotion = reducedMotion,
                scale = OnboardingLogoScale.Compact,
                absorbTrigger = absorbState.absorbTrigger,
                modifier = Modifier.onGloballyPositioned { coords ->
                    absorbState.logoCenter = coords.boundsInRoot().center
                },
            )

            Spacer(modifier = Modifier.height(metrics.iconToTitleSpacing))

            Text(
                text = "What usually starts it?",
                color = OnboardingPrimary,
                fontSize = metrics.titleFontSize,
                lineHeight = metrics.titleLineHeight,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(metrics.titleToSubtitleSpacing))

            Text(
                text = "Select all the triggers you recognize. It's okay if you're not sure yet.",
                color = OnboardingMutedText,
                fontSize = metrics.subtitleFontSize,
                lineHeight = metrics.subtitleLineHeight,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.92f),
            )

            Spacer(modifier = Modifier.height(metrics.subtitleToOptionsSpacing))

            QuestionOptionGroup(areaMinHeight = metrics.optionAreaMinHeight) {
                TriggerOptions.forEach { option ->
                    TriggerOptionChip(
                        option = option,
                        selected = option.id in selectedOptionIds,
                        onClick = {
                            val updatedSelection = if (option.id in selectedOptionIds) {
                                selectedOptionIds - option.id
                            } else {
                                selectedOptionIds + option.id
                            }
                            onSelectionChanged(updatedSelection)
                        },
                        onTokenLaunch = if (reducedMotion) {
                            null
                        } else {
                            { center -> absorbState.launchToken(center) }
                        },
                    )
                }
            }
        }

        OnboardingAbsorbOverlay(state = absorbState)
    }
}

@Composable
private fun TimingQuestionScreen(
    selectedOptionIds: List<String>,
    onSelectionChanged: (List<String>) -> Unit,
    stepUi: OnboardingStepUi,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onSkip: (() -> Unit)?,
) {
    val reducedMotion = rememberReducedMotion()
    val absorbState = rememberAbsorbAnimationState()

    Box(modifier = Modifier.fillMaxSize()) {
        OnboardingScreenShell(
            backgroundColors = listOf(
                Color(0xFFFFFEFC),
                Color(0xFFFBF8FE),
                Color(0xFFF5F2FB),
            ),
            stepUi = stepUi,
            onBack = onBack,
            onSkip = onSkip,
            bottomBar = {
                ContinueButton(
                    enabled = selectedOptionIds.isNotEmpty(),
                    onClick = onContinue,
                )
            },
        ) { compactHeight ->
            val metrics = rememberQuestionResponsiveMetrics(compactHeight = compactHeight)

            Spacer(modifier = Modifier.height(metrics.headerToIconSpacing))

            OnboardingLogoVisual(
                reducedMotion = reducedMotion,
                scale = OnboardingLogoScale.Compact,
                absorbTrigger = absorbState.absorbTrigger,
                modifier = Modifier.onGloballyPositioned { coords ->
                    absorbState.logoCenter = coords.boundsInRoot().center
                },
            )

            Spacer(modifier = Modifier.height(metrics.iconToTitleSpacing))

            Text(
                text = "When does it usually happen?",
                color = OnboardingPrimary,
                fontSize = metrics.titleFontSize,
                lineHeight = metrics.titleLineHeight,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(metrics.titleToSubtitleSpacing))

            Text(
                text = "Select all that apply. This helps us understand your patterns.",
                color = OnboardingMutedText,
                fontSize = metrics.subtitleFontSize,
                lineHeight = metrics.subtitleLineHeight,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.92f),
            )

            Spacer(modifier = Modifier.height(metrics.subtitleToOptionsSpacing))

            QuestionOptionGroup(areaMinHeight = metrics.optionAreaMinHeight) {
                TimingOptions.forEach { option ->
                    TimingOptionChip(
                        option = option,
                        selected = option.id in selectedOptionIds,
                        onClick = {
                            val updatedSelection = if (option.id in selectedOptionIds) {
                                selectedOptionIds - option.id
                            } else {
                                selectedOptionIds + option.id
                            }
                            onSelectionChanged(updatedSelection)
                        },
                        onTokenLaunch = if (reducedMotion) {
                            null
                        } else {
                            { center -> absorbState.launchToken(center) }
                        },
                    )
                }
            }
        }

        OnboardingAbsorbOverlay(state = absorbState)
    }
}

@Composable
private fun WeekOneQuestionScreen(
    selectedOptionId: String?,
    onSelectionChanged: (String?) -> Unit,
    stepUi: OnboardingStepUi,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onSkip: (() -> Unit)?,
) {
    val reducedMotion = rememberReducedMotion()
    val absorbState = rememberAbsorbAnimationState()

    Box(modifier = Modifier.fillMaxSize()) {
        OnboardingScreenShell(
            backgroundColors = listOf(
                Color(0xFFFFFEFC),
                Color(0xFFFBF8FE),
                Color(0xFFF5F2FB),
            ),
            stepUi = stepUi,
            onBack = onBack,
            onSkip = onSkip,
            bottomBar = {
                ContinueButton(
                    enabled = selectedOptionId != null,
                    onClick = onContinue,
                )
            },
        ) { compactHeight ->
            val metrics = rememberQuestionResponsiveMetrics(compactHeight = compactHeight)

            Spacer(modifier = Modifier.height(metrics.headerToIconSpacing))

            OnboardingLogoVisual(
                reducedMotion = reducedMotion,
                scale = OnboardingLogoScale.Compact,
                absorbTrigger = absorbState.absorbTrigger,
                modifier = Modifier.onGloballyPositioned { coords ->
                    absorbState.logoCenter = coords.boundsInRoot().center
                },
            )

            Spacer(modifier = Modifier.height(metrics.iconToTitleSpacing))

            Text(
                text = "What feels realistic for week one?",
                color = OnboardingPrimary,
                fontSize = metrics.titleFontSize,
                lineHeight = metrics.titleLineHeight,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(metrics.titleToSubtitleSpacing))

            Text(
                text = "Select a gentle starting point. You can always adjust this later.",
                color = OnboardingMutedText,
                fontSize = metrics.subtitleFontSize,
                lineHeight = metrics.subtitleLineHeight,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.92f),
            )

            Spacer(modifier = Modifier.height(metrics.subtitleToOptionsSpacing))

            QuestionOptionGroup(areaMinHeight = metrics.optionAreaMinHeight) {
                WeekOneOptions.forEach { option ->
                    ReduceOptionChip(
                        option = option,
                        selected = option.id == selectedOptionId,
                        onClick = {
                            val updatedSelection = if (option.id == selectedOptionId) {
                                null
                            } else {
                                option.id
                            }
                            onSelectionChanged(updatedSelection)
                        },
                        onTokenLaunch = if (reducedMotion) {
                            null
                        } else {
                            { center -> absorbState.launchToken(center) }
                        },
                    )
                }
            }
        }

        OnboardingAbsorbOverlay(state = absorbState)
    }
}

@Composable
fun OnboardingDailyRelapseCountScreen(
    state: OnboardingState,
    initialCount: Int,
    onBack: () -> Unit,
    onContinue: (Int) -> Unit,
) {
    val reducedMotion = rememberReducedMotion()
    var selectedCount by remember(initialCount) {
        mutableStateOf(initialCount.coerceIn(1, 10))
    }

    OnboardingScreenShell(
        backgroundColors = listOf(
            Color(0xFFFFFEFC),
            Color(0xFFFBF8FE),
            Color(0xFFF5F2FB),
        ),
        stepUi = OnboardingFlowStep.DailyRelapseCount.toStepUi(),
        onBack = onBack,
        onSkip = null,
        bottomBar = {
            ContinueButton(
                enabled = true,
                label = "Continue",
                onClick = { onContinue(selectedCount) },
            )
        },
    ) { compactHeight ->
        val metrics = rememberQuestionResponsiveMetrics(compactHeight = compactHeight)

        Spacer(modifier = Modifier.height(metrics.headerToIconSpacing))

        OnboardingLogoVisual(
            reducedMotion = reducedMotion,
            scale = OnboardingLogoScale.Compact,
        )

        Spacer(modifier = Modifier.height(metrics.iconToTitleSpacing))

        Text(
            text = "How many times in a day do you feel like relapsing?",
            color = OnboardingPrimary,
            fontSize = metrics.titleFontSize,
            lineHeight = metrics.titleLineHeight,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(metrics.titleToSubtitleSpacing))

        Column(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DailyRelapseCountWheelPicker(
                selectedCount = selectedCount,
                onSelectedCountChange = { selectedCount = it },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "We’ll use this as your starting point, then reduce it slowly.",
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
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = selectedCount.coerceIn(1, 10) - 1,
    )
    val haptics = rememberImpulsiveHaptics(enabled = true)
    var lastHapticCount by remember { mutableStateOf(selectedCount.coerceIn(1, 10)) }
    val centeredIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
            layoutInfo.visibleItemsInfo
                .minByOrNull { item ->
                    abs((item.offset + item.size / 2) - viewportCenter)
                }
                ?.index
                ?.coerceIn(0, values.lastIndex)
                ?: listState.firstVisibleItemIndex.coerceIn(0, values.lastIndex)
        }
    }

    LaunchedEffect(centeredIndex) {
        val nextCount = values[centeredIndex]
        if (nextCount != selectedCount) {
            onSelectedCountChange(nextCount)
        }
        if (nextCount != lastHapticCount) {
            haptics.light()
            lastHapticCount = nextCount
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { isScrolling ->
                if (!isScrolling) {
                    listState.animateScrollToItem(centeredIndex)
                }
            }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 220.dp)
                .fillMaxWidth()
                .height(rowHeight * 5)
                .background(ImpulsiveSurface, RoundedCornerShape(30.dp))
                .border(
                    BorderStroke(1.dp, ImpulsivePsychological.copy(alpha = 0.58f)),
                    RoundedCornerShape(30.dp),
                ),
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
                    val itemAlpha = when {
                        selected -> 1f
                        distanceFromSelected == 1 -> 0.56f
                        else -> 0.26f
                    }
                    val itemScale = when {
                        selected -> 1f
                        distanceFromSelected == 1 -> 0.90f
                        else -> 0.82f
                    }

                    Box(
                        modifier = Modifier
                            .height(rowHeight)
                            .widthIn(min = 132.dp)
                            .graphicsLayer {
                                alpha = itemAlpha
                                scaleX = itemScale
                                scaleY = itemScale
                            }
                            .then(
                                if (selected) {
                                    Modifier
                                        .background(
                                            ImpulsivePsychological.copy(alpha = 0.34f),
                                            RoundedCornerShape(24.dp),
                                        )
                                        .border(
                                            BorderStroke(
                                                1.dp,
                                                ImpulsiveOverallTheme.copy(alpha = 0.42f),
                                            ),
                                            RoundedCornerShape(24.dp),
                                        )
                                } else {
                                    Modifier
                                },
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

@Composable
fun OnboardingStartingPointScreen(
    state: OnboardingState,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val reducedMotion = rememberReducedMotion()

    OnboardingScreenShell(
        backgroundColors = listOf(
            Color(0xFFFFFEFC),
            Color(0xFFFBF8FE),
            Color(0xFFF5F2FB),
        ),
        stepUi = OnboardingFlowStep.StartingPoint.toStepUi(),
        onBack = onBack,
        onSkip = null,
        bottomBar = {
            ContinueButton(
                enabled = true,
                label = "Start week one",
                onClick = onContinue,
            )
        },
    ) { compactHeight ->
        val metrics = rememberQuestionResponsiveMetrics(compactHeight = compactHeight)
        val answers = state.answers
        val dailyRelapseLabel = if (answers.dailyRelapseUrgeCount == 1) {
            "1 time per day"
        } else {
            "${answers.dailyRelapseUrgeCount} times per day"
        }
        val summaryItems = listOf(
            StartingPointSummaryItem(
                title = "You're focusing on",
                value = selectedSummary(
                    selectedIds = answers.interrupting,
                    labels = ReduceOptions.map { it.id to it.label.lowercase() },
                    emptyText = "a small pattern you want to understand",
                ),
            ),
            StartingPointSummaryItem(
                title = "It may start with",
                value = selectedSummary(
                    selectedIds = answers.triggers,
                    labels = TriggerOptions.map { it.id to it.label.lowercase() },
                    emptyText = "a few moments to notice gently",
                ),
            ),
            StartingPointSummaryItem(
                title = "It tends to show up",
                value = selectedSummary(
                    selectedIds = answers.timing,
                    labels = TimingOptions.map { it.id to it.label.lowercase() },
                    emptyText = "at times you can learn from",
                ),
            ),
            StartingPointSummaryItem(
                title = "Your week-one step is to",
                value = selectedWeekOneLabel(
                    selectedId = answers.weekOneGoal,
                    emptyText = "start gently and adjust as you learn",
                ),
                emphasized = true,
            ),
            StartingPointSummaryItem(
                title = "Daily relapse urge count",
                value = "You chose: $dailyRelapseLabel",
            ),
        )

        Spacer(modifier = Modifier.height(metrics.headerToIconSpacing))

        OnboardingLogoVisual(
            reducedMotion = reducedMotion,
            scale = OnboardingLogoScale.Compact,
        )

        Spacer(modifier = Modifier.height(metrics.iconToTitleSpacing))

        Text(
            text = "Here's your starting point",
            color = OnboardingPrimary,
            fontSize = metrics.titleFontSize,
            lineHeight = metrics.titleLineHeight,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(metrics.titleToSubtitleSpacing))

        Text(
            text = "A simple week-one plan based on what you shared. No pressure, just a place to begin.",
            color = OnboardingMutedText,
            fontSize = metrics.subtitleFontSize,
            lineHeight = metrics.subtitleLineHeight,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.92f),
        )

        Spacer(modifier = Modifier.height(metrics.subtitleToOptionsSpacing))

        QuestionOptionGroup(
            areaMinHeight = metrics.optionAreaMinHeight + if (compactHeight) 28.dp else 72.dp,
        ) {
            summaryItems.forEach { item ->
                StartingPointSummaryLine(item = item)
            }
        }
    }
}

@Composable
private fun rememberQuestionResponsiveMetrics(
    compactHeight: Boolean,
): OnboardingQuestionMetrics {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return when {
        widthDp < 380 -> OnboardingQuestionMetrics(
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
        widthDp < 430 -> OnboardingQuestionMetrics(
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
        widthDp < 600 -> OnboardingQuestionMetrics(
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

private data class OnboardingQuestionMetrics(
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

@Composable
private fun QuestionGlowIcon() {
    Box(
        modifier = Modifier
            .size(64.dp)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ImpulsivePsychological.copy(alpha = 0.20f),
                            ImpulsiveOverallTheme.copy(alpha = 0.12f),
                            Color.Transparent,
                        ),
                        radius = size.minDimension * 0.9f,
                    ),
                    radius = size.minDimension * 0.66f,
                )
            }
            .background(Color(0xFFF1ECF0), CircleShape)
            .border(1.dp, Color(0xFFE6E1E5), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(30.dp)) {
            val strokeColor = OnboardingPrimary
            val strokeWidth = 2.2.dp.toPx()
            drawCircle(
                color = strokeColor,
                radius = size.minDimension * 0.28f,
                center = Offset(size.width * 0.42f, size.height * 0.42f),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            drawLine(
                color = strokeColor,
                start = Offset(size.width * 0.62f, size.height * 0.62f),
                end = Offset(size.width * 0.82f, size.height * 0.82f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = strokeColor,
                start = Offset(size.width * 0.24f, size.height * 0.42f),
                end = Offset(size.width * 0.60f, size.height * 0.42f),
                strokeWidth = 1.6.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = strokeColor,
                start = Offset(size.width * 0.42f, size.height * 0.24f),
                end = Offset(size.width * 0.42f, size.height * 0.60f),
                strokeWidth = 1.6.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun TriggerGlowIcon() {
    Box(
        modifier = Modifier
            .size(64.dp)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ImpulsivePsychological.copy(alpha = 0.22f),
                            ImpulsiveOverallTheme.copy(alpha = 0.12f),
                            Color.Transparent,
                        ),
                        radius = size.minDimension * 0.9f,
                    ),
                    radius = size.minDimension * 0.66f,
                )
            }
            .background(Color(0xFFF1ECF0), CircleShape)
            .border(1.dp, Color(0xFFE6E1E5), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(30.dp)) {
            val strokeColor = OnboardingPrimary
            val strokeWidth = 2.2.dp.toPx()
            drawCircle(
                color = strokeColor,
                radius = size.minDimension * 0.28f,
                center = Offset(size.width * 0.48f, size.height * 0.45f),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            drawLine(
                color = strokeColor,
                start = Offset(size.width * 0.30f, size.height * 0.66f),
                end = Offset(size.width * 0.30f, size.height * 0.84f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = strokeColor,
                start = Offset(size.width * 0.68f, size.height * 0.66f),
                end = Offset(size.width * 0.68f, size.height * 0.84f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = strokeColor,
                start = Offset(size.width * 0.22f, size.height * 0.26f),
                end = Offset(size.width * 0.34f, size.height * 0.26f),
                strokeWidth = 1.7.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = strokeColor,
                start = Offset(size.width * 0.28f, size.height * 0.20f),
                end = Offset(size.width * 0.28f, size.height * 0.32f),
                strokeWidth = 1.7.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = strokeColor,
                start = Offset(size.width * 0.74f, size.height * 0.28f),
                end = Offset(size.width * 0.86f, size.height * 0.28f),
                strokeWidth = 1.7.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = strokeColor,
                start = Offset(size.width * 0.80f, size.height * 0.22f),
                end = Offset(size.width * 0.80f, size.height * 0.34f),
                strokeWidth = 1.7.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun TimingGlowIcon() {
    Box(
        modifier = Modifier
            .size(64.dp)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ImpulsivePsychological.copy(alpha = 0.22f),
                            ImpulsiveOverallTheme.copy(alpha = 0.12f),
                            Color.Transparent,
                        ),
                        radius = size.minDimension * 0.9f,
                    ),
                    radius = size.minDimension * 0.66f,
                )
            }
            .background(Color(0xFFF1ECF0), CircleShape)
            .border(1.dp, Color(0xFFE6E1E5), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(30.dp)) {
            val strokeColor = OnboardingPrimary
            val strokeWidth = 2.2.dp.toPx()
            drawCircle(
                color = strokeColor,
                radius = size.minDimension * 0.34f,
                center = center,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            drawLine(
                color = strokeColor,
                start = center,
                end = Offset(size.width * 0.50f, size.height * 0.28f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = strokeColor,
                start = center,
                end = Offset(size.width * 0.68f, size.height * 0.56f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = ImpulsiveOverallTheme.copy(alpha = 0.72f),
                radius = 2.3.dp.toPx(),
                center = Offset(size.width * 0.78f, size.height * 0.22f),
            )
            drawCircle(
                color = ImpulsivePsychological.copy(alpha = 0.82f),
                radius = 1.8.dp.toPx(),
                center = Offset(size.width * 0.22f, size.height * 0.78f),
            )
        }
    }
}

@Composable
private fun QuestionOptionGroup(
    areaMinHeight: Dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = areaMinHeight),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            content()
        }
    }
}

@Composable
private fun StartingPointSummaryLine(
    item: StartingPointSummaryItem,
) {
    val shape = RoundedCornerShape(24.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (item.emphasized) Modifier.padding(top = 6.dp) else Modifier)
            .background(
                if (item.emphasized) OnboardingSelectedOptionSurface else Color.White,
                shape,
            )
            .border(
                BorderStroke(
                    width = 1.dp,
                    color = if (item.emphasized) OnboardingPrimary else OnboardingOptionPillBorder,
                ),
                shape,
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text(
            text = item.title,
            color = OnboardingMutedText,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = item.value,
            color = OnboardingText,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ReduceOptionChip(
    option: ReduceOption,
    selected: Boolean,
    onClick: () -> Unit,
    onTokenLaunch: ((Offset) -> Unit)? = null,
) {
    val haptics = rememberImpulsiveHaptics(enabled = true)
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) OnboardingSelectedOptionSurface else Color.White,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "reduce-option-background",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) OnboardingPrimary else OnboardingOptionPillBorder,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "reduce-option-border",
    )
    var chipCenter by remember { mutableStateOf<Offset?>(null) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .onGloballyPositioned { coords -> chipCenter = coords.boundsInRoot().center }
            .background(backgroundColor, CircleShape)
            .border(BorderStroke(1.dp, borderColor), CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if (!selected) {
                        haptics.light()
                        chipCenter?.let { onTokenLaunch?.invoke(it) }
                    }
                    onClick()
                },
            )
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OptionIconMark(
            icon = option.icon,
            selected = selected,
            plain = true,
            plainIconSize = 16.dp,
        )

        Spacer(modifier = Modifier.size(8.dp))

        Text(
            text = option.label,
            color = OnboardingText,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TriggerOptionChip(
    option: TriggerOption,
    selected: Boolean,
    onClick: () -> Unit,
    onTokenLaunch: ((Offset) -> Unit)? = null,
) {
    val haptics = rememberImpulsiveHaptics(enabled = true)
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) OnboardingSelectedOptionSurface else Color.White,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "trigger-option-background",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) OnboardingPrimary else OnboardingOptionPillBorder,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "trigger-option-border",
    )
    var chipCenter by remember { mutableStateOf<Offset?>(null) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .onGloballyPositioned { coords ->
                chipCenter = coords.boundsInRoot().center
            }
            .background(backgroundColor, CircleShape)
            .border(BorderStroke(1.dp, borderColor), CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if (!selected) {
                        haptics.light()
                        chipCenter?.let { onTokenLaunch?.invoke(it) }
                    }
                    onClick()
                },
            )
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OptionIconMark(icon = option.icon, selected = selected, plain = true, plainIconSize = 16.dp)

        Spacer(modifier = Modifier.size(8.dp))

        Text(
            text = option.label,
            color = OnboardingText,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun OptionIconMark(
    icon: OnboardingOptionIcon,
    selected: Boolean,
    plain: Boolean = false,
    plainIconSize: Dp = 18.dp,
) {
    val fillColor by animateColorAsState(
        targetValue = if (selected) Color(0xFFFFFEFC).copy(alpha = 0.74f) else OnboardingIconSurface,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "option-icon-fill",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) OnboardingPrimary else OnboardingIconMuted,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "option-icon-content",
    )
    val drawableResId = icon.drawableResId

    if (drawableResId != null) {
        Image(
            painter = painterResource(id = drawableResId),
            contentDescription = null,
            modifier = Modifier.size(if (plain) plainIconSize else 18.dp),
            colorFilter = ColorFilter.tint(contentColor),
        )
    } else {
        Box(
            modifier = if (plain) {
                Modifier.size(plainIconSize)
            } else {
                Modifier
                    .size(40.dp)
                    .background(fillColor, CircleShape)
                    .border(1.dp, OnboardingIconBorder, CircleShape)
            },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(if (plain) plainIconSize else 22.dp)) {
            val strokeWidth = 2.1.dp.toPx()
            val thinStroke = 1.7.dp.toPx()

            when (icon) {
                OnboardingOptionIcon.PrivateHabit,
                OnboardingOptionIcon.CompulsiveScrolling,
                OnboardingOptionIcon.LateNightPhone,
                OnboardingOptionIcon.BrowserHabit,
                OnboardingOptionIcon.SomethingElse,
                OnboardingOptionIcon.LateAtNight,
                OnboardingOptionIcon.RightAfterWaking,
                OnboardingOptionIcon.AloneOnPhone,
                OnboardingOptionIcon.WhenBored,
                OnboardingOptionIcon.WhenStressed,
                OnboardingOptionIcon.TroubleSleeping,
                OnboardingOptionIcon.SocialMedia,
                OnboardingOptionIcon.BrowserSearch,
                OnboardingOptionIcon.MemoryOrThought,
                OnboardingOptionIcon.BoredomTrigger,
                OnboardingOptionIcon.BeingAlone,
                OnboardingOptionIcon.StressTrigger,
                OnboardingOptionIcon.NoticeTriggers,
                OnboardingOptionIcon.CutDownLittle,
                OnboardingOptionIcon.DailyResetHabit,
                OnboardingOptionIcon.CutDownHalf -> Unit
                OnboardingOptionIcon.Shield -> {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(size.width * 0.50f, size.height * 0.10f)
                        lineTo(size.width * 0.82f, size.height * 0.24f)
                        lineTo(size.width * 0.76f, size.height * 0.64f)
                        quadraticBezierTo(size.width * 0.50f, size.height * 0.90f, size.width * 0.24f, size.height * 0.64f)
                        lineTo(size.width * 0.18f, size.height * 0.24f)
                        close()
                    }
                    drawPath(path, contentColor, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                    drawLine(
                        color = contentColor,
                        start = Offset(size.width * 0.34f, size.height * 0.52f),
                        end = Offset(size.width * 0.45f, size.height * 0.64f),
                        strokeWidth = thinStroke,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = contentColor,
                        start = Offset(size.width * 0.45f, size.height * 0.64f),
                        end = Offset(size.width * 0.66f, size.height * 0.40f),
                        strokeWidth = thinStroke,
                        cap = StrokeCap.Round,
                    )
                }
                OnboardingOptionIcon.Incognito -> {
                    drawArc(
                        color = contentColor,
                        startAngle = 205f,
                        sweepAngle = 130f,
                        useCenter = false,
                        topLeft = Offset(size.width * 0.18f, size.height * 0.18f),
                        size = androidx.compose.ui.geometry.Size(size.width * 0.64f, size.height * 0.52f),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                    drawLine(contentColor, Offset(size.width * 0.16f, size.height * 0.56f), Offset(size.width * 0.84f, size.height * 0.56f), strokeWidth, cap = StrokeCap.Round)
                    drawCircle(contentColor, radius = size.minDimension * 0.14f, center = Offset(size.width * 0.34f, size.height * 0.68f), style = Stroke(width = thinStroke, cap = StrokeCap.Round))
                    drawCircle(contentColor, radius = size.minDimension * 0.14f, center = Offset(size.width * 0.66f, size.height * 0.68f), style = Stroke(width = thinStroke, cap = StrokeCap.Round))
                    drawLine(contentColor, Offset(size.width * 0.48f, size.height * 0.68f), Offset(size.width * 0.52f, size.height * 0.68f), thinStroke, cap = StrokeCap.Round)
                }
                OnboardingOptionIcon.Social -> {
                    val left = Offset(size.width * 0.24f, size.height * 0.58f)
                    val top = Offset(size.width * 0.54f, size.height * 0.28f)
                    val right = Offset(size.width * 0.78f, size.height * 0.70f)
                    drawLine(contentColor, left, top, thinStroke, cap = StrokeCap.Round)
                    drawLine(contentColor, top, right, thinStroke, cap = StrokeCap.Round)
                    drawLine(contentColor, left, right, thinStroke, cap = StrokeCap.Round)
                    drawCircle(contentColor, radius = 2.5.dp.toPx(), center = left)
                    drawCircle(contentColor, radius = 2.5.dp.toPx(), center = top)
                    drawCircle(contentColor, radius = 2.5.dp.toPx(), center = right)
                }
                OnboardingOptionIcon.Loop -> {
                    drawArc(contentColor, 35f, 250f, false, Offset(size.width * 0.14f, size.height * 0.18f), androidx.compose.ui.geometry.Size(size.width * 0.72f, size.height * 0.62f), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                    drawLine(contentColor, Offset(size.width * 0.74f, size.height * 0.18f), Offset(size.width * 0.84f, size.height * 0.38f), strokeWidth, cap = StrokeCap.Round)
                    drawLine(contentColor, Offset(size.width * 0.74f, size.height * 0.18f), Offset(size.width * 0.54f, size.height * 0.22f), strokeWidth, cap = StrokeCap.Round)
                }
                OnboardingOptionIcon.Search -> {
                    drawCircle(contentColor, radius = size.minDimension * 0.25f, center = Offset(size.width * 0.42f, size.height * 0.42f), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                    drawLine(contentColor, Offset(size.width * 0.62f, size.height * 0.62f), Offset(size.width * 0.82f, size.height * 0.82f), strokeWidth, cap = StrokeCap.Round)
                }
                OnboardingOptionIcon.Stress -> {
                    drawLine(contentColor, Offset(size.width * 0.50f, size.height * 0.16f), Offset(size.width * 0.50f, size.height * 0.84f), strokeWidth, cap = StrokeCap.Round)
                    drawArc(contentColor, 180f, 145f, false, Offset(size.width * 0.14f, size.height * 0.30f), androidx.compose.ui.geometry.Size(size.width * 0.44f, size.height * 0.34f), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                    drawArc(contentColor, 215f, 145f, false, Offset(size.width * 0.42f, size.height * 0.30f), androidx.compose.ui.geometry.Size(size.width * 0.44f, size.height * 0.34f), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                }
                OnboardingOptionIcon.Boredom -> {
                    drawCircle(contentColor, radius = size.minDimension * 0.34f, center = center, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                    drawCircle(contentColor, radius = 1.5.dp.toPx(), center = Offset(size.width * 0.40f, size.height * 0.43f))
                    drawCircle(contentColor, radius = 1.5.dp.toPx(), center = Offset(size.width * 0.60f, size.height * 0.43f))
                    drawLine(contentColor, Offset(size.width * 0.38f, size.height * 0.65f), Offset(size.width * 0.62f, size.height * 0.65f), thinStroke, cap = StrokeCap.Round)
                }
                OnboardingOptionIcon.Lonely,
                OnboardingOptionIcon.Person -> {
                    drawCircle(contentColor, radius = size.minDimension * 0.15f, center = Offset(size.width * 0.50f, size.height * 0.34f), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                    drawArc(contentColor, 205f, 130f, false, Offset(size.width * 0.24f, size.height * 0.50f), androidx.compose.ui.geometry.Size(size.width * 0.52f, size.height * 0.36f), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                    if (icon == OnboardingOptionIcon.Lonely) {
                        drawCircle(contentColor.copy(alpha = 0.5f), radius = size.minDimension * 0.10f, center = Offset(size.width * 0.76f, size.height * 0.34f), style = Stroke(width = thinStroke, cap = StrokeCap.Round))
                    }
                }
                OnboardingOptionIcon.Heart -> {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(size.width * 0.50f, size.height * 0.80f)
                        cubicTo(size.width * 0.16f, size.height * 0.58f, size.width * 0.18f, size.height * 0.26f, size.width * 0.38f, size.height * 0.26f)
                        cubicTo(size.width * 0.48f, size.height * 0.26f, size.width * 0.50f, size.height * 0.36f, size.width * 0.50f, size.height * 0.36f)
                        cubicTo(size.width * 0.50f, size.height * 0.36f, size.width * 0.52f, size.height * 0.26f, size.width * 0.62f, size.height * 0.26f)
                        cubicTo(size.width * 0.82f, size.height * 0.26f, size.width * 0.84f, size.height * 0.58f, size.width * 0.50f, size.height * 0.80f)
                    }
                    drawPath(path, contentColor, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                }
                OnboardingOptionIcon.Moon -> {
                    drawArc(contentColor, 82f, 230f, false, Offset(size.width * 0.18f, size.height * 0.12f), androidx.compose.ui.geometry.Size(size.width * 0.62f, size.height * 0.76f), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                    drawArc(contentColor, 95f, 200f, false, Offset(size.width * 0.34f, size.height * 0.10f), androidx.compose.ui.geometry.Size(size.width * 0.58f, size.height * 0.78f), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                }
                OnboardingOptionIcon.Morning,
                OnboardingOptionIcon.Afternoon,
                OnboardingOptionIcon.Evening -> {
                    val radius = if (icon == OnboardingOptionIcon.Afternoon) size.minDimension * 0.20f else size.minDimension * 0.16f
                    val y = if (icon == OnboardingOptionIcon.Evening) size.height * 0.62f else size.height * 0.44f
                    drawCircle(contentColor, radius = radius, center = Offset(size.width * 0.50f, y), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                    repeat(6) { index ->
                        val angle = Math.toRadians((index * 60).toDouble())
                        val start = Offset(x = size.width * 0.50f + kotlin.math.cos(angle).toFloat() * size.minDimension * 0.31f, y = y + kotlin.math.sin(angle).toFloat() * size.minDimension * 0.31f)
                        val end = Offset(x = size.width * 0.50f + kotlin.math.cos(angle).toFloat() * size.minDimension * 0.42f, y = y + kotlin.math.sin(angle).toFloat() * size.minDimension * 0.42f)
                        drawLine(contentColor, start, end, thinStroke, cap = StrokeCap.Round)
                    }
                    if (icon == OnboardingOptionIcon.Evening) {
                        drawLine(contentColor, Offset(size.width * 0.18f, size.height * 0.78f), Offset(size.width * 0.82f, size.height * 0.78f), strokeWidth, cap = StrokeCap.Round)
                    }
                }
                OnboardingOptionIcon.Work -> {
                    drawRoundRect(contentColor, Offset(size.width * 0.18f, size.height * 0.36f), androidx.compose.ui.geometry.Size(size.width * 0.64f, size.height * 0.42f), androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                    drawLine(contentColor, Offset(size.width * 0.38f, size.height * 0.36f), Offset(size.width * 0.38f, size.height * 0.26f), thinStroke, cap = StrokeCap.Round)
                    drawLine(contentColor, Offset(size.width * 0.62f, size.height * 0.36f), Offset(size.width * 0.62f, size.height * 0.26f), thinStroke, cap = StrokeCap.Round)
                    drawLine(contentColor, Offset(size.width * 0.38f, size.height * 0.26f), Offset(size.width * 0.62f, size.height * 0.26f), thinStroke, cap = StrokeCap.Round)
                }
                OnboardingOptionIcon.Notice -> {
                    drawCircle(contentColor, radius = size.minDimension * 0.30f, center = center, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                    drawCircle(contentColor, radius = size.minDimension * 0.10f, center = center)
                }
                OnboardingOptionIcon.Pause -> {
                    drawRoundRect(contentColor, Offset(size.width * 0.30f, size.height * 0.22f), androidx.compose.ui.geometry.Size(size.width * 0.12f, size.height * 0.56f), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
                    drawRoundRect(contentColor, Offset(size.width * 0.58f, size.height * 0.22f), androidx.compose.ui.geometry.Size(size.width * 0.12f, size.height * 0.56f), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
                }
                OnboardingOptionIcon.Target -> {
                    drawCircle(contentColor, radius = size.minDimension * 0.34f, center = center, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                    drawCircle(contentColor, radius = size.minDimension * 0.18f, center = center, style = Stroke(width = thinStroke, cap = StrokeCap.Round))
                    drawCircle(contentColor, radius = 2.dp.toPx(), center = center)
                }
                OnboardingOptionIcon.Boundary -> {
                    drawRoundRect(contentColor, Offset(size.width * 0.22f, size.height * 0.18f), androidx.compose.ui.geometry.Size(size.width * 0.56f, size.height * 0.64f), androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                    drawLine(contentColor, Offset(size.width * 0.36f, size.height * 0.50f), Offset(size.width * 0.64f, size.height * 0.50f), strokeWidth, cap = StrokeCap.Round)
                }
                OnboardingOptionIcon.CheckIn -> {
                    drawCircle(contentColor, radius = size.minDimension * 0.34f, center = center, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                    drawLine(contentColor, Offset(size.width * 0.32f, size.height * 0.52f), Offset(size.width * 0.44f, size.height * 0.64f), strokeWidth, cap = StrokeCap.Round)
                    drawLine(contentColor, Offset(size.width * 0.44f, size.height * 0.64f), Offset(size.width * 0.70f, size.height * 0.38f), strokeWidth, cap = StrokeCap.Round)
                }
                OnboardingOptionIcon.Lock -> {
                    // shackle arc
                    drawArc(contentColor, 180f, 180f, false, Offset(size.width * 0.32f, size.height * 0.16f), androidx.compose.ui.geometry.Size(size.width * 0.36f, size.height * 0.36f), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                    // lock body
                    drawRoundRect(contentColor, Offset(size.width * 0.22f, size.height * 0.48f), androidx.compose.ui.geometry.Size(size.width * 0.56f, size.height * 0.36f), androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                    // keyhole dot
                    drawCircle(contentColor, radius = 2.6.dp.toPx(), center = Offset(size.width * 0.50f, size.height * 0.64f))
                }
                OnboardingOptionIcon.Swipe -> {
                    // up arrow
                    drawLine(contentColor, Offset(size.width * 0.50f, size.height * 0.16f), Offset(size.width * 0.50f, size.height * 0.50f), strokeWidth, cap = StrokeCap.Round)
                    drawLine(contentColor, Offset(size.width * 0.34f, size.height * 0.30f), Offset(size.width * 0.50f, size.height * 0.16f), strokeWidth, cap = StrokeCap.Round)
                    drawLine(contentColor, Offset(size.width * 0.66f, size.height * 0.30f), Offset(size.width * 0.50f, size.height * 0.16f), strokeWidth, cap = StrokeCap.Round)
                    // down arrow
                    drawLine(contentColor, Offset(size.width * 0.50f, size.height * 0.50f), Offset(size.width * 0.50f, size.height * 0.84f), strokeWidth, cap = StrokeCap.Round)
                    drawLine(contentColor, Offset(size.width * 0.34f, size.height * 0.70f), Offset(size.width * 0.50f, size.height * 0.84f), strokeWidth, cap = StrokeCap.Round)
                    drawLine(contentColor, Offset(size.width * 0.66f, size.height * 0.70f), Offset(size.width * 0.50f, size.height * 0.84f), strokeWidth, cap = StrokeCap.Round)
                }
                OnboardingOptionIcon.Globe -> {
                    // outer circle
                    drawCircle(contentColor, radius = size.minDimension * 0.34f, center = center, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                    // horizontal equator line
                    drawLine(contentColor, Offset(size.width * 0.16f, size.height * 0.50f), Offset(size.width * 0.84f, size.height * 0.50f), thinStroke, cap = StrokeCap.Round)
                    // vertical meridian line
                    drawLine(contentColor, Offset(size.width * 0.50f, size.height * 0.16f), Offset(size.width * 0.50f, size.height * 0.84f), thinStroke, cap = StrokeCap.Round)
                    // inner oval (longitude arc)
                    drawArc(contentColor, 0f, 360f, false, Offset(size.width * 0.32f, size.height * 0.16f), androidx.compose.ui.geometry.Size(size.width * 0.36f, size.height * 0.68f), style = Stroke(width = thinStroke, cap = StrokeCap.Round))
                }
                OnboardingOptionIcon.Add -> {
                    drawLine(contentColor, Offset(size.width * 0.50f, size.height * 0.18f), Offset(size.width * 0.50f, size.height * 0.82f), strokeWidth, cap = StrokeCap.Round)
                    drawLine(contentColor, Offset(size.width * 0.18f, size.height * 0.50f), Offset(size.width * 0.82f, size.height * 0.50f), strokeWidth, cap = StrokeCap.Round)
                }
                OnboardingOptionIcon.Smartphone -> {
                    // phone body
                    drawRoundRect(contentColor, Offset(size.width * 0.28f, size.height * 0.10f), androidx.compose.ui.geometry.Size(size.width * 0.44f, size.height * 0.80f), androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                    // home button dot
                    drawCircle(contentColor, radius = 2.2.dp.toPx(), center = Offset(size.width * 0.50f, size.height * 0.82f))
                    // screen line indicator
                    drawLine(contentColor, Offset(size.width * 0.38f, size.height * 0.18f), Offset(size.width * 0.62f, size.height * 0.18f), thinStroke, cap = StrokeCap.Round)
                }
                OnboardingOptionIcon.SleepTrouble -> {
                    // crescent moon arc
                    drawArc(contentColor, 82f, 230f, false, Offset(size.width * 0.18f, size.height * 0.12f), androidx.compose.ui.geometry.Size(size.width * 0.62f, size.height * 0.76f), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                    drawArc(contentColor, 95f, 200f, false, Offset(size.width * 0.34f, size.height * 0.10f), androidx.compose.ui.geometry.Size(size.width * 0.58f, size.height * 0.78f), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                    // small star dots
                    drawCircle(contentColor, radius = 1.6.dp.toPx(), center = Offset(size.width * 0.80f, size.height * 0.24f))
                    drawCircle(contentColor, radius = 1.2.dp.toPx(), center = Offset(size.width * 0.74f, size.height * 0.12f))
                    drawCircle(contentColor, radius = 1.0.dp.toPx(), center = Offset(size.width * 0.86f, size.height * 0.40f))
                }
                OnboardingOptionIcon.Thought -> {
                    // head outline circle
                    drawCircle(contentColor, radius = size.minDimension * 0.30f, center = Offset(size.width * 0.50f, size.height * 0.44f), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                    // brain — two small arcs side by side suggesting lobes
                    drawArc(contentColor, 0f, 180f, false, Offset(size.width * 0.32f, size.height * 0.33f), androidx.compose.ui.geometry.Size(size.width * 0.14f, size.height * 0.14f), style = Stroke(width = thinStroke, cap = StrokeCap.Round))
                    drawArc(contentColor, 0f, 180f, false, Offset(size.width * 0.46f, size.height * 0.33f), androidx.compose.ui.geometry.Size(size.width * 0.14f, size.height * 0.14f), style = Stroke(width = thinStroke, cap = StrokeCap.Round))
                    // base line connecting lobes
                    drawLine(contentColor, Offset(size.width * 0.32f, size.height * 0.40f), Offset(size.width * 0.60f, size.height * 0.40f), thinStroke, cap = StrokeCap.Round)
                    // neck stub
                    drawLine(contentColor, Offset(size.width * 0.43f, size.height * 0.74f), Offset(size.width * 0.57f, size.height * 0.74f), strokeWidth, cap = StrokeCap.Round)
                }
                OnboardingOptionIcon.SelfImprovement -> {
                    // head
                    drawCircle(contentColor, radius = size.minDimension * 0.13f, center = Offset(size.width * 0.50f, size.height * 0.24f), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                    // torso
                    drawLine(contentColor, Offset(size.width * 0.50f, size.height * 0.37f), Offset(size.width * 0.50f, size.height * 0.55f), strokeWidth, cap = StrokeCap.Round)
                    // arms stretched out
                    drawLine(contentColor, Offset(size.width * 0.50f, size.height * 0.44f), Offset(size.width * 0.22f, size.height * 0.56f), strokeWidth, cap = StrokeCap.Round)
                    drawLine(contentColor, Offset(size.width * 0.50f, size.height * 0.44f), Offset(size.width * 0.78f, size.height * 0.56f), strokeWidth, cap = StrokeCap.Round)
                    // lotus legs — two arcs curving outward and upward
                    drawArc(contentColor, 180f, 90f, false, Offset(size.width * 0.18f, size.height * 0.54f), androidx.compose.ui.geometry.Size(size.width * 0.32f, size.height * 0.24f), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                    drawArc(contentColor, 270f, 90f, false, Offset(size.width * 0.50f, size.height * 0.54f), androidx.compose.ui.geometry.Size(size.width * 0.32f, size.height * 0.24f), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                }
                OnboardingOptionIcon.Eye -> {
                    // outer eye almond shape — top arc
                    drawArc(contentColor, 200f, 140f, false, Offset(size.width * 0.14f, size.height * 0.28f), androidx.compose.ui.geometry.Size(size.width * 0.72f, size.height * 0.44f), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                    // outer eye — bottom arc
                    drawArc(contentColor, 20f, 140f, false, Offset(size.width * 0.14f, size.height * 0.28f), androidx.compose.ui.geometry.Size(size.width * 0.72f, size.height * 0.44f), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                    // pupil
                    drawCircle(contentColor, radius = size.minDimension * 0.14f, center = Offset(size.width * 0.50f, size.height * 0.50f), style = Stroke(width = thinStroke, cap = StrokeCap.Round))
                    // iris dot
                    drawCircle(contentColor, radius = size.minDimension * 0.05f, center = Offset(size.width * 0.50f, size.height * 0.50f))
                }
                OnboardingOptionIcon.TrendingDown -> {
                    // descending step line
                    drawLine(contentColor, Offset(size.width * 0.16f, size.height * 0.32f), Offset(size.width * 0.44f, size.height * 0.32f), strokeWidth, cap = StrokeCap.Round)
                    drawLine(contentColor, Offset(size.width * 0.44f, size.height * 0.32f), Offset(size.width * 0.44f, size.height * 0.58f), strokeWidth, cap = StrokeCap.Round)
                    drawLine(contentColor, Offset(size.width * 0.44f, size.height * 0.58f), Offset(size.width * 0.76f, size.height * 0.58f), strokeWidth, cap = StrokeCap.Round)
                    // arrowhead pointing right-down
                    drawLine(contentColor, Offset(size.width * 0.76f, size.height * 0.58f), Offset(size.width * 0.64f, size.height * 0.46f), strokeWidth, cap = StrokeCap.Round)
                    drawLine(contentColor, Offset(size.width * 0.76f, size.height * 0.58f), Offset(size.width * 0.64f, size.height * 0.70f), strokeWidth, cap = StrokeCap.Round)
                }
                OnboardingOptionIcon.EventRepeat -> {
                    // calendar body
                    drawRoundRect(contentColor, Offset(size.width * 0.16f, size.height * 0.22f), androidx.compose.ui.geometry.Size(size.width * 0.68f, size.height * 0.62f), androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                    // calendar header divider
                    drawLine(contentColor, Offset(size.width * 0.16f, size.height * 0.40f), Offset(size.width * 0.84f, size.height * 0.40f), thinStroke, cap = StrokeCap.Round)
                    // repeat arrows inside — a small circular arrow arc
                    drawArc(contentColor, 30f, 280f, false, Offset(size.width * 0.34f, size.height * 0.46f), androidx.compose.ui.geometry.Size(size.width * 0.32f, size.height * 0.28f), style = Stroke(width = thinStroke, cap = StrokeCap.Round))
                    // arrowhead tip on the arc
                    drawLine(contentColor, Offset(size.width * 0.66f, size.height * 0.52f), Offset(size.width * 0.72f, size.height * 0.46f), thinStroke, cap = StrokeCap.Round)
                    drawLine(contentColor, Offset(size.width * 0.66f, size.height * 0.52f), Offset(size.width * 0.60f, size.height * 0.46f), thinStroke, cap = StrokeCap.Round)
                }
                OnboardingOptionIcon.PieChart -> {
                    // full circle
                    drawCircle(contentColor, radius = size.minDimension * 0.34f, center = center, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                    // one radius to 12 o'clock
                    drawLine(contentColor, center, Offset(size.width * 0.50f, size.height * 0.16f), strokeWidth, cap = StrokeCap.Round)
                    // second radius to ~4 o'clock (forming ~half slice)
                    drawLine(contentColor, center, Offset(size.width * 0.79f, size.height * 0.66f), strokeWidth, cap = StrokeCap.Round)
                }
            }
            }
        }
    }
}

@Composable
private fun TimingOptionChip(
    option: TimingOption,
    selected: Boolean,
    onClick: () -> Unit,
    onTokenLaunch: ((Offset) -> Unit)? = null,
) {
    val haptics = rememberImpulsiveHaptics(enabled = true)
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) OnboardingSelectedOptionSurface else Color.White,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "timing-option-background",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) OnboardingPrimary else OnboardingOptionPillBorder,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "timing-option-border",
    )
    var chipCenter by remember { mutableStateOf<Offset?>(null) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .onGloballyPositioned { coords ->
                chipCenter = coords.boundsInRoot().center
            }
            .background(backgroundColor, CircleShape)
            .border(BorderStroke(1.dp, borderColor), CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if (!selected) {
                        haptics.light()
                        chipCenter?.let { onTokenLaunch?.invoke(it) }
                    }
                    onClick()
                },
            )
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OptionIconMark(icon = option.icon, selected = selected, plain = true, plainIconSize = 16.dp)

        Spacer(modifier = Modifier.size(8.dp))

        Text(
            text = option.label,
            color = OnboardingText,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SomethingElseInput(
    value: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester,
) {
    var focused by remember { mutableStateOf(false) }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            color = OnboardingText,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.Medium,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused },
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRoundRect(
                            color = Color(0xFF514866).copy(alpha = 0.045f),
                            topLeft = Offset(0f, 3.dp.toPx()),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx()),
                        )
                    }
                    .background(ImpulsiveSurface, RoundedCornerShape(24.dp))
                    .border(
                        1.5.dp,
                        if (focused) OnboardingPrimary else OnboardingOutlineVariant,
                        RoundedCornerShape(24.dp),
                    )
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = "Tell us more...",
                        color = OnboardingMutedText,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.Normal,
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun ContinueButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Continue",
) {
    val haptics = rememberImpulsiveHaptics(enabled = true)
    val shadowAlpha by animateFloatAsState(
        targetValue = if (enabled) 0.10f else 0.045f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "continue-button-shadow",
    )

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(top = 7.dp)
                .background(
                    Color(0xFF514866).copy(alpha = shadowAlpha),
                    RoundedCornerShape(28.dp),
                ),
        )

        Button(
            onClick = {
                haptics.confirm()
                onClick()
            },
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ImpulsivePsychological,
                contentColor = OnboardingPrimary,
                disabledContainerColor = OnboardingDisabledButton,
                disabledContentColor = OnboardingDisabledButtonText,
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        ) {
            Text(
                text = label,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private data class StartingPointSummaryItem(
    val title: String,
    val value: String,
    val emphasized: Boolean = false,
)

private data class ReduceOption(
    val id: String,
    val label: String,
    val icon: OnboardingOptionIcon,
)

private data class TriggerOption(
    val id: String,
    val label: String,
    val icon: OnboardingOptionIcon,
)

private data class TimingOption(
    val id: String,
    val label: String,
    val icon: OnboardingOptionIcon,
)

private enum class OnboardingOptionIcon {
    PrivateHabit,
    CompulsiveScrolling,
    LateNightPhone,
    BrowserHabit,
    SomethingElse,
    LateAtNight,
    RightAfterWaking,
    AloneOnPhone,
    WhenBored,
    WhenStressed,
    TroubleSleeping,
    SocialMedia,
    BrowserSearch,
    MemoryOrThought,
    BoredomTrigger,
    BeingAlone,
    StressTrigger,
    NoticeTriggers,
    CutDownLittle,
    DailyResetHabit,
    CutDownHalf,
    Shield,
    Incognito,
    Social,
    Loop,
    Moon,
    Search,
    Stress,
    Boredom,
    Lonely,
    Heart,
    Morning,
    Afternoon,
    Evening,
    Work,
    Person,
    Notice,
    Pause,
    Target,
    Boundary,
    CheckIn,
    Lock,
    Swipe,
    Globe,
    Add,
    Smartphone,
    SleepTrouble,
    Thought,
    SelfImprovement,
    Eye,
    TrendingDown,
    EventRepeat,
    PieChart,
}

private val OnboardingOptionIcon.drawableResId: Int?
    get() = when (this) {
        OnboardingOptionIcon.PrivateHabit -> R.drawable.ic_private_habit
        OnboardingOptionIcon.CompulsiveScrolling -> R.drawable.ic_compulsive_scrolling
        OnboardingOptionIcon.LateNightPhone -> R.drawable.ic_late_night_phone
        OnboardingOptionIcon.BrowserHabit -> R.drawable.ic_browser_habit
        OnboardingOptionIcon.SomethingElse -> R.drawable.ic_something_else
        OnboardingOptionIcon.LateAtNight -> R.drawable.ic_late_at_night
        OnboardingOptionIcon.RightAfterWaking -> R.drawable.ic_right_after_waking
        OnboardingOptionIcon.AloneOnPhone -> R.drawable.ic_alone_on_phone
        OnboardingOptionIcon.WhenBored -> R.drawable.ic_when_bored
        OnboardingOptionIcon.WhenStressed -> R.drawable.ic_when_stressed
        OnboardingOptionIcon.TroubleSleeping -> R.drawable.ic_trouble_sleeping
        OnboardingOptionIcon.SocialMedia -> R.drawable.ic_social_media
        OnboardingOptionIcon.BrowserSearch -> R.drawable.ic_browser_search
        OnboardingOptionIcon.MemoryOrThought -> R.drawable.ic_memory_or_thought
        OnboardingOptionIcon.BoredomTrigger -> R.drawable.ic_boredom
        OnboardingOptionIcon.BeingAlone -> R.drawable.ic_being_alone
        OnboardingOptionIcon.StressTrigger -> R.drawable.ic_stress
        OnboardingOptionIcon.NoticeTriggers -> R.drawable.ic_notice_triggers
        OnboardingOptionIcon.CutDownLittle -> R.drawable.ic_cut_down_little
        OnboardingOptionIcon.DailyResetHabit -> R.drawable.ic_daily_reset_habit
        OnboardingOptionIcon.CutDownHalf -> R.drawable.ic_cut_down_half
        else -> null
    }

private val ReduceOptions = listOf(
    ReduceOption(id = "private_habit", label = "A private habit", icon = OnboardingOptionIcon.PrivateHabit),
    ReduceOption(id = "compulsive_scrolling", label = "Compulsive scrolling", icon = OnboardingOptionIcon.CompulsiveScrolling),
    ReduceOption(id = "late_night_phone", label = "Late-night phone use", icon = OnboardingOptionIcon.LateNightPhone),
    ReduceOption(id = "browser_habit", label = "A browser habit", icon = OnboardingOptionIcon.BrowserHabit),
    ReduceOption(id = "something_else", label = "Something else", icon = OnboardingOptionIcon.SomethingElse),
)

private val TriggerOptions = listOf(
    TriggerOption(id = "social_media", label = "Social media", icon = OnboardingOptionIcon.SocialMedia),
    TriggerOption(id = "browser_search", label = "A browser search", icon = OnboardingOptionIcon.BrowserSearch),
    TriggerOption(id = "memory_or_thought", label = "A memory or thought", icon = OnboardingOptionIcon.MemoryOrThought),
    TriggerOption(id = "boredom", label = "Boredom", icon = OnboardingOptionIcon.BoredomTrigger),
    TriggerOption(id = "being_alone", label = "Being alone", icon = OnboardingOptionIcon.BeingAlone),
    TriggerOption(id = "stress", label = "Stress", icon = OnboardingOptionIcon.StressTrigger),
)

private val TimingOptions = listOf(
    TimingOption(id = "late_at_night", label = "Late at night", icon = OnboardingOptionIcon.LateAtNight),
    TimingOption(id = "right_after_waking", label = "Right after waking", icon = OnboardingOptionIcon.RightAfterWaking),
    TimingOption(id = "alone_on_phone", label = "Alone on my phone", icon = OnboardingOptionIcon.AloneOnPhone),
    TimingOption(id = "when_bored", label = "When bored", icon = OnboardingOptionIcon.WhenBored),
    TimingOption(id = "when_stressed", label = "When stressed", icon = OnboardingOptionIcon.WhenStressed),
    TimingOption(id = "trouble_sleeping", label = "Trouble sleeping", icon = OnboardingOptionIcon.TroubleSleeping),
)

private val WeekOneOptions = listOf(
    ReduceOption(id = "notice_triggers", label = "Just notice my triggers", icon = OnboardingOptionIcon.NoticeTriggers),
    ReduceOption(id = "cut_down_a_little", label = "Cut down a little", icon = OnboardingOptionIcon.CutDownLittle),
    ReduceOption(id = "daily_reset_habit", label = "Build one daily reset habit", icon = OnboardingOptionIcon.DailyResetHabit),
    ReduceOption(id = "cut_down_by_half", label = "Cut down by half", icon = OnboardingOptionIcon.CutDownHalf),
)

private fun selectedSummary(
    selectedIds: List<String>,
    labels: List<Pair<String, String>>,
    emptyText: String,
): String {
    val selectedLabels = selectedIds.mapNotNull { selectedId ->
        labels.firstOrNull { (id) -> id == selectedId }?.second
    }
    return selectedLabels.takeIf { it.isNotEmpty() }?.toNaturalSummary() ?: emptyText
}

private fun selectedWeekOneLabel(
    selectedId: String?,
    emptyText: String,
): String {
    val label = WeekOneOptions.firstOrNull { it.id == selectedId }?.label ?: return emptyText
    return label.lowercase().replace("my triggers", "your triggers")
}

private fun List<String>.toNaturalSummary(): String = when {
    size > 3 -> "${take(2).joinToString(", ")} and ${size - 2} more"
    size == 3 -> "${this[0]}, ${this[1]} and ${this[2]}"
    size == 2 -> "${this[0]} and ${this[1]}"
    size == 1 -> first()
    else -> ""
}

private val OnboardingPrimary = Color(0xFF635880)
private val OnboardingText = Color(0xFF1C1B1E)
private val OnboardingMutedText = Color(0xFF48454E)
private val OnboardingOutlineVariant = Color(0xFFCAC4CE)
private val OnboardingOptionPillBorder = Color(0xFFB9B0C4)
private val OnboardingIconSurface = Color(0xFFF1ECF0)
private val OnboardingIconMuted = Color(0xFF79757E)
private val OnboardingIconBorder = Color(0xFFE6E1E5)
private val OnboardingSelectedOptionSurface = Color(0xFFE8DDFF)
private val OnboardingDisabledButton = Color(0xFFE8DDFF)
private val OnboardingDisabledButtonText = Color(0xFF9C93A8)
