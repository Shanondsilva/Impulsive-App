package com.impulsive.app.backend.domain.engine.adaptive

import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycle
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCyclePhase
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycleTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase boundaries derived from the Support Cycle's remaining time.
 *
 * Boundary values are pinned exactly because an off-by-one at a threshold would
 * silently move where gameplay gives way to settling or to the Moment Plan.
 */
class AdaptiveSupportCyclePhasePolicyTest {
    @Test
    fun fullBudgetIsDenseGameplay() {
        assertEquals(
            AdaptiveSupportCyclePhase.DenseGameplay,
            AdaptiveSupportCyclePhasePolicy.resolve(90_000L),
        )
    }

    @Test
    fun oneMillisecondAboveTheSettlingThresholdIsStillDenseGameplay() {
        assertEquals(
            AdaptiveSupportCyclePhase.DenseGameplay,
            AdaptiveSupportCyclePhasePolicy.resolve(45_001L),
        )
    }

    @Test
    fun theSettlingThresholdItselfStartsSettlingAtZeroProgress() {
        val phase = AdaptiveSupportCyclePhasePolicy.resolve(45_000L)

        assertEquals(AdaptiveSupportCyclePhase.SettlingGameplay(0.0), phase)
        assertEquals(0.0, (phase as AdaptiveSupportCyclePhase.SettlingGameplay).progress, 0.0)
    }

    @Test
    fun settlingProgressAdvancesOnceInsideTheWindow() {
        val phase = AdaptiveSupportCyclePhasePolicy.resolve(44_000L)
            as AdaptiveSupportCyclePhase.SettlingGameplay

        assertTrue(phase.progress > 0.0)
        assertTrue(phase.progress < 1.0)
    }

    @Test
    fun theMiddleOfTheWindowIsSettling() {
        val phase = AdaptiveSupportCyclePhasePolicy.resolve(30_000L)
            as AdaptiveSupportCyclePhase.SettlingGameplay

        // 15s of the 25s settling window elapsed.
        assertEquals(0.6, phase.progress, 1e-9)
    }

    @Test
    fun oneMillisecondAboveTheMomentPlanThresholdIsStillSettling() {
        val phase = AdaptiveSupportCyclePhasePolicy.resolve(20_001L)
            as AdaptiveSupportCyclePhase.SettlingGameplay

        assertTrue(phase.progress < 1.0)
        assertTrue(phase.progress > 0.999)
    }

    @Test
    fun theMomentPlanThresholdItselfSwitchesToMomentPlan() {
        assertEquals(
            AdaptiveSupportCyclePhase.MomentPlan,
            AdaptiveSupportCyclePhasePolicy.resolve(20_000L),
        )
    }

    @Test
    fun oneRemainingMillisecondIsStillMomentPlan() {
        assertEquals(
            AdaptiveSupportCyclePhase.MomentPlan,
            AdaptiveSupportCyclePhasePolicy.resolve(1L),
        )
    }

    @Test
    fun anExhaustedBudgetIsComplete() {
        assertEquals(
            AdaptiveSupportCyclePhase.Complete,
            AdaptiveSupportCyclePhasePolicy.resolve(0L),
        )
    }

    @Test
    fun negativeRemainingTimeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AdaptiveSupportCyclePhasePolicy.resolve(-1L)
        }
    }

    @Test
    fun remainingTimeBeyondTheFixedBudgetIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AdaptiveSupportCyclePhasePolicy.resolve(90_001L)
        }
    }

    @Test
    fun settlingProgressNeverDecreasesAsTimeRunsDown() {
        var previous = -1.0
        var remaining = AdaptiveSupportCycleTiming.SettlingStartsAtRemainingMillis
        while (remaining > AdaptiveSupportCycleTiming.MomentPlanStartsAtRemainingMillis) {
            val progress = (
                AdaptiveSupportCyclePhasePolicy.resolve(remaining)
                    as AdaptiveSupportCyclePhase.SettlingGameplay
                ).progress
            assertTrue("progress regressed at $remaining", progress >= previous)
            previous = progress
            remaining -= 250L
        }
    }

    /** The persisted cycle is the authority; no separate clock is consulted. */
    @Test
    fun phaseResolvesFromTheCyclesOwnRemainingTime() {
        val cycle = AdaptiveSupportCycle(
            cycleId = "cycle-1",
            decisionId = "decision-1",
            protectionIncidentToken = "incident-1",
            initialDurationMillis = AdaptiveSupportCycleTiming.TotalDurationMillis,
            consumedDurationMillis = 70_000L,
        )

        assertEquals(20_000L, cycle.remainingDurationMillis)
        assertEquals(
            AdaptiveSupportCyclePhase.MomentPlan,
            AdaptiveSupportCyclePhasePolicy.resolve(cycle),
        )
    }

    @Test
    fun settlingProgressOutsideZeroToOneIsRejectedByTheModel() {
        assertThrows(IllegalArgumentException::class.java) {
            AdaptiveSupportCyclePhase.SettlingGameplay(1.5)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AdaptiveSupportCyclePhase.SettlingGameplay(-0.1)
        }
    }
}
