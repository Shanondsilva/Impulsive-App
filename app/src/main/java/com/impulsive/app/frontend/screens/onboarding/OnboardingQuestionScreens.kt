package com.impulsive.app.frontend.screens.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import com.impulsive.app.backend.domain.model.onboarding.OnboardingQuestionId
import com.impulsive.app.backend.session.onboarding.OnboardingState
import kotlinx.coroutines.delay

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
            onSelectionChanged = { onMultiSelectAnswerChanged(questionId, it) },
            stepUi = OnboardingFlowStep.Reduction.toStepUi(showSkip = onSkip != null),
            onBack = onBack, onContinue = onContinue, onSkip = onSkip,
        )
        OnboardingQuestionId.Triggers -> TriggerQuestionScreen(
            selectedOptionIds = state.answers.triggers,
            onSelectionChanged = { onMultiSelectAnswerChanged(questionId, it) },
            stepUi = OnboardingFlowStep.Triggers.toStepUi(showSkip = onSkip != null),
            onBack = onBack, onContinue = onContinue, onSkip = onSkip,
        )
        OnboardingQuestionId.Timing -> TimingQuestionScreen(
            selectedOptionIds = state.answers.timing,
            onSelectionChanged = { onMultiSelectAnswerChanged(questionId, it) },
            stepUi = OnboardingFlowStep.Timing.toStepUi(showSkip = onSkip != null),
            onBack = onBack, onContinue = onContinue, onSkip = onSkip,
        )
        OnboardingQuestionId.WeekOneGoal -> WeekOneQuestionScreen(
            selectedOptionId = state.answers.weekOneGoal,
            onSelectionChanged = { onSingleSelectAnswerChanged(questionId, it) },
            stepUi = OnboardingFlowStep.WeekOne.toStepUi(showSkip = onSkip != null),
            onBack = onBack, onContinue = onContinue, onSkip = onSkip,
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
        if (somethingElseSelected) { delay(150); somethingElseFocusRequester.requestFocus() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        OnboardingScreenShell(
            backgroundColors = listOf(Color(0xFFFFFEFC), Color(0xFFFCF8FD), Color(0xFFF6F2FA)),
            stepUi = stepUi,
            useImePadding = true,
            onBack = onBack,
            onSkip = onSkip,
            bottomBar = { ContinueButton(enabled = selectedOptionIds.isNotEmpty(), onClick = onContinue) },
        ) { compactHeight ->
            val metrics = rememberQuestionResponsiveMetrics(compactHeight = compactHeight)

            Spacer(modifier = Modifier.height(metrics.headerToIconSpacing))
            OnboardingLogoVisual(
                reducedMotion = reducedMotion,
                scale = OnboardingLogoScale.Compact,
                absorbTrigger = absorbState.absorbTrigger,
                modifier = Modifier.onGloballyPositioned { absorbState.logoCenter = it.boundsInRoot().center },
            )
            Spacer(modifier = Modifier.height(metrics.iconToTitleSpacing))
            Text("What do you want help pivoting from?", color = OnboardingPrimary, fontSize = metrics.titleFontSize, lineHeight = metrics.titleLineHeight, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(metrics.titleToSubtitleSpacing))
            Text("Select as many as you like. We'll tailor your Notice, Pivot and Understand steps around these areas.", color = OnboardingMutedText, fontSize = metrics.subtitleFontSize, lineHeight = metrics.subtitleLineHeight, fontWeight = FontWeight.Normal, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(0.92f))
            Spacer(modifier = Modifier.height(metrics.subtitleToOptionsSpacing))

            QuestionOptionGroup(areaMinHeight = metrics.optionAreaMinHeight) {
                ReduceOptions.forEach { option ->
                    ReduceOptionChip(
                        option = option,
                        selected = option.id in selectedOptionIds,
                        onClick = {
                            onSelectionChanged(if (option.id in selectedOptionIds) selectedOptionIds - option.id else selectedOptionIds + option.id)
                        },
                        onTokenLaunch = if (reducedMotion) null else { center -> absorbState.launchToken(center) },
                    )
                }
            }

            AnimatedVisibility(
                visible = somethingElseSelected,
                enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(150)),
            ) {
                SomethingElseInput(value = somethingElseText, onValueChange = { somethingElseText = it }, focusRequester = somethingElseFocusRequester)
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
            backgroundColors = listOf(Color(0xFFFFFEFC), Color(0xFFFCF8FD), Color(0xFFF6F2FA)),
            stepUi = stepUi,
            onBack = onBack,
            onSkip = onSkip,
            bottomBar = { ContinueButton(enabled = selectedOptionIds.isNotEmpty(), onClick = onContinue) },
        ) { compactHeight ->
            val metrics = rememberQuestionResponsiveMetrics(compactHeight = compactHeight)

            Spacer(modifier = Modifier.height(metrics.headerToIconSpacing))
            OnboardingLogoVisual(
                reducedMotion = reducedMotion,
                scale = OnboardingLogoScale.Compact,
                absorbTrigger = absorbState.absorbTrigger,
                modifier = Modifier.onGloballyPositioned { absorbState.logoCenter = it.boundsInRoot().center },
            )
            Spacer(modifier = Modifier.height(metrics.iconToTitleSpacing))
            Text("What usually starts a difficult habit moment?", color = OnboardingPrimary, fontSize = metrics.titleFontSize, lineHeight = metrics.titleLineHeight, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(metrics.titleToSubtitleSpacing))
            Text("Select all the cues you recognise. It's okay if you're not sure yet.", color = OnboardingMutedText, fontSize = metrics.subtitleFontSize, lineHeight = metrics.subtitleLineHeight, fontWeight = FontWeight.Normal, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(0.92f))
            Spacer(modifier = Modifier.height(metrics.subtitleToOptionsSpacing))

            QuestionOptionGroup(areaMinHeight = metrics.optionAreaMinHeight) {
                TriggerOptions.forEach { option ->
                    TriggerOptionChip(
                        option = option,
                        selected = option.id in selectedOptionIds,
                        onClick = {
                            onSelectionChanged(if (option.id in selectedOptionIds) selectedOptionIds - option.id else selectedOptionIds + option.id)
                        },
                        onTokenLaunch = if (reducedMotion) null else { center -> absorbState.launchToken(center) },
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
            backgroundColors = listOf(Color(0xFFFFFEFC), Color(0xFFFBF8FE), Color(0xFFF5F2FB)),
            stepUi = stepUi,
            onBack = onBack,
            onSkip = onSkip,
            bottomBar = { ContinueButton(enabled = selectedOptionIds.isNotEmpty(), onClick = onContinue) },
        ) { compactHeight ->
            val metrics = rememberQuestionResponsiveMetrics(compactHeight = compactHeight)

            Spacer(modifier = Modifier.height(metrics.headerToIconSpacing))
            OnboardingLogoVisual(
                reducedMotion = reducedMotion,
                scale = OnboardingLogoScale.Compact,
                absorbTrigger = absorbState.absorbTrigger,
                modifier = Modifier.onGloballyPositioned { absorbState.logoCenter = it.boundsInRoot().center },
            )
            Spacer(modifier = Modifier.height(metrics.iconToTitleSpacing))
            Text("When does it usually happen?", color = OnboardingPrimary, fontSize = metrics.titleFontSize, lineHeight = metrics.titleLineHeight, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(metrics.titleToSubtitleSpacing))
            Text("Select all that apply. This helps us understand your patterns.", color = OnboardingMutedText, fontSize = metrics.subtitleFontSize, lineHeight = metrics.subtitleLineHeight, fontWeight = FontWeight.Normal, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(0.92f))
            Spacer(modifier = Modifier.height(metrics.subtitleToOptionsSpacing))

            QuestionOptionGroup(areaMinHeight = metrics.optionAreaMinHeight) {
                TimingOptions.forEach { option ->
                    TimingOptionChip(
                        option = option,
                        selected = option.id in selectedOptionIds,
                        onClick = {
                            onSelectionChanged(if (option.id in selectedOptionIds) selectedOptionIds - option.id else selectedOptionIds + option.id)
                        },
                        onTokenLaunch = if (reducedMotion) null else { center -> absorbState.launchToken(center) },
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
            backgroundColors = listOf(Color(0xFFFFFEFC), Color(0xFFFBF8FE), Color(0xFFF5F2FB)),
            stepUi = stepUi,
            onBack = onBack,
            onSkip = onSkip,
            bottomBar = { ContinueButton(enabled = selectedOptionId != null, onClick = onContinue) },
        ) { compactHeight ->
            val metrics = rememberQuestionResponsiveMetrics(compactHeight = compactHeight)

            Spacer(modifier = Modifier.height(metrics.headerToIconSpacing))
            OnboardingLogoVisual(
                reducedMotion = reducedMotion,
                scale = OnboardingLogoScale.Compact,
                absorbTrigger = absorbState.absorbTrigger,
                modifier = Modifier.onGloballyPositioned { absorbState.logoCenter = it.boundsInRoot().center },
            )
            Spacer(modifier = Modifier.height(metrics.iconToTitleSpacing))
            Text("What feels realistic for week one?", color = OnboardingPrimary, fontSize = metrics.titleFontSize, lineHeight = metrics.titleLineHeight, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(metrics.titleToSubtitleSpacing))
            Text("Select a gentle starting point. You can always adjust this later.", color = OnboardingMutedText, fontSize = metrics.subtitleFontSize, lineHeight = metrics.subtitleLineHeight, fontWeight = FontWeight.Normal, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(0.92f))
            Spacer(modifier = Modifier.height(metrics.subtitleToOptionsSpacing))

            QuestionOptionGroup(areaMinHeight = metrics.optionAreaMinHeight) {
                WeekOneOptions.forEach { option ->
                    ReduceOptionChip(
                        option = option,
                        selected = option.id == selectedOptionId,
                        onClick = { onSelectionChanged(if (option.id == selectedOptionId) null else option.id) },
                        onTokenLaunch = if (reducedMotion) null else { center -> absorbState.launchToken(center) },
                    )
                }
            }
        }
        OnboardingAbsorbOverlay(state = absorbState)
    }
}
