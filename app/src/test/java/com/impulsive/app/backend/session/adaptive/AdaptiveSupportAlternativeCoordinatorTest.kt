package com.impulsive.app.backend.session.adaptive

import com.impulsive.app.backend.domain.model.adaptive.AdaptiveDecision
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSourceKind
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycle
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycleStatus
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycleStep
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycleTransitionReason
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportStepOutcome
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveSupportCycleClearAllResult
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveSupportCycleCreateResult
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveSupportCycleLoadResult
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveSupportCycleMutationResult
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveSupportCycleRepository
import com.impulsive.app.backend.domain.repository.adaptive.PersistedAdaptiveSupportCycle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Retry, failure and concurrency contract for the composed "choose another
 * support" operation.
 *
 * The operation spans two persistence systems with no shared transaction, so
 * every test here exists to prove the same property from a different angle: the
 * operation may be repeated at any point without creating a second follow-up
 * decision or spending a second explicit rejection.
 */
class AdaptiveSupportAlternativeCoordinatorTest {
    // ---------- TEST 1 ----------
    @Test
    fun firstRejectionCountsOnceCreatesFollowUpAndTransfersOwnership() = runBlocking {
        val harness = harness(step = inProgressStep())
        val before = harness.cycles.current()!!

        val result = harness.chooseAlternative() as AdaptiveSupportAlternativeResult.Ready

        val after = harness.cycles.current()!!
        assertEquals(1, after.cycle.alternativeRequestCount)
        assertEquals(
            AdaptiveSupportStepOutcome.Abandoned,
            after.cycle.currentStep?.outcome,
        )

        val followUp = harness.followUpDecision()
        assertEquals(followUp.decisionId, result.decisionId)
        assertEquals(AdaptiveSourceKind.ExplicitUserSupport, followUp.sourceKind)
        assertEquals(
            harness.previous().momentWindowStartedAtMillis,
            followUp.momentWindowStartedAtMillis,
        )
        assertNotEquals(
            harness.previous().protectionIncidentToken,
            followUp.protectionIncidentToken,
        )

        // Ownership moved; cycle identity, root token, budget and expiry did not.
        assertEquals(followUp.decisionId, after.cycle.decisionId)
        assertEquals(before.cycle.cycleId, after.cycle.cycleId)
        assertEquals(
            before.cycle.protectionIncidentToken,
            after.cycle.protectionIncidentToken,
        )
        assertEquals(before.cycle.initialDurationMillis, after.cycle.initialDurationMillis)
        assertEquals(before.expiresAtEpochMillis, after.expiresAtEpochMillis)
        assertEquals(AdaptiveSupportCycleStatus.Active, after.cycle.status)
    }

    // ---------- TEST 2 ----------
    @Test
    fun alreadyTerminalPreviousStepIsContinuationNotRejection() = runBlocking {
        val harness = harness(step = terminalStep(AdaptiveSupportStepOutcome.Completed))

        val result = harness.chooseAlternative()

        assertTrue(result is AdaptiveSupportAlternativeResult.Ready)
        val after = harness.cycles.current()!!
        assertEquals(0, after.cycle.alternativeRequestCount)
        assertEquals(harness.followUpDecision().decisionId, after.cycle.decisionId)
        // Only the ownership transfer wrote to the cycle.
        assertEquals(1, harness.cycles.updateCount)
    }

    // ---------- TEST 3 ----------
    @Test
    fun continuationAfterOnePriorRejectionKeepsCountAtOne() = runBlocking {
        val harness = harness(
            step = terminalStep(AdaptiveSupportStepOutcome.Completed),
            alternativeRequestCount = 1,
        )

        val result = harness.chooseAlternative()

        assertTrue(result is AdaptiveSupportAlternativeResult.Ready)
        val after = harness.cycles.current()!!
        assertEquals(1, after.cycle.alternativeRequestCount)
        assertEquals(AdaptiveSupportCycleStatus.Active, after.cycle.status)
        assertEquals(harness.followUpDecision().decisionId, after.cycle.decisionId)
    }

