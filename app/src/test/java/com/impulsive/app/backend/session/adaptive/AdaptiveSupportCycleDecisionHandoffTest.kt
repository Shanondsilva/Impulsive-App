package com.impulsive.app.backend.session.adaptive

import com.impulsive.app.backend.domain.model.adaptive.AdaptiveAssignment
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveDecision
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveReasonCode
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSourceKind
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycle
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycleStep
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycleTransitionReason
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportStepOutcome
import com.impulsive.app.backend.domain.model.adaptive.AssignmentMode
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentIntensity
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveSupportCycleClearAllResult
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveSupportCycleCreateResult
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveSupportCycleLoadResult
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveSupportCycleMutationResult
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveSupportCycleRepository
import com.impulsive.app.backend.domain.repository.adaptive.PersistedAdaptiveSupportCycle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for transferring the current decision ownership of one active support
 * cycle to an explicit same-Moment follow-up decision.
 */
class AdaptiveSupportCycleDecisionHandoffTest {
    @Test
    fun validSameMomentFollowUpTakesOwnershipOfTheExistingCycle() = runBlocking {
        val repository = FakeRepository(seeded())
        val result = coordinator(repository).handoffDecision(
            cycleId = CycleId,
            previousDecision = previousDecision(),
            followUpDecision = followUpDecision(),
        )

        assertTrue(result is AdaptiveSupportCycleDecisionHandoffResult.Transferred)
        result as AdaptiveSupportCycleDecisionHandoffResult.Transferred
        assertEquals(FollowUpDecisionId, result.state.cycle.decisionId)
        assertEquals(FollowUpDecisionId, repository.current()!!.cycle.decisionId)
    }

    @Test
    fun handoffChangesOnlyTheOwningDecision() = runBlocking {
        val before = seeded()
        val repository = FakeRepository(before)

        val result = coordinator(repository).handoffDecision(
            cycleId = CycleId,
            previousDecision = previousDecision(),
            followUpDecision = followUpDecision(),
        ) as AdaptiveSupportCycleDecisionHandoffResult.Transferred

        val after = result.state.cycle
        assertEquals(before.cycle.cycleId, after.cycleId)
        assertEquals(
            before.cycle.protectionIncidentToken,
            after.protectionIncidentToken,
        )
        assertEquals(before.cycle.initialDurationMillis, after.initialDurationMillis)
        assertEquals(before.cycle.consumedDurationMillis, after.consumedDurationMillis)
        assertEquals(before.cycle.currentStep, after.currentStep)
        assertEquals(
            before.cycle.consecutiveGameAssignments,
            after.consecutiveGameAssignments,
        )
        assertEquals(before.cycle.transitionReason, after.transitionReason)
        assertEquals(before.cycle.status, after.status)
        assertNotEquals(before.cycle.decisionId, after.decisionId)
        assertEquals(before.cycle.copy(decisionId = FollowUpDecisionId), after)
    }

    @Test
    fun handoffKeepsTheRootProtectionIncidentToken() = runBlocking {
        val repository = FakeRepository(seeded())

        val result = coordinator(repository).handoffDecision(
            cycleId = CycleId,
            previousDecision = previousDecision(),
            followUpDecision = followUpDecision(),
        ) as AdaptiveSupportCycleDecisionHandoffResult.Transferred

        assertEquals(RootIncidentToken, result.state.cycle.protectionIncidentToken)
        assertNotEquals(
            FollowUpIncidentToken,
            result.state.cycle.protectionIncidentToken,
        )
    }

