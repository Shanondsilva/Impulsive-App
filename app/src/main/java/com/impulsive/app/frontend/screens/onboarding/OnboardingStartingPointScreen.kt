package com.impulsive.app.frontend.screens.onboarding

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.impulsive.app.backend.session.onboarding.OnboardingState

@Composable
fun OnboardingStartingPointScreen(
    state: OnboardingState,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val reducedMotion = rememberReducedMotion()

    OnboardingScreenShell(
        backgroundColors = listOf(Color(0xFFFFFEFC), Color(0xFFFBF8FE), Color(0xFFF5F2FB)),
        stepUi = OnboardingFlowStep.StartingPoint.toStepUi(),
        onBack = onBack,
        onSkip = null,
        bottomBar = { ContinueButton(enabled = true, label = "Start week one", onClick = onContinue) },
    ) { compactHeight ->
        val metrics = rememberQuestionResponsiveMetrics(compactHeight = compactHeight)
        val answers = state.answers
        val dailyRelapseLabel = if (answers.dailyRelapseUrgeCount == 1) "1 time per day" else "${answers.dailyRelapseUrgeCount} times per day"

        val summaryItems = listOf(
            StartingPointSummaryItem(
                title = "You're focusing on",
                value = selectedSummary(answers.interrupting, ReduceOptions.map { it.id to it.label.lowercase() }, "a small pattern you want to understand"),
            ),
            StartingPointSummaryItem(
                title = "It may start with",
                value = selectedSummary(answers.triggers, TriggerOptions.map { it.id to it.label.lowercase() }, "a few moments to notice gently"),
            ),
            StartingPointSummaryItem(
                title = "It tends to show up",
                value = selectedSummary(answers.timing, TimingOptions.map { it.id to it.label.lowercase() }, "at times you can learn from"),
            ),
            StartingPointSummaryItem(
                title = "Your week-one step is to",
                value = selectedWeekOneLabel(answers.weekOneGoal, "start gently and adjust as you learn"),
                emphasized = true,
            ),
            StartingPointSummaryItem(
                title = "Daily relapse urge count",
                value = "You chose: $dailyRelapseLabel",
            ),
        )

        Spacer(modifier = Modifier.height(metrics.headerToIconSpacing))
        OnboardingLogoVisual(reducedMotion = reducedMotion, scale = OnboardingLogoScale.Compact)
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

        QuestionOptionGroup(areaMinHeight = metrics.optionAreaMinHeight + if (compactHeight) 28.dp else 72.dp) {
            summaryItems.forEach { item -> StartingPointSummaryLine(item = item) }
        }
    }
}

private fun selectedSummary(selectedIds: List<String>, labels: List<Pair<String, String>>, emptyText: String): String {
    val selectedLabels = selectedIds.mapNotNull { selectedId -> labels.firstOrNull { (id) -> id == selectedId }?.second }
    return selectedLabels.takeIf { it.isNotEmpty() }?.toNaturalSummary() ?: emptyText
}

private fun selectedWeekOneLabel(selectedId: String?, emptyText: String): String {
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