    // ---------- TEST 4 ----------
    @Test
    fun secondExplicitRejectionEndsCycleAndCreatesNoFollowUp() = runBlocking {
        val harness = harness(step = inProgressStep(), alternativeRequestCount = 1)
        val decisionsBefore = harness.decisions.stored.size

        val result = harness.chooseAlternative() as AdaptiveSupportAlternativeResult.CycleEnded

        assertEquals(AdaptiveSupportCycleStatus.Abandoned, result.cycle.status)
        assertEquals(2, result.cycle.alternativeRequestCount)
        // Active storage cleared, no follow-up, no handoff.
        assertNull(harness.cycles.current())
        assertEquals(0, harness.decisions.insertCalls)
        assertEquals(decisionsBefore, harness.decisions.stored.size)
    }

    // ---------- TEST 5 ----------
    @Test
    fun retryAfterFirstRejectionBeforeFollowUpDoesNotCountAgain() = runBlocking {
        // Process died after the rejection persisted but before the follow-up existed.
        val harness = harness(
            step = terminalStep(AdaptiveSupportStepOutcome.Abandoned),
            alternativeRequestCount = 1,
        )

        val result = harness.chooseAlternative()

        assertTrue(result is AdaptiveSupportAlternativeResult.Ready)
        val after = harness.cycles.current()!!
        assertEquals(1, after.cycle.alternativeRequestCount)
        assertEquals(harness.followUpDecision().decisionId, after.cycle.decisionId)
    }

    // ---------- TEST 6 ----------
    @Test
    fun retryAfterFollowUpCreatedBeforeHandoffReusesTheSameFollowUp() = runBlocking {
        val harness = harness(
            step = terminalStep(AdaptiveSupportStepOutcome.Abandoned),
            alternativeRequestCount = 1,
        )
        val first = harness.chooseAlternative() as AdaptiveSupportAlternativeResult.Ready
        // Rewind ownership as if the handoff write had been lost.
        harness.cycles.replaceOwner(PreviousDecisionId)
        val decisionsAfterFirst = harness.decisions.stored.size

        val retry = harness.chooseAlternative() as AdaptiveSupportAlternativeResult.Ready

        assertEquals(first.decisionId, retry.decisionId)
        assertEquals(decisionsAfterFirst, harness.decisions.stored.size)
        assertEquals(1, harness.cycles.current()!!.cycle.alternativeRequestCount)
        assertEquals(first.decisionId, harness.cycles.current()!!.cycle.decisionId)
    }

    // ---------- TEST 7 ----------
    @Test
    fun retryAfterHandoffRecognisesAlreadyTransferredWithoutMutating() = runBlocking {
        val harness = harness(step = inProgressStep())
        val first = harness.chooseAlternative() as AdaptiveSupportAlternativeResult.Ready
        // The transferred lifecycle has since begun.
        harness.markStarted(first.decisionId)
        val stateAfterFirst = harness.cycles.current()!!
        val updatesAfterFirst = harness.cycles.updateCount
        val decisionsAfterFirst = harness.decisions.stored.size

        val retry = harness.chooseAlternative() as AdaptiveSupportAlternativeResult.Ready

        assertEquals(first.decisionId, retry.decisionId)
        assertEquals(decisionsAfterFirst, harness.decisions.stored.size)
        assertEquals(1, harness.cycles.current()!!.cycle.alternativeRequestCount)
        // AlreadyTransferred writes nothing: no revision change, no extra update.
        assertEquals(updatesAfterFirst, harness.cycles.updateCount)
        assertEquals(stateAfterFirst.revision, harness.cycles.current()!!.revision)
    }

    // ---------- TEST 8 ----------
    @Test
    fun staleRequestFromPreviousOwnerCannotTerminateTheNewOwnersStep() = runBlocking {
        val harness = harness(step = inProgressStep())
        val first = harness.chooseAlternative() as AdaptiveSupportAlternativeResult.Ready
        harness.markStarted(first.decisionId)
        // The new owner has started a fresh in-progress step.
        harness.cycles.replaceStep(inProgressStep(sequence = 2))

        val prepared = harness.supportCycles.prepareAlternativeChoice(
            cycleId = CycleId,
            expectedDecisionId = PreviousDecisionId,
        )

        assertTrue(prepared is AdaptiveSupportCycleAlternativePreparationResult.OwnerMismatch)
        prepared as AdaptiveSupportCycleAlternativePreparationResult.OwnerMismatch
        assertEquals(first.decisionId, prepared.actualDecisionId)
        val after = harness.cycles.current()!!
        assertEquals(1, after.cycle.alternativeRequestCount)
        assertEquals(AdaptiveSupportStepOutcome.InProgress, after.cycle.currentStep?.outcome)
    }

