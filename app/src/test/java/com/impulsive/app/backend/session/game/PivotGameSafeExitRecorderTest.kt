package com.impulsive.app.backend.session.game

import com.impulsive.app.backend.domain.model.score.SafeExitAction
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
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class PivotGameSafeExitRecorderTest {
    @Test
    fun factoryCreatesPivotGameWalkAwayCandidateWithStableSourceId() {
        val completedAt =
            LocalDateTime.of(
                2026,
                4,
                12,
                10,
                30,
            )
        val record =
            scoreRecord(
                id = 42L,
                gameType = ScoreGameType.ReflexOverride,
                outcome = ScoreSessionOutcome.WalkedAway,
                completedAt = completedAt,
                validCompletion = true,
            )

        val candidate =
            PivotGameSafeExitCandidateFactory
                .create(
                    record,
                )

        assertEquals(
            SafeExitSource.PivotGame,
            candidate.source,
        )
        assertEquals(
            "REFLEX_OVERRIDE:42",
            candidate.sourceId,
        )
        assertEquals(
            SafeExitAction.WalkAway,
            candidate.action,
        )
        assertEquals(
            completedAt,
            candidate.completedAt,
        )
        assertTrue(
            candidate.validCompletion,
        )
    }

    @Test
    fun factoryPreservesInvalidCompletionSoUnfinishedWalkAwayIsRejectedDownstream() {
        val candidate =
            PivotGameSafeExitCandidateFactory
                .create(
                    scoreRecord(
                        outcome = ScoreSessionOutcome.WalkedAway,
                        validCompletion = false,
                    ),
                )

        assertFalse(
            candidate.validCompletion,
        )
    }

    @Test
    fun factoryRejectsNonWalkAwayOutcomes() {
        assertThrows(
            IllegalArgumentException::class.java,
        ) {
            PivotGameSafeExitCandidateFactory
                .create(
                    scoreRecord(
                        outcome = ScoreSessionOutcome.Completed,
                    ),
                )
        }
    }

    @Test
    fun factoryRejectsFocusSessionSource() {
        assertThrows(
            IllegalArgumentException::class.java,
        ) {
            PivotGameSafeExitCandidateFactory
                .create(
                    scoreRecord(
                        gameType = ScoreGameType.FocusSession,
                        outcome = ScoreSessionOutcome.WalkedAway,
                    ),
                )
        }
    }

    @Test
    fun recorderIgnoresNonWalkAwaySessions() =
        runBlocking {
            val fakeRecorder = RecordingCandidateRecorder()
            val recorder =
                PivotGameSafeExitRecorder(
                    fakeRecorder,
                )

            val result =
                recorder
                    .recordIfWalkedAway(
                        scoreRecord(
                            outcome = ScoreSessionOutcome.Completed,
                        ),
                    )

            assertNull(
                result,
            )
            assertEquals(
                emptyList<SafeExitCandidate>(),
                fakeRecorder.candidates,
            )
        }

    @Test
    fun recorderRetriesOnceAfterRetryableFailure() =
        runBlocking {
            val fakeRecorder =
                RecordingCandidateRecorder(
                    SafeExitRecordingResult.RetryableFailure,
                    SafeExitRecordingResult.Rejected(
                        SafeExitRejectionReason.InvalidCompletion,
                    ),
                )
            val recorder =
                PivotGameSafeExitRecorder(
                    fakeRecorder,
                )

            val result =
                recorder
                    .recordIfWalkedAway(
                        scoreRecord(
                            outcome = ScoreSessionOutcome.WalkedAway,
                            validCompletion = true,
                        ),
                    )

            assertEquals(
                SafeExitRecordingResult.Rejected(
                    SafeExitRejectionReason.InvalidCompletion,
                ),
                result,
            )
            assertEquals(
                2,
                fakeRecorder.candidates.size,
            )
        }

    @Test
    fun recorderDoesNotRetryDuplicates() =
        runBlocking {
            val duplicateRecord =
                SafeExitRecord(
                    sourceKey = "pivot_game:REFLEX_OVERRIDE:1",
                    source = SafeExitSource.PivotGame,
                    sourceId = "REFLEX_OVERRIDE:1",
                    completedAt =
                        LocalDateTime.of(
                            2026,
                            4,
                            12,
                            10,
                            30,
                        ),
                )
            val fakeRecorder =
                RecordingCandidateRecorder(
                    SafeExitRecordingResult.Duplicate(
                        duplicateRecord,
                    ),
                )
            val recorder =
                PivotGameSafeExitRecorder(
                    fakeRecorder,
                )

            val result =
                recorder
                    .recordIfWalkedAway(
                        scoreRecord(
                            id = 1L,
                            outcome = ScoreSessionOutcome.WalkedAway,
                            validCompletion = true,
                        ),
                    )

            assertEquals(
                SafeExitRecordingResult.Duplicate(
                    duplicateRecord,
                ),
                result,
            )
            assertEquals(
                1,
                fakeRecorder.candidates.size,
            )
        }

    @Test
    fun createOrNullReturnsNullForBlockCascade() {
        val candidate =
            PivotGameSafeExitCandidateFactory
                .createOrNull(
                    scoreRecord(
                        gameType = ScoreGameType.BlockCascade,
                        outcome = ScoreSessionOutcome.WalkedAway,
                    ),
                )

        assertNull(
            candidate,
        )
    }

    @Test
    fun createOrNullReturnsNullForSkylineReset() {
        val candidate =
            PivotGameSafeExitCandidateFactory
                .createOrNull(
                    scoreRecord(
                        gameType = ScoreGameType.SkylineReset,
                        outcome = ScoreSessionOutcome.WalkedAway,
                    ),
                )

        assertNull(
            candidate,
        )
    }

    @Test
    fun createOrNullReturnsNullForCompleted() {
        val candidate =
            PivotGameSafeExitCandidateFactory
                .createOrNull(
                    scoreRecord(
                        gameType = ScoreGameType.ReflexOverride,
                        outcome = ScoreSessionOutcome.Completed,
                    ),
                )

        assertNull(
            candidate,
        )
    }

    @Test
    fun createOrNullReturnsCandidateForReflexOverrideWalkedAway() {
        val completedAt =
            LocalDateTime.of(
                2026,
                8,
                3,
                11,
                0,
            )

        val candidate =
            PivotGameSafeExitCandidateFactory
                .createOrNull(
                    scoreRecord(
                        id = 101L,
                        gameType = ScoreGameType.ReflexOverride,
                        outcome = ScoreSessionOutcome.WalkedAway,
                        completedAt = completedAt,
                    ),
                )

        requireNotNull(
            candidate,
        )
        assertEquals(
            SafeExitSource.PivotGame,
            candidate.source,
        )
        assertEquals(
            "REFLEX_OVERRIDE:101",
            candidate.sourceId,
        )
        assertEquals(
            completedAt,
            candidate.completedAt,
        )
    }

    @Test
    fun createOrNullReturnsCandidateForRhythmTilesWalkedAway() {
        val completedAt =
            LocalDateTime.of(
                2026,
                8,
                3,
                11,
                5,
            )

        val candidate =
            PivotGameSafeExitCandidateFactory
                .createOrNull(
                    scoreRecord(
                        id = 102L,
                        gameType = ScoreGameType.RhythmTiles,
                        outcome = ScoreSessionOutcome.WalkedAway,
                        completedAt = completedAt,
                    ),
                )

        requireNotNull(
            candidate,
        )
        assertEquals(
            SafeExitSource.PivotGame,
            candidate.source,
        )
        assertEquals(
            "RHYTHM_TILES:102",
            candidate.sourceId,
        )
        assertEquals(
            completedAt,
            candidate.completedAt,
        )
    }
    private class RecordingCandidateRecorder(
        vararg results: SafeExitRecordingResult,
    ) : SafeExitCandidateRecorder {
        val candidates = mutableListOf<SafeExitCandidate>()
        private val queuedResults = results.toMutableList()

        override suspend fun record(
            candidate: SafeExitCandidate,
        ): SafeExitRecordingResult {
            candidates += candidate
            return queuedResults.removeAt(0)
        }
    }

    @Test
    fun validSnakeWalkAwayCreatesStableCandidate() {
        val record = scoreRecord(
            id = 303L,
            gameType = ScoreGameType.Snake,
            outcome = ScoreSessionOutcome.WalkedAway,
            validCompletion = true,
        )

        val candidate = PivotGameSafeExitCandidateFactory.create(record)

        assertEquals(SafeExitSource.PivotGame, candidate.source)
        assertEquals("SNAKE:303", candidate.sourceId)
        assertEquals(SafeExitAction.WalkAway, candidate.action)
        assertTrue(candidate.validCompletion)
    }

    @Test
    fun invalidSnakeWalkAwayKeepsInvalidCompletion() {
        /*
         * A short abandoned Snake attempt must stay invalid even after Walk
         * Away, so the existing policy can still reject it as
         * InvalidCompletion. Walk Away is not a reward loophole.
         */
        val record = scoreRecord(
            id = 304L,
            gameType = ScoreGameType.Snake,
            outcome = ScoreSessionOutcome.WalkedAway,
            validCompletion = false,
        )

        val candidate = PivotGameSafeExitCandidateFactory.create(record)

        assertEquals(SafeExitSource.PivotGame, candidate.source)
        assertEquals("SNAKE:304", candidate.sourceId)
        assertFalse(candidate.validCompletion)
    }

    private fun scoreRecord(
        id: Long = 7L,
        gameType: ScoreGameType = ScoreGameType.ReflexOverride,
        outcome: ScoreSessionOutcome = ScoreSessionOutcome.WalkedAway,
        completedAt: LocalDateTime =
            LocalDateTime.of(
                2026,
                4,
                12,
                10,
                30,
            ),
        validCompletion: Boolean = true,
    ): ScoreSessionRecord =
        ScoreSessionRecord(
            id = id,
            gameType = gameType,
            score = 120,
            startedAt = completedAt.minusSeconds(20),
            completedAt = completedAt,
            durationSec = 20,
            outcome = outcome,
            validCompletion = validCompletion,
        )
}