    @Test
    fun handoffNeverExtendsCycleExpiryOrCreationTime() = runBlocking {
        val before = seeded()
        val repository = FakeRepository(before)
        val followUp = followUpDecision(observationDeadlineAtMillis = 500_000L)

        assertEquals(OriginalExpiry, before.expiresAtEpochMillis)
        assertTrue(followUp.observationDeadlineAtMillis > OriginalExpiry)

        val result = coordinator(repository).handoffDecision(
            cycleId = CycleId,
            previousDecision = previousDecision(),
            followUpDecision = followUp,
        ) as AdaptiveSupportCycleDecisionHandoffResult.Transferred

        assertEquals(OriginalExpiry, result.state.expiresAtEpochMillis)
        assertEquals(before.createdAtEpochMillis, result.state.createdAtEpochMillis)
        assertEquals(OriginalExpiry, repository.current()!!.expiresAtEpochMillis)
    }

    @Test
    fun successfulHandoffIncrementsRevisionOnceAndRetryWritesNothing() = runBlocking {
        val repository = FakeRepository(seeded(revision = 7L))
        val coordinator = coordinator(repository)

        val first = coordinator.handoffDecision(
            cycleId = CycleId,
            previousDecision = previousDecision(),
            followUpDecision = followUpDecision(),
        ) as AdaptiveSupportCycleDecisionHandoffResult.Transferred
        assertEquals(8L, first.state.revision)
        assertEquals(1, repository.updateCount)

        val retry = coordinator.handoffDecision(
            cycleId = CycleId,
            previousDecision = previousDecision(),
            followUpDecision = followUpDecision(),
        )

        assertTrue(retry is AdaptiveSupportCycleDecisionHandoffResult.AlreadyTransferred)
        retry as AdaptiveSupportCycleDecisionHandoffResult.AlreadyTransferred
        assertEquals(8L, retry.state.revision)
        assertEquals(8L, repository.current()!!.revision)
        assertEquals(1, repository.updateCount)
    }

    @Test
    fun inProgressCurrentStepCannotBeTransferred() = runBlocking {
        val repository = FakeRepository(
            seeded(step = step(AdaptiveSupportStepOutcome.InProgress)),
        )

        val result = coordinator(repository).handoffDecision(
            cycleId = CycleId,
            previousDecision = previousDecision(),
            followUpDecision = followUpDecision(),
        )

        assertEquals(
            AdaptiveSupportCycleDecisionHandoffResult.CurrentStepNotResolved,
            result,
        )
        assertEquals(0, repository.updateCount)
        assertEquals(PreviousDecisionId, repository.current()!!.cycle.decisionId)
        assertEquals(5L, repository.current()!!.revision)
    }

    @Test
    fun absentCurrentStepCannotBeTransferred() = runBlocking {
        val repository = FakeRepository(seeded(step = null, consumed = 0L))

        val result = coordinator(repository).handoffDecision(
            cycleId = CycleId,
            previousDecision = previousDecision(),
            followUpDecision = followUpDecision(),
        )

        assertEquals(
            AdaptiveSupportCycleDecisionHandoffResult.CurrentStepNotResolved,
            result,
        )
        assertEquals(0, repository.updateCount)
    }

    @Test
    fun differentMomentWindowIsRejected() = runBlocking {
        val repository = FakeRepository(seeded())

        val result = coordinator(repository).handoffDecision(
            cycleId = CycleId,
            previousDecision = previousDecision(),
            followUpDecision = followUpDecision(momentWindowStartedAtMillis = 2_000L),
        )

        assertEquals(
            AdaptiveSupportCycleDecisionHandoffResult.InvalidDecisionLineage,
            result,
        )
        assertEquals(0, repository.updateCount)
    }

    @Test
    fun ordinaryNewDecisionCannotStealTheCycle() = runBlocking {
        listOf(AdaptiveSourceKind.App, AdaptiveSourceKind.Website).forEach { sourceKind ->
            val repository = FakeRepository(seeded())

            val result = coordinator(repository).handoffDecision(
                cycleId = CycleId,
                previousDecision = previousDecision(),
                followUpDecision = followUpDecision(sourceKind = sourceKind),
            )

            assertEquals(
                AdaptiveSupportCycleDecisionHandoffResult.InvalidDecisionLineage,
                result,
            )
            assertEquals(0, repository.updateCount)
        }
    }

