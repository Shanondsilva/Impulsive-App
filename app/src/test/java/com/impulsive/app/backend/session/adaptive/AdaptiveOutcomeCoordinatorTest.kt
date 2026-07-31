package com.impulsive.app.backend.session.adaptive

import com.impulsive.app.backend.domain.model.adaptive.FeedbackCode
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.RepeatObservation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveOutcomeCoordinatorTest {
    @Test
    fun startedDecisionCompletesOnceAndPreservesEvidence() = runBlocking {
        val harness = harness(
            decision(
                actual = InterventionFamily.PivotGame,
                presented = 2_000L,
                started = 3_000L,
                repeat = RepeatObservation.RepeatDetected,
                firstRepeat = 4_000L,
            ),
        )
        val before = harness.repository.stored.single()

        assertEquals(
            AdaptiveOutcomeResult.Applied,
            harness.coordinator.complete(before.decisionId, 5_000L),
        )
        assertEquals(
            AdaptiveOutcomeResult.Idempotent,
            harness.coordinator.complete(before.decisionId, 6_000L),
        )

        val after = harness.repository.stored.single()
        assertEquals(5_000L, after.completedAtMillis)
        assertNull(after.dismissedAtMillis)
        assertEquals(before.assignment, after.assignment)
        assertEquals(before.repeatObservation, after.repeatObservation)
        assertEquals(before.firstRepeatAtMillis, after.firstRepeatAtMillis)
        assertEquals(0, harness.scheduler.cancelCalls)
    }

    @Test
    fun startedDecisionDismissesOnceWithoutFabricatingCompletion() = runBlocking {
        val harness = harness(
            decision(
                actual = InterventionFamily.PivotReading,
                presented = 2_000L,
                started = 3_000L,
            ),
        )

        assertEquals(
            AdaptiveOutcomeResult.Applied,
            harness.coordinator.dismiss(harness.id, 4_000L),
        )
        assertEquals(
            AdaptiveOutcomeResult.Idempotent,
            harness.coordinator.dismiss(harness.id, 5_000L),
        )
        assertNull(harness.current.completedAtMillis)
        assertEquals(4_000L, harness.current.dismissedAtMillis)
    }

    @Test
    fun completionAndDismissalAreMutuallyExclusiveAndRequireStarted() = runBlocking {
        val unstarted = harness(
            decision(
                actual = InterventionFamily.ShortPause,
                presented = 2_000L,
            ),
        )
        assertEquals(
            AdaptiveOutcomeResult.NotStarted,
            unstarted.coordinator.complete(unstarted.id, 3_000L),
        )
        assertEquals(
            AdaptiveOutcomeResult.NotStarted,
            unstarted.coordinator.dismiss(unstarted.id, 3_000L),
        )

        val completed = harness(
            decision(
                actual = InterventionFamily.ShortPause,
                presented = 2_000L,
                started = 3_000L,
                completed = 4_000L,
            ),
        )
        assertEquals(
            AdaptiveOutcomeResult.ConflictingTerminalState,
            completed.coordinator.dismiss(completed.id, 5_000L),
        )

        val dismissed = harness(
            decision(
                actual = InterventionFamily.ShortPause,
                presented = 2_000L,
                started = 3_000L,
                dismissed = 4_000L,
            ),
        )
        assertEquals(
            AdaptiveOutcomeResult.ConflictingTerminalState,
            dismissed.coordinator.complete(dismissed.id, 5_000L),
        )
    }

    @Test
    fun futureTerminalTimestampFailsSafely() = runBlocking {
        val harness = harness(
            decision(
                actual = InterventionFamily.ShortPause,
                presented = 2_000L,
                started = 3_000L,
            ),
            now = 10_000L,
        )
        assertEquals(
            AdaptiveOutcomeResult.InvalidTimestamp,
            harness.coordinator.complete(harness.id, 10_001L),
        )
        assertNull(harness.current.completedAtMillis)
    }

    @Test
    fun pauseTimerIsRecreationSafeAndOnlyFinishesAtThirtySeconds() {
        val started = 1_000L
        assertEquals(
            30_000L,
            AdaptiveCompletionGate.pauseRemainingMillis(started, started),
        )
        assertEquals(
            15_000L,
            AdaptiveCompletionGate.pauseRemainingMillis(started, 16_000L),
        )
        assertFalse(AdaptiveCompletionGate.pauseFinished(started, 30_999L))
        assertTrue(AdaptiveCompletionGate.pauseFinished(started, 31_000L))
        assertTrue(AdaptiveCompletionGate.pauseFinished(started, 90_000L))
    }

    @Test
    fun readingGateKeepsNinetySecondAndArticleEndRequirements() {
        assertFalse(AdaptiveCompletionGate.readingCompleted(89, true, true))
        assertFalse(AdaptiveCompletionGate.readingCompleted(90, false, true))
        assertFalse(AdaptiveCompletionGate.readingCompleted(90, true, false))
        assertTrue(AdaptiveCompletionGate.readingCompleted(90, true, true))
    }

    @Test
    fun gameGateUsesOnlyExistingValidCompletion() {
        assertFalse(AdaptiveCompletionGate.gameCompleted(false))
        assertTrue(AdaptiveCompletionGate.gameCompleted(true))
    }

    @Test
    fun everyFeedbackValuePersistsWithoutChangingOutcomeOrRepeat() = runBlocking {
        FeedbackCode.entries.forEachIndexed { index, feedback ->
            val original = decision(
                id = "feedback-$index",
                actual = InterventionFamily.PivotGame,
                presented = 2_000L,
                started = 3_000L,
                completed = 4_000L,
                repeat = RepeatObservation.RepeatDetected,
                firstRepeat = 3_500L,
            )
            val harness = harness(original)
            assertEquals(
                AdaptiveOutcomeResult.Applied,
                harness.coordinator.submitFeedback(
                    harness.id,
                    feedback,
                    5_000L,
                ),
            )
            val saved = harness.current
            assertEquals(feedback, saved.feedbackCode)
            assertEquals(5_000L, saved.feedbackUpdatedAtMillis)
            assertEquals(original.completedAtMillis, saved.completedAtMillis)
            assertEquals(original.dismissedAtMillis, saved.dismissedAtMillis)
            assertEquals(original.repeatObservation, saved.repeatObservation)
            assertEquals(original.assignment, saved.assignment)
            assertEquals(0, harness.scheduler.cancelCalls)
        }
    }

    @Test
    fun skipAndNeverAnsweredRemainDistinct() = runBlocking {
        val harness = harness(
            decision(
                actual = InterventionFamily.PivotGame,
                presented = 2_000L,
                started = 3_000L,
                dismissed = 4_000L,
            ),
        )
        assertEquals(FeedbackCode.NotProvided, harness.current.feedbackCode)
        assertNull(harness.current.feedbackUpdatedAtMillis)

        harness.coordinator.submitFeedback(
            harness.id,
            FeedbackCode.NotProvided,
            5_000L,
        )
        assertEquals(FeedbackCode.NotProvided, harness.current.feedbackCode)
        assertEquals(5_000L, harness.current.feedbackUpdatedAtMillis)
    }

    @Test
    fun feedbackRevisionUpdatesSameDecisionAndIdenticalAnswerIsIdempotent() = runBlocking {
        val harness = harness(
            decision(
                actual = InterventionFamily.PivotReading,
                presented = 2_000L,
                started = 3_000L,
                completed = 4_000L,
            ),
        )
        harness.coordinator.submitFeedback(
            harness.id,
            FeedbackCode.Helped,
            5_000L,
        )
        assertEquals(
            AdaptiveOutcomeResult.Idempotent,
            harness.coordinator.submitFeedback(
                harness.id,
                FeedbackCode.Helped,
                6_000L,
            ),
        )
        assertEquals(5_000L, harness.current.feedbackUpdatedAtMillis)

        assertEquals(
            AdaptiveOutcomeResult.Applied,
            harness.coordinator.submitFeedback(
                harness.id,
                FeedbackCode.WrongTiming,
                7_000L,
            ),
        )
        assertEquals(1, harness.repository.stored.size)
        assertEquals(FeedbackCode.WrongTiming, harness.current.feedbackCode)
        assertEquals(7_000L, harness.current.feedbackUpdatedAtMillis)
    }

    @Test
    fun feedbackRequiresTerminalStartedDecisionAndRejectsFutureTime() = runBlocking {
        val active = harness(
            decision(
                actual = InterventionFamily.PivotGame,
                presented = 2_000L,
                started = 3_000L,
            ),
            now = 10_000L,
        )
        assertEquals(
            AdaptiveOutcomeResult.NotTerminal,
            active.coordinator.submitFeedback(
                active.id,
                FeedbackCode.Helped,
                4_000L,
            ),
        )
        active.coordinator.complete(active.id, 5_000L)
        assertEquals(
            AdaptiveOutcomeResult.InvalidTimestamp,
            active.coordinator.submitFeedback(
                active.id,
                FeedbackCode.Helped,
                10_001L,
            ),
        )
    }

    @Test
    fun followUpDecisionsKeepIndependentOutcomesAndFeedback() = runBlocking {
        val repository = FakeDecisionRepository()
        val original = decision(
            id = "original",
            actual = InterventionFamily.ShortPause,
            presented = 2_000L,
            started = 3_000L,
            completed = 4_000L,
        )
        val game = decision(
            id = "game-follow-up",
            token = "game-token",
            actual = InterventionFamily.PivotGame,
            presented = 5_000L,
            started = 6_000L,
        )
        val reading = decision(
            id = "reading-follow-up",
            token = "reading-token",
            actual = InterventionFamily.PivotReading,
            presented = 7_000L,
            started = 8_000L,
        )
        repository.stored += listOf(original, game, reading)
        val harness = harness(repository = repository)

        harness.coordinator.dismiss(game.decisionId, 9_000L)
        harness.coordinator.complete(reading.decisionId, 10_000L)
        harness.coordinator.submitFeedback(
            reading.decisionId,
            FeedbackCode.HelpedALittle,
            11_000L,
        )

        assertEquals(original, repository.getById(original.decisionId))
        assertNotNull(repository.getById(game.decisionId)?.dismissedAtMillis)
        assertNull(repository.getById(game.decisionId)?.completedAtMillis)
        assertNotNull(repository.getById(reading.decisionId)?.completedAtMillis)
        assertEquals(
            FeedbackCode.HelpedALittle,
            repository.getById(reading.decisionId)?.feedbackCode,
        )
    }

    @Test
    fun pendingFeedbackSelectsMostRecentEligibleAndPromptsOncePerSession() = runBlocking {
        val repository = FakeDecisionRepository()
        repository.stored += listOf(
            decision(
                id = "unstarted",
                presented = 2_000L,
                dismissed = 3_000L,
            ),
            decision(
                id = "answered",
                actual = InterventionFamily.PivotGame,
                presented = 2_000L,
                started = 3_000L,
                completed = 4_000L,
                feedback = FeedbackCode.Helped,
                feedbackAt = 5_000L,
            ),
            decision(
                id = "older",
                token = "older-token",
                actual = InterventionFamily.PivotReading,
                presented = 2_000L,
                started = 3_000L,
                dismissed = 6_000L,
            ),
            decision(
                id = "newest",
                token = "newest-token",
                actual = InterventionFamily.MomentPlan,
                presented = 2_000L,
                started = 3_000L,
                completed = 7_000L,
            ),
        )
        val pending = AdaptivePendingFeedbackCoordinator(repository)
        val safe = AdaptivePendingFeedbackSafety(false, false, false)
        assertEquals("newest", pending.claimMostRecentEligible(safe)?.decisionId)
        assertNull(pending.claimMostRecentEligible(safe))
    }

    @Test
    fun unsafePresentationDoesNotClaimTheSessionOpportunity() = runBlocking {
        val repository = FakeDecisionRepository().apply {
            stored += decision(
                actual = InterventionFamily.PivotGame,
                presented = 2_000L,
                started = 3_000L,
                completed = 4_000L,
            )
        }
        val pending = AdaptivePendingFeedbackCoordinator(repository)
        assertNull(
            pending.claimMostRecentEligible(
                AdaptivePendingFeedbackSafety(
                    protectionOverlayVisible = true,
                    activeInterventionRunning = false,
                    appLockPending = false,
                ),
            ),
        )
        assertNotNull(
            pending.claimMostRecentEligible(
                AdaptivePendingFeedbackSafety(false, false, false),
            ),
        )
    }

    @Test
    fun outcomeGuardPreventsRapidDuplicateAndClearsForRetry() {
        val guard = AdaptiveOutcomeOperationGuard()
        assertTrue(guard.tryStart())
        assertFalse(guard.tryStart())
        guard.clear()
        assertTrue(guard.tryStart())
    }

    private fun harness(
        initial: com.impulsive.app.backend.domain.model.adaptive.AdaptiveDecision? = null,
        repository: FakeDecisionRepository = FakeDecisionRepository(),
        now: Long = 20_000L,
    ): Harness {
        if (initial != null) repository.stored += initial
        val clock = FakeClock(now)
        val scheduler = FakeScheduler()
        val lifecycle = AdaptiveDecisionLifecycle(
            decisions = repository,
            momentPlans = FakeMomentPlanRepository(),
            scheduler = scheduler,
            clock = clock,
            logger = AdaptiveSafeLogger { _, _ -> },
        )
        return Harness(
            repository = repository,
            scheduler = scheduler,
            coordinator = AdaptiveOutcomeCoordinator(repository, lifecycle, clock),
        )
    }

    private data class Harness(
        val repository: FakeDecisionRepository,
        val scheduler: FakeScheduler,
        val coordinator: AdaptiveOutcomeCoordinator,
    ) {
        val id: String get() = repository.stored.single().decisionId
        val current get() = repository.stored.single()
    }
}
