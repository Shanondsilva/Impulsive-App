package com.impulsive.app.backend.domain.engine.adaptive

import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycle
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycleStatus
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycleStep
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycleTransitionReason
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportStepOutcome
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.score.ScoreGameType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveSupportCycleTransitionPolicyTest {
    @Test
    fun firstStepStartsWithTheAvailableCycleBudget() {
        val result =
            AdaptiveSupportCycleTransitionPolicy
                .startStep(
                    cycle =
                        cycle(
                            currentStep =
                                null,
                        ),
                    intervention =
                        InterventionFamily.PivotGame,
                    gameType =
                        ScoreGameType.ReflexOverride,
                    requestedDurationMillis =
                        90_000L,
                    minimumUsefulDurationMillis =
                        10_000L,
                )
                .appliedCycle()

        assertEquals(
            1,
            result.currentStep
                ?.sequence,
        )

        assertEquals(
            0L,
            result.currentStep
                ?.startedAtCycleConsumedDurationMillis,
        )

        assertEquals(
            90_000L,
            result.currentStep
                ?.allottedDurationMillis,
        )

        assertEquals(
            AdaptiveSupportCycleTransitionReason
                .InterventionStarted,
            result.transitionReason,
        )
    }

    @Test
    fun nextStepUsesOnlyTheFiftySecondsRemaining() {
        val previousStep =
            step(
                sequence =
                    1,
                allottedDurationMillis =
                    90_000L,
                consumedDurationMillis =
                    40_000L,
                outcome =
                    AdaptiveSupportStepOutcome
                        .Failed,
            )

        val result =
            AdaptiveSupportCycleTransitionPolicy
                .startStep(
                    cycle =
                        cycle(
                            consumedDurationMillis =
                                40_000L,
                            currentStep =
                                previousStep,
                        ),
                    intervention =
                        InterventionFamily.PivotGame,
                    gameType =
                        ScoreGameType.RhythmTiles,
                    requestedDurationMillis =
                        90_000L,
                    minimumUsefulDurationMillis =
                        10_000L,
                )
                .appliedCycle()

        assertEquals(
            2,
            result.currentStep
                ?.sequence,
        )

        assertEquals(
            40_000L,
            result.currentStep
                ?.startedAtCycleConsumedDurationMillis,
        )

        assertEquals(
            50_000L,
            result.currentStep
                ?.allottedDurationMillis,
        )

        assertEquals(
            ScoreGameType.RhythmTiles,
            result.currentStep
                ?.gameType,
        )
    }

    @Test
    fun activeStepPreventsConcurrentStepStart() {
        val result =
            AdaptiveSupportCycleTransitionPolicy
                .startStep(
                    cycle =
                        cycle(
                            currentStep =
                                step(),
                        ),
                    intervention =
                        InterventionFamily.PivotReading,
                    requestedDurationMillis =
                        90_000L,
                    minimumUsefulDurationMillis =
                        10_000L,
                )

        assertRejected(
            expected =
                AdaptiveSupportCycleTransitionRejection
                    .StepAlreadyInProgress,
            actual =
                result,
        )
    }

    @Test
    fun terminalCycleRejectsAnotherStep() {
        val result =
            AdaptiveSupportCycleTransitionPolicy
                .startStep(
                    cycle =
                        cycle(
                            currentStep =
                                step(
                                    outcome =
                                        AdaptiveSupportStepOutcome
                                            .Completed,
                                ),
                            status =
                                AdaptiveSupportCycleStatus
                                    .Completed,
                        ),
                    intervention =
                        InterventionFamily.PivotReading,
                    requestedDurationMillis =
                        90_000L,
                    minimumUsefulDurationMillis =
                        10_000L,
                )

        assertRejected(
            expected =
                AdaptiveSupportCycleTransitionRejection
                    .CycleTerminal,
            actual =
                result,
        )
    }

    @Test
    fun insufficientUsefulBudgetRejectsAnotherStep() {
        val result =
            AdaptiveSupportCycleTransitionPolicy
                .startStep(
                    cycle =
                        cycle(
                            consumedDurationMillis =
                                85_000L,
                            currentStep =
                                step(
                                    allottedDurationMillis =
                                        90_000L,
                                    consumedDurationMillis =
                                        85_000L,
                                    outcome =
                                        AdaptiveSupportStepOutcome
                                            .Failed,
                                ),
                        ),
                    intervention =
                        InterventionFamily.PivotReading,
                    requestedDurationMillis =
                        90_000L,
                    minimumUsefulDurationMillis =
                        10_000L,
                )

        assertRejected(
            expected =
                AdaptiveSupportCycleTransitionRejection
                    .InsufficientUsefulBudget,
            actual =
                result,
        )
    }

    @Test
    fun requestBelowMinimumUsefulDurationReturnsTypedRejection() {
        val result =
            AdaptiveSupportCycleTransitionPolicy
                .startStep(
                    cycle =
                        cycle(
                            currentStep =
                                null,
                        ),
                    intervention =
                        InterventionFamily.PivotReading,
                    requestedDurationMillis =
                        5_000L,
                    minimumUsefulDurationMillis =
                        10_000L,
                )

        assertRejected(
            expected =
                AdaptiveSupportCycleTransitionRejection
                    .InsufficientUsefulBudget,
            actual =
                result,
        )
    }

    @Test
    fun completedStepCanEndTheCycle() {
        val result =
            AdaptiveSupportCycleTransitionPolicy
                .resolveCurrentStep(
                    cycle =
                        cycle(
                            consumedDurationMillis =
                                40_000L,
                            currentStep =
                                step(
                                    consumedDurationMillis =
                                        40_000L,
                                ),
                        ),
                    resolution =
                        AdaptiveSupportCycleStepResolution
                            .CompletedAndEndCycle,
                )
                .appliedCycle()

        assertEquals(
            AdaptiveSupportCycleStatus
                .Completed,
            result.status,
        )

        assertEquals(
            AdaptiveSupportStepOutcome
                .Completed,
            result.currentStep
                ?.outcome,
        )
    }

    @Test
    fun failedStepCanLeaveTheCycleActiveForHandOff() {
        val result =
            AdaptiveSupportCycleTransitionPolicy
                .resolveCurrentStep(
                    cycle =
                        cycle(
                            consumedDurationMillis =
                                40_000L,
                            currentStep =
                                step(
                                    consumedDurationMillis =
                                        40_000L,
                                ),
                        ),
                    resolution =
                        AdaptiveSupportCycleStepResolution
                            .FailedAndContinue,
                )
                .appliedCycle()

        assertEquals(
            AdaptiveSupportCycleStatus
                .Active,
            result.status,
        )

        assertEquals(
            AdaptiveSupportStepOutcome
                .Failed,
            result.currentStep
                ?.outcome,
        )

        assertEquals(
            AdaptiveSupportCycleTransitionReason
                .InterventionFailed,
            result.transitionReason,
        )
    }

    @Test
    fun abandonedStepCanEndTheCycle() {
        val result =
            AdaptiveSupportCycleTransitionPolicy
                .resolveCurrentStep(
                    cycle =
                        cycle(
                            consumedDurationMillis =
                                20_000L,
                            currentStep =
                                step(
                                    consumedDurationMillis =
                                        20_000L,
                                ),
                        ),
                    resolution =
                        AdaptiveSupportCycleStepResolution
                            .AbandonedAndEndCycle,
                )
                .appliedCycle()

        assertEquals(
            AdaptiveSupportCycleStatus
                .Abandoned,
            result.status,
        )

        assertEquals(
            AdaptiveSupportStepOutcome
                .Abandoned,
            result.currentStep
                ?.outcome,
        )

        assertEquals(
            AdaptiveSupportCycleTransitionReason
                .InterventionAbandoned,
            result.transitionReason,
        )
    }

    @Test
    fun alternativeRequestAbandonsOnlyTheCurrentStep() {
        val result =
            AdaptiveSupportCycleTransitionPolicy
                .resolveCurrentStep(
                    cycle =
                        cycle(
                            consumedDurationMillis =
                                20_000L,
                            currentStep =
                                step(
                                    consumedDurationMillis =
                                        20_000L,
                                ),
                        ),
                    resolution =
                        AdaptiveSupportCycleStepResolution
                            .AlternativeRequested,
                )
                .appliedCycle()

        assertEquals(
            AdaptiveSupportCycleStatus
                .Active,
            result.status,
        )

        assertEquals(
            AdaptiveSupportStepOutcome
                .Abandoned,
            result.currentStep
                ?.outcome,
        )

        assertEquals(
            AdaptiveSupportCycleTransitionReason
                .UserRequestedAlternative,
            result.transitionReason,
        )

        assertEquals(
            1,
            result.alternativeRequestCount,
        )
    }

    @Test
    fun secondAlternativeRequestTerminatesTheCycleAtomically() {
        val result =
            AdaptiveSupportCycleTransitionPolicy
                .resolveCurrentStep(
                    cycle =
                        cycle(
                            consumedDurationMillis =
                                20_000L,
                            currentStep =
                                step(
                                    sequence =
                                        2,
                                    consumedDurationMillis =
                                        20_000L,
                                ),
                            alternativeRequestCount =
                                1,
                        ),
                    resolution =
                        AdaptiveSupportCycleStepResolution
                            .AlternativeRequested,
                )
                .appliedCycle()

        assertEquals(
            2,
            result.alternativeRequestCount,
        )

        assertEquals(
            AdaptiveSupportCycleStatus
                .Abandoned,
            result.status,
        )

        assertEquals(
            AdaptiveSupportStepOutcome
                .Abandoned,
            result.currentStep
                ?.outcome,
        )

        /*
         * The terminal cycle must not report UserRequestedAlternative, which the
         * outcome mapper prioritises ahead of status.
         */
        assertEquals(
            AdaptiveSupportCycleTransitionReason
                .InterventionAbandoned,
            result.transitionReason,
        )
    }

    @Test
    fun thirdAlternativeRequestIsRejectedAsTerminal() {
        val result =
            AdaptiveSupportCycleTransitionPolicy
                .resolveCurrentStep(
                    cycle =
                        cycle(
                            consumedDurationMillis =
                                20_000L,
                            currentStep =
                                step(
                                    consumedDurationMillis =
                                        20_000L,
                                    outcome =
                                        AdaptiveSupportStepOutcome
                                            .Abandoned,
                                ),
                            alternativeRequestCount =
                                2,
                            status =
                                AdaptiveSupportCycleStatus
                                    .Abandoned,
                        ),
                    resolution =
                        AdaptiveSupportCycleStepResolution
                            .AlternativeRequested,
                )

        assertRejected(
            expected =
                AdaptiveSupportCycleTransitionRejection
                    .CycleTerminal,
            actual =
                result,
        )
    }

    @Test
    fun startingTheNextStepPreservesTheFirstAlternativeRequest() {
        val afterFirstRequest =
            AdaptiveSupportCycleTransitionPolicy
                .resolveCurrentStep(
                    cycle =
                        cycle(
                            consumedDurationMillis =
                                20_000L,
                            currentStep =
                                step(
                                    consumedDurationMillis =
                                        20_000L,
                                ),
                        ),
                    resolution =
                        AdaptiveSupportCycleStepResolution
                            .AlternativeRequested,
                )
                .appliedCycle()

        assertEquals(
            1,
            afterFirstRequest.alternativeRequestCount,
        )

        val next =
            AdaptiveSupportCycleTransitionPolicy
                .startStep(
                    cycle =
                        afterFirstRequest,
                    intervention =
                        InterventionFamily.PivotReading,
                    requestedDurationMillis =
                        90_000L,
                    minimumUsefulDurationMillis =
                        10_000L,
                )
                .appliedCycle()

        assertEquals(
            2,
            next.currentStep
                ?.sequence,
        )

        assertEquals(
            AdaptiveSupportStepOutcome
                .InProgress,
            next.currentStep
                ?.outcome,
        )

        assertEquals(
            AdaptiveSupportCycleTransitionReason
                .InterventionStarted,
            next.transitionReason,
        )

        /*
         * The durability contract: the first rejection stays known even though
         * the transition reason has moved on to the next intervention.
         */
        assertEquals(
            1,
            next.alternativeRequestCount,
        )
    }

    @Test
    fun otherContinuingResolutionsDoNotChangeTheAlternativeRequestCount() {
        listOf(
            AdaptiveSupportCycleStepResolution
                .CompletedAndContinue,
            AdaptiveSupportCycleStepResolution
                .FailedAndContinue,
            AdaptiveSupportCycleStepResolution
                .AbandonedAndContinue,
        ).forEach { resolution ->
            val result =
                AdaptiveSupportCycleTransitionPolicy
                    .resolveCurrentStep(
                        cycle =
                            cycle(
                                consumedDurationMillis =
                                    20_000L,
                                currentStep =
                                    step(
                                        consumedDurationMillis =
                                            20_000L,
                                    ),
                                alternativeRequestCount =
                                    1,
                            ),
                        resolution =
                            resolution,
                    )
                    .appliedCycle()

            assertEquals(
                1,
                result.alternativeRequestCount,
            )

            assertEquals(
                AdaptiveSupportCycleStatus
                    .Active,
                result.status,
            )
        }
    }

    @Test
    fun cancellationTerminatesTheCycle() {
        val result =
            AdaptiveSupportCycleTransitionPolicy
                .resolveCurrentStep(
                    cycle =
                        cycle(
                            consumedDurationMillis =
                                20_000L,
                            currentStep =
                                step(
                                    consumedDurationMillis =
                                        20_000L,
                                ),
                        ),
                    resolution =
                        AdaptiveSupportCycleStepResolution
                            .CancelledAndEndCycle,
                )
                .appliedCycle()

        assertEquals(
            AdaptiveSupportCycleStatus
                .Cancelled,
            result.status,
        )

        assertEquals(
            AdaptiveSupportStepOutcome
                .Cancelled,
            result.currentStep
                ?.outcome,
        )

        assertEquals(
            AdaptiveSupportCycleTransitionReason
                .CycleCancelled,
            result.transitionReason,
        )
    }

    @Test
    fun resolvingWithoutCurrentStepIsRejected() {
        val result =
            AdaptiveSupportCycleTransitionPolicy
                .resolveCurrentStep(
                    cycle =
                        cycle(
                            currentStep =
                                null,
                        ),
                    resolution =
                        AdaptiveSupportCycleStepResolution
                            .FailedAndContinue,
                )

        assertRejected(
            expected =
                AdaptiveSupportCycleTransitionRejection
                    .NoCurrentStep,
            actual =
                result,
        )
    }

    @Test
    fun resolvingTerminalCurrentStepAgainIsRejected() {
        val result =
            AdaptiveSupportCycleTransitionPolicy
                .resolveCurrentStep(
                    cycle =
                        cycle(
                            currentStep =
                                step(
                                    outcome =
                                        AdaptiveSupportStepOutcome
                                            .Failed,
                                ),
                        ),
                    resolution =
                        AdaptiveSupportCycleStepResolution
                            .FailedAndContinue,
                )

        assertRejected(
            expected =
                AdaptiveSupportCycleTransitionRejection
                    .CurrentStepAlreadyTerminal,
            actual =
                result,
        )
    }

    @Test
    fun completedResolvedStepCanTerminaliseWithoutRewritingTimeOrOutcome() {
        val before = cycle(
            consumedDurationMillis = 40_000L,
            currentStep = step(
                consumedDurationMillis = 40_000L,
                outcome = AdaptiveSupportStepOutcome.Completed,
            ),
        )
        val after = AdaptiveSupportCycleTransitionPolicy.finishCycleAfterResolvedStep(
            before,
            AdaptiveSupportCycleStatus.Completed,
        ).appliedCycle()

        assertEquals(AdaptiveSupportCycleStatus.Completed, after.status)
        assertEquals(before.consumedDurationMillis, after.consumedDurationMillis)
        assertEquals(before.currentStep, after.currentStep)
    }

    @Test
    fun resolvedStepRejectsIncompatibleTerminalStatus() {
        val result = AdaptiveSupportCycleTransitionPolicy.finishCycleAfterResolvedStep(
            cycle(
                currentStep = step(outcome = AdaptiveSupportStepOutcome.Completed),
            ),
            AdaptiveSupportCycleStatus.Failed,
        )
        assertRejected(AdaptiveSupportCycleTransitionRejection.InvalidTerminalOutcome, result)
    }

    private fun cycle(
        consumedDurationMillis:
            Long = 0L,
        currentStep:
            AdaptiveSupportCycleStep? =
            null,
        alternativeRequestCount:
            Int = 0,
        status:
            AdaptiveSupportCycleStatus =
            AdaptiveSupportCycleStatus.Active,
    ): AdaptiveSupportCycle {
        return AdaptiveSupportCycle(
            cycleId =
                "cycle-1",
            decisionId =
                "decision-1",
            protectionIncidentToken =
                "incident-1",
            initialDurationMillis =
                90_000L,
            consumedDurationMillis =
                consumedDurationMillis,
            currentStep =
                currentStep,
            consecutiveGameAssignments =
                0,
            alternativeRequestCount =
                alternativeRequestCount,
            transitionReason =
                AdaptiveSupportCycleTransitionReason
                    .Created,
            status =
                status,
        )
    }

    private fun step(
        sequence:
            Int = 1,
        startedAtCycleConsumedDurationMillis:
            Long = 0L,
        allottedDurationMillis:
            Long = 90_000L,
        consumedDurationMillis:
            Long = 0L,
        outcome:
            AdaptiveSupportStepOutcome =
            AdaptiveSupportStepOutcome
                .InProgress,
    ): AdaptiveSupportCycleStep {
        return AdaptiveSupportCycleStep(
            sequence =
                sequence,
            intervention =
                InterventionFamily.PivotGame,
            gameType =
                ScoreGameType.ReflexOverride,
            startedAtCycleConsumedDurationMillis =
                startedAtCycleConsumedDurationMillis,
            allottedDurationMillis =
                allottedDurationMillis,
            consumedDurationMillis =
                consumedDurationMillis,
            outcome =
                outcome,
        )
    }

    private fun AdaptiveSupportCycleTransitionResult
        .appliedCycle():
        AdaptiveSupportCycle {
        assertTrue(
            this is
                AdaptiveSupportCycleTransitionResult
                    .Applied,
        )

        return (
            this as
                AdaptiveSupportCycleTransitionResult
                    .Applied
            )
            .cycle
    }

    private fun assertRejected(
        expected:
            AdaptiveSupportCycleTransitionRejection,
        actual:
            AdaptiveSupportCycleTransitionResult,
    ) {
        assertTrue(
            actual is
                AdaptiveSupportCycleTransitionResult
                    .Rejected,
        )

        assertEquals(
            expected,
            (
                actual as
                    AdaptiveSupportCycleTransitionResult
                        .Rejected
                )
                .reason,
        )
    }
}