    @Test
    fun unstartedPreviousDecisionIsRejected() = runBlocking {
        val repository = FakeRepository(seeded())

        val result = coordinator(repository).handoffDecision(
            cycleId = CycleId,
            previousDecision = previousDecision(startedAtMillis = null),
            followUpDecision = followUpDecision(),
        )

        assertEquals(
            AdaptiveSupportCycleDecisionHandoffResult.InvalidDecisionLineage,
            result,
        )
        assertEquals(0, repository.updateCount)
    }

    @Test
    fun followUpMustHaveBeenPresented() = runBlocking {
        val repository = FakeRepository(seeded())

        val result = coordinator(repository).handoffDecision(
            cycleId = CycleId,
            previousDecision = previousDecision(),
            followUpDecision = followUpDecision(presentedAtMillis = null),
        )

        assertEquals(
            AdaptiveSupportCycleDecisionHandoffResult.InvalidDecisionLineage,
            result,
        )
        assertEquals(0, repository.updateCount)
    }

    @Test
    fun followUpMustCarryAnActualSelectedIntervention() = runBlocking {
        val repository = FakeRepository(seeded())

        val result = coordinator(repository).handoffDecision(
            cycleId = CycleId,
            previousDecision = previousDecision(),
            followUpDecision = followUpDecision(actualIntervention = null),
        )

        assertEquals(
            AdaptiveSupportCycleDecisionHandoffResult.InvalidDecisionLineage,
            result,
        )
        assertEquals(0, repository.updateCount)
    }

    @Test
    fun followUpCreatedBeforeThePreviousDecisionIsRejected() = runBlocking {
        val repository = FakeRepository(seeded())

        val result = coordinator(repository).handoffDecision(
            cycleId = CycleId,
            previousDecision = previousDecision(),
            followUpDecision = followUpDecision(createdAtMillis = 900L),
        )

        assertEquals(
            AdaptiveSupportCycleDecisionHandoffResult.InvalidDecisionLineage,
            result,
        )
        assertEquals(0, repository.updateCount)
    }

    /**
     * Paired with [alreadyTransferredRemainsIdempotentWhenFollowUpDecisionIsStarted].
     *
     * A started follow-up that does NOT already own the cycle is rejected, while
     * a started follow-up that DOES already own it is idempotent success.
     */
    @Test
    fun alreadyStartedFollowUpCannotPerformAFirstTransfer() = runBlocking {
        val repository = FakeRepository(seeded())

        val result = coordinator(repository).handoffDecision(
            cycleId = CycleId,
            previousDecision = previousDecision(),
            followUpDecision = followUpDecision(startedAtMillis = 3_000L),
        )

        assertEquals(
            AdaptiveSupportCycleDecisionHandoffResult.InvalidDecisionLineage,
            result,
        )
        assertEquals(0, repository.updateCount)
        assertEquals(PreviousDecisionId, repository.current()!!.cycle.decisionId)
        assertEquals(5L, repository.current()!!.revision)
    }

    @Test
    fun aCycleOwnedByAThirdDecisionCannotBeStolen() = runBlocking {
        val repository = FakeRepository(seeded(owner = "decision-x"))

        val result = coordinator(repository).handoffDecision(
            cycleId = CycleId,
            previousDecision = previousDecision(),
            followUpDecision = followUpDecision(),
        )

        assertEquals(AdaptiveSupportCycleDecisionHandoffResult.OwnerMismatch, result)
        assertEquals(0, repository.updateCount)
        assertEquals("decision-x", repository.current()!!.cycle.decisionId)
    }

