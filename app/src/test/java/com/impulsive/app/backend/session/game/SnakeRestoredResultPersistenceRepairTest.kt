package com.impulsive.app.backend.session.game

import com.impulsive.app.backend.domain.game.SnakeGameHistory
import com.impulsive.app.backend.domain.game.SnakeGameResult
import com.impulsive.app.backend.domain.game.SnakeRoundEndReason
import com.impulsive.app.backend.domain.model.score.ScoreGameType
import com.impulsive.app.backend.domain.model.score.ScoreSessionOutcome
import com.impulsive.app.backend.domain.model.score.ScoreSessionRecord
import java.time.LocalDateTime
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SnakeRestoredResultPersistenceRepairTest {

    @Test
    fun `a valid restored result re-records the score session`() = runBlocking {
        val scores = mutableListOf<ScoreSessionRecord>()
        val repair = repair(scoreWriter = { scores += it })

        val repaired = repair.repair(
            result = result(validCompletion = true),
            history = SnakeGameHistory(personalBest = 120, previousScore = 120),
            record = record(),
        )

        assertTrue(repaired)
        assertEquals(1, scores.size)
        assertEquals(303L, scores.single().id)
        assertEquals(ScoreGameType.Snake, scores.single().gameType)
    }

    @Test
    fun `a valid restored result re-saves history`() = runBlocking {
        val histories = mutableListOf<SnakeGameHistory>()
        val repair = repair(historyWriter = { histories += it })
        val history = SnakeGameHistory(personalBest = 120, previousScore = 120)

        val repaired = repair.repair(
            result = result(validCompletion = true),
            history = history,
            record = record(),
        )

        assertTrue(repaired)
        assertEquals(listOf(history), histories)
    }

    @Test
    fun `an invalid restored result still re-records the truthful score session`() = runBlocking {
        val scores = mutableListOf<ScoreSessionRecord>()
        val repair = repair(scoreWriter = { scores += it })

        val repaired = repair.repair(
            result = result(validCompletion = false),
            history = SnakeGameHistory(),
            record = record(validCompletion = false),
        )

        assertTrue(repaired)
        assertEquals(1, scores.size)
        assertFalse(scores.single().validCompletion)
    }

    @Test
    fun `an invalid restored result never rewrites history`() = runBlocking {
        val histories = mutableListOf<SnakeGameHistory>()
        val repair = repair(historyWriter = { histories += it })

        repair.repair(
            result = result(validCompletion = false),
            history = SnakeGameHistory(personalBest = 500),
            record = record(validCompletion = false),
        )

        assertTrue("invalid attempts must not touch official history", histories.isEmpty())
    }

    @Test
    fun `a failing score writer reports failure`() = runBlocking {
        val histories = mutableListOf<SnakeGameHistory>()
        val repair = repair(
            scoreWriter = { error("score store unavailable") },
            historyWriter = { histories += it },
        )

        val repaired = repair.repair(
            result = result(validCompletion = true),
            history = SnakeGameHistory(personalBest = 120),
            record = record(),
        )

        assertFalse(repaired)
        // History must not be claimed as repaired when the score write failed.
        assertTrue(histories.isEmpty())
    }

    @Test
    fun `a failing history writer reports failure for a valid result`() = runBlocking {
        val repair = repair(historyWriter = { error("history store unavailable") })

        val repaired = repair.repair(
            result = result(validCompletion = true),
            history = SnakeGameHistory(personalBest = 120),
            record = record(),
        )

        assertFalse(repaired)
    }

    @Test
    fun `repeated repairs reuse the same session identity`() = runBlocking {
        val scores = mutableListOf<ScoreSessionRecord>()
        val repair = repair(scoreWriter = { scores += it })
        val record = record()

        repeat(3) {
            repair.repair(
                result = result(validCompletion = true),
                history = SnakeGameHistory(personalBest = 120, previousScore = 120),
                record = record,
            )
        }

        assertEquals(3, scores.size)
        // The score store replaces by ID, so repeats stay idempotent.
        assertEquals(setOf(303L), scores.map { it.id }.toSet())
    }

    @Test
    fun `cancellation is rethrown rather than swallowed`() {
        val repair = repair(scoreWriter = { throw CancellationException("scope closed") })

        assertThrows(CancellationException::class.java) {
            runBlocking {
                repair.repair(
                    result = result(validCompletion = true),
                    history = SnakeGameHistory(personalBest = 120),
                    record = record(),
                )
            }
        }
    }

    private fun repair(
        scoreWriter: suspend (ScoreSessionRecord) -> Unit = {},
        historyWriter: suspend (SnakeGameHistory) -> Unit = {},
    ) = SnakeRestoredResultPersistenceRepair(
        scoreWriter = SnakeRestoredScoreWriter { scoreWriter(it) },
        historyWriter = SnakeRestoredHistoryWriter { historyWriter(it) },
    )

    private fun result(validCompletion: Boolean) = SnakeGameResult(
        score = 120,
        fruitsEaten = 12,
        previousBest = 0,
        previousScore = null,
        durationSec = 45,
        elapsedDurationMillis = 45_000L,
        endReason = if (validCompletion) {
            SnakeRoundEndReason.TimeLimit
        } else {
            SnakeRoundEndReason.SelfCollision
        },
        validCompletion = validCompletion,
    )

    private fun record(validCompletion: Boolean = true) = ScoreSessionRecord(
        id = 303L,
        gameType = ScoreGameType.Snake,
        score = 120,
        startedAt = LocalDateTime.of(2026, 1, 1, 10, 0),
        completedAt = LocalDateTime.of(2026, 1, 1, 10, 1),
        durationSec = 45,
        urgeBefore = null,
        urgeAfter = null,
        outcome = if (validCompletion) {
            ScoreSessionOutcome.Completed
        } else {
            ScoreSessionOutcome.Abandoned
        },
        validCompletion = validCompletion,
    )
}
