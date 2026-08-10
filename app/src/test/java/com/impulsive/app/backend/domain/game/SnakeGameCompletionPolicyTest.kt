package com.impulsive.app.backend.domain.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SnakeGameCompletionPolicyTest {

    @Test
    fun `self collision just below the time threshold is not a valid completion`() {
        assertFalse(
            SnakeGameCompletionPolicy.isValidCompletion(
                endReason = SnakeRoundEndReason.SelfCollision,
                elapsedDurationMillis = 19_999L,
                fruitsEaten = 1,
            ),
        )
    }

    @Test
    fun `self collision at the time threshold without fruit is not a valid completion`() {
        assertFalse(
            SnakeGameCompletionPolicy.isValidCompletion(
                endReason = SnakeRoundEndReason.SelfCollision,
                elapsedDurationMillis = 20_000L,
                fruitsEaten = 0,
            ),
        )
    }

    @Test
    fun `self collision at the time threshold with one fruit is a valid completion`() {
        assertTrue(
            SnakeGameCompletionPolicy.isValidCompletion(
                endReason = SnakeRoundEndReason.SelfCollision,
                elapsedDurationMillis = 20_000L,
                fruitsEaten = 1,
            ),
        )
    }

    @Test
    fun `self collision above both thresholds is a valid completion`() {
        assertTrue(
            SnakeGameCompletionPolicy.isValidCompletion(
                endReason = SnakeRoundEndReason.SelfCollision,
                elapsedDurationMillis = 25_000L,
                fruitsEaten = 1,
            ),
        )
        assertTrue(
            SnakeGameCompletionPolicy.isValidCompletion(
                endReason = SnakeRoundEndReason.SelfCollision,
                elapsedDurationMillis = 25_000L,
                fruitsEaten = 8,
            ),
        )
    }

    @Test
    fun `a short attempt with fruit is still not a valid completion`() {
        assertFalse(
            SnakeGameCompletionPolicy.isValidCompletion(
                endReason = SnakeRoundEndReason.SelfCollision,
                elapsedDurationMillis = 12_000L,
                fruitsEaten = 2,
            ),
        )
        assertFalse(
            SnakeGameCompletionPolicy.isValidCompletion(
                endReason = SnakeRoundEndReason.SelfCollision,
                elapsedDurationMillis = 3_000L,
                fruitsEaten = 0,
            ),
        )
    }

    @Test
    fun `surviving the allocation is always valid even with no fruit`() {
        assertTrue(
            SnakeGameCompletionPolicy.isValidCompletion(
                endReason = SnakeRoundEndReason.TimeLimit,
                elapsedDurationMillis = 30_000L,
                fruitsEaten = 0,
            ),
        )
        assertTrue(
            SnakeGameCompletionPolicy.isValidCompletion(
                endReason = SnakeRoundEndReason.TimeLimit,
                elapsedDurationMillis = 90_000L,
                fruitsEaten = 0,
            ),
        )
    }

    @Test
    fun `clearing the board is always a valid completion`() {
        assertTrue(
            SnakeGameCompletionPolicy.isValidCompletion(
                endReason = SnakeRoundEndReason.BoardCleared,
                elapsedDurationMillis = 5_000L,
                fruitsEaten = 0,
            ),
        )
    }

    @Test
    fun `negative elapsed duration is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            SnakeGameCompletionPolicy.isValidCompletion(
                endReason = SnakeRoundEndReason.TimeLimit,
                elapsedDurationMillis = -1L,
                fruitsEaten = 0,
            )
        }
    }

    @Test
    fun `negative fruit count is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            SnakeGameCompletionPolicy.isValidCompletion(
                endReason = SnakeRoundEndReason.TimeLimit,
                elapsedDurationMillis = 0L,
                fruitsEaten = -1,
            )
        }
    }

    // ------------------------------------------------------------------
    // History
    // ------------------------------------------------------------------

    @Test
    fun `an invalid result never becomes official history`() {
        val history = SnakeGameHistory(personalBest = 120, previousScore = 80)

        val next = history.afterResult(score = 200, validCompletion = false)

        assertEquals(120, next.personalBest)
        assertEquals(80, next.previousScore)
        assertEquals(history, next)
    }

    @Test
    fun `a valid lower score updates previous score but keeps the larger best`() {
        val history = SnakeGameHistory(personalBest = 120, previousScore = 80)

        val next = history.afterResult(score = 30, validCompletion = true)

        assertEquals(120, next.personalBest)
        assertEquals(30, next.previousScore)
    }

    @Test
    fun `a valid higher score updates both best and previous score`() {
        val history = SnakeGameHistory(personalBest = 120, previousScore = 80)

        val next = history.afterResult(score = 250, validCompletion = true)

        assertEquals(250, next.personalBest)
        assertEquals(250, next.previousScore)
    }

    @Test
    fun `the first valid result populates empty history`() {
        val next = SnakeGameHistory().afterResult(score = 50, validCompletion = true)

        assertEquals(50, next.personalBest)
        assertEquals(50, next.previousScore)
    }

    @Test
    fun `history rejects negative values`() {
        assertThrows(IllegalArgumentException::class.java) {
            SnakeGameHistory(personalBest = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SnakeGameHistory(previousScore = -1)
        }
    }
}
