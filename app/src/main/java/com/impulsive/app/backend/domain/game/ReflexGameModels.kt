package com.impulsive.app.backend.domain.game

enum class TargetType { Hit, Decoy }
enum class GameView { Ready, Countdown, Playing, Result, Walked }
enum class ReflexGameLaunchSource { RECOVERY_GAME, TASK_TO_COMPLETE }

data class Target(
    val id: Long,
    val type: TargetType,
    val xFraction: Float,
    val yFraction: Float,
    val sizeDp: Int,
    val colorHex: Long?,
    val createdAtMs: Long,
    val lifetimeMs: Long,
)

data class Flash(
    val id: Long,
    val xFraction: Float,
    val yFraction: Float,
    val text: String,
)

data class GameHistory(
    val pb: Int = 0,
    val prev: Int = 0,
    val bestReactionMs: Int? = null,
    val bestCombo: Int = 0,
)

data class GameResult(
    val score: Int,
    val previousBest: Int,
    val previousScore: Int,
    val bestReactionMs: Int?,
    val maxCombo: Int,
    val hits: Int,
    val misses: Int,
    val difficulty: Int,
    val gameOver: Boolean,
    val durationSec: Int,
    val validCompletion: Boolean,
)

data class DifficultyTier(val lifetimeMs: Long, val spawnMs: Long, val decoyProb: Float)

object ReflexGameConfig {
    val DIFFICULTY = listOf(
        DifficultyTier(1250, 700, 0.10f),
        DifficultyTier(1000, 560, 0.22f),
        DifficultyTier(820, 430, 0.34f),
        DifficultyTier(650, 330, 0.46f),
    )
    val TARGET_COLORS = listOf(0xFFC77DFF, 0xFF4DB8FF, 0xFFFFE45E, 0xFFFF4FA3, 0xFF44FFB2, 0xFFFF8A3D)
    const val ROUND_SECONDS = 90
    val DIFFICULTY_STEP_SECONDS: Float = ROUND_SECONDS / DIFFICULTY.size.toFloat()
    const val WALK_AWAY_BONUS = 2000
    const val MAX_BOMBS = 5
}
