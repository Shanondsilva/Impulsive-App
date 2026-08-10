package com.impulsive.app.backend.domain.usecase

import com.impulsive.app.backend.domain.model.score.ScoreGameType
import com.impulsive.app.backend.domain.model.score.ScoreSessionOutcome
import com.impulsive.app.backend.domain.model.score.ScoreSessionRecord
import java.io.File
import java.time.LocalDateTime
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSelectionEngineTest {
    @Test
    fun noHistoryAndInsufficientEvidenceExploreDeterministically() {
        val eligible = listOf(ScoreGameType.Snake, ScoreGameType.BlockCascade)
        assertEquals(
            ScoreGameType.Snake,
            select(emptyList(), eligible = eligible),
        )
        assertEquals(
            ScoreGameType.BlockCascade,
            select(listOf(session(ScoreGameType.Snake)), eligible = eligible),
        )
    }

    @Test
    fun successfulGameMayRepeatThroughThreeAssignmentsThenRotates() {
        val sessions = evidenceForEveryGame()
        repeat(3) { priorCount ->
            assertEquals(
                ScoreGameType.Snake,
                select(sessions, served = List(priorCount) { ScoreGameType.Snake }),
            )
        }
        assertNotEquals(
            ScoreGameType.Snake,
            select(sessions, served = List(3) { ScoreGameType.Snake }),
        )
    }

    @Test
    fun failureAbandonmentInvalidCompletionAndPoorFeedbackRotateImmediately() {
        val adverse = listOf(
            session(ScoreGameType.Snake, outcome = ScoreSessionOutcome.Replayed),
            session(ScoreGameType.Snake, outcome = ScoreSessionOutcome.Abandoned),
            session(ScoreGameType.Snake, valid = false),
            session(ScoreGameType.Snake, urgeBefore = 5, urgeAfter = 6),
        )
        adverse.forEach { latest ->
            val history = evidenceForEveryGame(oldestFirst = true) +
                latest.copy(completedAt = base.plusMinutes(20))
            assertNotEquals(ScoreGameType.Snake, select(history))
        }
    }

    @Test
    fun favourableEvidenceIsPreferredWithInjectedRandomAndSingleGameIsSafe() {
        val history = evidenceForEveryGame().map { record ->
            if (record.gameType == ScoreGameType.Snake) record
            else record.copy(outcome = ScoreSessionOutcome.Abandoned)
        }
        val first = select(history, random = ZeroRandom())
        val second = select(history, random = ZeroRandom())
        assertEquals(ScoreGameType.Snake, first)
        assertEquals(first, second)
        assertEquals(
            ScoreGameType.SkylineReset,
            select(history, eligible = listOf(ScoreGameType.SkylineReset)),
        )
    }

    @Test
    fun servedHistoryPersistenceIsBoundedAndCatalogContainsAllFourGames() {
        val source = File(
            "src/main/java/com/impulsive/app/backend/data/local/preferences/ServedGamesDataSource.kt",
        ).readText()
        assertTrue(source.contains("takeLast(MaxStoredServedGames)"))
        assertEquals(4, GameSelectionEngine.candidates.size)
        assertTrue(GameSelectionEngine.candidates.contains(ScoreGameType.RhythmTiles))
    }

    private fun select(
        sessions: List<ScoreSessionRecord>,
        served: List<ScoreGameType> = emptyList(),
        eligible: List<ScoreGameType> = GameSelectionEngine.candidates,
        random: Random = ZeroRandom(),
    ) = GameSelectionEngine.selectNextGame(
        sessions = sessions,
        urgeEvents = emptyList(),
        recentlyServed = served,
        eligibleGames = eligible,
        random = random,
    )

    private fun evidenceForEveryGame(oldestFirst: Boolean = false): List<ScoreSessionRecord> {
        val values = GameSelectionEngine.candidates.mapIndexed { index, game ->
            session(game).copy(completedAt = base.plusMinutes(index.toLong()))
        }
        return if (oldestFirst) values else values.reversed()
    }

    private fun session(
        game: ScoreGameType,
        outcome: ScoreSessionOutcome = ScoreSessionOutcome.Completed,
        valid: Boolean = true,
        urgeBefore: Int? = null,
        urgeAfter: Int? = null,
    ) = ScoreSessionRecord(
        gameType = game,
        score = 100,
        startedAt = base.minusMinutes(2),
        completedAt = base,
        durationSec = 90,
        urgeBefore = urgeBefore,
        urgeAfter = urgeAfter,
        outcome = outcome,
        validCompletion = valid,
    )

    private class ZeroRandom : Random() {
        override fun nextBits(bitCount: Int): Int = 0
    }

    private companion object {
        val base: LocalDateTime = LocalDateTime.of(2026, 1, 1, 12, 0)
    }

    @Test
    fun activeCandidatesAreTheSnakeEraFour() {
        assertEquals(
            listOf(
                ScoreGameType.Snake,
                ScoreGameType.BlockCascade,
                ScoreGameType.SkylineReset,
                ScoreGameType.RhythmTiles,
            ),
            GameSelectionEngine.candidates,
        )
        assertFalse(ScoreGameType.ReflexOverride in GameSelectionEngine.candidates)
    }

    @Test
    fun historicalReflexEvidenceDoesNotMakeSnakePlayed() {
        /*
         * Reflex history belongs to the retired game. With Snake the only
         * eligible option, a completed Reflex session must not make it look
         * already played and divert selection elsewhere.
         */
        val selected = GameSelectionEngine.selectNextGame(
            sessions = listOf(
                session(ScoreGameType.ReflexOverride, ScoreSessionOutcome.Completed),
            ),
            urgeEvents = emptyList(),
            recentlyServed = emptyList(),
            eligibleGames = listOf(ScoreGameType.Snake),
            random = Random(0),
        )

        assertEquals(ScoreGameType.Snake, selected)
    }
}