    @Test
    fun alreadyTransferredIsIdempotentEvenAfterTheNextStepStarted() = runBlocking {
        val repository = FakeRepository(
            seeded(
                owner = FollowUpDecisionId,
                step = step(AdaptiveSupportStepOutcome.InProgress),
            ),
        )

        val result = coordinator(repository).handoffDecision(
            cycleId = CycleId,
            previousDecision = previousDecision(),
            followUpDecision = followUpDecision(),
        )

        assertTrue(result is AdaptiveSupportCycleDecisionHandoffResult.AlreadyTransferred)
        result as AdaptiveSupportCycleDecisionHandoffResult.AlreadyTransferred
        assertEquals(FollowUpDecisionId, result.state.cycle.decisionId)
        assertEquals(5L, result.state.revision)
        assertEquals(0, repository.updateCount)
    }

    /**
     * Process-death / retry contract.
     *
     * Once ownership A -> B was durably persisted and Decision B was then marked
     * started, retrying the operation must recognise the persisted cycle
     * ownership rather than repeating or rejecting the completed transfer. The
     * persisted Support Cycle and the persisted decision record are the durable
     * authorities; no retry token or extra persisted flag exists or is needed.
     */
    @Test
    fun alreadyTransferredRemainsIdempotentWhenFollowUpDecisionIsStarted() = runBlocking {
        val before = seeded(
            owner = FollowUpDecisionId,
            step = step(AdaptiveSupportStepOutcome.InProgress),
        )
        val repository = FakeRepository(before)

        val result = coordinator(repository).handoffDecision(
            cycleId = CycleId,
            previousDecision = previousDecision(),
            // The transferred lifecycle has since begun.
            followUpDecision = followUpDecision(startedAtMillis = 3_000L),
        )

        assertTrue(result is AdaptiveSupportCycleDecisionHandoffResult.AlreadyTransferred)
        result as AdaptiveSupportCycleDecisionHandoffResult.AlreadyTransferred
        assertEquals(FollowUpDecisionId, result.state.cycle.decisionId)
        assertEquals(0, repository.updateCount)
        assertEquals(before.revision, result.state.revision)
        assertEquals(before.cycle.currentStep, result.state.cycle.currentStep)
        assertEquals(
            before.cycle.consumedDurationMillis,
            result.state.cycle.consumedDurationMillis,
        )
        assertEquals(
            before.cycle.protectionIncidentToken,
            result.state.cycle.protectionIncidentToken,
        )
        assertEquals(before.expiresAtEpochMillis, result.state.expiresAtEpochMillis)
        assertEquals(before, repository.current())
    }

    @Test
    fun differentMomentWindowCannotExploitAlreadyTransferred() = runBlocking {
        val repository = FakeRepository(seeded(owner = FollowUpDecisionId))

        val result = coordinator(repository).handoffDecision(
            cycleId = CycleId,
            previousDecision = previousDecision(),
            followUpDecision = followUpDecision(momentWindowStartedAtMillis = 2_000L),
        )

        assertEquals(
            AdaptiveSupportCycleDecisionHandoffResult.InvalidDecisionLineage,
            result,
        )
        assertEquals(0, repository.updateCount)
    }

    @Test
    fun wrongSourceKindCannotExploitAlreadyTransferred() = runBlocking {
        listOf(AdaptiveSourceKind.App, AdaptiveSourceKind.Website).forEach { sourceKind ->
            val repository = FakeRepository(seeded(owner = FollowUpDecisionId))

            val result = coordinator(repository).handoffDecision(
                cycleId = CycleId,
                previousDecision = previousDecision(),
                followUpDecision = followUpDecision(sourceKind = sourceKind),
            )

            assertEquals(
                AdaptiveSupportCycleDecisionHandoffResult.InvalidDecisionLineage,
                result,
            )
            assertEquals(0, repository.updateCount)
        }
    }

    @Test
    fun unstartedPreviousDecisionCannotExploitAlreadyTransferred() = runBlocking {
        val repository = FakeRepository(seeded(owner = FollowUpDecisionId))

        val result = coordinator(repository).handoffDecision(
            cycleId = CycleId,
            previousDecision = previousDecision(startedAtMillis = null),
            followUpDecision = followUpDecision(),
        )

        assertEquals(
            AdaptiveSupportCycleDecisionHandoffResult.InvalidDecisionLineage,
            result,
        )
        assertEquals(0, repository.updateCount)
    }