    // ---------- TEST 9 ----------
    @Test
    fun duplicateFirstRequestsCannotDoubleCountTheRejection() = runBlocking {
        val harness = harness(step = inProgressStep())

        val first = harness.chooseAlternative() as AdaptiveSupportAlternativeResult.Ready
        // A duplicate tap arrives; ownership is rewound so it re-enters preparation.
        harness.cycles.replaceOwner(PreviousDecisionId)
        val second = harness.chooseAlternative() as AdaptiveSupportAlternativeResult.Ready

        assertEquals(first.decisionId, second.decisionId)
        assertEquals(1, harness.followUpDecisions().size)
        assertEquals(1, harness.cycles.current()!!.cycle.alternativeRequestCount)
        assertEquals(first.decisionId, harness.cycles.current()!!.cycle.decisionId)
    }

    // ---------- TEST 10 ----------
    /**
     * The initial token lookup finds nothing, but a competing caller creates the
     * follow-up and takes ownership before this caller's owner check. Exactly one
     * token recheck must resolve that into the same follow-up, with no retry of
     * preparation and no rejection counted by this caller.
     */
    @Test
    fun ownerMismatchRaceRechecksTokenOnceAndContinues() = runBlocking {
        val harness = harness(step = terminalStep(AdaptiveSupportStepOutcome.Abandoned))
        // Land the competing writer between our token lookup and our owner check.
        harness.cycles.onNextLoad = { harness.seedRacingFollowUpBlocking() }

        val result = harness.chooseAlternative()
        assertTrue("Unexpected race outcome: $result", result is AdaptiveSupportAlternativeResult.Ready)
        result as AdaptiveSupportAlternativeResult.Ready

        assertEquals(RacedFollowUpId, result.decisionId)
        assertEquals(1, harness.followUpDecisions().size)
        // This caller counted no rejection of its own.
        assertEquals(0, harness.cycles.current()!!.cycle.alternativeRequestCount)
        assertEquals(RacedFollowUpId, harness.cycles.current()!!.cycle.decisionId)
    }

    // ---------- TEST 13 ----------
    @Test
    fun conflictingChoiceOnExistingFollowUpFailsClosed() = runBlocking {
        val harness = harness(step = inProgressStep())
        val first = harness.chooseAlternative(
            intervention = InterventionFamily.PivotReading,
        ) as AdaptiveSupportAlternativeResult.Ready
        harness.markStarted(first.decisionId)
        val decisionsAfterFirst = harness.decisions.stored.size
        val countAfterFirst = harness.cycles.current()!!.cycle.alternativeRequestCount

        val conflicting = harness.chooseAlternative(
            intervention = InterventionFamily.MomentPlan,
        )

        // No third decision, no overwritten choice, no second cycle mutation.
        assertEquals(decisionsAfterFirst, harness.decisions.stored.size)
        assertEquals(
            InterventionFamily.PivotReading,
            harness.decisions.getById(first.decisionId)!!.assignment.actualIntervention,
        )
        assertEquals(countAfterFirst, harness.cycles.current()!!.cycle.alternativeRequestCount)
        // Any fail-closed outcome is acceptable; silently re-choosing is not.
        assertTrue(
            "Unexpected conflicting-choice outcome: $conflicting",
            conflicting is AdaptiveSupportAlternativeResult.IneligibleChoice ||
                conflicting is AdaptiveSupportAlternativeResult.InvalidMomentPlan ||
                conflicting is AdaptiveSupportAlternativeResult.PersistenceFailure,
        )
    }

