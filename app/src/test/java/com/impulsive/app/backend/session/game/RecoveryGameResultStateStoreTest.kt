package com.impulsive.app.backend.session.game

import androidx.lifecycle.SavedStateHandle
import com.impulsive.app.backend.domain.game.RecoveryGameLaunchContext
import com.impulsive.app.backend.domain.model.score.ScoreGameType
import com.impulsive.app.backend.domain.model.score.ScoreSessionOutcome
import com.impulsive.app.backend.domain.model.score.ScoreSessionRecord
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryGameResultStateStoreTest {
    @Test
    fun resultSnapshotRejectsAnotherCycle() {
        val store =
            RecoveryGameResultStateStore(
                SavedStateHandle(),
            )

        store.save(snapshot())

        val restored =
            store.restore(
                launchContext =
                    launch(
                        cycleId = "cycle-b",
                    ),
                expectedGameType =
                    ScoreGameType.ReflexOverride,
            )

        assertNull(restored)
    }

    @Test
    fun resultSnapshotRejectsAnotherDecision() {
        val store =
            RecoveryGameResultStateStore(
                SavedStateHandle(),
            )

        store.save(snapshot())

        val restored =
            store.restore(
                launchContext =
                    RecoveryGameLaunchContext.SupportCycle(
                        cycleId = "cycle-a",
                        decisionId = "another-decision",
                        gameType = ScoreGameType.ReflexOverride,
                        maxDurationMillis = 90_000L,
                    ),
                expectedGameType =
                    ScoreGameType.ReflexOverride,
            )

        assertNull(restored)
    }

    @Test
    fun resultSnapshotRejectsAnotherGameType() {
        val store =
            RecoveryGameResultStateStore(
                SavedStateHandle(),
            )

        store.save(snapshot())

        val restored =
            store.restore(
                launchContext =
                    RecoveryGameLaunchContext.SupportCycle(
                        cycleId = "cycle-a",
                        decisionId = "decision",
                        gameType = ScoreGameType.RhythmTiles,
                        maxDurationMillis = 90_000L,
                    ),
                expectedGameType =
                    ScoreGameType.RhythmTiles,
            )

        assertNull(restored)
    }

    @Test
    fun resultSnapshotDoesNotRestoreForStandaloneLaunch() {
        val store =
            RecoveryGameResultStateStore(
                SavedStateHandle(),
            )

        store.save(snapshot())

        val restored =
            store.restore(
                launchContext =
                    RecoveryGameLaunchContext.Standalone,
                expectedGameType =
                    ScoreGameType.ReflexOverride,
            )

        assertNull(restored)
    }

    @Test
    fun resultSnapshotSurvivesSavedStateHandleRecreation() {
        val original = snapshot()
        val firstHandle = SavedStateHandle()

        RecoveryGameResultStateStore(
            firstHandle,
        ).save(original)

        val persisted =
            checkNotNull(
                firstHandle.get<RecoveryGameResultSnapshot>(
                    RecoveryGameResultStateStore.SnapshotKey,
                ),
            )

        val recreatedHandle =
            SavedStateHandle(
                mapOf(
                    RecoveryGameResultStateStore.SnapshotKey to
                        persisted,
                ),
            )

        val restored =
            RecoveryGameResultStateStore(
                recreatedHandle,
            ).restore(
                launchContext = launch(),
                expectedGameType =
                    ScoreGameType.ReflexOverride,
            )

        assertEquals(
            "cycle-a",
            restored?.cycleId,
        )
        assertEquals(
            "decision",
            restored?.decisionId,
        )
        assertEquals(
            ScoreGameType.ReflexOverride.id,
            restored?.gameTypeId,
        )
        assertEquals(
            SupportCycleGameTerminalOutcome.Completed,
            restored?.supportOutcomeOrNull(),
        )
        assertEquals(
            40_000L,
            restored?.supportElapsedDurationMillis,
        )
        assertEquals(
            123L,
            restored?.activeSessionId,
        )
        assertEquals(
            999L,
            restored?.lastRecordedSession?.id,
        )
        assertTrue(
            restored?.payload is
                RecoveryGameResultPayload.Reflex,
        )
    }

    @Test
    fun scoreSessionSnapshotRestoresOriginalRecord() {
        val original = scoreSession()
        val restored =
            ScoreSessionSnapshot
                .from(original)
                .toRecordOrNull()

        assertEquals(
            original,
            restored,
        )
    }

    @Test
    fun corruptScoreSessionSnapshotFailsClosed() {
        val corrupt =
            ScoreSessionSnapshot(
                id = 1L,
                gameTypeId = "NOT_A_GAME",
                score = 100,
                startedAtIso = "not-a-date",
                completedAtIso = "not-a-date",
                durationSec = 10,
                urgeBefore = null,
                urgeAfter = null,
                outcomeId = "NOT_AN_OUTCOME",
                validCompletion = true,
            )

        assertNull(
            corrupt.toRecordOrNull(),
        )
    }

    @Test
    fun snapshotPayloadIsSerializable() {
        val original = snapshot()

        val bytes =
            ByteArrayOutputStream().use { output ->
                ObjectOutputStream(output).use {
                    it.writeObject(original)
                }
                output.toByteArray()
            }

        val restored =
            ObjectInputStream(
                ByteArrayInputStream(bytes),
            ).use {
                it.readObject() as
                    RecoveryGameResultSnapshot
            }

        assertEquals(
            original,
            restored,
        )
    }

    @Test
    fun rhythmTilesSnapshotSurvivesSavedStateHandleRecreation() {
        val original =
            RecoveryGameResultSnapshot(
                cycleId = "rhythm-cycle",
                decisionId = "rhythm-decision",
                gameTypeId = ScoreGameType.RhythmTiles.id,
                supportOutcomeName =
                    SupportCycleGameTerminalOutcome.Completed.name,
                supportElapsedDurationMillis = 60_000L,
                activeSessionId = 456L,
                sessionStartedAtIso =
                    LocalDateTime.of(2026, 8, 5, 12, 0).toString(),
                urgeBeforeRating = 8,
                urgeAfterRating = 4,
                lastRecordedSession = null,
                payload =
                    RecoveryGameResultPayload.RhythmTiles(
                        selectedSongId = "TWINKLE_TWINKLE",
                        score = 700,
                        combo = 6,
                        lives = 2,
                        historyPersonalBest = 900,
                        historyPrevious = 650,
                        historyBestReactionMs = null,
                        historyBestCombo = 8,
                        resultScore = 700,
                        resultPreviousBest = 900,
                        resultPreviousScore = 650,
                        resultMaxCombo = 6,
                        resultHits = 20,
                        resultMisses = 1,
                        resultLoopsCompleted = 1,
                        resultGameOver = false,
                        resultDurationSec = 60,
                        resultValidCompletion = true,
                    ),
            )

        val firstHandle = SavedStateHandle()

        RecoveryGameResultStateStore(firstHandle).save(original)

        val persisted =
            checkNotNull(
                firstHandle.get<RecoveryGameResultSnapshot>(
                    RecoveryGameResultStateStore.SnapshotKey,
                ),
            )

        val recreatedHandle =
            SavedStateHandle(
                mapOf(
                    RecoveryGameResultStateStore.SnapshotKey to persisted,
                ),
            )

        val restored =
            RecoveryGameResultStateStore(recreatedHandle).restore(
                launchContext =
                    RecoveryGameLaunchContext.SupportCycle(
                        cycleId = "rhythm-cycle",
                        decisionId = "rhythm-decision",
                        gameType = ScoreGameType.RhythmTiles,
                        maxDurationMillis = 90_000L,
                    ),
                expectedGameType = ScoreGameType.RhythmTiles,
            )

        assertEquals(original, restored)

        assertTrue(
            restored?.payload is RecoveryGameResultPayload.RhythmTiles,
        )
    }

    @Test
    fun blockCascadeSnapshotSurvivesSavedStateHandleRecreation() {
        val original =
            RecoveryGameResultSnapshot(
                cycleId = "block-cycle",
                decisionId = "block-decision",
                gameTypeId = ScoreGameType.BlockCascade.id,
                supportOutcomeName =
                    SupportCycleGameTerminalOutcome.Completed.name,
                supportElapsedDurationMillis = 90_000L,
                activeSessionId = 789L,
                sessionStartedAtIso =
                    LocalDateTime.of(2026, 8, 5, 13, 0).toString(),
                urgeBeforeRating = 7,
                urgeAfterRating = 3,
                lastRecordedSession = null,
                payload =
                    RecoveryGameResultPayload.BlockCascade(
                        secondsPlayed = 90,
                        linesCleared = 2,
                        validMoves = 14,
                        completed = true,
                        failed = false,
                        failureReason = null,
                    ),
            )

        val firstHandle = SavedStateHandle()

        RecoveryGameResultStateStore(firstHandle).save(original)

        val persisted =
            checkNotNull(
                firstHandle.get<RecoveryGameResultSnapshot>(
                    RecoveryGameResultStateStore.SnapshotKey,
                ),
            )

        val recreatedHandle =
            SavedStateHandle(
                mapOf(
                    RecoveryGameResultStateStore.SnapshotKey to persisted,
                ),
            )

        val restored =
            RecoveryGameResultStateStore(recreatedHandle).restore(
                launchContext =
                    RecoveryGameLaunchContext.SupportCycle(
                        cycleId = "block-cycle",
                        decisionId = "block-decision",
                        gameType = ScoreGameType.BlockCascade,
                        maxDurationMillis = 90_000L,
                    ),
                expectedGameType = ScoreGameType.BlockCascade,
            )

        assertEquals(original, restored)

        assertTrue(
            restored?.payload is RecoveryGameResultPayload.BlockCascade,
        )
    }

    @Test
    fun skylineResetSnapshotSurvivesSavedStateHandleRecreation() {
        val startedAt = LocalDateTime.of(2026, 8, 5, 14, 0)

        val recordedSession =
            ScoreSessionRecord(
                id = 987L,
                gameType = ScoreGameType.SkylineReset,
                score = 270,
                startedAt = startedAt,
                completedAt = startedAt.plusSeconds(90),
                durationSec = 90,
                urgeBefore = 8,
                urgeAfter = 3,
                outcome = ScoreSessionOutcome.Completed,
                validCompletion = true,
            )

        val original =
            RecoveryGameResultSnapshot(
                cycleId = "skyline-cycle",
                decisionId = "skyline-decision",
                gameTypeId = ScoreGameType.SkylineReset.id,
                supportOutcomeName =
                    SupportCycleGameTerminalOutcome.Completed.name,
                supportElapsedDurationMillis = 90_000L,
                activeSessionId = recordedSession.id,
                sessionStartedAtIso = startedAt.toString(),
                urgeBeforeRating = 8,
                urgeAfterRating = 3,
                lastRecordedSession = ScoreSessionSnapshot.from(recordedSession),
                payload =
                    RecoveryGameResultPayload.SkylineReset(
                        floorsBuilt = 4,
                        perfectCount = 2,
                        secondsPlayed = 90,
                        completed = true,
                        failed = false,
                        controlPointsBanked = 4,
                        resultRecorded = true,
                        perfectPointsBanked = true,
                    ),
            )

        val firstHandle = SavedStateHandle()

        RecoveryGameResultStateStore(firstHandle).save(original)

        val persisted =
            checkNotNull(
                firstHandle.get<RecoveryGameResultSnapshot>(
                    RecoveryGameResultStateStore.SnapshotKey,
                ),
            )

        val recreatedHandle =
            SavedStateHandle(
                mapOf(
                    RecoveryGameResultStateStore.SnapshotKey to persisted,
                ),
            )

        val restored =
            RecoveryGameResultStateStore(recreatedHandle).restore(
                launchContext =
                    RecoveryGameLaunchContext.SupportCycle(
                        cycleId = "skyline-cycle",
                        decisionId = "skyline-decision",
                        gameType = ScoreGameType.SkylineReset,
                        maxDurationMillis = 90_000L,
                    ),
                expectedGameType = ScoreGameType.SkylineReset,
            )

        assertEquals(original, restored)

        assertTrue(
            restored?.payload is RecoveryGameResultPayload.SkylineReset,
        )
    }

    @Test
    fun clearRemovesSavedSnapshot() {
        val store =
            RecoveryGameResultStateStore(
                SavedStateHandle(),
            )

        store.save(snapshot())
        store.clear()

        assertNull(
            store.restore(
                launchContext = launch(),
                expectedGameType =
                    ScoreGameType.ReflexOverride,
            ),
        )
    }

    // ------------------------------------------------------------------
    // Snake
    // ------------------------------------------------------------------

    @Test
    fun snakeSnapshotSurvivesSavedStateRecreation() {
        val handle = SavedStateHandle()
        RecoveryGameResultStateStore(handle).save(snakeSnapshot())

        val recreated = SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) })

        val restored = RecoveryGameResultStateStore(recreated).restore(
            launchContext = snakeLaunch(),
            expectedGameType = ScoreGameType.Snake,
        )

        assertEquals(ScoreGameType.Snake.id, restored?.gameTypeId)
        assertEquals("SNAKE", restored?.gameTypeId)
    }

    @Test
    fun snakePayloadFieldsSurviveExactly() {
        val store = RecoveryGameResultStateStore(SavedStateHandle())
        store.save(snakeSnapshot())

        val restored = store.restore(
            launchContext = snakeLaunch(),
            expectedGameType = ScoreGameType.Snake,
        )
        val payload = restored?.payload as RecoveryGameResultPayload.Snake

        assertEquals(120, payload.resultScore)
        assertEquals(12, payload.resultFruitsEaten)
        assertEquals(120, payload.historyPersonalBest)
        assertEquals(120, payload.historyPreviousScore)
        assertEquals(0, payload.resultPreviousBest)
        assertNull(payload.resultPreviousScore)
        assertEquals(45, payload.resultDurationSec)
        assertEquals(45_000L, payload.resultElapsedDurationMillis)
        assertEquals("TimeLimit", payload.resultEndReasonName)
        assertTrue(payload.resultValidCompletion)
        assertEquals(55_000L, restored.supportElapsedDurationMillis)
    }

    @Test
    fun snakeScoreSessionSnapshotDecodesToSnake() {
        val store = RecoveryGameResultStateStore(SavedStateHandle())
        store.save(snakeSnapshot())

        val restored = store.restore(
            launchContext = snakeLaunch(),
            expectedGameType = ScoreGameType.Snake,
        )
        val record = restored?.lastRecordedSession?.toRecordOrNull()

        assertEquals(ScoreGameType.Snake, record?.gameType)
        assertEquals(303L, record?.id)
        assertEquals(120, record?.score)
    }

    @Test
    fun snakeSnapshotDoesNotRestoreForReflex() {
        val store = RecoveryGameResultStateStore(SavedStateHandle())
        store.save(snakeSnapshot())

        assertNull(
            store.restore(
                launchContext = snakeLaunch(),
                expectedGameType = ScoreGameType.ReflexOverride,
            ),
        )
    }

    @Test
    fun reflexSnapshotDoesNotRestoreForSnake() {
        val store = RecoveryGameResultStateStore(SavedStateHandle())
        store.save(snapshot())

        assertNull(
            store.restore(
                launchContext = launch(),
                expectedGameType = ScoreGameType.Snake,
            ),
        )
    }

    @Test
    fun snakeSnapshotSurvivesJavaSerialization() {
        val original = snakeSnapshot()

        val bytes = ByteArrayOutputStream().also { output ->
            ObjectOutputStream(output).use { it.writeObject(original) }
        }.toByteArray()

        val decoded = ObjectInputStream(ByteArrayInputStream(bytes)).use {
            it.readObject() as RecoveryGameResultSnapshot
        }

        assertEquals(original, decoded)
        assertEquals(ScoreGameType.Snake.id, decoded.gameTypeId)
        assertEquals(
            120,
            (decoded.payload as RecoveryGameResultPayload.Snake).resultScore,
        )
    }

    private fun snakeLaunch(): RecoveryGameLaunchContext.SupportCycle =
        RecoveryGameLaunchContext.SupportCycle(
            cycleId = "snake-cycle",
            decisionId = "snake-decision",
            gameType = ScoreGameType.Snake,
            maxDurationMillis = 60_000L,
        )

    private fun snakeSnapshot(): RecoveryGameResultSnapshot {
        val startedAt = LocalDateTime.of(2026, 8, 4, 12, 0)
        val record = ScoreSessionRecord(
            id = 303L,
            gameType = ScoreGameType.Snake,
            score = 120,
            startedAt = startedAt,
            completedAt = startedAt.plusSeconds(45),
            durationSec = 45,
            urgeBefore = 7,
            urgeAfter = 3,
            outcome = ScoreSessionOutcome.Completed,
            validCompletion = true,
        )

        return RecoveryGameResultSnapshot(
            cycleId = "snake-cycle",
            decisionId = "snake-decision",
            gameTypeId = ScoreGameType.Snake.id,
            supportOutcomeName = SupportCycleGameTerminalOutcome.TimedOut.name,
            // Total support consumption exceeds this attempt's 45s duration.
            supportElapsedDurationMillis = 55_000L,
            activeSessionId = 303L,
            sessionStartedAtIso = startedAt.toString(),
            urgeBeforeRating = 7,
            urgeAfterRating = 3,
            lastRecordedSession = ScoreSessionSnapshot.from(record),
            payload = RecoveryGameResultPayload.Snake(
                historyPersonalBest = 120,
                historyPreviousScore = 120,
                resultScore = 120,
                resultFruitsEaten = 12,
                resultPreviousBest = 0,
                resultPreviousScore = null,
                resultDurationSec = 45,
                resultElapsedDurationMillis = 45_000L,
                resultEndReasonName = "TimeLimit",
                resultValidCompletion = true,
            ),
        )
    }

    private fun launch(
        cycleId: String = "cycle-a",
    ): RecoveryGameLaunchContext.SupportCycle =
        RecoveryGameLaunchContext.SupportCycle(
            cycleId = cycleId,
            decisionId = "decision",
            gameType = ScoreGameType.ReflexOverride,
            maxDurationMillis = 90_000L,
        )

    private fun snapshot(): RecoveryGameResultSnapshot {
        val record = scoreSession()

        return RecoveryGameResultSnapshot(
            cycleId = "cycle-a",
            decisionId = "decision",
            gameTypeId =
                ScoreGameType.ReflexOverride.id,
            supportOutcomeName =
                SupportCycleGameTerminalOutcome
                    .Completed
                    .name,
            supportElapsedDurationMillis = 40_000L,
            activeSessionId = 123L,
            sessionStartedAtIso =
                record.startedAt.toString(),
            urgeBeforeRating = 8,
            urgeAfterRating = 4,
            lastRecordedSession =
                ScoreSessionSnapshot.from(record),
            payload =
                RecoveryGameResultPayload.Reflex(
                    score = 500,
                    combo = 4,
                    lives = 5,
                    historyPersonalBest = 500,
                    historyPrevious = 420,
                    historyBestReactionMs = 250,
                    historyBestCombo = 4,
                    resultScore = 500,
                    resultPreviousBest = 450,
                    resultPreviousScore = 420,
                    resultBestReactionMs = 250,
                    resultMaxCombo = 4,
                    resultHits = 10,
                    resultMisses = 1,
                    resultDifficulty = 2,
                    resultGameOver = false,
                    resultDurationSec = 40,
                    resultValidCompletion = true,
                ),
        )
    }

    private fun scoreSession(): ScoreSessionRecord {
        val startedAt =
            LocalDateTime.of(
                2026,
                8,
                4,
                12,
                0,
            )

        return ScoreSessionRecord(
            id = 999L,
            gameType =
                ScoreGameType.ReflexOverride,
            score = 500,
            startedAt = startedAt,
            completedAt =
                startedAt.plusSeconds(40),
            durationSec = 40,
            urgeBefore = 8,
            urgeAfter = 4,
            outcome =
                ScoreSessionOutcome.Completed,
            validCompletion = true,
        )
    }
}
