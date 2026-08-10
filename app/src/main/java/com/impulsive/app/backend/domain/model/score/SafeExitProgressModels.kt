package com.impulsive.app.backend.domain.model.score

import java.time.LocalDateTime

data class SafeExitTimelineItem(
    val sourceKey:
        String,
    val source:
        SafeExitSource,
    val completedAt:
        LocalDateTime,
    val additionalControlPoints:
        Int,
)

data class SafeExitProgressSnapshot(
    val ledgerSafeExitCount:
        Int = 0,
    val additionalControlPoints:
        Int = 0,
    val persistedPivotSourceKeys:
        Set<String> =
        emptySet(),
    val recentSafeExits:
        List<SafeExitTimelineItem> =
        emptyList(),
)

data class SafeExitProgressRange(
    val startInclusive:
        LocalDateTime,
    val endExclusive:
        LocalDateTime,
)

object SafeExitProgressRangePolicy {
    fun range(
        selectedRange:
            ScoreRange,
        now:
            LocalDateTime,
    ): SafeExitProgressRange {
        return when (
            selectedRange
        ) {
            ScoreRange.Today -> {
                val start =
                    now
                        .toLocalDate()
                        .atStartOfDay()

                SafeExitProgressRange(
                    startInclusive =
                        start,
                    endExclusive =
                        start.plusDays(
                            1,
                        ),
                )
            }

            ScoreRange.Week ->
                SafeExitProgressRange(
                    /*
                     * ScoreModels currently treats durations whose toDays()
                     * value is 0 through 6 as the Week range. This boundary
                     * is the equivalent open seven-day boundary represented
                     * as an inclusive value for SQL.
                     */
                    startInclusive =
                        now
                            .minusDays(
                                7,
                            )
                            .plusNanos(
                                1,
                            ),
                    endExclusive =
                        now.plusNanos(
                            1,
                        ),
                )

            ScoreRange.Month -> {
                val start =
                    now
                        .toLocalDate()
                        .withDayOfMonth(
                            1,
                        )
                        .atStartOfDay()

                SafeExitProgressRange(
                    startInclusive =
                        start,
                    endExclusive =
                        start.plusMonths(
                            1,
                        ),
                )
            }

            ScoreRange.Year -> {
                val start =
                    now
                        .toLocalDate()
                        .withDayOfYear(
                            1,
                        )
                        .atStartOfDay()

                SafeExitProgressRange(
                    startInclusive =
                        start,
                    endExclusive =
                        start.plusYears(
                            1,
                        ),
                )
            }
        }
    }
}