    // ---------- TEST 14 ----------
    @Test
    fun followUpFailureLeavesTheCountedRejectionIntactAndRetryable() = runBlocking {
        val harness = harness(step = inProgressStep())
        harness.decisions.failInserts = true

        val failed = harness.chooseAlternative()

        assertEquals(AdaptiveSupportAlternativeResult.PersistenceFailure, failed)
        val afterFailure = harness.cycles.current()!!
        // No rollback: the rejection the user made is durably recorded.
        assertEquals(1, afterFailure.cycle.alternativeRequestCount)
        assertEquals(
            AdaptiveSupportStepOutcome.Abandoned,
            afterFailure.cycle.currentStep?.outcome,
        )
        assertEquals(AdaptiveSupportCycleStatus.Active, afterFailure.cycle.status)

        // The retry continues from the terminal step without counting again.
        harness.decisions.failInserts = false
        val retry = harness.chooseAlternative()
        assertTrue(retry is AdaptiveSupportAlternativeResult.Ready)
        assertEquals(1, harness.cycles.current()!!.cycle.alternativeRequestCount)
    }

    // ---------- TEST 15 ----------
    @Test
    fun handoffFailureDoesNotCreateAnotherFollowUpOnRetry() = runBlocking {
        val harness = harness(step = inProgressStep())
        harness.cycles.failNextHandoffUpdate = true

        val failed = harness.chooseAlternative()
        assertEquals(AdaptiveSupportAlternativeResult.RevisionConflict, failed)
        val decisionsAfterFailure = harness.decisions.stored.size

        val retry = harness.chooseAlternative() as AdaptiveSupportAlternativeResult.Ready

        assertEquals(decisionsAfterFailure, harness.decisions.stored.size)
        assertEquals(1, harness.followUpDecisions().size)
        assertEquals(retry.decisionId, harness.cycles.current()!!.cycle.decisionId)
        assertEquals(1, harness.cycles.current()!!.cycle.alternativeRequestCount)
    }

    // ---------- TEST 16 ----------
    @Test
    fun absentCurrentStepFailsWithoutCreatingAFollowUp() = runBlocking {
        val harness = harness(step = null)

        val result = harness.chooseAlternative()

        assertEquals(AdaptiveSupportAlternativeResult.PersistenceFailure, result)
        assertEquals(0, harness.decisions.insertCalls)
        assertEquals(1, harness.decisions.stored.size)
    }

    // ---------- TEST 17 ----------
    @Test
    fun ownerMismatchWithoutAnExistingFollowUpFailsWithZeroMutation() = runBlocking {
        val harness = harness(step = inProgressStep())
        harness.cycles.replaceOwner("decision-x")
        val updatesBefore = harness.cycles.updateCount

        val result = harness.chooseAlternative()

        assertEquals(
            AdaptiveSupportAlternativeResult.OwnerMismatch("decision-x"),
            result,
        )
        assertEquals(updatesBefore, harness.cycles.updateCount)
        assertEquals(0, harness.decisions.insertCalls)
        assertEquals(0, harness.cycles.current()!!.cycle.alternativeRequestCount)
        assertEquals(
            AdaptiveSupportStepOutcome.InProgress,
            harness.cycles.current()!!.cycle.currentStep?.outcome,
        )
    }

    // ---------- TEST 18 ----------
    @Test
    fun rootIdentityBudgetAndExpiryArePreservedAcrossTheWholeOperation() = runBlocking {
        val harness = harness(step = inProgressStep())
        val before = harness.cycles.current()!!

        harness.chooseAlternative() as AdaptiveSupportAlternativeResult.Ready

        val after = harness.cycles.current()!!
        assertEquals(before.cycle.cycleId, after.cycle.cycleId)
        assertEquals(
            before.cycle.protectionIncidentToken,
            after.cycle.protectionIncidentToken,
        )
        assertEquals(before.cycle.initialDurationMillis, after.cycle.initialDurationMillis)
        assertEquals(before.cycle.consumedDurationMillis, after.cycle.consumedDurationMillis)
        assertEquals(before.expiresAtEpochMillis, after.expiresAtEpochMillis)
        assertEquals(before.createdAtEpochMillis, after.createdAtEpochMillis)
        // Only the expected fields moved.
        assertNotEquals(before.cycle.decisionId, after.cycle.decisionId)
        assertEquals(0, before.cycle.alternativeRequestCount)
        assertEquals(1, after.cycle.alternativeRequestCount)
    }

