package com.impulsive.app.backend.session.tasks

import com.impulsive.app.backend.domain.model.score.SafeExitCandidate
import com.impulsive.app.backend.domain.model.score.SafeExitRecord
import com.impulsive.app.backend.domain.model.score.SafeExitRejectionReason
import com.impulsive.app.backend.domain.model.score.SafeExitSource
import com.impulsive.app.backend.domain.model.tasks.ResetReadSessionRecord
import com.impulsive.app.backend.session.progress.SafeExitCandidateRecorder
import com.impulsive.app.backend.session.progress.SafeExitRecordingResult
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ResetReadSafeExitRecorderTest {
    @Test
    fun validSessionBuildsExactCanonicalCandidate() =
        runBlocking {
            val recorder =
                FakeRecorder(
                    mutableListOf(
                        SafeExitRecordingResult
                            .Recorded(
                                safeExitRecord(),
                            ),
                    ),
                )

            ResetReadSafeExitRecorder(
                recorder,
            )
                .recordExplicitWalkAway(
                    session(),
                )

            val candidate =
                recorder
                    .candidates
                    .single()

            assertEquals(
                SafeExitSource.ResetReading,
                candidate.source,
            )

            assertEquals(
                "10",
                candidate.sourceId,
            )

            assertEquals(
                CompletedAt,
                candidate.completedAt,
            )

            assertTrue(
                candidate.validCompletion,
            )
        }

    @Test
    fun invalidCompletionRemainsAvailableForPolicyRejection() =
        runBlocking {
            val recorder =
                FakeRecorder(
                    mutableListOf(
                        SafeExitRecordingResult
                            .Rejected(
                                SafeExitRejectionReason
                                    .InvalidCompletion,
                            ),
                    ),
                )

            val result =
                ResetReadSafeExitRecorder(
                    recorder,
                )
                    .recordExplicitWalkAway(
                        session(
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

            assertFalse(
                recorder
                    .candidates
                    .single()
                    .validCompletion,
            )
        }

    @Test
    fun retryableFailureIsRetriedExactlyOnce() =
        runBlocking {
            val recorder =
                FakeRecorder(
                    mutableListOf(
                        SafeExitRecordingResult
                            .RetryableFailure,
                        SafeExitRecordingResult
                            .Recorded(
                                safeExitRecord(),
                            ),
                    ),
                )

            val result =
                ResetReadSafeExitRecorder(
                    recorder,
                )
                    .recordExplicitWalkAway(
                        session(),
                    )

            assertEquals(
                SafeExitRecordingResult
                    .Recorded(
                        safeExitRecord(),
                    ),
                result,
            )

            assertEquals(
                2,
                recorder
                    .candidates
                    .size,
            )
        }

    @Test
    fun zeroSessionIdIsRejectedBeforeRecorderAccess() {
        val recorder =
            FakeRecorder(
                mutableListOf(),
            )

        assertThrows(
            IllegalArgumentException::class.java,
        ) {
            runBlocking {
                ResetReadSafeExitRecorder(
                    recorder,
                )
                    .recordExplicitWalkAway(
                        session(
                            id =
                                0L,
                        ),
                    )
            }
        }

        assertTrue(
            recorder
                .candidates
                .isEmpty(),
        )
    }

    private fun session(
        id: Long = 10L,
        validCompletion:
            Boolean = true,
    ): ResetReadSessionRecord {
        return ResetReadSessionRecord(
            id =
                id,
            articleId =
                "surf_the_urge",
            articleTitle =
                "Surf the Urge",
            startedAt =
                CompletedAt.minusSeconds(
                    90,
                ),
            completedAt =
                CompletedAt,
            selectedDurationSeconds =
                90,
            requiredDurationSeconds =
                90,
            secondsSpent =
                90,
            selectedOptionIndex =
                0,
            validCompletion =
                validCompletion,
        )
    }

    private fun safeExitRecord():
        SafeExitRecord {
        return SafeExitRecord(
            sourceKey =
                "reset_reading:10",
            source =
                SafeExitSource.ResetReading,
            sourceId =
                "10",
            completedAt =
                CompletedAt,
        )
    }

    private class FakeRecorder(
        private val results:
            MutableList<
                SafeExitRecordingResult
            >,
    ) : SafeExitCandidateRecorder {
        val candidates =
            mutableListOf<
                SafeExitCandidate
            >()

        override suspend fun record(
            candidate:
                SafeExitCandidate,
        ): SafeExitRecordingResult {
            candidates +=
                candidate

            return results.removeFirst()
        }
    }

    private companion object {
        val CompletedAt:
            LocalDateTime =
            LocalDateTime.of(
                2026,
                8,
                3,
                11,
                0,
            )
    }
}