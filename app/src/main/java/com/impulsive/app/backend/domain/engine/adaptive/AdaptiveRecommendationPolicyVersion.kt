package com.impulsive.app.backend.domain.engine.adaptive

object AdaptiveRecommendationPolicyVersion {
    const val Current = 1

    fun isValid(value: Int): Boolean = value > 0
}