    /**
     * Pins the exact runtime value, separators included.
     *
     * The separator is a NUL character so no realistic identifier can forge a
     * boundary between the prefix, the cycle and the owning decision. It is
     * written in source as a textual \u0000 escape: a raw 0x00 byte in a .kt
     * file makes source tooling treat the file as binary. This test fails if
     * either the separator or that source representation regresses.
     */
    /**
     * APP-002 regression: changing support inside one Moment keeps the same
     * shrinking cycle.
     *
     * The follow-up decision is another persisted decision record, but it is
     * not another genuine protection attempt, so it must never trigger a new
     * cycle at the reduced second-attempt budget.
     */
    @Test
    fun followUpHandoffNeverResetsOrShrinksTheCycleBudget() = runBlocking {
        val harness = harness(step = inProgressStep())
        val before = harness.cycles.current()!!
        assertEquals(90_000L, before.cycle.initialDurationMillis)

        val result = harness.chooseAlternative() as AdaptiveSupportAlternativeResult.Ready

        val after = harness.cycles.current()!!
        assertEquals(90_000L, after.cycle.initialDurationMillis)
        assertEquals(before.cycle.cycleId, after.cycle.cycleId)
        assertEquals(result.decisionId, after.cycle.decisionId)
        assertEquals(before.cycle.consumedDurationMillis, after.cycle.consumedDurationMillis)
    }

    @Test
    fun attemptIdentityHasTheExactDeterministicRuntimeValue() {
        val identity = AdaptiveSupportAlternativeAttemptIdentityFactory.create(
            cycleId = "cycle-1",
            previousDecisionId = "decision-a",
        )

        assertEquals(
            "support-cycle-alternative-v1\u0000cycle-1\u0000decision-a",
            identity,
        )
        assertEquals(2, identity.count { it == '\u0000' })
    }

    @Test
    fun attemptIdentityIsDeterministicPerCycleAndOwnerAndNeverLeaksIntoTheToken() {
        val first = AdaptiveSupportAlternativeAttemptIdentityFactory.create(
            cycleId = CycleId,
            previousDecisionId = PreviousDecisionId,
        )
        val same = AdaptiveSupportAlternativeAttemptIdentityFactory.create(
            cycleId = CycleId,
            previousDecisionId = PreviousDecisionId,
        )
        val otherCycle = AdaptiveSupportAlternativeAttemptIdentityFactory.create(
            cycleId = "other-cycle",
            previousDecisionId = PreviousDecisionId,
        )

        assertEquals(first, same)
        assertNotEquals(first, otherCycle)

        val token = AdaptiveFollowUpIncidentTokenFactory.create(
            previousDecisionId = PreviousDecisionId,
            attemptIdentity = first,
        )
        assertTrue(token.startsWith("afu1_"))
        // The persisted token must not carry raw cycle or decision identifiers.
        assertTrue(!token.contains(CycleId))
        assertTrue(!token.contains(PreviousDecisionId))
    }

    // ---------------- harness ----------------

    /**
     * Shared decision fake plus one fault switch, so follow-up creation can be
     * failed after the support cycle has already recorded the rejection.
     */
    private class FaultingDecisionRepository : FakeDecisionRepository() {
        var failInserts: Boolean = false

        override suspend fun insertOnce(
            decision: AdaptiveDecision,
        ): Boolean {
            if (failInserts) {
                insertCalls++
                return false
            }
            return super.insertOnce(decision)
        }
    }

