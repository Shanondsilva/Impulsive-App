package com.impulsive.app.backend.session.adaptive

import com.impulsive.app.backend.domain.engine.adaptive.AdaptiveSupportCycleTransitionRejection
import com.impulsive.app.backend.domain.game.RecoveryGameLaunchContext
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveAssignment
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveDecision
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveReasonCode
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSourceKind
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycle
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycleStatus
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycleTiming
import com.impulsive.app.backend.domain.model.adaptive.AssignmentMode
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentIntensity
import com.impulsive.app.backend.domain.model.score.ScoreGameType
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveSupportCycleClearAllResult
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveSupportCycleCreateResult
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveSupportCycleLoadResult
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveSupportCycleMutationResult
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveSupportCycleRepository
import com.impulsive.app.backend.domain.repository.adaptive.PersistedAdaptiveSupportCycle
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveSupportCycleCoordinatorTest {
    @Test
    fun createOrRecover_reusesCycleForSameDecision() = runBlocking {
        val repository = FakeRepository()
        val coordinator = coordinator(repository)

        val first = coordinator.createOrRecover(decision()) as AdaptiveSupportCycleCommandResult.Active
        val second = coordinator.createOrRecover(decision())
            as AdaptiveSupportCycleCommandResult.ExistingActive

        assertEquals("cycle-1", first.state.cycle.cycleId)
        assertEquals(first.state.cycle.cycleId, second.state.cycle.cycleId)
        assertEquals("decision-1", second.state.cycle.decisionId)
    }

    @Test
    fun createOrRecover_conflictCarriesCompleteExistingState() = runBlocking {
        val repository = FakeRepository()
        val coordinator = coordinator(repository)

        val original = coordinator.createOrRecover(decision()) as
            AdaptiveSupportCycleCommandResult.Active

        val conflict = coordinator.createOrRecover(decision("decision-2")) as
            AdaptiveSupportCycleCommandResult.ActiveDecisionConflict

        assertEquals(original.state, conflict.state)

        assertEquals("cycle-1", conflict.existingCycleId)

        assertEquals("decision-1", conflict.existingDecisionId)

        assertEquals("decision-1", conflict.state.cycle.decisionId)
    }

    @Test
    fun decisionConflict_doesNotLoseExistingCycleSilently() = runBlocking {
        val repository = FakeRepository()
        val coordinator = coordinator(repository)
        coordinator.createOrRecover(decision())

        coordinator.createOrRecover(decision("decision-2"))
        val stillActive = repository.load(200L) as AdaptiveSupportCycleLoadResult.Active

        assertEquals("cycle-1", stillActive.state.cycle.cycleId)
        assertEquals("decision-1", stillActive.state.cycle.decisionId)
    }

    @Test
    fun gameStartReturnsTypedBoundedLaunchAndRejectsSecondActiveStep() = runBlocking {
        val coordinator = coordinator(FakeRepository())
        val created = coordinator.createOrRecover(decision()) as AdaptiveSupportCycleCommandResult.Active

        val launch = coordinator.startGame(
            cycleId = created.state.cycle.cycleId,
            gameType = ScoreGameType.ReflexOverride,
            requestedDurationMillis = 120_000L,
            minimumUsefulDurationMillis = 10_000L,
        ) as AdaptiveSupportCycleGameLaunchResult.Ready

        assertEquals(90_000L, launch.launch.maxDurationMillis)
        assertEquals("decision-1", launch.launch.decisionId)
        assertEquals(ScoreGameType.ReflexOverride, launch.launch.gameType)

        val duplicate = coordinator.startGame(
            cycleId = created.state.cycle.cycleId,
            gameType = ScoreGameType.RhythmTiles,
            requestedDurationMillis = 90_000L,
            minimumUsefulDurationMillis = 10_000L,
        ) as AdaptiveSupportCycleGameLaunchResult.Unavailable
        assertEquals(
            AdaptiveSupportCycleCommandResult.Rejected(
                AdaptiveSupportCycleTransitionRejection.StepAlreadyInProgress,
            ),
            duplicate.result,
        )
    }

    @Test
    fun elapsedTimeRemainsBoundedAcrossStepHandOff() = runBlocking {
        val coordinator = coordinator(FakeRepository())
        val created = coordinator.createOrRecover(decision()) as AdaptiveSupportCycleCommandResult.Active
        coordinator.startGame(
            created.state.cycle.cycleId,
            ScoreGameType.ReflexOverride,
            90_000L,
            10_000L,
        )
        val elapsed = coordinator.recordElapsed(created.state.cycle.cycleId, 40_000L)
            as AdaptiveSupportCycleCommandResult.Active
        coordinator.failStep(created.state.cycle.cycleId, endCycle = false)
        val next = coordinator.startGame(
            created.state.cycle.cycleId,
            ScoreGameType.RhythmTiles,
            90_000L,
            10_000L,
        ) as AdaptiveSupportCycleGameLaunchResult.Ready

        assertEquals(50_000L, elapsed.state.cycle.remainingDurationMillis)
        assertEquals(50_000L, next.launch.maxDurationMillis)
    }

    @Test
    fun terminalAndConcurrentDuplicateCallbacksClearExactlyOnce() = runBlocking {
        val repository = FakeRepository()
        val coordinator = coordinator(repository)
        val created = coordinator.createOrRecover(decision()) as AdaptiveSupportCycleCommandResult.Active
        coordinator.startGame(
            created.state.cycle.cycleId,
            ScoreGameType.ReflexOverride,
            90_000L,
            10_000L,
        )

        val results = listOf(1, 2).map {
            async { coordinator.completeStep(created.state.cycle.cycleId, endCycle = true) }
        }.awaitAll()

        assertEquals(1, results.count { it is AdaptiveSupportCycleCommandResult.Terminal })
        assertEquals(1, results.count { it == AdaptiveSupportCycleCommandResult.NotFound })
        assertEquals(
            AdaptiveSupportCycleCommandResult.NotFound,
            coordinator.completeStep(created.state.cycle.cycleId, endCycle = true),
        )
    }

    @Test
    fun firstAlternativeRequestKeepsTheCycleActiveWithOneRecordedRequest() = runBlocking {
        val coordinator = coordinator(FakeRepository())
        val created = coordinator.createOrRecover(decision())
            as AdaptiveSupportCycleCommandResult.Active
        coordinator.startGame(
            created.state.cycle.cycleId,
            ScoreGameType.ReflexOverride,
            90_000L,
            10_000L,
        )

        val first = coordinator.requestAlternative(created.state.cycle.cycleId)
            as AdaptiveSupportCycleCommandResult.Active

        assertEquals(1, first.state.cycle.alternativeRequestCount)
        assertEquals(
            AdaptiveSupportCycleStatus.Active,
            first.state.cycle.status,
        )
    }

    /**
     * The second explicit rejection must terminate the cycle and clear its
     * persisted active record through one repository mutation, leaving no
     * process-death window between an "abandon step" write and an "end cycle"
     * write.
     */
    @Test
    fun secondAlternativeRequestTerminatesAndClearsThroughOneMutation() = runBlocking {
        val repository = FakeRepository()
        val coordinator = coordinator(repository)
        val created = coordinator.createOrRecover(decision())
            as AdaptiveSupportCycleCommandResult.Active
        val cycleId = created.state.cycle.cycleId

        coordinator.startGame(cycleId, ScoreGameType.ReflexOverride, 90_000L, 10_000L)
        coordinator.requestAlternative(cycleId)
        coordinator.startGame(cycleId, ScoreGameType.RhythmTiles, 90_000L, 10_000L)

        val updatesBefore = repository.updateCount
        val second = coordinator.requestAlternative(cycleId)
            as AdaptiveSupportCycleCommandResult.Terminal

        assertEquals(2, second.cycle.alternativeRequestCount)
        assertEquals(AdaptiveSupportCycleStatus.Abandoned, second.cycle.status)
        assertEquals(1, repository.updateCount - updatesBefore)
        assertEquals(0, repository.clearCount)
        assertEquals(
            AdaptiveSupportCycleLoadResult.NotFound,
            repository.load(200L),
        )
    }

    @Test
    fun startingTheNextStepPreservesTheFirstAlternativeRequest() = runBlocking {
        val coordinator = coordinator(FakeRepository())
        val created = coordinator.createOrRecover(decision())
            as AdaptiveSupportCycleCommandResult.Active
        val cycleId = created.state.cycle.cycleId

        coordinator.startGame(cycleId, ScoreGameType.ReflexOverride, 90_000L, 10_000L)
        coordinator.requestAlternative(cycleId)
        val next = coordinator.startGame(
            cycleId,
            ScoreGameType.RhythmTiles,
            90_000L,
            10_000L,
        ) as AdaptiveSupportCycleGameLaunchResult.Ready

        assertEquals(1, next.state.cycle.alternativeRequestCount)
        assertEquals(2, next.state.cycle.currentStep?.sequence)
    }

    // ---------- APP-002 initial budget ladder ----------

    /**
     * Every protected cycle is the same fixed length, whatever the decision's
     * source and however many protected Moments preceded it. There is no
     * attempt ordinal and no decision-history lookup.
     */
    @Test
    fun everyNewCycleReceivesTheFixedProtectedDuration() = runBlocking {
        listOf(
            AdaptiveSourceKind.App,
            AdaptiveSourceKind.Website,
            AdaptiveSourceKind.ExplicitUserSupport,
        ).forEach { sourceKind ->
            val created = coordinator(FakeRepository())
                .createOrRecover(decision(sourceKind = sourceKind))
                as AdaptiveSupportCycleCommandResult.Active

            assertEquals(
                "source $sourceKind",
                AdaptiveSupportCycleTiming.TotalDurationMillis,
                created.state.cycle.initialDurationMillis,
            )
        }
    }

    @Test
    fun aRepeatedProtectedMomentStillReceivesTheFullFixedDuration() = runBlocking {
        // First protected cycle runs and ends.
        val first = coordinator(FakeRepository())
            .createOrRecover(decision()) as AdaptiveSupportCycleCommandResult.Active
        assertEquals(90_000L, first.state.cycle.initialDurationMillis)

        // A later, entirely separate protected Moment is not shortened.
        val repeat = coordinator(FakeRepository())
            .createOrRecover(decision("decision-repeat"))
            as AdaptiveSupportCycleCommandResult.Active

        assertEquals(90_000L, repeat.state.cycle.initialDurationMillis)
    }

    /** A persisted active cycle stays authoritative and is never recomputed. */
    @Test
    fun existingActiveCycleKeepsItsOriginalBudget() = runBlocking {
        val repository = FakeRepository()
        coordinator(repository).createOrRecover(decision())

        val existing = coordinator(repository).createOrRecover(decision())
            as AdaptiveSupportCycleCommandResult.ExistingActive

        assertEquals(90_000L, existing.state.cycle.initialDurationMillis)
        assertEquals(
            90_000L,
            (repository.load(200L) as AdaptiveSupportCycleLoadResult.Active)
                .state.cycle.initialDurationMillis,
        )
    }

    @Test
    fun conflictingDecisionDoesNotAlterTheExistingCycleBudget() = runBlocking {
        val repository = FakeRepository()
        coordinator(repository).createOrRecover(decision())

        val conflict = coordinator(repository)
            .createOrRecover(decision("decision-2"))
            as AdaptiveSupportCycleCommandResult.ActiveDecisionConflict

        assertEquals(90_000L, conflict.state.cycle.initialDurationMillis)
        assertEquals("decision-1", conflict.state.cycle.decisionId)
    }

    /** The step-level requested maximum equals the fixed protected duration. */
    @Test
    fun defaultStepDurationMatchesTheFixedProtectedDuration() {
        assertEquals(
            AdaptiveSupportCycleTiming.TotalDurationMillis,
            AdaptiveSupportCycleCoordinator.DefaultCycleDurationMillis,
        )
    }

    /**
     * A step is still capped by the cycle's remaining time, so a generous
     * request cannot exceed what the cycle actually has left.
     */
    @Test
    fun aPartlyConsumedCycleStillCapsAGenerousGameStepRequest() = runBlocking {
        val coordinator = coordinator(FakeRepository())
        val created = coordinator.createOrRecover(decision())
            as AdaptiveSupportCycleCommandResult.Active
        coordinator.startGame(
            created.state.cycle.cycleId,
            ScoreGameType.ReflexOverride,
            90_000L,
            10_000L,
        )
        coordinator.recordElapsed(created.state.cycle.cycleId, 45_000L)
        coordinator.failStep(created.state.cycle.cycleId, endCycle = false)

        val launch = coordinator.startGame(
            cycleId = created.state.cycle.cycleId,
            gameType = ScoreGameType.RhythmTiles,
            requestedDurationMillis = 90_000L,
            minimumUsefulDurationMillis = 10_000L,
        ) as AdaptiveSupportCycleGameLaunchResult.Ready

        assertEquals(45_000L, launch.launch.maxDurationMillis)
    }

    private fun coordinator(
        repository: AdaptiveSupportCycleRepository,
    ) = AdaptiveSupportCycleCoordinator(
        repository = repository,
        clock = AdaptiveClock { 100L },
        idSource = AdaptiveIdSource { "cycle-1" },
    )

    private fun decision(
        id: String = "decision-1",
        sourceKind: AdaptiveSourceKind = AdaptiveSourceKind.App,
    ) = AdaptiveDecision(
        decisionId = id,
        protectionIncidentToken = "incident-$id",
        sourceKind = sourceKind,
        createdAtMillis = 100L,
        momentWindowStartedAtMillis = 100L,
        momentCue = null,
        baselineUrgeRating = null,
        assignment = AdaptiveAssignment(
            momentIntensity = MomentIntensity.FirstAttempt,
            assignmentMode = AssignmentMode.MinimumFriction,
            eligibleInterventions = setOf(InterventionFamily.ShortPause),
            assignedSuggestion = InterventionFamily.ShortPause,
            selectionProbability = 1.0,
            reasonCode = AdaptiveReasonCode.MinimumEffectiveFriction,
        ),
        observationDeadlineAtMillis = 1_200_000L,
    )

    private class FakeRepository : AdaptiveSupportCycleRepository {
        private val mutex = Mutex()
        private var state: PersistedAdaptiveSupportCycle? = null

        var updateCount: Int = 0
            private set

        var clearCount: Int = 0
            private set

        override suspend fun create(
            cycle: AdaptiveSupportCycle,
            createdAtEpochMillis: Long,
            expiresAtEpochMillis: Long,
        ): AdaptiveSupportCycleCreateResult = mutex.withLock {
            state?.let { return@withLock AdaptiveSupportCycleCreateResult.ExistingActive(it) }
            PersistedAdaptiveSupportCycle(
                cycle,
                createdAtEpochMillis,
                createdAtEpochMillis,
                expiresAtEpochMillis,
                1L,
            ).also { state = it }.let(AdaptiveSupportCycleCreateResult::Created)
        }

        override suspend fun load(nowEpochMillis: Long): AdaptiveSupportCycleLoadResult =
            mutex.withLock {
                state?.let(AdaptiveSupportCycleLoadResult::Active)
                    ?: AdaptiveSupportCycleLoadResult.NotFound
            }

        override suspend fun update(
            cycleId: String,
            expectedRevision: Long,
            cycle: AdaptiveSupportCycle,
            updatedAtEpochMillis: Long,
        ): AdaptiveSupportCycleMutationResult = mutex.withLock {
            updateCount += 1
            val current = state ?: return@withLock AdaptiveSupportCycleMutationResult.NotFound
            if (current.cycle.cycleId != cycleId) {
                return@withLock AdaptiveSupportCycleMutationResult.CycleMismatch
            }
            if (current.revision != expectedRevision) {
                return@withLock AdaptiveSupportCycleMutationResult.RevisionConflict(
                    current.revision,
                )
            }
            if (cycle.isTerminal) {
                state = null
                return@withLock AdaptiveSupportCycleMutationResult.Cleared
            }
            current.copy(
                cycle = cycle,
                updatedAtEpochMillis = updatedAtEpochMillis,
                revision = current.revision + 1L,
            ).also { state = it }.let(AdaptiveSupportCycleMutationResult::Updated)
        }

        override suspend fun clear(cycleId: String) = mutex.withLock {
            clearCount += 1
            state = null
            AdaptiveSupportCycleMutationResult.Cleared
        }

        override suspend fun clearAll() = mutex.withLock {
            state = null
            AdaptiveSupportCycleClearAllResult.Cleared
        }
    }
}
