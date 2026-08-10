package com.impulsive.app.backend.domain.game

/** Full standalone Snake round allocation. */
const val SnakeStandaloneRoundDurationMillis = 90_000L

/** A self-collision only counts as recovery below this elapsed time. */
const val SnakeMinimumValidSelfCollisionMillis = 20_000L

/** A self-collision also needs at least this much real engagement. */
const val SnakeMinimumValidSelfCollisionFruits = 1

enum class SnakeGameView {
    Ready,
    Playing,
    Paused,
    Result,
    Walked,
}

data class SnakeGameHistory(
    val personalBest: Int = 0,
    val previousScore: Int? = null,
) {
    init {
        require(personalBest >= 0) { "personalBest must not be negative" }
        require(previousScore == null || previousScore >= 0) {
            "previousScore must not be negative"
        }
    }
}

/**
 * Official history only moves on a valid completion, so a short abandoned
 * attempt can never become a personal best. The attempt still shows its own
 * score on the result screen.
 */
fun SnakeGameHistory.afterResult(
    score: Int,
    validCompletion: Boolean,
): SnakeGameHistory {
    if (!validCompletion) return this

    return copy(
        personalBest = maxOf(personalBest, score),
        previousScore = score,
    )
}

data class SnakeGameResult(
    val score: Int,
    val fruitsEaten: Int,
    val previousBest: Int,
    val previousScore: Int?,
    val durationSec: Int,
    val elapsedDurationMillis: Long,
    val endReason: SnakeRoundEndReason,
    val validCompletion: Boolean,
) {
    init {
        require(score >= 0) { "score must not be negative" }
        require(fruitsEaten >= 0) { "fruitsEaten must not be negative" }
        require(previousBest >= 0) { "previousBest must not be negative" }
        require(previousScore == null || previousScore >= 0) {
            "previousScore must not be negative"
        }
        require(durationSec >= 0) { "durationSec must not be negative" }
        require(elapsedDurationMillis >= 0L) {
            "elapsedDurationMillis must not be negative"
        }
    }
}

/**
 * Whether this result's Game Store play has reached durable storage.
 *
 * Transient UI orchestration only: the DataStore receipt is the real authority,
 * so this is deliberately never saved to SavedStateHandle.
 */
enum class SnakeGameStorePersistenceState {
    NotRequired,
    Pending,
    Persisted,
    RetryableFailure,
}

data class SnakeGameUiState(
    val view: SnakeGameView = SnakeGameView.Ready,
    val gameState: SnakeGameState? = null,
    val elapsedDurationMillis: Long = 0L,
    val timeLeftSeconds: Int = 90,
    val result: SnakeGameResult? = null,
    val history: SnakeGameHistory = SnakeGameHistory(),
    /** Mirrors the session's after-rating so a restored result stays faithful. */
    val urgeAfterRating: Int? = null,
    val gameStorePersistenceState: SnakeGameStorePersistenceState =
        SnakeGameStorePersistenceState.NotRequired,
) {
    init {
        require(urgeAfterRating == null || urgeAfterRating in 0..10) {
            "urgeAfterRating must be between 0 and 10"
        }
    }
}

/**
 * A result may only be acted on once its Game Store play is durable, because
 * navigating away clears the ViewModel and cancels any in-flight write.
 */
val SnakeGameUiState.isGameStoreResultDurable: Boolean
    get() = view != SnakeGameView.Result ||
        gameStorePersistenceState == SnakeGameStorePersistenceState.Persisted
