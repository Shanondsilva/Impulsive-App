package com.impulsive.app.backend.session.tasks

import androidx.work.Data
import com.impulsive.app.backend.domain.model.score.SafeExitAction
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
import org.junit.Assert.assertNull
import org.junit.Test

class ResetReadSafeExitReconciliationTest {
    @Test
    fun codecRoundTripPreservesCanonicalTechnicalFields() {
        val decoded =
            ResetReadSafeExitWorkDataCodec
                .decode(
                    ResetReadSafeExitWorkDataCodec
                        .encode(
                            session(),
                        ),
                )

        assertEquals(
            22L,
            decoded?.sessionId,
        )
        assertEquals(
            CompletedAt,
            decoded?.completedAt,
        )
        assertEquals(
            true,
            decoded?.validCompletion,
        )
    }

    @Test
    fun malformedOrIncompleteWorkDataIsRejected() {
        val valid =
            ResetReadSafeExitWorkDataCodec
                .encode(
                    session(),
                )

        listOf(
            Data.EMPTY,
            valid
                .newBuilder()
                .putInt(
                    ResetReadSafeExitWorkDataCodec.FormatVersionKey,
                    99,
                )
                .build(),
            valid
                .without(
                    ResetReadSafeExitWorkDataCodec.SessionIdKey,
                ),
            valid
                .newBuilder()
                .putLong(
                    ResetReadSafeExitWorkDataCodec.SessionIdKey,
                    0L,
                )
                .build(),
            valid
                .without(
                    ResetReadSafeExitWorkDataCodec.CompletedAtKey,
                ),
            valid
                .newBuilder()
                .putString(
                    ResetReadSafeExitWorkDataCodec.CompletedAtKey,
                    "not-a-date",
                )
                .build(),
            valid
                .without(
                    ResetReadSafeExitWorkDataCodec.ValidCompletionKey,
                ),
            valid
                .newBuilder()
                .putString(
                    ResetReadSafeExitWorkDataCodec.ValidCompletionKey,
                    "false",
                )
                .build(),
        ).forEach { data ->
            assertNull(
                ResetReadSafeExitWorkDataCodec
                    .decode(
                        data,
                    ),
            )
        }
    }

    @Test
    fun payloadBuildsCanonicalResetReadingCandidate() =
        runBlocking {
            val recorder =
                FakeCandidateRecorder(
                    SafeExitRecordingResult
                        .Recorded(
                            safeExitRecord(
                                id = 22L,
                            ),
                        ),
                )

            val result =
                ResetReadSafeExitReconciler(
                    recorder,
                )
                    .reconcile(
                        ResetReadSafeExitWorkPayload(
                            sessionId = 22L,
                            completedAt = CompletedAt,
                            validCompletion = true,
                        ),
                    )

            assertEquals(
                ResetReadSafeExitReconciliationResult.Recorded,
                result,
            )
            assertEquals(
                SafeExitSource.ResetReading,
                recorder.lastCandidate?.source,
            )
            assertEquals(
                "22",
                recorder.lastCandidate?.sourceId,
            )
            assertEquals(
                CompletedAt,
                recorder.lastCandidate?.completedAt,
            )
            assertEquals(
                true,
                recorder.lastCandidate?.validCompletion,
            )
            assertEquals(
                SafeExitAction.WalkAway,
                recorder.lastCandidate?.action,
            )
        }

    @Test
    fun duplicateIsTerminal() =
        runBlocking {
            val result =
                ResetReadSafeExitReconciler(
                    FakeCandidateRecorder(
                        SafeExitRecordingResult
                            .Duplicate(
                                safeExitRecord(
                                    id = 30L,
                                ),
                            ),
                    ),
                )
                    .reconcile(
                        payload(
                            id = 30L,
                        ),
                    )

            assertEquals(
                ResetReadSafeExitReconciliationResult.Duplicate,
                result,
            )
        }

