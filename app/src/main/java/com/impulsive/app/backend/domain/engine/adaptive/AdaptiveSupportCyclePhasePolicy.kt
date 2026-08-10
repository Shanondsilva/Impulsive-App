package com.impulsive.app.backend.domain.engine.adaptive

import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycle
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCyclePhase
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycleTiming

/**
 * Derives the protected Moment's phase from the Support Cycle's remaining time.
 *
 * The persisted cycle is the single authority. Phase is never read from a
 * wall clock, a game timer, a ViewModel or an animation, so a recreated process
 * resolves exactly the same phase from the same durable remaining time.
 */
object AdaptiveSupportCyclePhasePolicy {
    fun resolve(
        remainingDurationMillis: Long,
    ): AdaptiveSupportCyclePhase {
        require(remainingDurationMillis >= 0L) {
            "Remaining support-cycle duration must not be negative."
        }
        require(
            remainingDurationMillis <= AdaptiveSupportCycleTiming.TotalDurationMillis,
        ) {
            "Remaining support-cycle duration cannot exceed the fixed cycle budget."
        }

        return when {
            remainingDurationMillis >
                AdaptiveSupportCycleTiming.SettlingStartsAtRemainingMillis ->
                AdaptiveSupportCyclePhase.DenseGameplay

            remainingDurationMillis >
                AdaptiveSupportCycleTiming.MomentPlanStartsAtRemainingMillis ->
                AdaptiveSupportCyclePhase.SettlingGameplay(
                    progress = settlingProgress(remainingDurationMillis),
                )

            remainingDurationMillis > 0L ->
                AdaptiveSupportCyclePhase.MomentPlan

            else -> AdaptiveSupportCyclePhase.Complete
        }
    }

    fun resolve(
        cycle: AdaptiveSupportCycle,
    ): AdaptiveSupportCyclePhase = resolve(cycle.remainingDurationMillis)

    /**
     * Fraction of the settling window already elapsed: 0.0 exactly at the start
     * of settling, approaching 1.0 as the Moment Plan threshold is reached.
     */
    private fun settlingProgress(
        remainingDurationMillis: Long,
    ): Double {
        val elapsedInsideSettling =
            AdaptiveSupportCycleTiming.SettlingStartsAtRemainingMillis -
                remainingDurationMillis
        val settlingWindow =
            AdaptiveSupportCycleTiming.SettlingStartsAtRemainingMillis -
                AdaptiveSupportCycleTiming.MomentPlanStartsAtRemainingMillis
        return (elapsedInsideSettling.toDouble() / settlingWindow.toDouble())
            .coerceIn(0.0, 1.0)
    }
}
