package com.impulsive.app.backend.domain.model.protection

import com.impulsive.app.backend.domain.model.release.calculateReleasePlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

class ProtectionWindowEvaluatorTest {
    @Test
    fun pausesDuringPlannedReleaseWindow() {
        val now = LocalDateTime.of(2026, 5, 31, 10, 10)
        val releasePlan = calculateReleasePlan(
            selectedDailyUrgeCount = 1,
            now = now,
            activeDayStart = LocalTime.of(7, 0),
            activeDayEnd = LocalTime.of(13, 0),
        )

        val snapshot = ProtectionWindowEvaluator.evaluate(
            now = now,
            releasePlan = releasePlan,
            adjustedNextReleaseWindow = null,
        )

        assertTrue(snapshot.isProtectionPaused)
        assertEquals(LocalDateTime.of(2026, 5, 31, 10, 0), snapshot.pausedWindowStart)
        assertEquals(LocalDateTime.of(2026, 5, 31, 10, 25), snapshot.pausedWindowEnd)
    }

    @Test
    fun pausesDuringAdjustedRewardWindow() {
        val now = LocalDateTime.of(2026, 5, 31, 9, 45)
        val basePlan = calculateReleasePlan(
            selectedDailyUrgeCount = 1,
            now = now,
            activeDayStart = LocalTime.of(7, 0),
            activeDayEnd = LocalTime.of(13, 0),
        )
        val adjustedStart = LocalDateTime.of(2026, 5, 31, 9, 40)

        val snapshot = ProtectionWindowEvaluator.evaluate(
            now = now,
            releasePlan = basePlan,
            adjustedNextReleaseWindow = adjustedStart,
        )

        assertTrue(snapshot.isProtectionPaused)
        assertEquals(adjustedStart, snapshot.pausedWindowStart)
    }

    @Test
    fun staysProtectedOutsideWindow() {
        val now = LocalDateTime.of(2026, 5, 31, 8, 0)
        val releasePlan = calculateReleasePlan(
            selectedDailyUrgeCount = 1,
            now = now,
            activeDayStart = LocalTime.of(7, 0),
            activeDayEnd = LocalTime.of(13, 0),
        )

        val snapshot = ProtectionWindowEvaluator.evaluate(
            now = now,
            releasePlan = releasePlan,
            adjustedNextReleaseWindow = null,
        )

        assertFalse(snapshot.isProtectionPaused)
        assertEquals(LocalDateTime.of(2026, 5, 31, 10, 0), snapshot.nextWindowStart)
    }
}
