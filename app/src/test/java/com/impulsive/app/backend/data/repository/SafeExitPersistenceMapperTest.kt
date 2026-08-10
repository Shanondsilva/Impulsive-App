package com.impulsive.app.backend.data.repository

import com.impulsive.app.backend.data.local.entity.SafeExitEntity
import com.impulsive.app.backend.domain.model.score.SafeExitRecord
import com.impulsive.app.backend.domain.model.score.SafeExitSource
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SafeExitPersistenceMapperTest {
    @Test
    fun canonicalRecordRoundTripsWithoutChangingIdentity() {
        val record =
            record(
                source =
                    SafeExitSource.PivotGame,
                sourceId =
                    "REFLEX_OVERRIDE:8001",
                completedAt =
                    LocalDateTime.of(
                        2026,
                        8,
                        3,
                        1,
                        15,
                        30,
                        123_000_000,
                    ),
            )

        val entity =
            SafeExitPersistenceMapper
                .toEntity(record)

        assertEquals(
            record,
            SafeExitPersistenceMapper
                .toDomainOrNull(entity),
        )
    }

    @Test
    fun entityStoresOnlyStableTechnicalFields() {
        val record =
            record(
                source =
                    SafeExitSource.ResetReading,
                sourceId =
                    "8002",
                completedAt =
                    LocalDateTime.of(
                        2026,
                        8,
                        3,
                        1,
                        20,
                    ),
            )

        val entity =
            SafeExitPersistenceMapper
                .toEntity(record)

        assertEquals(
            "reset_reading:8002",
            entity.sourceKey,
        )
        assertEquals(
            "reset_reading",
            entity.source,
        )
        assertEquals(
            "8002",
            entity.sourceId,
        )
        assertEquals(
            "2026-08-03T01:20",
            entity.completedAt,
        )
        assertEquals(
            80,
            record.controlPoints,
        )
    }

    @Test
    fun nonCanonicalSourceKeyIsRejectedBeforeDaoAccess() {
        val invalid =
            SafeExitRecord(
                sourceKey =
                    "reset_reading:wrong",
                source =
                    SafeExitSource.ResetReading,
                sourceId =
                    "8003",
                completedAt =
                    LocalDateTime.of(
                        2026,
                        8,
                        3,
                        1,
                        25,
                    ),
            )

        assertThrows(
            IllegalArgumentException::class.java,
        ) {
            SafeExitPersistenceMapper
                .toEntity(invalid)
        }
    }

    @Test
    fun untrimmedSourceIdIsRejectedBeforeDaoAccess() {
        val invalid =
            SafeExitRecord(
                sourceKey =
                    "moment_plan:decision-4",
                source =
                    SafeExitSource.MomentPlan,
                sourceId =
                    " decision-4 ",
                completedAt =
                    LocalDateTime.of(
                        2026,
                        8,
                        3,
                        1,
                        30,
                    ),
            )

        assertThrows(
            IllegalArgumentException::class.java,
        ) {
            SafeExitPersistenceMapper
                .toEntity(invalid)
        }
    }

    @Test
    fun unknownPersistedSourceIsOmitted() {
        val entity =
            SafeExitEntity(
                sourceKey =
                    "unknown:8005",
                source =
                    "unknown",
                sourceId =
                    "8005",
                completedAt =
                    "2026-08-03T01:35",
            )

        assertNull(
            SafeExitPersistenceMapper
                .toDomainOrNull(entity),
        )
    }

    @Test
    fun malformedPersistedDateIsOmitted() {
        val entity =
            SafeExitEntity(
                sourceKey =
                    "reset_reading:8006",
                source =
                    "reset_reading",
                sourceId =
                    "8006",
                completedAt =
                    "not-a-date",
            )

        assertNull(
            SafeExitPersistenceMapper
                .toDomainOrNull(entity),
        )
    }

    @Test
    fun mismatchedPersistedSourceKeyIsOmitted() {
        val entity =
            SafeExitEntity(
                sourceKey =
                    "moment_plan:wrong",
                source =
                    "reset_reading",
                sourceId =
                    "8007",
                completedAt =
                    "2026-08-03T01:40",
            )

        assertNull(
            SafeExitPersistenceMapper
                .toDomainOrNull(entity),
        )
    }

    @Test
    fun nonCanonicalPersistedDateTextIsOmitted() {
        val entity =
            SafeExitEntity(
                sourceKey =
                    "reset_reading:8008",
                source =
                    "reset_reading",
                sourceId =
                    "8008",
                completedAt =
                    "2026-08-03T01:45:00",
            )

        assertNull(
            SafeExitPersistenceMapper
                .toDomainOrNull(entity),
        )
    }

    private fun record(
        source: SafeExitSource,
        sourceId: String,
        completedAt: LocalDateTime,
    ): SafeExitRecord {
        return SafeExitRecord(
            sourceKey =
                "${source.storageValue}:$sourceId",
            source =
                source,
            sourceId =
                sourceId,
            completedAt =
                completedAt,
        )
    }
}