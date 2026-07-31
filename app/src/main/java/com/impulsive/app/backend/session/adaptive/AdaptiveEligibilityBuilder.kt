package com.impulsive.app.backend.session.adaptive

import com.impulsive.app.backend.domain.engine.adaptive.AdaptiveModelValidator
import com.impulsive.app.backend.domain.model.adaptive.AdaptivePreferences
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentPlan

data class AdaptiveEligibility(
    val productEligibleInterventions: Set<InterventionFamily>,
    val validEnabledMomentPlans: List<MomentPlan>,
)

object AdaptiveEligibilityBuilder {
    fun build(
        request: AdaptiveProtectionIncidentRequest,
        preferences: AdaptivePreferences,
        plans: List<MomentPlan>,
    ): AdaptiveEligibility {
        val allowed = request.currentlyAllowedInterventions
        val productEligible = buildSet {
            if (
                request.gameProductEligible &&
                preferences.gameSuggestionsEnabled &&
                InterventionFamily.PivotGame in allowed
            ) {
                add(InterventionFamily.PivotGame)
            }
            if (
                request.readingProductEligible &&
                preferences.readingSuggestionsEnabled &&
                InterventionFamily.PivotReading in allowed
            ) {
                add(InterventionFamily.PivotReading)
            }
            if (
                request.momentPlansProductEligible &&
                preferences.momentPlanSuggestionsEnabled &&
                InterventionFamily.MomentPlan in allowed
            ) {
                add(InterventionFamily.MomentPlan)
            }
        }
        return AdaptiveEligibility(
            productEligibleInterventions = productEligible,
            validEnabledMomentPlans = plans.filter {
                it.enabled && AdaptiveModelValidator.isSafeAndValid(it)
            },
        )
    }
}

