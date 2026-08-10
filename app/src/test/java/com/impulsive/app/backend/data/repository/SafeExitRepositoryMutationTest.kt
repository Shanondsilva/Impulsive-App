package com.impulsive.app.backend.data.repository

import com.impulsive.app.backend.data.local.dao.SafeExitDao
import com.impulsive.app.backend.data.local.dao.SafeExitSourceCountRow
import com.impulsive.app.backend.data.local.entity.SafeExitEntity
import com.impulsive.app.backend.domain.model.score.SafeExitRecord
import com.impulsive.app.backend.domain.model.score.SafeExitSource
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeExitRepositoryMutationTest {
    @Test
    fun newInsertRequestsBackupRefreshOnce() =
        runBlocking {
            var refreshCount =
                0

            val repository =
                SafeExitRepository(
                    dao =
                        FakeDao(
                            insertResult =
                                1L,
                        ),
                    onBackupRelevantDataChanged = {
                        refreshCount += 1
                    },
                )

            assertTrue(
                repository
                    .recordIfAbsent(
                        record(),
                    ),
            )

            assertEquals(
                1,
                refreshCount,
            )
        }

    @Test
    fun duplicateInsertDoesNotRequestBackupRefresh() =
        runBlocking {
            var refreshCount =
                0

            val repository =
                SafeExitRepository(
                    dao =
                        FakeDao(
                            insertResult =
                                -1L,
                        ),
                    onBackupRelevantDataChanged = {
                        refreshCount += 1
                    },
                )

            assertFalse(
                repository
                    .recordIfAbsent(
                        record(),
                    ),
            )

            assertEquals(
                0,
                refreshCount,
            )
        }

    @Test
    fun backupRefreshFailureDoesNotMisreportTheDurableInsert() =
        runBlocking {
            val repository =
                SafeExitRepository(
                    dao =
                        FakeDao(
                            insertResult =
                                1L,
                        ),
                    onBackupRelevantDataChanged = {
                        throw IllegalStateException(
                            "WorkManager unavailable",
                        )
                    },
                )

            assertTrue(
                repository
                    .recordIfAbsent(
                        record(),
                    ),
            )
        }
    private fun record():
        SafeExitRecord {
        return SafeExitRecord(
            sourceKey =
                "reset_reading:6001",
            source =
                SafeExitSource.ResetReading,
            sourceId =
                "6001",
            completedAt =
                LocalDateTime.of(
                    2026,
                    8,
                    3,
                    7,
                    30,
                ),
        )
    }

    private class FakeDao(
        private val insertResult:
            Long,
    ) : SafeExitDao {
        override suspend fun insertOnce(
            record: SafeExitEntity,
        ): Long {
            return insertResult
        }

        override fun observeAll():
            Flow<List<SafeExitEntity>> {
            return flowOf(
                emptyList(),
            )
        }

        override fun observeSourceCountsInRange(
            startInclusive: String,
            endExclusive: String,
        ): Flow<List<SafeExitSourceCountRow>> {
            return flowOf(
                emptyList(),
            )
        }

        override fun observeRecentNonPivotInRange(
            startInclusive: String,
            endExclusive: String,
            excludedSource: String,
            limit: Int,
        ): Flow<List<SafeExitEntity>> {
            return flowOf(
                emptyList(),
            )
        }
        override fun observeExistingSourceKeysInRange(
            startInclusive: String,
            endExclusive: String,
            source: String,
            sourceKeys: List<String>,
        ): Flow<List<String>> {
            return flowOf(
                emptyList(),
            )
        }

        override fun observeRecordCount(): Flow<Long> {
            return flowOf(
                0L,
            )
        }

        override suspend fun getAllForBackup():
            List<SafeExitEntity> {
            return emptyList()
        }

        override suspend fun insertForRestore(
            records: List<SafeExitEntity>,
        ) = Unit

        override suspend fun clearAllForRestore() =
            Unit

        override suspend fun getBySourceKey(
            sourceKey: String,
        ): SafeExitEntity? {
            return null
        }

        override suspend fun count():
            Int {
            return 0
        }
    }
}