    @Test
    fun sameTargetRevisionRaceReconcilesAsAlreadyTransferred() = runBlocking {
        val repository = FakeRepository(seeded())
        // The competing writer already moved ownership to the same follow-up.
        repository.onNextUpdate = {
            repository.replace(seeded(owner = FollowUpDecisionId, revision = 6L))
        }

        val result = coordinator(repository).handoffDecision(
            cycleId = CycleId,
            previousDecision = previousDecision(),
            followUpDecision = followUpDecision(),
        )

        assertTrue(result is AdaptiveSupportCycleDecisionHandoffResult.AlreadyTransferred)
        result as AdaptiveSupportCycleDecisionHandoffResult.AlreadyTransferred
        assertEquals(FollowUpDecisionId, result.state.cycle.decisionId)
        assertEquals(6L, result.state.revision)
        assertEquals(6L, repository.current()!!.revision)
        assertEquals(1, repository.updateCount)
    }

    @Test
    fun differentTargetRevisionRaceIsReportedAsAConflict() = runBlocking {
        val repository = FakeRepository(seeded())
        // A different decision won the compare-and-set race.
        repository.onNextUpdate = {
            repository.replace(seeded(owner = "decision-z", revision = 6L))
        }

        val result = coordinator(repository).handoffDecision(
            cycleId = CycleId,
            previousDecision = previousDecision(),
            followUpDecision = followUpDecision(),
        )

        assertEquals(AdaptiveSupportCycleDecisionHandoffResult.RevisionConflict, result)
        assertEquals("decision-z", repository.current()!!.cycle.decisionId)
        assertEquals(6L, repository.current()!!.revision)
        assertEquals(1, repository.updateCount)
    }

    @Test
    fun anAbsentCycleIsReportedAsNotFound() = runBlocking {
        val repository = FakeRepository(null)

        val result = coordinator(repository).handoffDecision(
            cycleId = CycleId,
            previousDecision = previousDecision(),
            followUpDecision = followUpDecision(),
        )

        assertEquals(AdaptiveSupportCycleDecisionHandoffResult.NotFound, result)
        assertEquals(0, repository.updateCount)
    }

    @Test
    fun anUnrelatedCycleIdIsReportedAsMismatch() = runBlocking {
        val repository = FakeRepository(seeded())

        val result = coordinator(repository).handoffDecision(
            cycleId = "some-other-cycle",
            previousDecision = previousDecision(),
            followUpDecision = followUpDecision(),
        )

        assertEquals(AdaptiveSupportCycleDecisionHandoffResult.CycleMismatch, result)
        assertEquals(0, repository.updateCount)
    }

    /**
     * Protects the APP-004B sequence: a first explicit alternative request
     * records one durable rejection, an explicit follow-up decision then takes
     * ownership, and that rejection must survive the handoff unchanged.
     */
    @Test
    fun handoffPreservesTheDurableAlternativeRequestCount() = runBlocking {
        val before = seeded(alternativeRequestCount = 1)
        val repository = FakeRepository(before)

        assertEquals(1, before.cycle.alternativeRequestCount)

        val result = coordinator(repository).handoffDecision(
            cycleId = CycleId,
            previousDecision = previousDecision(),
            followUpDecision = followUpDecision(),
        ) as AdaptiveSupportCycleDecisionHandoffResult.Transferred

        assertEquals(FollowUpDecisionId, result.state.cycle.decisionId)
        assertEquals(1, result.state.cycle.alternativeRequestCount)
        assertEquals(1, repository.current()!!.cycle.alternativeRequestCount)
    }

    private fun coordinator(repository: AdaptiveSupportCycleRepository) =
        AdaptiveSupportCycleCoordinator(
            repository = repository,
            clock = AdaptiveClock { 5_000L },
            idSource = AdaptiveIdSource { CycleId },
        )

