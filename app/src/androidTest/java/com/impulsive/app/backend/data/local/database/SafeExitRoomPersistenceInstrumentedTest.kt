package com.impulsive.app.backend.data.local.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.impulsive.app.backend.data.repository.SafeExitRepository
import com.impulsive.app.backend.domain.model.score.SafeExitRecord
import com.impulsive.app.backend.domain.model.score.SafeExitSource
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SafeExitRoomPersistenceInstrumentedTest {
    private lateinit var database:
        AppDatabase

    private lateinit var repository:
        SafeExitRepository

    @Before
    fun setUp() {
        val context =
            ApplicationProvider
                .getApplicationContext<Context>()

        database =
            Room
                .inMemoryDatabaseBuilder(
                    context,
                    AppDatabase::class.java,
                )
                .build()

        repository =
            SafeExitRepository(
                database.safeExitDao(),
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun duplicatePrimaryKeyPreservesOriginalRecord() =
        runBlocking {
            val original =
                record(
                    source =
                        SafeExitSource.PivotGame,
                    sourceId =
                        "REFLEX_OVERRIDE:9001",
                    completedAt =
                        LocalDateTime.of(
                            2026,
                            8,
                            3,
                            2,
                            0,
                        ),
                )

            val duplicate =
                original.copy(
                    completedAt =
                        LocalDateTime.of(
                            2026,
                            8,
                            4,
                            2,
                            0,
                        ),
                )

            assertTrue(
                repository
                    .recordIfAbsent(original),
            )

            assertFalse(
                repository
                    .recordIfAbsent(duplicate),
            )

            assertEquals(
                listOf(original),
                repository.records.first(),
            )
        }

    @Test
    fun concurrentDuplicateRequestsInsertExactlyOneRow() =
        runBlocking {
            val record =
                record(
                    source =
                        SafeExitSource.ResetReading,
                    sourceId =
                        "9002",
                    completedAt =
                        LocalDateTime.of(
                            2026,
                            8,
                            3,
                            2,
                            10,
                        ),
                )

            val results =
                coroutineScope {
                    List(8) {
                        async(
                            Dispatchers.Default,
                        ) {
                            repository
                                .recordIfAbsent(
                                    record,
                                )
                        }
                    }.awaitAll()
                }

            assertEquals(
                1,
                results.count { inserted ->
                    inserted
                },
            )

            assertEquals(
                1,
                database
                    .safeExitDao()
                    .count(),
            )

            assertEquals(
                listOf(record),
                repository.records.first(),
            )
        }

    @Test
    fun sameSourceIdUnderDifferentSourcesCreatesDistinctRows() =
        runBlocking {
            val sourceId =
                "shared-id"

            val resetReading =
                record(
                    source =
                        SafeExitSource.ResetReading,
                    sourceId =
                        sourceId,
                    completedAt =
                        LocalDateTime.of(
                            2026,
                            8,
                            3,
                            2,
                            20,
                        ),
                )

            val momentPlan =
                record(
                    source =
                        SafeExitSource.MomentPlan,
                    sourceId =
                        sourceId,
                    completedAt =
                        LocalDateTime.of(
                            2026,
                            8,
                            3,
                            2,
                            25,
                        ),
                )

            assertTrue(
                repository.recordIfAbsent(
                    resetReading,
                ),
            )

            assertTrue(
                repository.recordIfAbsent(
                    momentPlan,
                ),
            )

            assertEquals(
                listOf(
                    momentPlan,
                    resetReading,
                ),
                repository.records.first(),
            )
        }

    @Test
    fun recordsAreOrderedNewestFirstWithStableTieBreak() =
        runBlocking {
            val completedAt =
                LocalDateTime.of(
                    2026,
                    8,
                    3,
                    2,
                    30,
                )

            val laterKey =
                record(
                    source =
                        SafeExitSource.ResetReading,
                    sourceId =
                        "z-key",
                    completedAt =
                        completedAt,
                )

            val earlierKey =
                record(
                    source =
                        SafeExitSource.ResetReading,
                    sourceId =
                        "a-key",
                    completedAt =
                        completedAt,
                )

            repository.recordIfAbsent(
                laterKey,
            )

            repository.recordIfAbsent(
                earlierKey,
            )

            assertEquals(
                listOf(
                    earlierKey,
                    laterKey,
                ),
                repository.records.first(),
            )
        }

    @Test
    fun clearAllTablesRemovesSafeExitLedger() =
        runBlocking {
            val record =
                record(
                    source =
                        SafeExitSource.MomentPlan,
                    sourceId =
                        "decision-9003",
                    completedAt =
                        LocalDateTime.of(
                            2026,
                            8,
                            3,
                            2,
                            40,
                        ),
                )

            repository.recordIfAbsent(
                record,
            )

            assertEquals(
                1,
                database
                    .safeExitDao()
                    .count(),
            )

            database.clearAllTables()

            assertEquals(
                0,
                database
                    .safeExitDao()
                    .count(),
            )

            assertTrue(
                repository
                    .records
                    .first()
                    .isEmpty(),
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