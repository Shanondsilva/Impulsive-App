package com.impulsive.app.backend.domain.model.score

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class SafeExitProgressRangePolicyTest {
    @Test
    fun todayStartsAtLocalMidnightAndEndsAtNextLocalMidnight() {
        val range =
            SafeExitProgressRangePolicy
                .range(
                    selectedRange = ScoreRange.Today,
                    now = Now,
                )

        assertEquals(
            LocalDateTime.of(2026, 8, 3, 0, 0),
            range.startInclusive,
        )
        assertEquals(
            LocalDateTime.of(2026, 8, 4, 0, 0),
            range.endExclusive,
        )
    }

    @Test
    fun weekStartsSevenDaysBeforeNowPlusOneNanosecondAndEndsAfterNow() {
        val range =
            SafeExitProgressRangePolicy
                .range(
                    selectedRange = ScoreRange.Week,
                    now = Now,
                )

        assertEquals(
            Now.minusDays(7).plusNanos(1),
            range.startInclusive,
        )
        assertEquals(
            Now.plusNanos(1),
            range.endExclusive,
        )
    }

    @Test
    fun monthStartsOnFirstDayAndEndsAtNextMonth() {
        val range =
            SafeExitProgressRangePolicy
                .range(
                    selectedRange = ScoreRange.Month,
                    now = Now,
                )

        assertEquals(
            LocalDateTime.of(2026, 8, 1, 0, 0),
            range.startInclusive,
        )
        assertEquals(
            LocalDateTime.of(2026, 9, 1, 0, 0),
            range.endExclusive,
        )
    }

    @Test
    fun yearStartsOnJanuaryFirstAndEndsNextJanuaryFirst() {
        val range =
            SafeExitProgressRangePolicy
                .range(
                    selectedRange = ScoreRange.Year,
                    now = Now,
                )

        assertEquals(
            LocalDateTime.of(2026, 1, 1, 0, 0),
            range.startInclusive,
        )
        assertEquals(
            LocalDateTime.of(2027, 1, 1, 0, 0),
            range.endExclusive,
        )
    }

    private companion object {
        val Now: LocalDateTime =
            LocalDateTime.of(2026, 8, 3, 14, 15, 16, 17)
    }
}