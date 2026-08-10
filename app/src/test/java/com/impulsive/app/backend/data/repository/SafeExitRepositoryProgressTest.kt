package com.impulsive.app.backend.data.repository

import com.impulsive.app.backend.data.local.dao.SafeExitDao
import com.impulsive.app.backend.data.local.dao.SafeExitSourceCountRow
import com.impulsive.app.backend.data.local.entity.SafeExitEntity
import com.impulsive.app.backend.domain.model.score.SAFE_EXIT_CONTROL_POINT_BONUS
import com.impulsive.app.backend.domain.model.score.SafeExitSource
import com.impulsive.app.backend.domain.model.score.ScoreRange
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SafeExitRepositoryProgressTest {
    @Test
    fun ledgerCountsPersistedPivotKeysAndRecentRowsProduceProgressSnapshot() =
        runBlocking {
            val dao =
                FakeDao().apply {
                    sourceCounts.value =
                        listOf(
                            SafeExitSourceCountRow(
                                source = SafeExitSource.ResetReading.storageValue,
                                recordCount = 2L,
                            ),
                            SafeExitSourceCountRow(
                                source = SafeExitSource.MomentPlan.storageValue,
                                recordCount = 1L,
                            ),
                            SafeExitSourceCountRow(
                                source = SafeExitSource.PivotGame.storageValue,
                                recordCount = 99L,
                            ),
                            SafeExitSourceCountRow(
                                source = "unknown_source",
                                recordCount = 11L,
                            ),
                        )
                    persistedPivotKeys.value =
                        listOf(
                            "pivot_game:REFLEX_OVERRIDE:1",
                        )
                    recent.value =
                        listOf(
                            entity(
                                source = SafeExitSource.PivotGame,
                                sourceId = "REFLEX_OVERRIDE:1",
                                completedAt = LocalDateTime.of(2026, 8, 3, 11, 0),
                            ),
                            entity(
                                source = SafeExitSource.ResetReading,
                                sourceId = "reset-1",
                                completedAt = LocalDateTime.of(2026, 8, 3, 10, 0),
                            ),
                            malformedEntity(),
                            entity(
                                source = SafeExitSource.MomentPlan,
                                sourceId = "decision-1",
                                completedAt = LocalDateTime.of(2026, 8, 3, 12, 0),
                            ),
                        )
                }

            val snapshot =
                SafeExitRepository(
                    dao = dao,
                )
                    .observeProgressSnapshot(
                        selectedRange = ScoreRange.Today,
                        now = Now,
                        pivotCandidateSourceKeys =
                            setOf(
                                "pivot_game:REFLEX_OVERRIDE:1",
                                "pivot_game:REFLEX_OVERRIDE:2",
                                "reset_reading:ignored",
                            ),
                    )
                    .first()

            assertEquals(
                102,
                snapshot.ledgerSafeExitCount,
            )
            assertEquals(
                240,
                snapshot.additionalControlPoints,
            )
            assertEquals(
                setOf(
                    "pivot_game:REFLEX_OVERRIDE:1",
                ),
                snapshot.persistedPivotSourceKeys,
            )
            assertEquals(
                listOf(
                    "pivot_game:REFLEX_OVERRIDE:1",
                    "pivot_game:REFLEX_OVERRIDE:2",
                ),
                dao.observedPivotSourceKeys,
            )
            assertEquals(
                listOf(
                    SafeExitSource.MomentPlan,
                    SafeExitSource.ResetReading,
                ),
                snapshot.recentSafeExits.map { it.source },
            )
            assertEquals(
                listOf(
                    SAFE_EXIT_CONTROL_POINT_BONUS,
                    SAFE_EXIT_CONTROL_POINT_BONUS,
                ),
                snapshot.recentSafeExits.map { it.additionalControlPoints },
            )
            assertEquals(
                SafeExitSource.PivotGame.storageValue,
                dao.excludedSourceForRecent,
            )
            assertEquals(
                "2026-08-03T00:00",
                dao.startInclusiveForCounts,
            )
            assertEquals(
                "2026-08-04T00:00",
                dao.endExclusiveForCounts,
            )
            assertEquals(
                10,
                dao.limitForRecent,
            )
        }


    @Test
    fun emptyPivotCandidateSetSkipsMatchingKeyQuery() =
        runBlocking {
            val dao =
                FakeDao()

            SafeExitRepository(
                dao = dao,
            )
                .observeProgressSnapshot(
                    selectedRange = ScoreRange.Today,
                    now = Now,
                    pivotCandidateSourceKeys = emptySet(),
                )
                .first()

            assertEquals(
                0,
                dao.existingSourceKeyQueryCalls,
            )
        }

    @Test
    fun ledgerChangesEmitAfterObservedRecordCountChanges() =
        runBlocking {
            val dao =
                FakeDao()
            val repository =
                SafeExitRepository(
                    dao = dao,
                )
            val emissions =
                mutableListOf<Unit>()
            val job =
                async(
                    start = CoroutineStart.UNDISPATCHED,
                ) {
                    repository
                        .observeLedgerChanges()
                        .take(2)
                        .toList(emissions)
                }

            dao.recordCount.value = 1L
            job.await()

            assertEquals(
                2,
                emissions.size,
            )
        }
    private fun entity(
        source: SafeExitSource,
        sourceId: String,
        completedAt: LocalDateTime,
    ): SafeExitEntity {
        return SafeExitEntity(
            sourceKey =
                "${source.storageValue}:$sourceId",
            source =
                source.storageValue,
            sourceId =
                sourceId,
            completedAt =
                completedAt.toString(),
        )
    }

    private fun malformedEntity(): SafeExitEntity {
        return SafeExitEntity(
            sourceKey =
                "reset_reading:broken",
            source =
                SafeExitSource.ResetReading.storageValue,
            sourceId =
                "broken",
            completedAt =
                "not-a-date",
        )
    }

    private class FakeDao : SafeExitDao {
        val sourceCounts =
            MutableStateFlow<List<SafeExitSourceCountRow>>(emptyList())
        val persistedPivotKeys =
            MutableStateFlow<List<String>>(emptyList())
        val recent =
            MutableStateFlow<List<SafeExitEntity>>(emptyList())
        val recordCount =
            MutableStateFlow(0L)
        var existingSourceKeyQueryCalls = 0
        var observedPivotSourceKeys: List<String>? = null
        var startInclusiveForCounts: String? = null
        var endExclusiveForCounts: String? = null
        var startInclusiveForRecent: String? = null
        var endExclusiveForRecent: String? = null
        var excludedSourceForRecent: String? = null
        var limitForRecent: Int? = null

        override suspend fun insertOnce(record: SafeExitEntity): Long = 1L

        override fun observeAll(): Flow<List<SafeExitEntity>> =
            flowOf(emptyList())

        override fun observeSourceCountsInRange(
            startInclusive: String,
            endExclusive: String,
        ): Flow<List<SafeExitSourceCountRow>> {
            startInclusiveForCounts = startInclusive
            endExclusiveForCounts = endExclusive
            return sourceCounts
        }

        override fun observeRecentNonPivotInRange(
            startInclusive: String,
            endExclusive: String,
            excludedSource: String,
            limit: Int,
        ): Flow<List<SafeExitEntity>> {
            startInclusiveForRecent = startInclusive
            endExclusiveForRecent = endExclusive
            excludedSourceForRecent = excludedSource
            limitForRecent = limit
            return recent
        }

        override fun observeExistingSourceKeysInRange(
            startInclusive: String,
            endExclusive: String,
            source: String,
            sourceKeys: List<String>,
        ): Flow<List<String>> {
            existingSourceKeyQueryCalls += 1
            observedPivotSourceKeys = sourceKeys
            return persistedPivotKeys
        }

        override fun observeRecordCount(): Flow<Long> =
            recordCount

        override suspend fun getAllForBackup(): List<SafeExitEntity> = emptyList()

        override suspend fun insertForRestore(records: List<SafeExitEntity>) = Unit

        override suspend fun clearAllForRestore() = Unit

        override suspend fun getBySourceKey(sourceKey: String): SafeExitEntity? = null

        override suspend fun count(): Int = 0
    }

    private companion object {
        val Now: LocalDateTime =
            LocalDateTime.of(2026, 8, 3, 14, 0)
    }
}