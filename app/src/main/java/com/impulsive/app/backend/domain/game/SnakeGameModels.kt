package com.impulsive.app.backend.domain.game

/**
 * Zero-based grid coordinate. Valid x is `0 until columns`, valid y is
 * `0 until rows`. The engine owns the bounds; a cell carries no board size.
 */
data class SnakeCell(
    val x: Int,
    val y: Int,
)

enum class SnakeDirection {
    Up,
    Right,
    Down,
    Left,
}

/**
 * Explicit pairing rather than ordinal arithmetic, so reordering the enum
 * cannot silently break reversal safety.
 */
fun SnakeDirection.isOpposite(other: SnakeDirection): Boolean = when (this) {
    SnakeDirection.Up -> other == SnakeDirection.Down
    SnakeDirection.Down -> other == SnakeDirection.Up
    SnakeDirection.Left -> other == SnakeDirection.Right
    SnakeDirection.Right -> other == SnakeDirection.Left
}

enum class SnakeGamePhase {
    /** Created but the player has not supplied the first directional input. */
    Ready,

    /** A first direction was accepted and movement is active. */
    Playing,

    /** The round has terminally ended. */
    Finished,
}

/**
 * Why a round ended. There is deliberately no wall-collision reason: leaving an
 * edge wraps to the opposite edge and is never terminal.
 */
enum class SnakeRoundEndReason {
    SelfCollision,
    TimeLimit,
    BoardCleared,
}

/**
 * A complete, externally observable snapshot of a round.
 *
 * The head is `snake.first()` and the tail is `snake.last()`.
 */
data class SnakeGameState(
    val phase: SnakeGamePhase,
    val snake: List<SnakeCell>,
    val food: SnakeCell?,
    val direction: SnakeDirection?,
    val queuedDirections: List<SnakeDirection>,
    val fruitsEaten: Int,
    val score: Int,
    val tickIntervalMillis: Long,
    val endReason: SnakeRoundEndReason?,
)