    private fun step(
        outcome: AdaptiveSupportStepOutcome = AdaptiveSupportStepOutcome.Abandoned,
    ) = AdaptiveSupportCycleStep(
        sequence = 1,
        intervention = InterventionFamily.ShortPause,
        startedAtCycleConsumedDurationMillis = 0L,
        allottedDurationMillis = 30_000L,
        consumedDurationMillis = 20_000L,
        outcome = outcome,
    )

    private fun seeded(
        owner: String = PreviousDecisionId,
        step: AdaptiveSupportCycleStep? = step(),
        consumed: Long = 20_000L,
        revision: Long = 5L,
        alternativeRequestCount: Int = 0,
    ) = PersistedAdaptiveSupportCycle(
        cycle = AdaptiveSupportCycle(
            cycleId = CycleId,
            decisionId = owner,
            protectionIncidentToken = RootIncidentToken,
            initialDurationMillis = 90_000L,
            consumedDurationMillis = consumed,
            currentStep = step,
            consecutiveGameAssignments = 2,
            alternativeRequestCount = alternativeRequestCount,
            transitionReason =
                AdaptiveSupportCycleTransitionReason.InterventionAbandoned,
        ),
        createdAtEpochMillis = 1_000L,
        updatedAtEpochMillis = 2_000L,
        expiresAtEpochMillis = OriginalExpiry,
        revision = revision,
    )

    private fun previousDecision(
        startedAtMillis: Long? = 1_500L,
    ) = decision(
        decisionId = PreviousDecisionId,
        incidentToken = RootIncidentToken,
        sourceKind = AdaptiveSourceKind.App,
        createdAtMillis = 1_000L,
        presentedAtMillis = 1_200L,
        startedAtMillis = startedAtMillis,
        actualIntervention = InterventionFamily.ShortPause,
        observationDeadlineAtMillis = OriginalExpiry,
    )

    private fun followUpDecision(
        sourceKind: AdaptiveSourceKind = AdaptiveSourceKind.ExplicitUserSupport,
        momentWindowStartedAtMillis: Long = MomentWindowStart,
        createdAtMillis: Long = 2_500L,
        presentedAtMillis: Long? = 2_600L,
        startedAtMillis: Long? = null,
        actualIntervention: InterventionFamily? = InterventionFamily.PivotReading,
        observationDeadlineAtMillis: Long = 200_000L,
    ) = decision(
        decisionId = FollowUpDecisionId,
        incidentToken = FollowUpIncidentToken,
        sourceKind = sourceKind,
        createdAtMillis = createdAtMillis,
        momentWindowStartedAtMillis = momentWindowStartedAtMillis,
        presentedAtMillis = presentedAtMillis,
        startedAtMillis = startedAtMillis,
        actualIntervention = actualIntervention,
        observationDeadlineAtMillis = observationDeadlineAtMillis,
    )

    @Suppress("LongParameterList")
    private fun decision(
        decisionId: String,
        incidentToken: String,
        sourceKind: AdaptiveSourceKind,
        createdAtMillis: Long,
        momentWindowStartedAtMillis: Long = MomentWindowStart,
        presentedAtMillis: Long?,
        startedAtMillis: Long?,
        actualIntervention: InterventionFamily?,
        observationDeadlineAtMillis: Long,
    ) = AdaptiveDecision(
        decisionId = decisionId,
        protectionIncidentToken = incidentToken,
        sourceKind = sourceKind,
        createdAtMillis = createdAtMillis,
        momentWindowStartedAtMillis = momentWindowStartedAtMillis,
        momentCue = null,
        baselineUrgeRating = null,
        assignment = AdaptiveAssignment(
            momentIntensity = MomentIntensity.FirstAttempt,
            assignmentMode = AssignmentMode.MinimumFriction,
            eligibleInterventions = setOf(
                InterventionFamily.ShortPause,
                InterventionFamily.PivotReading,
            ),
            assignedSuggestion = InterventionFamily.ShortPause,
            selectionProbability = 1.0,
            reasonCode = AdaptiveReasonCode.MinimumEffectiveFriction,
            actualIntervention = actualIntervention,
        ),
        presentedAtMillis = presentedAtMillis,
        startedAtMillis = startedAtMillis,
        observationDeadlineAtMillis = observationDeadlineAtMillis,
    )

