package com.impulsive.app.backend.domain.engine.adaptive

import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycle
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycleStatus
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycleStep
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycleTransitionReason
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportStepOutcome
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.score.ScoreGameType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class AdaptiveSupportCycleTimePolicyTest {
    @Test
    fun fortySecondsConsumedLeavesFiftySecondsForNextGame() {
        val afterFirstGame =
            AdaptiveSupportCycleTimePolicy
                .recordElapsedDuration(
                    cycle =
                        activeCycle(),
                    elapsedDurationMillis =
                        40_000L,
                )

        assertEquals(
            40_000L,
            afterFirstGame
                .consumedDurationMillis,
        )

        assertEquals(
            40_000L,
            afterFirstGame
                .currentStep
                ?.consumedDurationMillis,
        )

        assertEquals(
            50_000L,
            afterFirstGame
                .remainingDurationMillis,
        )
    }

    @Test
    fun stepBudgetStopsElapsedConsumptionBeforeCycleBudget() {
        val result =
            AdaptiveSupportCycleTimePolicy
                .recordElapsedDuration(
                    cycle =
                        activeCycle(
                            stepAllottedDurationMillis =
                                30_000L,
                        ),
                    elapsedDurationMillis =
                        40_000L,
                )

        assertEquals(
            30_000L,
            result
                .consumedDurationMillis,
        )

        assertEquals(
            60_000L,
            result
                .remainingDurationMillis,
        )

        assertEquals(
            30_000L,
            result
                .currentStep
                ?.consumedDurationMillis,
        )

        assertEquals(
            AdaptiveSupportStepOutcome
                .TimedOut,
            result
                .currentStep
                ?.outcome,
        )

        assertEquals(
            AdaptiveSupportCycleStatus
                .Active,
            result.status,
        )

        assertEquals(
            AdaptiveSupportCycleTransitionReason
                .StepBudgetExhausted,
            result.transitionReason,
        )
    }

    @Test
    fun requestedDurationSmallerThanRemainingBudgetIsPreserved() {
        assertEquals(
            30_000L,
            AdaptiveSupportCycleTimePolicy
                .nextStepDurationMillis(
                    cycle =
                        activeCycle(),
                    requestedDurationMillis =
                        30_000L,
                    minimumUsefulDurationMillis =
                        10_000L,
            ),
        )
    }

    @Test
    fun requestedDurationBelowMinimumUsefulDurationIsRejected() {
        assertNull(
            AdaptiveSupportCycleTimePolicy
                .nextStepDurationMillis(
                    cycle =
                        activeCycle(),
                    requestedDurationMillis =
                        5_000L,
                    minimumUsefulDurationMillis =
                        10_000L,
                ),
        )
    }

    @Test
    fun elapsedDurationSaturatesAtInitialBudget() {
        val result =
            AdaptiveSupportCycleTimePolicy
                .recordElapsedDuration(
                    cycle =
                        activeCycle(
                            consumedDurationMillis =
                                80_000L,
                            stepConsumedDurationMillis =
                                80_000L,
                        ),
                    elapsedDurationMillis =
                        40_000L,
                )

        assertEquals(
            90_000L,
            result
                .consumedDurationMillis,
        )

        assertEquals(
            0L,
            result
                .remainingDurationMillis,
        )

        assertEquals(
            90_000L,
            result
                .currentStep
                ?.consumedDurationMillis,
        )

        assertEquals(
            AdaptiveSupportCycleStatus
                .TimedOut,
            result.status,
        )

        assertEquals(
            AdaptiveSupportCycleTransitionReason
                .BudgetExhausted,
            result.transitionReason,
        )

        assertEquals(
            AdaptiveSupportStepOutcome
                .TimedOut,
            result
                .currentStep
                ?.outcome,
        )
    }

    @Test
    fun insufficientRemainingDurationPreventsAnotherStep() {
        val cycle =
            activeCycle(
                consumedDurationMillis =
                    85_000L,
                stepConsumedDurationMillis =
                    85_000L,
            )

        assertNull(
            AdaptiveSupportCycleTimePolicy
                .nextStepDurationMillis(
                    cycle =
                        cycle,
                    requestedDurationMillis =
                        90_000L,
                    minimumUsefulDurationMillis =
                        10_000L,
                ),
        )
    }

    @Test
    fun terminalCycleDoesNotConsumeAdditionalDuration() {
        val cycle =
            AdaptiveSupportCycle(
                cycleId =
                    "cycle-terminal",
                decisionId =
                    "decision-terminal",
                protectionIncidentToken =
                    "incident-terminal",
                initialDurationMillis =
                    90_000L,
                consumedDurationMillis =
                    40_000L,
                currentStep =
                    activeStep(
                        consumedDurationMillis =
                            40_000L,
                        outcome =
                            AdaptiveSupportStepOutcome
                                .Completed,
                    ),
                consecutiveGameAssignments =
                    1,
                transitionReason =
                    AdaptiveSupportCycleTransitionReason
                        .InterventionCompleted,
                status =
                    AdaptiveSupportCycleStatus
                        .Completed,
            )

        assertEquals(
            cycle,
            AdaptiveSupportCycleTimePolicy
                .recordElapsedDuration(
                    cycle =
                        cycle,
                    elapsedDurationMillis =
                        10_000L,
                ),
        )
    }

    @Test
    fun zeroElapsedDurationLeavesCycleUnchanged() {
        val cycle =
            activeCycle()

        assertEquals(
            cycle,
            AdaptiveSupportCycleTimePolicy
                .recordElapsedDuration(
                    cycle =
                        cycle,
                    elapsedDurationMillis =
                        0L,
                ),
        )
    }

    @Test
    fun elapsedRealtimeDeltaIsDeterministic() {
        assertEquals(
            40_000L,
            AdaptiveSupportCycleTimePolicy
                .elapsedDurationMillis(
                    startedAtElapsedRealtimeMillis =
                        100_000L,
                    endedAtElapsedRealtimeMillis =
                        140_000L,
                ),
        )
    }

    @Test
    fun elapsedRealtimeRegressionIsRejected() {
        assertThrows(
            IllegalArgumentException::class.java,
        ) {
            AdaptiveSupportCycleTimePolicy
                .elapsedDurationMillis(
                    startedAtElapsedRealtimeMillis =
                        140_000L,
                    endedAtElapsedRealtimeMillis =
                        100_000L,
                )
        }
    }

    @Test
    fun negativeElapsedDurationIsRejected() {
        assertThrows(
            IllegalArgumentException::class.java,
        ) {
            AdaptiveSupportCycleTimePolicy
                .recordElapsedDuration(
                    cycle =
                        activeCycle(),
                    elapsedDurationMillis =
                        -1L,
                )
        }
    }

    private fun activeCycle(
        consumedDurationMillis:
            Long = 0L,
        stepAllottedDurationMillis:
            Long = 90_000L,
        stepConsumedDurationMillis:
            Long = consumedDurationMillis,
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
                activeStep(
                    allottedDurationMillis =
                        stepAllottedDurationMillis,
                    consumedDurationMillis =
                        stepConsumedDurationMillis,
                ),
            consecutiveGameAssignments =
                1,
            transitionReason =
                AdaptiveSupportCycleTransitionReason
                    .InterventionStarted,
            status =
                AdaptiveSupportCycleStatus
                    .Active,
        )
    }

    private fun activeStep(
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
                1,
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
}
