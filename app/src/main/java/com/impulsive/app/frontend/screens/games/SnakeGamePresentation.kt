package com.impulsive.app.frontend.screens.games

import com.impulsive.app.backend.domain.game.SnakeCell
import com.impulsive.app.backend.domain.game.SnakeDirection
import com.impulsive.app.backend.domain.game.SnakeGameConfig
import com.impulsive.app.backend.domain.game.SnakeGameResult
import com.impulsive.app.backend.domain.game.SnakeMinimumValidSelfCollisionMillis
import com.impulsive.app.backend.domain.game.SnakeRoundEndReason
import kotlin.math.abs

/**
 * Deterministic presentation rules for Snake. Deliberately free of Compose so
 * the wording and gesture mapping stay unit-testable.
 */

/** Resolves a drag into a single orthogonal direction; there is no diagonal. */
internal fun snakeDirectionFromDrag(
    deltaX: Float,
    deltaY: Float,
): SnakeDirection? {
    if (deltaX == 0f && deltaY == 0f) return null

    return if (abs(deltaX) >= abs(deltaY)) {
        if (deltaX > 0f) SnakeDirection.Right else SnakeDirection.Left
    } else {
        if (deltaY > 0f) SnakeDirection.Down else SnakeDirection.Up
    }
}

/** How two consecutive snake cells sit relative to each other on the board. */
internal enum class SnakeCellConnection {
    Horizontal,
    Vertical,
    WrappedHorizontal,
    WrappedVertical,
    Disconnected,
}

/**
 * Classifies the geometry between two consecutive snake cells.
 *
 * This describes position only, never travel direction, and exists so the
 * renderer can tell an ordinary neighbour from a pair that meets across a
 * wrapped edge — drawing a connector straight between the latter would streak
 * a line across the whole board.
 */
internal fun snakeCellConnection(
    first: SnakeCell,
    second: SnakeCell,
    columns: Int = SnakeGameConfig.DEFAULT_COLUMNS,
    rows: Int = SnakeGameConfig.DEFAULT_ROWS,
): SnakeCellConnection {
    val dx = abs(first.x - second.x)
    val dy = abs(first.y - second.y)

    return when {
        dy == 0 && dx == 1 -> SnakeCellConnection.Horizontal
        dx == 0 && dy == 1 -> SnakeCellConnection.Vertical
        dy == 0 && dx == columns - 1 -> SnakeCellConnection.WrappedHorizontal
        dx == 0 && dy == rows - 1 -> SnakeCellConnection.WrappedVertical
        else -> SnakeCellConnection.Disconnected
    }
}

internal fun snakeResultTitle(result: SnakeGameResult): String = when (result.endReason) {
    SnakeRoundEndReason.BoardCleared -> "Board cleared"
    SnakeRoundEndReason.TimeLimit -> "Snake complete"
    SnakeRoundEndReason.SelfCollision ->
        if (result.validCompletion) "Snake complete" else "Round didn't finish"
}

/**
 * Factual guidance for a round that did not meet the recovery threshold. The
 * wording states what is still needed and never blames the player.
 */
internal fun snakeInvalidCompletionMessage(result: SnakeGameResult): String? {
    if (result.validCompletion) return null

    val tooShort = result.elapsedDurationMillis < SnakeMinimumValidSelfCollisionMillis
    val noFruit = result.fruitsEaten == 0

    return when {
        tooShort && noFruit ->
            "Keep going a little longer and collect at least one fruit for this round to count."

        tooShort -> "Keep going a little longer for this round to count."

        noFruit -> "Collect at least one fruit for this round to count."

        else -> null
    }
}
