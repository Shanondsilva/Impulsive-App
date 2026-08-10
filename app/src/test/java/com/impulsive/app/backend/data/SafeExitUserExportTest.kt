package com.impulsive.app.backend.data

import com.impulsive.app.backend.data.local.entity.SafeExitEntity
import com.impulsive.app.backend.domain.model.score.SafeExitRecord
import com.impulsive.app.backend.domain.model.score.SafeExitSource
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeExitUserExportTest {
    @Test
    fun canonicalRowsAreReturnedNewestFirstWithStableTieBreak() {
        val completedAt =
            LocalDateTime.of(
                2026,
                8,
                3,
                6,
                0,
            )

        val laterKey =
            entity(
                sourceKey =
                    "reset_reading:z",
                source =
                    "reset_reading",
                sourceId =
                    "z",
                completedAt =
                    completedAt.toString(),
            )

        val earlierKey =
            entity(
                sourceKey =
                    "reset_reading:a",
                source =
                    "reset_reading",
                sourceId =
                    "a",
                completedAt =
                    completedAt.toString(),
            )

        val newer =
            entity(
                sourceKey =
                    "moment_plan:decision-new",
                source =
                    "moment_plan",
                sourceId =
                    "decision-new",
                completedAt =
                    completedAt
                        .plusMinutes(
                            1,
                        )
                        .toString(),
            )

        assertEquals(
            listOf(
                "moment_plan:decision-new",
                "reset_reading:a",
                "reset_reading:z",
            ),
            SafeExitUserExport
                .canonicalRecords(
                    listOf(
                        laterKey,
                        newer,
                        earlierKey,
                    ),
                )
                .map {
                    it.sourceKey
                },
        )
    }

    @Test
    fun malformedPersistedRowsAreOmitted() {
        val malformed =
            listOf(
                entity(
                    sourceKey =
                        "unknown:1",
                    source =
                        "unknown",
                    sourceId =
                        "1",
                    completedAt =
                        "2026-08-03T06:10",
                ),
                entity(
                    sourceKey =
                        "reset_reading:2",
                    source =
                        "reset_reading",
                    sourceId =
                        "2",
                    completedAt =
                        "not-a-date",
                ),
                entity(
                    sourceKey =
                        "moment_plan:wrong",
                    source =
                        "reset_reading",
                    sourceId =
                        "3",
                    completedAt =
                        "2026-08-03T06:20",
                ),
            )

        assertTrue(
            SafeExitUserExport
                .canonicalRecords(
                    malformed,
                )
                .isEmpty(),
        )
    }

    @Test
    fun sourceLabelsUseOnlyApprovedUserFacingNames() {
        assertEquals(
            "Pivot Game",
            SafeExitUserExport
                .displayName(
                    SafeExitSource.PivotGame,
                ),
        )

        assertEquals(
            "Reset Reading",
            SafeExitUserExport
                .displayName(
                    SafeExitSource.ResetReading,
                ),
        )

        assertEquals(
            "Moment Plan",
            SafeExitUserExport
                .displayName(
                    SafeExitSource.MomentPlan,
                ),
        )
    }

    @Test
    fun structuredJsonContainsOnlyCanonicalTechnicalFields() {
        val record =
            SafeExitRecord(
                sourceKey =
                    "pivot_game:REFLEX_OVERRIDE:4001",
                source =
                    SafeExitSource.PivotGame,
                sourceId =
                    "REFLEX_OVERRIDE:4001",
                completedAt =
                    LocalDateTime.of(
                        2026,
                        8,
                        3,
                        6,
                        30,
                    ),
            )

        val json =
            SafeExitUserExport
                .toJson(
                    listOf(
                        record,
                    ),
                )
                .getJSONObject(
                    0,
                )

        assertEquals(
            setOf(
                "sourceKey",
                "source",
                "sourceId",
                "completedAt",
            ),
            json
                .keys()
                .asSequence()
                .toSet(),
        )

        assertEquals(
            record.sourceKey,
            json.getString(
                "sourceKey",
            ),
        )

        assertEquals(
            "pivot_game",
            json.getString(
                "source",
            ),
        )

        assertEquals(
            "REFLEX_OVERRIDE:4001",
            json.getString(
                "sourceId",
            ),
        )

        assertEquals(
            "2026-08-03T06:30",
            json.getString(
                "completedAt",
            ),
        )

        assertFalse(
            json.has(
                "controlPoints",
            ),
        )

        assertFalse(
            json.has(
                "action",
            ),
        )

        assertFalse(
            json.has(
                "validCompletion",
            ),
        )

        assertFalse(
            json.has(
                "packageName",
            ),
        )

        assertFalse(
            json.has(
                "url",
            ),
        )

        assertFalse(
            json.has(
                "trigger",
            ),
        )
    }

    private fun entity(
        sourceKey: String,
        source: String,
        sourceId: String,
        completedAt: String,
    ): SafeExitEntity {
        return SafeExitEntity(
            sourceKey =
                sourceKey,
            source =
                source,
            sourceId =
                sourceId,
            completedAt =
                completedAt,
        )
    }
}