    /**
     * Deterministic in-memory support-cycle store with revision compare-and-set
     * and an optional hook that simulates a competing writer landing first.
     */
    private class FakeRepository(
        initial: PersistedAdaptiveSupportCycle?,
    ) : AdaptiveSupportCycleRepository {
        private var state: PersistedAdaptiveSupportCycle? = initial
        var updateCount: Int = 0
            private set

        /** Runs once immediately before the next [update] evaluates its revision. */
        var onNextUpdate: (() -> Unit)? = null

        fun current(): PersistedAdaptiveSupportCycle? = state

        fun replace(next: PersistedAdaptiveSupportCycle?) {
            state = next
        }

        override suspend fun create(
            cycle: AdaptiveSupportCycle,
            createdAtEpochMillis: Long,
            expiresAtEpochMillis: Long,
        ): AdaptiveSupportCycleCreateResult {
            state?.let { return AdaptiveSupportCycleCreateResult.ExistingActive(it) }
            return PersistedAdaptiveSupportCycle(
                cycle = cycle,
                createdAtEpochMillis = createdAtEpochMillis,
                updatedAtEpochMillis = createdAtEpochMillis,
                expiresAtEpochMillis = expiresAtEpochMillis,
                revision = 1L,
            ).also { state = it }.let(AdaptiveSupportCycleCreateResult::Created)
        }

        override suspend fun load(nowEpochMillis: Long): AdaptiveSupportCycleLoadResult {
            val current = state ?: return AdaptiveSupportCycleLoadResult.NotFound
            return if (current.expiresAtEpochMillis <= nowEpochMillis) {
                AdaptiveSupportCycleLoadResult.Expired
            } else {
                AdaptiveSupportCycleLoadResult.Active(current)
            }
        }

        override suspend fun update(
            cycleId: String,
            expectedRevision: Long,
            cycle: AdaptiveSupportCycle,
            updatedAtEpochMillis: Long,
        ): AdaptiveSupportCycleMutationResult {
            updateCount += 1
            onNextUpdate?.let {
                onNextUpdate = null
                it()
            }
            val current = state ?: return AdaptiveSupportCycleMutationResult.NotFound
            if (current.cycle.cycleId != cycleId || cycle.cycleId != cycleId) {
                return AdaptiveSupportCycleMutationResult.CycleMismatch
            }
            if (current.revision != expectedRevision) {
                return AdaptiveSupportCycleMutationResult.RevisionConflict(current.revision)
            }
            if (cycle.isTerminal) {
                state = null
                return AdaptiveSupportCycleMutationResult.Cleared
            }
            // Mirrors production: creation time and expiry survive every update.
            return current.copy(
                cycle = cycle,
                updatedAtEpochMillis = updatedAtEpochMillis,
                revision = current.revision + 1L,
            ).also { state = it }.let(AdaptiveSupportCycleMutationResult::Updated)
        }

        override suspend fun clear(cycleId: String): AdaptiveSupportCycleMutationResult {
            state = null
            return AdaptiveSupportCycleMutationResult.Cleared
        }

        override suspend fun clearAll(): AdaptiveSupportCycleClearAllResult {
            state = null
            return AdaptiveSupportCycleClearAllResult.Cleared
        }
    }

    private companion object {
        const val CycleId = "cycle-root"
        const val PreviousDecisionId = "decision-a"
        const val FollowUpDecisionId = "decision-b"
        const val RootIncidentToken = "root-private-token"
        const val FollowUpIncidentToken = "follow-up-private-token"
        const val MomentWindowStart = 1_000L
        const val OriginalExpiry = 91_000L
    }
}
