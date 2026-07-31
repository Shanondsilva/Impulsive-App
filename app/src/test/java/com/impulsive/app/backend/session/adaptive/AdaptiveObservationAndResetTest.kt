package com.impulsive.app.backend.session.adaptive

import com.impulsive.app.backend.domain.model.adaptive.AdaptivePreferences
import com.impulsive.app.backend.domain.model.adaptive.RepeatObservation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveObservationAndResetTest {
    @Test
    fun firstRepeatTimestampIsPreserved() = runBlocking {
        val repository = FakeDecisionRepository()
        repository.stored += decision()
        val id = repository.stored.single().decisionId
        assertTrue(repository.markFirstRepeatOnce(id, 2_000L))
        repository.markFirstRepeatOnce(id, 3_000L)
        assertEquals(2_000L, repository.stored.single().firstRepeatAtMillis)
    }

    @Test
    fun laterRepeatDoesNotOverwriteFirstRepeat() = runBlocking {
        val repository = FakeDecisionRepository()
        repository.stored += decision(
            repeat = RepeatObservation.RepeatDetected,
            firstRepeat = 2_000L,
        )
        assertEquals(
            false,
            repository.markFirstRepeatOnce(
                repository.stored.single().decisionId,
                5_000L,
            ),
        )
        assertEquals(2_000L, repository.stored.single().firstRepeatAtMillis)
    }

    @Test
    fun finalizerMarksUnknownRepeatFalse() = runBlocking {
        val repository = FakeDecisionRepository()
        repository.stored += decision(deadline = 2_000L)
        val result = finalizer(repository, now = 2_000L).finalise(
            repository.stored.single().decisionId,
        )
        assertEquals(AdaptiveFinalisationResult.Finalised, result)
        assertEquals(RepeatObservation.NoRepeatDetected, repository.stored.single().repeatObservation)
    }

    @Test
    fun finalizerPreservesTrueRepeat() = runBlocking {
        val repository = FakeDecisionRepository()
        repository.stored += decision(
            repeat = RepeatObservation.RepeatDetected,
            firstRepeat = 1_500L,
            deadline = 2_000L,
        )
        finalizer(repository, now = 2_000L).finalise(
            repository.stored.single().decisionId,
        )
        assertEquals(RepeatObservation.RepeatDetected, repository.stored.single().repeatObservation)
        assertEquals(1_500L, repository.stored.single().firstRepeatAtMillis)
    }

    @Test
    fun observationFinalisationPreservesDecisionPassport() = runBlocking {
        val repository = FakeDecisionRepository()
        repository.stored += decision(deadline = 2_000L).copy(
            recommendationPolicyVersion = 7,
            assignedProtocolId = "pivot_game",
            assignedProtocolVersion = 1,
            actualProtocolId = "pivot_game",
            actualProtocolVersion = 1,
            eligibleMomentPlanCount = 2,
        )

        finalizer(repository, now = 2_000L).finalise(
            repository.stored.single().decisionId,
        )

        val stored = repository.stored.single()
        assertEquals(7, stored.recommendationPolicyVersion)
        assertEquals("pivot_game", stored.assignedProtocolId)
        assertEquals(1, stored.assignedProtocolVersion)
        assertEquals("pivot_game", stored.actualProtocolId)
        assertEquals(1, stored.actualProtocolVersion)
        assertEquals(2, stored.eligibleMomentPlanCount)
    }

    @Test
    fun finalizerIsIdempotent() = runBlocking {
        val repository = FakeDecisionRepository()
        repository.stored += decision(deadline = 2_000L)
        val finalizer = finalizer(repository, now = 2_000L)
        val id = repository.stored.single().decisionId
        assertEquals(AdaptiveFinalisationResult.Finalised, finalizer.finalise(id))
        assertEquals(AdaptiveFinalisationResult.AlreadyFinalised, finalizer.finalise(id))
    }

    @Test
    fun missingDecisionFinalizationIsSafe() = runBlocking {
        assertEquals(
            AdaptiveFinalisationResult.Missing,
            finalizer(FakeDecisionRepository(), now = 2_000L).finalise("missing"),
        )
    }

    @Test
    fun alreadyFinalizedDecisionIsSafe() = runBlocking {
        val repository = FakeDecisionRepository()
        repository.stored += decision(
            repeat = RepeatObservation.NoRepeatDetected,
            deadline = 2_000L,
            finalised = 2_000L,
        )
        assertEquals(
            AdaptiveFinalisationResult.AlreadyFinalised,
            finalizer(repository, now = 3_000L).finalise(
                repository.stored.single().decisionId,
            ),
        )
    }

    @Test
    fun earlyWorkerFinalizationIsNotDue() = runBlocking {
        val repository = FakeDecisionRepository()
        repository.stored += decision(deadline = 3_000L)
        assertEquals(
            AdaptiveFinalisationResult.NotDue,
            finalizer(repository, now = 2_999L).finalise(
                repository.stored.single().decisionId,
            ),
        )
    }

    @Test
    fun overdueRecoveryFinalizesDecision() = runBlocking {
        val repository = FakeDecisionRepository()
        repository.stored += decision(deadline = 2_000L)
        val scheduler = FakeScheduler()
        val clock = FakeClock(3_000L)
        val result = recovery(repository, scheduler, clock).recover()
        assertEquals(1, result.finalisedCount)
        assertEquals(3_000L, repository.stored.single().observationFinalisedAtMillis)
    }

    @Test
    fun futureRecoveryReschedulesUniqueWork() = runBlocking {
        val repository = FakeDecisionRepository()
        repository.stored += decision(deadline = 4_000L)
        val scheduler = FakeScheduler()
        val result = recovery(repository, scheduler, FakeClock(3_000L)).recover()
        assertEquals(1, result.rescheduledCount)
        assertEquals(1, scheduler.scheduled.size)
    }

    @Test
    fun recoveryDoesNotCreateDecisions() = runBlocking {
        val repository = FakeDecisionRepository()
        recovery(repository, FakeScheduler(), FakeClock(3_000L)).recover()
        assertEquals(0, repository.insertCalls)
        assertTrue(repository.stored.isEmpty())
    }

    @Test
    fun recoveryToleratesSchedulerFailure() = runBlocking {
        val repository = FakeDecisionRepository()
        repository.stored += decision(deadline = 4_000L)
        val scheduler = FakeScheduler().apply { fail = true }
        val result = recovery(repository, scheduler, FakeClock(3_000L)).recover()
        assertEquals(1, result.failedCount)
    }

    @Test
    fun resetLearningUsesScopedPersonalLearningClear() = runBlocking {
        val decisions = FakeDecisionRepository()
        val allData = FakeAdaptiveDataRepository()
        val reset = AdaptiveResetCoordinator(
            decisions,
            allData,
            FakeScheduler(),
            AdaptiveSafeLogger { _, _ -> },
        )
        assertEquals(AdaptiveLifecycleResult.Applied, reset.resetPersonalLearning())
        assertEquals(1, allData.clearLearningCalls)
        assertEquals(0, allData.clearCalls)
    }

    @Test
    fun resetLearningPreservesPlans() = runBlocking {
        val plans = FakeMomentPlanRepository(listOf(momentPlan()))
        val reset = AdaptiveResetCoordinator(
            FakeDecisionRepository(),
            FakeAdaptiveDataRepository(),
            FakeScheduler(),
            AdaptiveSafeLogger { _, _ -> },
        )
        reset.resetPersonalLearning()
        assertEquals(1, plans.plans.value.size)
    }

    @Test
    fun resetLearningPreservesPreferences() = runBlocking {
        val preferences = FakePreferenceRepository(
            AdaptivePreferences(gameSuggestionsEnabled = false),
        )
        val reset = AdaptiveResetCoordinator(
            FakeDecisionRepository(),
            FakeAdaptiveDataRepository(),
            FakeScheduler(),
            AdaptiveSafeLogger { _, _ -> },
        )
        reset.resetPersonalLearning()
        assertEquals(false, preferences.current.gameSuggestionsEnabled)
    }

    @Test
    fun completeClearUsesAllAdaptiveDataRepository() = runBlocking {
        val allData = FakeAdaptiveDataRepository()
        val reset = AdaptiveResetCoordinator(
            FakeDecisionRepository(),
            allData,
            FakeScheduler(),
            AdaptiveSafeLogger { _, _ -> },
        )
        assertEquals(AdaptiveLifecycleResult.Applied, reset.clearAllAdaptiveData())
        assertEquals(1, allData.clearCalls)
    }

    @Test
    fun resetCancelsAdaptiveObservationWork() = runBlocking {
        val scheduler = FakeScheduler()
        val reset = AdaptiveResetCoordinator(
            FakeDecisionRepository(),
            FakeAdaptiveDataRepository(),
            scheduler,
            AdaptiveSafeLogger { _, _ -> },
        )
        reset.resetPersonalLearning()
        assertEquals(1, scheduler.cancelCalls)
    }

    @Test
    fun completeClearCancelsAdaptiveObservationWork() = runBlocking {
        val scheduler = FakeScheduler()
        val reset = AdaptiveResetCoordinator(
            FakeDecisionRepository(),
            FakeAdaptiveDataRepository(),
            scheduler,
            AdaptiveSafeLogger { _, _ -> },
        )
        reset.clearAllAdaptiveData()
        assertEquals(1, scheduler.cancelCalls)
    }

    private fun finalizer(
        repository: FakeDecisionRepository,
        now: Long,
    ) = AdaptiveObservationFinalizer(
        repository,
        FakeClock(now),
        AdaptiveSafeLogger { _, _ -> },
    )

    private fun recovery(
        repository: FakeDecisionRepository,
        scheduler: FakeScheduler,
        clock: FakeClock,
    ) = AdaptiveObservationRecovery(
        repository,
        AdaptiveObservationFinalizer(
            repository,
            clock,
            AdaptiveSafeLogger { _, _ -> },
        ),
        scheduler,
        clock,
        AdaptiveSafeLogger { _, _ -> },
    )
}
