package com.impulsive.app.backend.session.adaptive

import com.impulsive.app.backend.domain.game.RecoveryGameLaunchContext
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycle
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycleStep
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportStepOutcome
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.score.ScoreGameType
import com.impulsive.app.backend.domain.repository.adaptive.PersistedAdaptiveSupportCycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveSupportCycleResumePolicyTest {
    @Test
    fun adaptiveMomentTargetConvertsToOwningDecisionRoute() {
        val request = AdaptiveSupportCycleResumeTarget.AdaptiveMoment(
            decisionId = "old-decision",
        ).toRouteRequest()

        assertEquals(AdaptiveRouteKind.AdaptiveMoment, request.kind)

        assertEquals("old-decision", request.decisionId)

        assertEquals(null, request.gameLaunchContext)
    }

    @Test
    fun gameResumeRoutePreservesSupportCycleLaunch() {
        val launch = RecoveryGameLaunchContext.SupportCycle(
            cycleId = "old-cycle",
            decisionId = "old-decision",
            gameType = ScoreGameType.ReflexOverride,
            maxDurationMillis = 40_000L,
        )

        val request = AdaptiveSupportCycleResumeTarget.Route(
            AdaptiveMomentRoutingPolicy.forSupportCycleGame(launch),
        ).toRouteRequest()

        assertEquals(AdaptiveRouteKind.Game, request.kind)

        assertEquals("old-decision", request.decisionId)

        assertEquals(launch, request.gameLaunchContext)

        assertTrue(request.gameLaunchContext is RecoveryGameLaunchContext.SupportCycle)
    }

    @Test
    fun cycleWithoutCurrentStepReturnsToOwningAdaptiveMoment() {
        val target = AdaptiveSupportCycleResumePolicy.target(
            state(
                step = null,
                consumedDurationMillis = 0L,
            ),
        )

        assertEquals(
            AdaptiveSupportCycleResumeTarget.AdaptiveMoment(
                decisionId = "old-decision",
            ),
            target,
        )
    }

    @Test
    fun inProgressGameResumesRemainingStepBudget() {
        val target = AdaptiveSupportCycleResumePolicy.target(
            state(
                step = gameStep(
                    outcome = AdaptiveSupportStepOutcome.InProgress,
                    consumedDurationMillis = 20_000L,
                    allottedDurationMillis = 60_000L,
                ),
                consumedDurationMillis = 20_000L,
            ),
        ) as AdaptiveSupportCycleResumeTarget.Route

        assertEquals(AdaptiveRouteKind.Game, target.request.kind)

        assertEquals("old-decision", target.request.decisionId)

        val launch = target.request.gameLaunchContext as
            RecoveryGameLaunchContext.SupportCycle

        assertEquals("old-cycle", launch.cycleId)

        assertEquals(ScoreGameType.ReflexOverride, launch.gameType)

        assertEquals(40_000L, launch.maxDurationMillis)
    }

    @Test
    fun terminalGameResumesResultUsingRemainingCycleBudget() {
        val target = AdaptiveSupportCycleResumePolicy.target(
            state(
                step = gameStep(
                    outcome = AdaptiveSupportStepOutcome.Completed,
                    consumedDurationMillis = 20_000L,
                    allottedDurationMillis = 60_000L,
                ),
                consumedDurationMillis = 20_000L,
            ),
        ) as AdaptiveSupportCycleResumeTarget.Route

        val launch = target.request.gameLaunchContext as
            RecoveryGameLaunchContext.SupportCycle

        assertEquals("old-decision", launch.decisionId)

        /*
         * Remaining cycle budget is 70 seconds even though the terminal step
         * itself has only 40 seconds of unused allocation.
         */
        assertEquals(70_000L, launch.maxDurationMillis)
    }

    @Test
    fun inProgressReadingResumesExistingReadingDecision() {
        val target = AdaptiveSupportCycleResumePolicy.target(
            state(
                step = AdaptiveSupportCycleStep(
                    sequence = 1,
                    intervention = InterventionFamily.PivotReading,
                    startedAtCycleConsumedDurationMillis = 0L,
                    allottedDurationMillis = 60_000L,
                    consumedDurationMillis = 10_000L,
                    outcome = AdaptiveSupportStepOutcome.InProgress,
                ),
                consumedDurationMillis = 10_000L,
            ),
        ) as AdaptiveSupportCycleResumeTarget.Route

        assertEquals(AdaptiveRouteKind.Reading, target.request.kind)

        assertEquals("old-decision", target.request.decisionId)
    }

    @Test
    fun terminalReadingReturnsToOwningAdaptiveMoment() {
        val target = AdaptiveSupportCycleResumePolicy.target(
            state(
                step = AdaptiveSupportCycleStep(
                    sequence = 1,
                    intervention = InterventionFamily.PivotReading,
                    startedAtCycleConsumedDurationMillis = 0L,
                    allottedDurationMillis = 60_000L,
                    consumedDurationMillis = 10_000L,
                    outcome = AdaptiveSupportStepOutcome.Completed,
                ),
                consumedDurationMillis = 10_000L,
            ),
        )

        assertEquals(
            AdaptiveSupportCycleResumeTarget.AdaptiveMoment(
                decisionId = "old-decision",
            ),
            target,
        )
    }

    @Test
    fun shortPauseReturnsToOwningAdaptiveMoment() {
        val target = AdaptiveSupportCycleResumePolicy.target(
            state(
                step = AdaptiveSupportCycleStep(
                    sequence = 1,
                    intervention = InterventionFamily.ShortPause,
                    startedAtCycleConsumedDurationMillis = 0L,
                    allottedDurationMillis = 30_000L,
                    consumedDurationMillis = 5_000L,
                    outcome = AdaptiveSupportStepOutcome.InProgress,
                ),
                consumedDurationMillis = 5_000L,
            ),
        )

        assertTrue(target is AdaptiveSupportCycleResumeTarget.AdaptiveMoment)

        assertEquals(
            "old-decision",
            (target as AdaptiveSupportCycleResumeTarget.AdaptiveMoment).decisionId,
        )
    }

    @Test
    fun terminalReadingDoesNotBlockStartingNextGame() {
        val persisted = state(
            step = AdaptiveSupportCycleStep(
                sequence = 1,
                intervention = InterventionFamily.PivotReading,
                startedAtCycleConsumedDurationMillis = 0L,
                allottedDurationMillis = 60_000L,
                consumedDurationMillis = 10_000L,
                outcome = AdaptiveSupportStepOutcome.Completed,
            ),
            consumedDurationMillis = 10_000L,
        )

        assertEquals(
            false,
            AdaptiveSupportCycleResumePolicy.requiresResumeBeforeStartingGame(persisted),
        )
    }

    @Test
    fun terminalMomentPlanDoesNotBlockStartingNextGame() {
        val persisted = state(
            step = AdaptiveSupportCycleStep(
                sequence = 1,
                intervention = InterventionFamily.MomentPlan,
                startedAtCycleConsumedDurationMillis = 0L,
                allottedDurationMillis = 60_000L,
                consumedDurationMillis = 10_000L,
                outcome = AdaptiveSupportStepOutcome.Completed,
            ),
            consumedDurationMillis = 10_000L,
        )

        assertEquals(
            false,
            AdaptiveSupportCycleResumePolicy.requiresResumeBeforeStartingGame(persisted),
        )
    }

    @Test
    fun inProgressReadingMustResumeBeforeStartingGame() {
        val persisted = state(
            step = AdaptiveSupportCycleStep(
                sequence = 1,
                intervention = InterventionFamily.PivotReading,
                startedAtCycleConsumedDurationMillis = 0L,
                allottedDurationMillis = 60_000L,
                consumedDurationMillis = 10_000L,
                outcome = AdaptiveSupportStepOutcome.InProgress,
            ),
            consumedDurationMillis = 10_000L,
        )

        assertEquals(
            true,
            AdaptiveSupportCycleResumePolicy.requiresResumeBeforeStartingGame(persisted),
        )
    }

    @Test
    fun completedGameMustRestoreResultBeforeStartingAnotherGame() {
        val persisted = state(
            step = gameStep(
                outcome = AdaptiveSupportStepOutcome.Completed,
                consumedDurationMillis = 20_000L,
                allottedDurationMillis = 60_000L,
            ),
            consumedDurationMillis = 20_000L,
        )

        assertEquals(
            true,
            AdaptiveSupportCycleResumePolicy.requiresResumeBeforeStartingGame(persisted),
        )
    }

    @Test
    fun cancelledGameDoesNotAttemptResultRestoration() {
        val persisted = state(
            step = gameStep(
                outcome = AdaptiveSupportStepOutcome.Cancelled,
                consumedDurationMillis = 20_000L,
                allottedDurationMillis = 60_000L,
            ),
            consumedDurationMillis = 20_000L,
        )

        assertEquals(
            false,
            AdaptiveSupportCycleResumePolicy.requiresResumeBeforeStartingGame(persisted),
        )

        assertEquals(
            AdaptiveSupportCycleResumeTarget.AdaptiveMoment(
                decisionId = "old-decision",
            ),
            AdaptiveSupportCycleResumePolicy.target(persisted),
        )
    }

    @Test
    fun missingStepDoesNotBlockStartingGame() {
        val persisted = state(
            step = null,
            consumedDurationMillis = 0L,
        )

        assertEquals(
            false,
            AdaptiveSupportCycleResumePolicy.requiresResumeBeforeStartingGame(persisted),
        )
    }

    private fun gameStep(
        outcome: AdaptiveSupportStepOutcome,
        consumedDurationMillis: Long,
        allottedDurationMillis: Long,
    ): AdaptiveSupportCycleStep = AdaptiveSupportCycleStep(
        sequence = 1,
        intervention = InterventionFamily.PivotGame,
        gameType = ScoreGameType.ReflexOverride,
        startedAtCycleConsumedDurationMillis = 0L,
        allottedDurationMillis = allottedDurationMillis,
        consumedDurationMillis = consumedDurationMillis,
        outcome = outcome,
    )

    private fun state(
        step: AdaptiveSupportCycleStep?,
        consumedDurationMillis: Long,
    ): PersistedAdaptiveSupportCycle = PersistedAdaptiveSupportCycle(
        cycle = AdaptiveSupportCycle(
            cycleId = "old-cycle",
            decisionId = "old-decision",
            protectionIncidentToken = "old-incident",
            initialDurationMillis = 90_000L,
            consumedDurationMillis = consumedDurationMillis,
            currentStep = step,
        ),
        createdAtEpochMillis = 100L,
        updatedAtEpochMillis = 200L,
        expiresAtEpochMillis = 100_000L,
        revision = 3L,
    )
}