    @Test
    fun retryableFailureRemainsRetryable() =
        runBlocking {
            val result =
                ResetReadSafeExitReconciler(
                    FakeCandidateRecorder(
                        SafeExitRecordingResult.RetryableFailure,
                    ),
                )
                    .reconcile(
                        payload(
                            id = 40L,
                        ),
                    )

            assertEquals(
                ResetReadSafeExitReconciliationResult.RetryableFailure,
                result,
            )
        }

    @Test
    fun invalidCompletionIsRoutedToTheRecorderWithoutBeingChanged() =
        runBlocking {
            val recorder =
                FakeCandidateRecorder(
                    SafeExitRecordingResult
                        .Rejected(
                            SafeExitRejectionReason.InvalidCompletion,
                        ),
                )

            val result =
                ResetReadSafeExitReconciler(
                    recorder,
                )
                    .reconcile(
                        payload(
                            id = 50L,
                            validCompletion = false,
                        ),
                    )

            assertEquals(
                ResetReadSafeExitReconciliationResult.Rejected,
                result,
            )
            assertEquals(
                false,
                recorder.lastCandidate?.validCompletion,
            )
        }

    private fun Data.without(
        key: String,
    ): Data {
        val builder =
            Data.Builder()
        keyValueMap
            .filterKeys {
                it != key
            }
            .forEach { (entryKey, value) ->
                when (value) {
                    is Boolean ->
                        builder.putBoolean(
                            entryKey,
                            value,
                        )

                    is Int ->
                        builder.putInt(
                            entryKey,
                            value,
                        )

                    is Long ->
                        builder.putLong(
                            entryKey,
                            value,
                        )

                    is String ->
                        builder.putString(
                            entryKey,
                            value,
                        )
                }
            }
        return builder.build()
    }

    private fun Data.newBuilder(): Data.Builder {
        val builder =
            Data.Builder()
        keyValueMap.forEach { (key, value) ->
            when (value) {
                is Boolean -> builder.putBoolean(key, value)
                is Int -> builder.putInt(key, value)
                is Long -> builder.putLong(key, value)
                is String -> builder.putString(key, value)
            }
        }
        return builder
    }

    private fun session(): ResetReadSessionRecord {
        return ResetReadSessionRecord(
            id = 22L,
            articleId = "surf_the_urge",
            articleTitle = "Surf the Urge",
            startedAt = CompletedAt.minusSeconds(90),
            completedAt = CompletedAt,
            selectedDurationSeconds = 90,
            requiredDurationSeconds = 90,
            secondsSpent = 90,
            selectedOptionIndex = 2,
            validCompletion = true,
            answerText = "I can wait this out.",
            completionQuality = "valid",
            failureReason = null,
            rewardApplied = true,
            waitCutMinutes = 5,
            helpfulnessRating = 4,
        )
    }

    private fun payload(
        id: Long,
        validCompletion: Boolean = true,
    ): ResetReadSafeExitWorkPayload {
        return ResetReadSafeExitWorkPayload(
            sessionId = id,
            completedAt = CompletedAt,
            validCompletion = validCompletion,
        )
    }

    private fun safeExitRecord(
        id: Long,
    ): SafeExitRecord {
        return SafeExitRecord(
            sourceKey =
                "reset_reading:$id",
            source =
                SafeExitSource.ResetReading,
            sourceId =
                id.toString(),
            completedAt =
                CompletedAt,
        )
    }

    private class FakeCandidateRecorder(
        private val result: SafeExitRecordingResult,
    ) : SafeExitCandidateRecorder {
        var lastCandidate: SafeExitCandidate? = null

        override suspend fun record(
            candidate: SafeExitCandidate,
        ): SafeExitRecordingResult {
            lastCandidate = candidate
            return result
        }
    }

    private companion object {
        val CompletedAt: LocalDateTime =
            LocalDateTime.of(
                2026,
                8,
                3,
                11,
                30,
            )
    }
}