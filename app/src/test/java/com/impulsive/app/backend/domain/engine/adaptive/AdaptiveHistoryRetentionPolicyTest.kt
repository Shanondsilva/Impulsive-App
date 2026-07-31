package com.impulsive.app.backend.domain.engine.adaptive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdaptiveHistoryRetentionPolicyTest {
    @Test
    fun defaultPreferencePolicyIsSixMonths() {
        assertEquals(
            AdaptiveHistoryRetentionPolicy.SixMonths,
            com.impulsive.app.backend.domain.model.adaptive
                .AdaptivePreferences()
                .historyRetentionPolicy,
        )
    }

    @Test
    fun ninetyDaysMapsToCorrectCutoff() {
        assertEquals(
            Now - days(90),
            AdaptiveHistoryRetentionPolicy.NinetyDays.cutoffMillis(Now),
        )
    }

    @Test
    fun sixMonthsMapsToConservativeDayCutoff() {
        assertEquals(
            Now - days(183),
            AdaptiveHistoryRetentionPolicy.SixMonths.cutoffMillis(Now),
        )
    }

    @Test
    fun oneYearMapsToCorrectCutoff() {
        assertEquals(
            Now - days(365),
            AdaptiveHistoryRetentionPolicy.OneYear.cutoffMillis(Now),
        )
    }

    @Test
    fun keepUntilResetHasNoAgeCutoff() {
        assertNull(
            AdaptiveHistoryRetentionPolicy.KeepUntilReset.cutoffMillis(Now),
        )
    }

    @Test
    fun futureOrInvalidClockFailsSafely() {
        assertNull(
            AdaptiveHistoryRetentionPolicy.SixMonths.cutoffMillis(-1L),
        )
    }

    @Test
    fun earlyEpochClampsWithoutOverflow() {
        assertEquals(
            0L,
            AdaptiveHistoryRetentionPolicy.OneYear.cutoffMillis(1_000L),
        )
    }

    private fun days(value: Long): Long = value * 86_400_000L

    private companion object {
        const val Now = 40_000_000_000L
    }
}
