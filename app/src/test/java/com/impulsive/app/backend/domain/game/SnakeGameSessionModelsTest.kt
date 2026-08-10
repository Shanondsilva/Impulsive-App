package com.impulsive.app.backend.domain.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SnakeGameSessionModelsTest {

    @Test
    fun `a fresh ui state has no urge rating`() {
        assertNull(SnakeGameUiState().urgeAfterRating)
    }

    @Test
    fun `the urge rating accepts the whole scale`() {
        (0..10).forEach { rating ->
            assertEquals(rating, SnakeGameUiState(urgeAfterRating = rating).urgeAfterRating)
        }
    }

    @Test
    fun `a negative urge rating is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            SnakeGameUiState(urgeAfterRating = -1)
        }
    }

    @Test
    fun `an urge rating above the scale is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            SnakeGameUiState(urgeAfterRating = 11)
        }
    }

    @Test
    fun `the default ui state stays on the ready view`() {
        val state = SnakeGameUiState()

        assertEquals(SnakeGameView.Ready, state.view)
        assertNull(state.result)
        assertEquals(90, state.timeLeftSeconds)
    }

    @Test
    fun `a fresh ui state requires no store persistence`() {
        assertEquals(
            SnakeGameStorePersistenceState.NotRequired,
            SnakeGameUiState().gameStorePersistenceState,
        )
        assertTrue(SnakeGameUiState().isGameStoreResultDurable)
    }

    @Test
    fun `a result is durable only once its store play is persisted`() {
        val result = SnakeGameResult(
            score = 120,
            fruitsEaten = 12,
            previousBest = 0,
            previousScore = null,
            durationSec = 62,
            elapsedDurationMillis = 62_000L,
            endReason = SnakeRoundEndReason.TimeLimit,
            validCompletion = true,
        )

        fun state(persistence: SnakeGameStorePersistenceState) = SnakeGameUiState(
            view = SnakeGameView.Result,
            result = result,
            gameStorePersistenceState = persistence,
        )

        assertFalse(state(SnakeGameStorePersistenceState.Pending).isGameStoreResultDurable)
        assertFalse(
            state(SnakeGameStorePersistenceState.RetryableFailure).isGameStoreResultDurable,
        )
        assertTrue(state(SnakeGameStorePersistenceState.Persisted).isGameStoreResultDurable)
    }
}
