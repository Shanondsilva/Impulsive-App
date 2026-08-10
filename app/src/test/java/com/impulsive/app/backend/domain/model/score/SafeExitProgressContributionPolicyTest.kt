package com.impulsive.app.backend.domain.model.score

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class SafeExitProgressContributionPolicyTest {
    @Test
    fun pivotGameCountsOneSafeExitButAddsNoSecondControlPointBonus() {
        val contribution =
            SafeExitProgressContributionPolicy
                .contribution(
                    record(
                        source =
                            SafeExitSource
                                .PivotGame,
                        sourceId =
                            "REFLEX_OVERRIDE:9001",
                    ),
                )

        assertEquals(
            1,
            contribution
                .safeExitCount,
        )

        assertEquals(
            0,
            contribution
                .additionalControlPoints,
        )
    }

    @Test
    fun resetReadingCountsOneSafeExitAndAddsTheExistingBonus() {
        val contribution =
            SafeExitProgressContributionPolicy
                .contribution(
                    record(
                        source =
                            SafeExitSource
                                .ResetReading,
                        sourceId =
                            "9002",
                    ),
                )

        assertEquals(
            1,
            contribution
                .safeExitCount,
        )

        assertEquals(
            SAFE_EXIT_CONTROL_POINT_BONUS,
            contribution
                .additionalControlPoints,
        )
    }

    @Test
    fun momentPlanCountsOneSafeExitAndAddsTheExistingBonus() {
        val contribution =
            SafeExitProgressContributionPolicy
                .contribution(
                    record(
                        source =
                            SafeExitSource
                                .MomentPlan,
                        sourceId =
                            "decision-9003",
                    ),
                )

        assertEquals(
            1,
            contribution
                .safeExitCount,
        )

        assertEquals(
            SAFE_EXIT_CONTROL_POINT_BONUS,
            contribution
                .additionalControlPoints,
        )
    }

    private fun record(
        source:
            SafeExitSource,
        sourceId:
            String,
    ): SafeExitRecord {
        return SafeExitRecord(
            sourceKey =
                "${source.storageValue}:$sourceId",
            source =
                source,
            sourceId =
                sourceId,
            completedAt =
                LocalDateTime.of(
                    2026,
                    8,
                    3,
                    10,
                    0,
                ),
        )
    }
}