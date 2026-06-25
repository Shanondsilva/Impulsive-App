package com.impulsive.app.backend.domain.game

object RhythmTilesConfig {
    const val ROUND_SECONDS = 90
    const val LANES = 4
    const val MAX_MISSES = 3
    const val BASE_FALL_MS = 2000L
    const val START_SPEED_MULTIPLIER = 1.1f
    const val END_SPEED_MULTIPLIER = 1.65f
    const val HIT_POINTS = 10
    const val EMPTY_TAP_PENALTY = 25
    const val FINISH_BONUS = 500
    const val NO_MISS_BONUS = 1000
    const val WALK_AWAY_BONUS = 250
}

/**
 * A falling tile. The UI derives the tile's vertical position from
 * spawnAtMs and fallDurationMs against the current uptime, so the
 * ViewModel never needs to push per-frame position updates.
 */
data class RhythmTile(
    val id: Long,
    val lane: Int,
    val semitone: Int,
    val spawnAtMs: Long,
    val fallDurationMs: Long,
)

data class RhythmTilesResult(
    val score: Int,
    val previousBest: Int,
    val previousScore: Int,
    val maxCombo: Int,
    val hits: Int,
    val misses: Int,
    val loopsCompleted: Int,
    val gameOver: Boolean,
    val durationSec: Int,
    val validCompletion: Boolean,
)
