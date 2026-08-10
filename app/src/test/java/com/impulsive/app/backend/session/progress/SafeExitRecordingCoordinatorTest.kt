package com.impulsive.app.backend.session.progress

import com.impulsive.app.backend.domain.model.score.SafeExitAction
import com.impulsive.app.backend.domain.model.score.SafeExitCandidate
import com.impulsive.app.backend.domain.model.score.SafeExitRecord
import com.impulsive.app.backend.domain.model.score.SafeExitRejectionReason
import com.impulsive.app.backend.domain.model.score.SafeExitSource
import com.impulsive.app.backend.domain.repository.score.SafeExitRecordRepository
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SafeExitRecordingCoordinatorTest {
    @Test
    fun acceptedCandidateIsPersistedAndReportedAsRecorded() =
        runBlocking {
            val repository =
                FakeRepository(
                    insertResult =
                        true,
                )

            val result =
                SafeExitRecordingCoordinator(
                    repository,
                )
                    .record(
                        candidate(
                            sourceId =
                                " 5001 ",
                        ),
                    )

            val expected =
                SafeExitRecord(
                    sourceKey =
                        "reset_reading:5001",
                    source =
                        SafeExitSource.ResetReading,
                    sourceId =
                        "5001",
                    completedAt =
                        CompletedAt,
                )

            assertEquals(
                SafeExitRecordingResult
                    .Recorded(
                        expected,
                    ),
                result,
            )

            assertEquals(
                1,
                repository.calls,
            )

            assertEquals(
                expected,
                repository.lastRecord,
            )
        }

    @Test
    fun existingSourceKeyIsReportedAsDuplicate() =
        runBlocking {
            val repository =
                FakeRepository(
                    insertResult =
                        false,
                )

            val result =
                SafeExitRecordingCoordinator(
                    repository,
                )
                    .record(
                        candidate(),
                    )

            assertEquals(
                SafeExitRecordingResult
                    .Duplicate(
                        requireNotNull(
                            repository
                                .lastRecord,
                        ),
                    ),
                result,
            )

            assertEquals(
                1,
                repository.calls,
            )
        }

    @Test
    fun blankSourceIdIsRejectedWithoutRepositoryAccess() =
        runBlocking {
            val repository =
                FakeRepository()

            val result =
                SafeExitRecordingCoordinator(
                    repository,
                )
                    .record(
                        candidate(
                            sourceId =
                                "   ",
                        ),
                    )

            assertEquals(
                SafeExitRecordingResult
                    .Rejected(
                        SafeExitRejectionReason
                            .BlankSourceId,
                    ),
                result,
            )

            assertEquals(
                0,
                repository.calls,
            )
        }

    @Test
    fun invalidCompletionIsRejectedWithoutRepositoryAccess() =
        runBlocking {
            val repository =
                FakeRepository()

            val result =
                SafeExitRecordingCoordinator(
                    repository,
                )
                    .record(
                        candidate(
                            validCompletion =
                                false,
                        ),
                    )

            assertEquals(
                SafeExitRecordingResult
                    .Rejected(
                        SafeExitRejectionReason
                            .InvalidCompletion,
                    ),
                result,
            )

            assertEquals(
                0,
                repository.calls,
            )
        }

    @Test
    fun nonWalkAwayActionIsRejectedWithoutRepositoryAccess() =
        runBlocking {
            val repository =
                FakeRepository()

            val result =
                SafeExitRecordingCoordinator(
                    repository,
                )
                    .record(
                        candidate(
                            action =
                                SafeExitAction.Done,
                        ),
                    )

            assertEquals(
                SafeExitRecordingResult
                    .Rejected(
                        SafeExitRejectionReason
                            .NonWalkAwayAction,
                    ),
                result,
            )

            assertEquals(
                0,
                repository.calls,
            )
        }

    @Test
    fun repositoryExceptionBecomesRetryableFailure() =
        runBlocking {
            val repository =
                FakeRepository(
                    failure =
                        IllegalStateException(
                            "database unavailable",
                        ),
                )

            assertEquals(
                SafeExitRecordingResult
                    .RetryableFailure,
                SafeExitRecordingCoordinator(
                    repository,
                )
                    .record(
                        candidate(),
                    ),
            )
        }

    @Test
    fun coroutineCancellationIsRethrown() {
        val repository =
            FakeRepository(
                failure =
                    CancellationException(
                        "cancelled",
                    ),
            )

        assertThrows(
            CancellationException::class.java,
        ) {
            runBlocking {
                SafeExitRecordingCoordinator(
                    repository,
                )
                    .record(
                        candidate(),
                    )
            }
        }
    }

    private fun candidate(
        sourceId: String = "5001",
        action: SafeExitAction =
            SafeExitAction.WalkAway,
        validCompletion: Boolean = true,
    ): SafeExitCandidate {
        return SafeExitCandidate(
            source =
                SafeExitSource.ResetReading,
            sourceId =
                sourceId,
            action =
                action,
            completedAt =
                CompletedAt,
            validCompletion =
                validCompletion,
        )
    }

    private class FakeRepository(
        private val insertResult:
            Boolean = true,
        private val failure:
            Throwable? = null,
    ) : SafeExitRecordRepository {
        private val stored =
            MutableStateFlow<
                List<SafeExitRecord>
            >(
                emptyList(),
            )

        override val records:
            Flow<List<SafeExitRecord>> =
            stored

        var calls:
            Int = 0
            private set

        var lastRecord:
            SafeExitRecord? = null
            private set

        override suspend fun recordIfAbsent(
            record: SafeExitRecord,
        ): Boolean {
            calls += 1
            lastRecord =
                record

            failure?.let {
                throw it
            }

            if (
                insertResult
            ) {
                stored.value =
                    listOf(
                        record,
                    )
            }

            return insertResult
        }
    }

    private companion object {
        val CompletedAt:
            LocalDateTime =
            LocalDateTime.of(
                2026,
                8,
                3,
                7,
                0,
            )
    }
}