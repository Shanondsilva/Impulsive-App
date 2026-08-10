package com.impulsive.app.backend.domain.game

/**
 * Decides whether a finished Snake round counts as a genuine recovery
 * completion. The rule is deliberately based on fruit count and elapsed
 * foreground time rather than score, so it stays readable as engagement.
 */
object SnakeGameCompletionPolicy {
    fun isValidCompletion(
        endReason: SnakeRoundEndReason,
        elapsedDurationMillis: Long,
        fruitsEaten: Int,
    ): Boolean {
        require(elapsedDurationMillis >= 0L) {
            "elapsedDurationMillis must not be negative"
        }
        require(fruitsEaten >= 0) { "fruitsEaten must not be negative" }

        return when (endReason) {
            // Surviving the whole allocation always counts, even with no fruit.
            SnakeRoundEndReason.TimeLimit -> true

            SnakeRoundEndReason.BoardCleared -> true

            SnakeRoundEndReason.SelfCollision ->
                elapsedDurationMillis >= SnakeMinimumValidSelfCollisionMillis &&
                    fruitsEaten >= SnakeMinimumValidSelfCollisionFruits
        }
    }
}
