package com.impulsive.app.backend.domain.model.adaptive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the fixed protected-cycle timing contract.
 *
 * These are product decisions, not implementation details: every protected
 * Support Cycle runs the same total duration, and the phase thresholds divide
 * that one budget.
 */
class AdaptiveSupportCycleTimingTest {
    @Test
    fun everyProtectedCycleUsesTheSameFixedTotalDuration() {
        assertEquals(90_000L, AdaptiveSupportCycleTiming.TotalDurationMillis)
    }

    @Test
    fun settlingBeginsAtFortyFiveSecondsRemaining() {
        assertEquals(45_000L, AdaptiveSupportCycleTiming.SettlingStartsAtRemainingMillis)
    }

    @Test
    fun momentPlanBeginsAtTwentySecondsRemaining() {
        assertEquals(20_000L, AdaptiveSupportCycleTiming.MomentPlanStartsAtRemainingMillis)
    }

    @Test
    fun thresholdsAreStrictlyOrderedInsideTheBudget() {
        assertTrue(
            AdaptiveSupportCycleTiming.TotalDurationMillis >
                AdaptiveSupportCycleTiming.SettlingStartsAtRemainingMillis,
        )
        assertTrue(
            AdaptiveSupportCycleTiming.SettlingStartsAtRemainingMillis >
                AdaptiveSupportCycleTiming.MomentPlanStartsAtRemainingMillis,
        )
        assertTrue(AdaptiveSupportCycleTiming.MomentPlanStartsAtRemainingMillis > 0L)
    }
}
