package com.impulsive.app.backend.domain.model.adaptive

import com.impulsive.app.backend.domain.model.score.ScoreGameType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveSupportCycleModelsTest {
    @Test
    fun activeCycleExposesBoundedRemainingDuration() {
        val cycle =
            cycle(
                initialDurationMillis =
                    90_000L,
                consumedDurationMillis =
                    40_000L,
                currentStep =
                    step(
                        consumedDurationMillis =
                            40_000L,
                    ),
            )

        assertEquals(
            50_000L,
            cycle.remainingDurationMillis,
        )

        assertFalse(
            cycle.isTerminal,
        )
    }

    @Test
    fun currentStepExposesItsOwnRemainingDuration() {
        val step =
            step(
                allottedDurationMillis =
                    30_000L,
                consumedDurationMillis =
                    12_000L,
            )

        assertEquals(
            18_000L,
            step.remainingDurationMillis,
        )
    }

    @Test
    fun pivotGameStepRequiresConcreteGameType() {
        assertThrows(
            IllegalArgumentException::class.java,
        ) {
            AdaptiveSupportCycleStep(
                sequence =
                    1,
                intervention =
                    InterventionFamily.PivotGame,
                gameType =
                    null,
                startedAtCycleConsumedDurationMillis =
                    0L,
                allottedDurationMillis =
                    90_000L,
            )
        }
    }

    @Test
    fun nonGameStepRejectsGameType() {
        assertThrows(
            IllegalArgumentException::class.java,
        ) {
            AdaptiveSupportCycleStep(
                sequence =
                    1,
                intervention =
                    InterventionFamily.PivotReading,
                gameType =
                    ScoreGameType.ReflexOverride,
                startedAtCycleConsumedDurationMillis =
                    0L,
                allottedDurationMillis =
                    90_000L,
            )
        }
    }

    @Test
    fun stepConsumptionCannotExceedItsAllocation() {
        assertThrows(
            IllegalArgumentException::class.java,
        ) {
            step(
                allottedDurationMillis =
                    30_000L,
                consumedDurationMillis =
                    30_001L,
            )
        }
    }

    @Test
    fun fullyConsumedStepCannotRemainInProgress() {
        assertThrows(
            IllegalArgumentException::class.java,
        ) {
            step(
                allottedDurationMillis =
                    30_000L,
                consumedDurationMillis =
                    30_000L,
            )
        }
    }

    @Test
    fun timedOutStepRequiresCompleteStepConsumption() {
        assertThrows(
            IllegalArgumentException::class.java,
        ) {
            step(
                allottedDurationMillis =
                    30_000L,
                consumedDurationMillis =
                    20_000L,
                outcome =
                    AdaptiveSupportStepOutcome
                        .TimedOut,
            )
        }
    }

    @Test
    fun cycleAndStepConsumptionMustDescribeOneTimeline() {
        assertThrows(
            IllegalArgumentException::class.java,
        ) {
            cycle(
                consumedDurationMillis =
                    40_000L,
                currentStep =
                    step(
                        startedAtCycleConsumedDurationMillis =
                            10_000L,
                        consumedDurationMillis =
                            20_000L,
                    ),
            )
        }
    }

    @Test
    fun stepAllocationCannotExceedBudgetRemainingAtItsStart() {
        assertThrows(
            IllegalArgumentException::class.java,
        ) {
            cycle(
                initialDurationMillis =
                    90_000L,
                consumedDurationMillis =
                    40_000L,
                currentStep =
                    step(
                        startedAtCycleConsumedDurationMillis =
                            40_000L,
                        allottedDurationMillis =
                            60_000L,
                        consumedDurationMillis =
                            0L,
                    ),
            )
        }
    }

    @Test
    fun consumedDurationCannotExceedInitialBudget() {
        assertThrows(
            IllegalArgumentException::class.java,
        ) {
            cycle(
                initialDurationMillis =
                    90_000L,
                consumedDurationMillis =
                    90_001L,
                currentStep =
                    null,
            )
        }
    }

    @Test
    fun exhaustedCycleCannotRemainActive() {
        assertThrows(
            IllegalArgumentException::class.java,
        ) {
            cycle(
                initialDurationMillis =
                    90_000L,
                consumedDurationMillis =
                    90_000L,
                currentStep =
                    null,
            )
        }
    }

    @Test
    fun timedOutCycleRequiresExhaustedBudget() {
        assertThrows(
            IllegalArgumentException::class.java,
        ) {
            cycle(
                initialDurationMillis =
                    90_000L,
                consumedDurationMillis =
                    40_000L,
                currentStep =
                    null,
                status =
                    AdaptiveSupportCycleStatus
                        .TimedOut,
            )
        }
    }

    @Test
    fun terminalCycleRejectsInProgressStep() {
        assertThrows(
            IllegalArgumentException::class.java,
        ) {
            cycle(
                currentStep =
                    step(),
                status =
                    AdaptiveSupportCycleStatus
                        .Cancelled,
            )
        }
    }

    @Test
    fun terminalCycleAcceptsTerminalStep() {
        val cycle =
            cycle(
                currentStep =
                    step(
                        outcome =
                            AdaptiveSupportStepOutcome
                                .Cancelled,
                    ),
                status =
                    AdaptiveSupportCycleStatus
                        .Cancelled,
            )

        assertTrue(
            cycle.isTerminal,
        )
    }

    @Test
    fun blankCycleIdentityIsRejected() {
        assertThrows(
            IllegalArgumentException::class.java,
        ) {
            cycle(
                cycleId =
                    " ",
            )
        }
    }

    @Test
    fun nonPositiveInitialBudgetIsRejected() {
        assertThrows(
            IllegalArgumentException::class.java,
        ) {
            cycle(
                initialDurationMillis =
                    0L,
                currentStep =
                    null,
            )
        }
    }

    @Test
    fun theFixedProtectedDurationIsAccepted() {
        val cycle =
            cycle(
                initialDurationMillis =
                    AdaptiveSupportCycleTiming
                        .TotalDurationMillis,
                currentStep =
                    null,
            )

        assertEquals(
            90_000L,
            cycle.initialDurationMillis,
        )
    }

    /**
     * The superseded attempt ladder produced shorter cycles. Those totals are no
     * longer a valid protected cycle, so an obsolete persisted one fails to
     * reconstruct rather than silently continuing.
     */
    @Test
    fun obsoleteShorterAttemptBudgetsAreRejected() {
        listOf(
            60_000L,
            45_000L,
            1L,
            89_999L,
            90_001L,
        ).forEach { obsoleteDuration ->
            assertThrows(
                "duration $obsoleteDuration must not be a valid protected cycle",
                IllegalArgumentException::class.java,
            ) {
                cycle(
                    initialDurationMillis =
                        obsoleteDuration,
                    currentStep =
                        null,
                )
            }
        }
    }

    /**
     * The fixed total constrains the cycle budget only. A step may still take
     * part of the remaining time.
     */
    @Test
    fun aStepMayStillUseLessThanTheWholeCycleBudget() {
        val cycle =
            cycle(
                initialDurationMillis =
                    90_000L,
                consumedDurationMillis =
                    0L,
                currentStep =
                    step(
                        allottedDurationMillis =
                            30_000L,
                    ),
            )

        assertEquals(
            30_000L,
            cycle.currentStep
                ?.allottedDurationMillis,
        )
    }

    @Test
    fun alternativeRequestCountDefaultsToZero() {
        val cycle =
            AdaptiveSupportCycle(
                cycleId =
                    "cycle-1",
                decisionId =
                    "decision-1",
                protectionIncidentToken =
                    "incident-1",
                initialDurationMillis =
                    90_000L,
            )

        assertEquals(
            0,
            cycle.alternativeRequestCount,
        )
    }

    @Test
    fun activeCycleAcceptsNoOrOneAlternativeRequest() {
        assertEquals(
            0,
            cycle(
                alternativeRequestCount =
                    0,
            ).alternativeRequestCount,
        )

        assertEquals(
            1,
            cycle(
                alternativeRequestCount =
                    1,
            ).alternativeRequestCount,
        )
    }

    @Test
    fun terminalCycleAcceptsSecondAlternativeRequest() {
        val cycle =
            cycle(
                alternativeRequestCount =
                    2,
                currentStep =
                    step(
                        outcome =
                            AdaptiveSupportStepOutcome
                                .Abandoned,
                    ),
                status =
                    AdaptiveSupportCycleStatus
                        .Abandoned,
            )

        assertEquals(
            2,
            cycle.alternativeRequestCount,
        )

        assertTrue(
            cycle.isTerminal,
        )
    }

    @Test
    fun activeCycleCannotHoldASecondAlternativeRequest() {
        assertThrows(
            IllegalArgumentException::class.java,
        ) {
            cycle(
                alternativeRequestCount =
                    2,
            )
        }
    }

    @Test
    fun negativeAlternativeRequestCountIsRejected() {
        assertThrows(
            IllegalArgumentException::class.java,
        ) {
            cycle(
                alternativeRequestCount =
                    -1,
            )
        }
    }

    @Test
    fun alternativeRequestCountAboveTwoIsRejected() {
        assertThrows(
            IllegalArgumentException::class.java,
        ) {
            cycle(
                alternativeRequestCount =
                    3,
                currentStep =
                    step(
                        outcome =
                            AdaptiveSupportStepOutcome
                                .Abandoned,
                    ),
                status =
                    AdaptiveSupportCycleStatus
                        .Abandoned,
            )
        }
    }

    private fun cycle(
        cycleId:
            String = "cycle-1",
        alternativeRequestCount:
            Int = 0,
        initialDurationMillis:
            Long = 90_000L,
        consumedDurationMillis:
            Long = 0L,
        currentStep:
            AdaptiveSupportCycleStep? =
            step(),
        status:
            AdaptiveSupportCycleStatus =
            AdaptiveSupportCycleStatus.Active,
    ): AdaptiveSupportCycle {
        return AdaptiveSupportCycle(
            cycleId =
                cycleId,
            decisionId =
                "decision-1",
            protectionIncidentToken =
                "incident-1",
            initialDurationMillis =
                initialDurationMillis,
            consumedDurationMillis =
                consumedDurationMillis,
            currentStep =
                currentStep,
            consecutiveGameAssignments =
                1,
            alternativeRequestCount =
                alternativeRequestCount,
            transitionReason =
                AdaptiveSupportCycleTransitionReason
                    .InterventionStarted,
            status =
                status,
        )
    }

    private fun step(
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
