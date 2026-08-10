package com.impulsive.app.backend.session.game

import com.impulsive.app.backend.domain.model.score.SafeExitCandidate
import com.impulsive.app.backend.domain.model.score.SafeExitRecord
import com.impulsive.app.backend.domain.model.score.SafeExitRejectionReason
import com.impulsive.app.backend.domain.model.score.SafeExitSource
import com.impulsive.app.backend.domain.model.score.ScoreGameType
import com.impulsive.app.backend.domain.model.score.ScoreSessionOutcome
import com.impulsive.app.backend.domain.model.score.ScoreSessionRecord
import com.impulsive.app.backend.session.progress.SafeExitCandidateRecorder
import com.impulsive.app.backend.session.progress.SafeExitRecordingResult
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PivotGameSafeExitReconciliationTest {
    @Test
    fun reconcilerProcessesOnlySupportedWalkAwayScoreSessions() =
        runBlocking {
            val recorder =
                RoutingRecorder()

            val result =
                PivotGameSafeExitReconciler(
                    recorder,
                )
                    .reconcile(
                        listOf(
                            record(
                                id =
                                    1L,
                                gameType =
                                    ScoreGameType
                                        .ReflexOverride,
                            ),
                            record(
                                id =
                                    2L,
                                gameType =
                                    ScoreGameType
                                        .RhythmTiles,
                            ),
                            record(
                                id =
                                    3L,
                                gameType =
                                    ScoreGameType
                                        .BlockCascade,
                            ),
                            record(
                                id =
                                    4L,
                                gameType =
                                    ScoreGameType
                                        .FocusSession,
                            ),
                            record(
                                id =
                                    5L,
                                gameType =
                                    ScoreGameType
                                        .ReflexOverride,
                                outcome =
                                    ScoreSessionOutcome
                                        .Completed,
                            ),
                        ),
                    )

            assertEquals(
                5,
                result
                    .inspectedSessions,
            )

            assertEquals(
                2,
                result
                    .eligibleSessions,
            )

            assertEquals(
                listOf(
                    "REFLEX_OVERRIDE:1",
                    "RHYTHM_TILES:2",
                ),
                recorder
                    .candidates
                    .map {
                        it.sourceId
                    },
            )
        }

    @Test
    fun reconciliationTracksRecordedDuplicateRejectedAndRetryableResults() =
        runBlocking {
            val recorder =
                RoutingRecorder()

            val result =
                PivotGameSafeExitReconciler(
                    recorder,
                )
                    .reconcile(
                        listOf(
                            record(
                                id =
                                    10L,
                            ),
                            record(
                                id =
                                    11L,
                            ),
                            record(
                                id =
                                    12L,
                                validCompletion =
                                    false,
                            ),
                            record(
                                id =
                                    13L,
                            ),
                        ),
                    )

            assertEquals(
                1,
                result.recorded,
            )
            assertEquals(
                1,
                result.duplicates,
            )
            assertEquals(
                1,
                result.rejected,
            )
            assertEquals(
                1,
                result
                    .retryableFailures,
            )
            assertTrue(
                result.requiresRetry,
            )
        }

    @Test
    fun successfulAndDuplicateOnlyReconciliationDoesNotRequireRetry() =
        runBlocking {
            val result =
                PivotGameSafeExitReconciler(
                    RoutingRecorder(),
                )
                    .reconcile(
                        listOf(
                            record(
                                id =
                                    10L,
                            ),
                            record(
                                id =
                                    11L,
                            ),
                        ),
                    )

            assertFalse(
                result.requiresRetry,
            )
        }

    private class RoutingRecorder :
        SafeExitCandidateRecorder {
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

            return when (
                candidate.sourceId
                    .substringAfterLast(
                        ':',
                    )
                    .toLong()
            ) {
                10L ->
                    SafeExitRecordingResult
                        .Recorded(
                            candidate
                                .toRecord(),
                        )

                11L ->
                    SafeExitRecordingResult
                        .Duplicate(
                            candidate
                                .toRecord(),
                        )

                12L ->
                    SafeExitRecordingResult
                        .Rejected(
                            SafeExitRejectionReason
                                .InvalidCompletion,
                        )

                13L ->
                    SafeExitRecordingResult
                        .RetryableFailure

                else ->
                    SafeExitRecordingResult
                        .Recorded(
                            candidate
                                .toRecord(),
                        )
            }
        }

        private fun SafeExitCandidate.toRecord():
            SafeExitRecord {
            return SafeExitRecord(
                sourceKey =
                    "${source.storageValue}:$sourceId",
                source =
                    SafeExitSource.PivotGame,
                sourceId =
                    sourceId,
                completedAt =
                    completedAt,
            )
        }
    }

    private fun record(
        id:
            Long,
        gameType:
            ScoreGameType =
                ScoreGameType
                    .ReflexOverride,
        outcome:
            ScoreSessionOutcome =
                ScoreSessionOutcome
                    .WalkedAway,
        validCompletion:
            Boolean = true,
    ): ScoreSessionRecord {
        return ScoreSessionRecord(
            id =
                id,
            gameType =
                gameType,
            score =
                100,
            startedAt =
                CompletedAt.minusSeconds(
                    60,
                ),
            completedAt =
                CompletedAt,
            durationSec =
                60,
            outcome =
                outcome,
            validCompletion =
                validCompletion,
        )
    }

    private companion object {
        val CompletedAt:
            LocalDateTime =
            LocalDateTime.of(
                2026,
                8,
                3,
                9,
                30,
            )
    }
}