    private fun harness(
        step: AdaptiveSupportCycleStep?,
        alternativeRequestCount: Int = 0,
    ): Harness {
        val decisions = FaultingDecisionRepository()
        decisions.stored += decision(
            id = PreviousDecisionId,
            token = RootIncidentToken,
            created = 1_000L,
            eligible = setOf(
                InterventionFamily.PivotGame,
                InterventionFamily.PivotReading,
                InterventionFamily.MomentPlan,
            ),
            actual = InterventionFamily.PivotGame,
            presented = 1_200L,
            started = 1_500L,
            deadline = OriginalExpiry,
        )
        decisions.insertCalls = 0

        val plans = FakeMomentPlanRepository(listOf(momentPlan()))
        val preferences = FakePreferenceRepository()
        val clock = FakeClock(5_000L)
        val lifecycle = AdaptiveDecisionLifecycle(
            decisions = decisions,
            momentPlans = plans,
            scheduler = FakeScheduler(),
            clock = clock,
            logger = AdaptiveSafeLogger { _, _ -> },
        )
        val followUp = AdaptiveFollowUpSupport(
            coordinator = coordinatorHarness(
                decisions = decisions,
                preferences = preferences,
                plans = plans,
                clock = clock,
            ),
            decisions = decisions,
            momentPlans = plans,
            lifecycle = lifecycle,
            clock = clock,
        )

        val consumed = step?.consumedDurationMillis ?: 0L
        val cycles = RecordingSupportCycleRepository(
            PersistedAdaptiveSupportCycle(
                cycle = AdaptiveSupportCycle(
                    cycleId = CycleId,
                    decisionId = PreviousDecisionId,
                    protectionIncidentToken = RootIncidentToken,
                    initialDurationMillis = 90_000L,
                    consumedDurationMillis = consumed,
                    currentStep = step,
                    alternativeRequestCount = alternativeRequestCount,
                    transitionReason =
                        AdaptiveSupportCycleTransitionReason.InterventionStarted,
                ),
                createdAtEpochMillis = 1_000L,
                updatedAtEpochMillis = 2_000L,
                expiresAtEpochMillis = OriginalExpiry,
                revision = 5L,
            ),
        )
        val supportCycles = AdaptiveSupportCycleCoordinator(
            repository = cycles,
            clock = clock,
            idSource = AdaptiveIdSource { CycleId },
        )
        return Harness(decisions, cycles, followUp, supportCycles)
    }

    private class Harness(
        val decisions: FaultingDecisionRepository,
        val cycles: RecordingSupportCycleRepository,
        val followUp: AdaptiveFollowUpSupport,
        val supportCycles: AdaptiveSupportCycleCoordinator,
    ) {
        private val orchestrator = AdaptiveSupportAlternativeCoordinator(
            supportCycles = supportCycles,
            followUpSupport = followUp,
            decisions = decisions,
        )

        suspend fun chooseAlternative(
            intervention: InterventionFamily = InterventionFamily.PivotReading,
        ): AdaptiveSupportAlternativeResult = orchestrator.chooseAlternative(
            AdaptiveSupportAlternativeRequest(
                cycleId = CycleId,
                previousDecisionId = PreviousDecisionId,
                intervention = intervention,
            ),
        )

        fun previous(): AdaptiveDecision =
            decisions.stored.first { it.decisionId == PreviousDecisionId }

        fun followUpDecisions(): List<AdaptiveDecision> =
            decisions.stored.filter { it.decisionId != PreviousDecisionId }

        fun followUpDecision(): AdaptiveDecision = followUpDecisions().single()

        suspend fun markStarted(decisionId: String) {
            decisions.markStartedOnce(decisionId, 6_000L)
        }

        /**
         * Simulates a competing caller that created the deterministic follow-up
         * and took ownership before this caller's owner check.
         */
        fun seedRacingFollowUpBlocking(): String = kotlinx.coroutines.runBlocking {
            seedRacingFollowUp()
        }

        suspend fun seedRacingFollowUp(): String {
            val attemptIdentity = AdaptiveSupportAlternativeAttemptIdentityFactory.create(
                cycleId = CycleId,
                previousDecisionId = PreviousDecisionId,
            )
            val token = AdaptiveFollowUpIncidentTokenFactory.create(
                previousDecisionId = PreviousDecisionId,
                attemptIdentity = attemptIdentity,
            )
            /*
             * The presented timestamp must match the one the production path
             * recomputes for this Moment window, otherwise the idempotent
             * lifecycle step would report a genuine invalid transition.
             */
            val raced = decision(
                id = RacedFollowUpId,
                token = token,
                created = FollowUpActionAtMillis,
                eligible = setOf(
                    InterventionFamily.PivotGame,
                    InterventionFamily.PivotReading,
                    InterventionFamily.MomentPlan,
                ),
                actual = InterventionFamily.PivotReading,
                presented = FollowUpActionAtMillis,
                started = FollowUpActionAtMillis + 100L,
                deadline = OriginalExpiry,
            ).copy(
                sourceKind = AdaptiveSourceKind.ExplicitUserSupport,
                momentWindowStartedAtMillis = 1_000L,
            )
            decisions.stored += raced
            cycles.replaceOwner(RacedFollowUpId)
            return RacedFollowUpId
        }
    }

