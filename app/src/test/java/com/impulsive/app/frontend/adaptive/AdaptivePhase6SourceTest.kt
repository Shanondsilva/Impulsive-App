package com.impulsive.app.frontend.adaptive

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptivePhase6SourceTest {
    private val root = File("src/main")

    @Test
    fun feedbackRouteCarriesOnlyDecisionIdAndUsesCautiousPrivateCopy() {
        val nav = source("frontend/navigation/AppNavHost.kt")
        val screen = source("frontend/screens/adaptive/AdaptiveFeedbackScreen.kt")

        assertTrue(nav.contains("adaptive_feedback/{decisionId}"))
        assertTrue(nav.contains("adaptiveFeedback(decisionId: String)"))
        assertFalse(nav.contains("adaptive_feedback/{decisionId}/{"))
        assertTrue(screen.contains("How did that feel?"))
        assertTrue(screen.contains("Was this useful right now?"))
        assertTrue(screen.contains("It helped"))
        assertTrue(screen.contains("It helped a little"))
        assertTrue(screen.contains("It didn't help"))
        assertTrue(screen.contains("The timing was wrong"))
        assertTrue(screen.contains("Skip"))
        assertTrue(
            screen.contains(
                "Thanks. Your response was saved privately on this device.",
            ),
        )
        listOf(
            "success",
            "failure",
            "relapse",
            "treatment",
            "cure",
            "clinically matched",
            "AI recommendation",
            "proven",
            "guaranteed",
            "\u2014",
        ).forEach { forbidden ->
            assertFalse("Forbidden feedback copy: $forbidden", screen.contains(forbidden))
        }
    }

    @Test
    fun shortPauseUsesPersistedStartedTimeAndExplicitTerminalSignals() {
        val screen = source("frontend/screens/adaptive/AdaptiveMomentScreens.kt")
        val viewModel = source("backend/session/adaptive/AdaptiveMomentViewModel.kt")

        assertTrue(screen.contains("key1 = startedAtMillis"))
        assertTrue(screen.contains("System.currentTimeMillis() - started"))
        assertTrue(screen.contains("remainingSeconds == 0"))
        assertTrue(screen.contains("onCompleted()"))
        assertTrue(screen.contains("onAbandon"))
        assertTrue(viewModel.contains("outcomeCoordinator.complete"))
        assertTrue(viewModel.contains("outcomeCoordinator.dismiss"))
        assertTrue(viewModel.contains("AdaptiveRouteKind.Feedback"))
        assertFalse(screen.contains("points"))
    }

    @Test
    fun gamesUseGenuineResultStateAndReadingKeepsExistingValidityGate() {
        val reflex = source("frontend/screens/games/ReflexGameScreen.kt")
        val cascade = source("frontend/screens/games/BlockCascadeScreen.kt")
        val skyline = source("frontend/screens/games/SkylineResetScreen.kt")
        val rhythm = source("frontend/screens/games/RhythmTilesScreen.kt")
        val reading = source("frontend/screens/tasks/ResetReadScreen.kt")

        assertTrue(reflex.contains("uiState.result?.validCompletion == true"))
        assertTrue(cascade.contains("if (uiState.completed) onAdaptiveCompleted"))
        assertTrue(skyline.contains("if (uiState.completed) onAdaptiveCompleted"))
        assertTrue(rhythm.contains("uiState.result?.validCompletion == true"))
        assertTrue(rhythm.contains("uiState.result?.gameOver == false"))
        assertTrue(reading.contains("if (uiState.validCompletion)"))
        assertTrue(reading.contains("onAdaptiveCompleted?.invoke()"))
        assertTrue(reading.contains("onAdaptiveExit?.invoke(uiState.validCompletion)"))
        assertFalse(reading.contains("secondsSpent >= 0"))
    }

    @Test
    fun momentPlansDoNotInferExternalCompletionAndUnknownTargetsCannotComplete() {
        val screen = source("frontend/screens/adaptive/AdaptiveMomentScreens.kt")
        val navigation = source("frontend/navigation/AppNavHost.kt")
        val routing = source("backend/session/adaptive/AdaptivePhase5Integration.kt")

        assertTrue(screen.contains("Did you complete your Moment Plan?"))
        assertTrue(screen.contains("Yes, I did"))
        assertTrue(screen.contains("Not yet"))
        assertTrue(screen.contains("I've done this"))
        assertTrue(screen.contains("Take the small action you prepared. Return when you are ready."))
        assertTrue(
            navigation.contains(
                "onSuccess {\n                adaptiveScope.launch { markAdaptiveStarted",
            ),
        )
        assertFalse(
            navigation.substringAfter("route = AppRoutes.MomentPlanRun")
                .substringBefore("route = AppRoutes.AdaptiveFeedback")
                .contains("AdaptiveStartedEffect(decisionId)"),
        )
        assertTrue(routing.contains("null -> null"))
    }

    @Test
    fun focusUsesManualConfirmationAndJournalUsesRealSaveWithoutCopyingContent() {
        val focus = source("frontend/screens/focus/FocusScreen.kt")
        val journalUi = source("frontend/screens/journal/JournalScreens.kt")
        val journalVm = source("backend/session/tasks/JournalViewModel.kt")
        val navigation = source("frontend/navigation/AppNavHost.kt")

        assertTrue(focus.contains("adaptiveMomentPlan"))
        assertTrue(focus.contains("Did you complete your Moment Plan?"))
        assertTrue(focus.contains("Confirm Moment Plan"))
        val existingFocusCompletion = focus.substringAfter(
            "currentSession?.phase == FocusSessionPhase.Completed",
        ).substringBefore("currentSession != null")
        assertFalse(existingFocusCompletion.contains("onAdaptiveCompleted"))
        assertTrue(journalUi.contains("onAdaptiveSaved"))
        assertTrue(journalVm.contains("onPersisted()"))
        assertTrue(navigation.contains("onAdaptiveSaved = adaptiveDecisionId?.let"))
        assertFalse(
            source("backend/session/adaptive/AdaptiveOutcomeCoordinator.kt")
                .contains("bodyDraft"),
        )
    }

    @Test
    fun feedbackPersistenceSupportsRevisionSkipRetryAndNoDuplicateDecision() {
        val coordinator = source("backend/session/adaptive/AdaptiveOutcomeCoordinator.kt")
        val viewModel = source("backend/session/adaptive/AdaptiveFeedbackViewModel.kt")

        assertTrue(coordinator.contains("submitFeedback("))
        assertTrue(coordinator.contains("current.feedbackCode == feedbackCode"))
        assertTrue(coordinator.contains("feedbackUpdatedAtMillis != null"))
        assertTrue(viewModel.contains("FeedbackCode.NotProvided"))
        assertTrue(viewModel.contains("operationGuard.tryStart()"))
        assertTrue(viewModel.contains("finally"))
        assertTrue(viewModel.contains("operationGuard.clear()"))
        assertTrue(viewModel.contains("changeAnswer()"))
        assertFalse(coordinator.contains("insertOnce"))
        assertFalse(coordinator.contains("AdaptiveMomentCoordinator"))
    }

    @Test
    fun pendingRecoveryIsNewestOncePerSessionAndOnlyFromSafeHome() {
        val coordinator = source("backend/session/adaptive/AdaptiveOutcomeCoordinator.kt")
        val dao = root.resolve(
            "java/com/impulsive/app/backend/data/local/dao/AdaptiveDecisionDao.kt",
        ).readText()
        val navigation = source("frontend/navigation/AppNavHost.kt")

        assertTrue(coordinator.contains("automaticPresentationClaimed"))
        assertTrue(coordinator.contains("compareAndSet(false, true)"))
        assertTrue(dao.contains("feedbackUpdatedAtMillis IS NULL"))
        assertTrue(dao.contains("ORDER BY COALESCE(completedAtMillis, dismissedAtMillis) DESC"))
        assertTrue(navigation.contains("bottomNavCurrentRoute == AppRoutes.Home"))
        assertTrue(navigation.contains("initialBlockRequest == null"))
    }

    @Test
    fun observationProtectionPrivacyAndSchemaStaySeparate() {
        val outcome = source("backend/session/adaptive/AdaptiveOutcomeCoordinator.kt")
        val feedback = source("frontend/screens/adaptive/AdaptiveFeedbackScreen.kt")
        val database = root.resolve(
            "java/com/impulsive/app/backend/data/local/database/AppDatabase.kt",
        ).readText()

        assertFalse(outcome.contains("schedule("))
        assertFalse(outcome.contains("cancel"))
        assertFalse(outcome.contains("finalise"))
        assertFalse(outcome.contains("repeatDetectedWithin20Minutes ="))
        assertFalse(outcome.contains("Firebase"))
        assertFalse(outcome.contains("analytics"))
        assertFalse(outcome.contains("VPN"))
        assertFalse(feedback.contains("packageName"))
        assertFalse(feedback.contains("\"URL\""))
        assertFalse(feedback.contains("\"domain\""))
        assertFalse(feedback.contains("plan.actionText"))
        assertTrue(database.contains("version = 12"))
        assertTrue(database.contains("Migration11To12"))
    }

    @Test
    fun phaseSevenDashboardAndReleaseWorkAreNotIntroduced() {
        val phaseSix = listOf(
            source("backend/session/adaptive/AdaptiveOutcomeCoordinator.kt"),
            source("backend/session/adaptive/AdaptiveFeedbackViewModel.kt"),
            source("frontend/screens/adaptive/AdaptiveFeedbackScreen.kt"),
        ).joinToString("\n")

        assertFalse(phaseSix.contains("Dashboard"))
        assertFalse(phaseSix.contains("What Works"))
        assertFalse(phaseSix.contains("backup"))
        assertFalse(phaseSix.contains("export"))
        assertFalse(phaseSix.contains("account deletion"))
        assertFalse(phaseSix.contains("utility score"))
    }

    private fun source(relative: String): String =
        root.resolve("java/com/impulsive/app/$relative").readText()
}
