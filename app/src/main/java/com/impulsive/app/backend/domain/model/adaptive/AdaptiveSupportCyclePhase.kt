package com.impulsive.app.backend.domain.model.adaptive

/**
 * Phase of one protected Moment, derived entirely from the Support Cycle's
 * remaining time.
 *
 * This is never persisted. `initialDurationMillis` and `consumedDurationMillis`
 * are already durable, so the phase can always be reconstructed after process
 * death without storing a second, drift-prone copy of the same fact.
 */
sealed interface AdaptiveSupportCyclePhase {
    /** The opening stretch of the protected Moment. */
    data object DenseGameplay : AdaptiveSupportCyclePhase

    /**
     * The winding-down stretch.
     *
     * [progress] runs from 0.0 at the start of settling to just under 1.0 at
     * its end, letting each game shape its own gradual descent without
     * introducing another timer.
     */
    data class SettlingGameplay(
        val progress: Double,
    ) : AdaptiveSupportCyclePhase {
        init {
            require(progress in 0.0..1.0) {
                "Settling progress must be between zero and one."
            }
        }
    }

    /** The closing stretch, where the user's Moment Plan is presented. */
    data object MomentPlan : AdaptiveSupportCyclePhase

    /** The cycle budget is fully consumed. */
    data object Complete : AdaptiveSupportCyclePhase
}