    /**
     * In-memory support-cycle store mirroring production revision compare-and-set
     * and terminal-clear semantics, with counters and fault injection.
     */
    private class RecordingSupportCycleRepository(
        initial: PersistedAdaptiveSupportCycle,
    ) : AdaptiveSupportCycleRepository {
        private var state: PersistedAdaptiveSupportCycle? = initial

        var updateCount: Int = 0
            private set
        var clearCount: Int = 0
            private set
        var failNextHandoffUpdate: Boolean = false

        /** Runs once immediately before the next [load] returns. */
        var onNextLoad: (() -> Unit)? = null

        fun current(): PersistedAdaptiveSupportCycle? = state

        fun replaceOwner(decisionId: String) {
            state = state?.let { it.copy(cycle = it.cycle.copy(decisionId = decisionId)) }
        }

        fun replaceStep(step: AdaptiveSupportCycleStep) {
            state = state?.let {
                it.copy(
                    cycle = it.cycle.copy(
                        currentStep = step,
                        consumedDurationMillis = step.consumedDurationMillis +
                            step.startedAtCycleConsumedDurationMillis,
                    ),
                )
            }
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
            onNextLoad?.let {
                onNextLoad = null
                it()
            }
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
            val current = state ?: return AdaptiveSupportCycleMutationResult.NotFound
            /*
             * Ownership-only writes are the handoff; that is the write this flag
             * simulates losing.
             */
            if (failNextHandoffUpdate && cycle.decisionId != current.cycle.decisionId) {
                failNextHandoffUpdate = false
                return AdaptiveSupportCycleMutationResult.RevisionConflict(current.revision + 1L)
            }
            updateCount += 1
            if (current.cycle.cycleId != cycleId || cycle.cycleId != cycleId) {
                return AdaptiveSupportCycleMutationResult.CycleMismatch
            }
            if (current.revision != expectedRevision) {
                return AdaptiveSupportCycleMutationResult.RevisionConflict(current.revision)
            }
            if (cycle.isTerminal) {
                clearCount += 1
                state = null
                return AdaptiveSupportCycleMutationResult.Cleared
            }
            return current.copy(
                cycle = cycle,
                updatedAtEpochMillis = updatedAtEpochMillis,
                revision = current.revision + 1L,
            ).also { state = it }.let(AdaptiveSupportCycleMutationResult::Updated)
        }

        override suspend fun clear(cycleId: String): AdaptiveSupportCycleMutationResult {
            clearCount += 1
            state = null
            return AdaptiveSupportCycleMutationResult.Cleared
        }

        override suspend fun clearAll(): AdaptiveSupportCycleClearAllResult {
            clearCount += 1
            state = null
            return AdaptiveSupportCycleClearAllResult.Cleared
        }
    }

    private fun inProgressStep(sequence: Int = 1) = AdaptiveSupportCycleStep(
        sequence = sequence,
        intervention = InterventionFamily.PivotGame,
        gameType = com.impulsive.app.backend.domain.model.score.ScoreGameType.ReflexOverride,
        startedAtCycleConsumedDurationMillis = 0L,
        allottedDurationMillis = 30_000L,
        consumedDurationMillis = 0L,
        outcome = AdaptiveSupportStepOutcome.InProgress,
    )

    private fun terminalStep(outcome: AdaptiveSupportStepOutcome) = AdaptiveSupportCycleStep(
        sequence = 1,
        intervention = InterventionFamily.PivotGame,
        gameType = com.impulsive.app.backend.domain.model.score.ScoreGameType.ReflexOverride,
        startedAtCycleConsumedDurationMillis = 0L,
        allottedDurationMillis = 30_000L,
        consumedDurationMillis = 20_000L,
        outcome = outcome,
    )

    private companion object {
        const val CycleId = "cycle-root"
        const val PreviousDecisionId = "decision-a"
        const val RacedFollowUpId = "decision-raced"
        const val RootIncidentToken = "root-private-token"
        const val OriginalExpiry = 91_000L

        /** The timestamp the follow-up path derives from this harness's clock. */
        const val FollowUpActionAtMillis = 5_000L
    }
}
