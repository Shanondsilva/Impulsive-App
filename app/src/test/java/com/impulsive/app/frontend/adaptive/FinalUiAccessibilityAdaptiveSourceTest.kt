package com.impulsive.app.frontend.adaptive

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinalUiAccessibilityAdaptiveSourceTest {
    private val feedback = source("frontend/screens/adaptive/AdaptiveFeedbackScreen.kt")
    private val moment = source("frontend/screens/adaptive/AdaptiveMomentScreens.kt")
    private val plans = source("frontend/screens/adaptive/MomentPlanScreens.kt")
    private val urge = source("frontend/components/UrgeRatingRow.kt")
    private val viewModels = source("backend/session/adaptive/MomentPlanViewModels.kt")

    @Test
    fun shortPauseAndFeedbackUseThemeRolesAndScrollableSafeInsets() {
        assertTrue(feedback.contains("color = MaterialTheme.colorScheme.background"))
        assertTrue(feedback.contains("contentColor = MaterialTheme.colorScheme.onBackground"))
        assertTrue(feedback.contains(".verticalScroll(rememberScrollState())"))
        assertTrue(feedback.contains(".navigationBarsPadding()"))
        assertTrue(moment.contains("private fun PauseRunning"))
        assertTrue(moment.section("private fun PauseRunning", "private fun LoadingMoment")
            .contains(".verticalScroll(rememberScrollState())"))
        assertTrue(moment.section("private fun PauseRunning", "private fun LoadingMoment")
            .contains(".navigationBarsPadding()"))
    }

    @Test
    fun feedbackOptionsAndWrongTimingControlsAvoidHardCodedSelectedText() {
        assertTrue(urge.contains("contentColorFor(selectedContainer)"))
        assertTrue(urge.contains("MaterialTheme.colorScheme.primaryContainer"))
        assertTrue(urge.contains("MaterialTheme.colorScheme.onSurfaceVariant"))
        assertFalse(urge.contains("Color(0xFF281D38)"))
        assertFalse(feedback.contains("Color.Black"))
        assertFalse(feedback.contains("Color.White"))
    }

    @Test
    fun momentPlanEditorUsesImePaddingAndBringIntoViewForEveryTextField() {
        assertTrue(plans.contains(".imePadding()"))
        assertTrue(plans.contains(".navigationBarsPadding()"))
        assertTrue(plans.contains("BringIntoViewRequester()"))
        assertTrue(plans.contains("requester.bringIntoView()"))
        assertTrue(plans.contains("fun Modifier.bringIntoViewWhenFocused()"))
        assertTrue(plans.contains("updateFutureCue"))
        assertTrue(plans.contains("updateActionText"))
    }

    @Test
    fun guidedPracticeActionsScrollAtLargeFontWithoutWeightedSpacer() {
        val guided = plans.section("private fun GuidedRehearsalContent", "private fun QuickRehearsalContent")
        assertTrue(guided.contains(".verticalScroll(rememberScrollState())"))
        assertTrue(guided.contains(".navigationBarsPadding()"))
        assertFalse(guided.contains("Spacer(Modifier.weight(1f))"))
        assertTrue(guided.contains("Text(\"Previous\")"))
        assertTrue(guided.contains("\"Continue\""))
    }

    @Test
    fun planListAndPreviewUseSemanticCardContentColors() {
        val card = plans.section("private fun PlanListCard", "fun MomentPlanEditorScreen")
        assertTrue(card.contains("containerColor = MaterialTheme.colorScheme.surface"))
        assertTrue(card.contains("contentColor = MaterialTheme.colorScheme.onSurface"))
        assertTrue(card.contains("contentColor = MaterialTheme.colorScheme.primary"))
        val preview = plans.section("private fun PlanPreview", "private fun DetailValue")
        assertTrue(preview.contains("containerColor = MaterialTheme.colorScheme.surfaceVariant"))
        assertTrue(preview.contains("contentColor = MaterialTheme.colorScheme.onSurfaceVariant"))
    }

    @Test
    fun practicePreviewObservesRoomSourceOfTruthForCurrentRevision() {
        val detail = viewModels.section("class MomentPlanDetailViewModel", "class AdaptivePreferencesViewModel")
        assertTrue(detail.contains("repository.observeAll()"))
        assertTrue(detail.contains("plans.firstOrNull { it.planId == planId }"))
        assertFalse(detail.contains("init {\n        reload()\n    }"))
    }

    private fun source(path: String): String =
        File("src/main/java/com/impulsive/app/$path").readText()

    private fun String.section(from: String, to: String): String =
        substring(indexOf(from), indexOf(to, indexOf(from) + from.length))
}
