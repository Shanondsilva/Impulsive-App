package com.impulsive.app.frontend.screens.games

import com.impulsive.app.backend.domain.game.SnakeCell
import com.impulsive.app.backend.domain.game.SnakeDirection
import com.impulsive.app.backend.domain.game.SnakeGameResult
import com.impulsive.app.backend.domain.game.SnakeRoundEndReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SnakeGamePresentationTest {

    // ------------------------------------------------------------------
    // Drag direction
    // ------------------------------------------------------------------

    @Test
    fun `a rightward drag moves right`() {
        assertEquals(SnakeDirection.Right, snakeDirectionFromDrag(12f, 3f))
    }

    @Test
    fun `a leftward drag moves left`() {
        assertEquals(SnakeDirection.Left, snakeDirectionFromDrag(-12f, 3f))
    }

    @Test
    fun `a downward drag moves down`() {
        assertEquals(SnakeDirection.Down, snakeDirectionFromDrag(3f, 12f))
    }

    @Test
    fun `an upward drag moves up`() {
        assertEquals(SnakeDirection.Up, snakeDirectionFromDrag(3f, -12f))
    }

    @Test
    fun `an exactly diagonal drag resolves horizontally`() {
        // Ties favour the horizontal axis; there is no diagonal direction.
        assertEquals(SnakeDirection.Right, snakeDirectionFromDrag(10f, 10f))
    }

    @Test
    fun `a zero drag produces no direction`() {
        assertNull(snakeDirectionFromDrag(0f, 0f))
    }

    // ------------------------------------------------------------------
    // Cell connection
    // ------------------------------------------------------------------

    @Test
    fun `adjacent cells in a row connect horizontally`() {
        assertEquals(
            SnakeCellConnection.Horizontal,
            snakeCellConnection(SnakeCell(4, 7), SnakeCell(5, 7)),
        )
    }

    @Test
    fun `adjacent cells in a column connect vertically`() {
        assertEquals(
            SnakeCellConnection.Vertical,
            snakeCellConnection(SnakeCell(4, 7), SnakeCell(4, 8)),
        )
    }

    @Test
    fun `opposite side columns are a horizontal wrap in both orders`() {
        // Never an ordinary neighbour: drawing straight between these would
        // streak a line across the whole board.
        assertEquals(
            SnakeCellConnection.WrappedHorizontal,
            snakeCellConnection(SnakeCell(0, 7), SnakeCell(17, 7)),
        )
        assertEquals(
            SnakeCellConnection.WrappedHorizontal,
            snakeCellConnection(SnakeCell(17, 7), SnakeCell(0, 7)),
        )
    }

    @Test
    fun `opposite side rows are a vertical wrap in both orders`() {
        assertEquals(
            SnakeCellConnection.WrappedVertical,
            snakeCellConnection(SnakeCell(4, 0), SnakeCell(4, 23)),
        )
        assertEquals(
            SnakeCellConnection.WrappedVertical,
            snakeCellConnection(SnakeCell(4, 23), SnakeCell(4, 0)),
        )
    }

    @Test
    fun `a diagonal pair is disconnected`() {
        assertEquals(
            SnakeCellConnection.Disconnected,
            snakeCellConnection(SnakeCell(1, 1), SnakeCell(2, 2)),
        )
    }

    @Test
    fun `an identical pair is disconnected`() {
        assertEquals(
            SnakeCellConnection.Disconnected,
            snakeCellConnection(SnakeCell(3, 3), SnakeCell(3, 3)),
        )
    }

    @Test
    fun `distant cells in the same row are disconnected`() {
        assertEquals(
            SnakeCellConnection.Disconnected,
            snakeCellConnection(SnakeCell(2, 7), SnakeCell(9, 7)),
        )
    }

    // ------------------------------------------------------------------
    // Result title
    // ------------------------------------------------------------------

    @Test
    fun `an early self collision did not finish the round`() {
        assertEquals(
            "Round didn't finish",
            snakeResultTitle(
                result(
                    endReason = SnakeRoundEndReason.SelfCollision,
                    validCompletion = false,
                ),
            ),
        )
    }

    @Test
    fun `a valid self collision completes the round`() {
        assertEquals(
            "Snake complete",
            snakeResultTitle(
                result(
                    endReason = SnakeRoundEndReason.SelfCollision,
                    validCompletion = true,
                ),
            ),
        )
    }

    @Test
    fun `surviving the allocation completes the round`() {
        assertEquals(
            "Snake complete",
            snakeResultTitle(
                result(endReason = SnakeRoundEndReason.TimeLimit, validCompletion = true),
            ),
        )
    }

    @Test
    fun `clearing the board has its own title`() {
        assertEquals(
            "Board cleared",
            snakeResultTitle(
                result(endReason = SnakeRoundEndReason.BoardCleared, validCompletion = true),
            ),
        )
    }

    @Test
    fun `no result title blames the player`() {
        val blaming = listOf("fail", "lost", "game over", "poor", "bad")

        SnakeRoundEndReason.entries.forEach { endReason ->
            listOf(true, false).forEach { valid ->
                val title = snakeResultTitle(result(endReason, valid)).lowercase()

                blaming.forEach { word ->
                    assert(!title.contains(word)) { "\"$title\" contains \"$word\"" }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Invalid completion guidance
    // ------------------------------------------------------------------

    @Test
    fun `a short round with no fruit asks for both`() {
        assertEquals(
            "Keep going a little longer and collect at least one fruit for this round to count.",
            snakeInvalidCompletionMessage(
                result(
                    endReason = SnakeRoundEndReason.SelfCollision,
                    validCompletion = false,
                    elapsedDurationMillis = 5_000L,
                    fruitsEaten = 0,
                ),
            ),
        )
    }

    @Test
    fun `a short round with fruit only asks for more time`() {
        assertEquals(
            "Keep going a little longer for this round to count.",
            snakeInvalidCompletionMessage(
                result(
                    endReason = SnakeRoundEndReason.SelfCollision,
                    validCompletion = false,
                    elapsedDurationMillis = 5_000L,
                    fruitsEaten = 1,
                ),
            ),
        )
    }

    @Test
    fun `a long round with no fruit only asks for fruit`() {
        assertEquals(
            "Collect at least one fruit for this round to count.",
            snakeInvalidCompletionMessage(
                result(
                    endReason = SnakeRoundEndReason.SelfCollision,
                    validCompletion = false,
                    elapsedDurationMillis = 25_000L,
                    fruitsEaten = 0,
                ),
            ),
        )
    }

    @Test
    fun `a valid result needs no guidance`() {
        assertNull(
            snakeInvalidCompletionMessage(
                result(endReason = SnakeRoundEndReason.TimeLimit, validCompletion = true),
            ),
        )
    }

    private fun result(
        endReason: SnakeRoundEndReason,
        validCompletion: Boolean,
        elapsedDurationMillis: Long = 45_000L,
        fruitsEaten: Int = 5,
    ) = SnakeGameResult(
        score = fruitsEaten * 10,
        fruitsEaten = fruitsEaten,
        previousBest = 0,
        previousScore = null,
        durationSec = (elapsedDurationMillis / 1_000L).toInt(),
        elapsedDurationMillis = elapsedDurationMillis,
        endReason = endReason,
        validCompletion = validCompletion,
    )
}
