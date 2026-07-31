package com.impulsive.app.backend.domain.engine.adaptive

import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily

object AdaptiveFatiguePolicy {
    const val FullPenalty = 1.0
    const val PartialPenalty = 0.5

    fun penalty(
        intervention: InterventionFamily,
        recentActualSelections: List<InterventionFamily>,
    ): Double {
        val latestThree = recentActualSelections.take(3)
        return when {
            latestThree.take(2).size == 2 &&
                latestThree.take(2).all { it == intervention } -> FullPenalty

            latestThree.count { it == intervention } >= 2 -> PartialPenalty
            else -> 0.0
        }
    }
}
