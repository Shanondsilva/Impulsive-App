package com.impulsive.app.backend.domain.engine.adaptive

import com.impulsive.app.backend.domain.model.adaptive.AdaptiveAssignment
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveOutcomeRecord
import com.impulsive.app.backend.domain.model.adaptive.AdaptivePreferences
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveReasonCode
import com.impulsive.app.backend.domain.model.adaptive.AssignmentMode
import com.impulsive.app.backend.domain.model.adaptive.FeedbackCode
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentCue
import com.impulsive.app.backend.domain.model.adaptive.MomentIntensity
import com.impulsive.app.backend.domain.model.adaptive.MomentPlan
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanRehearsal

data class AdaptiveRecommendationRequest(
    val momentIntensity: MomentIntensity,
    val selectedCue: MomentCue?,
    val preferences: AdaptivePreferences = AdaptivePreferences(),
    val momentPlans: List<MomentPlan> = emptyList(),
    val recentCompletedRehearsals: List<MomentPlanRehearsal> = emptyList(),
    val history: List<AdaptiveOutcomeRecord> = emptyList(),
    val recentActualSelections: List<InterventionFamily> = emptyList(),
    val productEligibleInterventions: Set<InterventionFamily> = RepeatedInterventions,
) {
    companion object {
        val RepeatedInterventions = setOf(
            InterventionFamily.PivotGame,
            InterventionFamily.PivotReading,
            InterventionFamily.MomentPlan,
        )
    }
}

data class AdaptiveRecommendation(
    val assignment: AdaptiveAssignment,
    val utilityByIntervention: Map<InterventionFamily, AdaptiveUtilityBreakdown>,
)

class AdaptiveRecommendationPolicy(
    private val randomisationSource: RandomisationSource,
) {
    fun recommend(request: AdaptiveRecommendationRequest): AdaptiveRecommendation {
        if (request.momentIntensity == MomentIntensity.FirstAttempt) {
            return AdaptiveRecommendation(
                assignment = AdaptiveAssignment(
                    momentIntensity = MomentIntensity.FirstAttempt,
                    assignmentMode = AssignmentMode.MinimumFriction,
                    eligibleInterventions = setOf(InterventionFamily.ShortPause),
                    assignedSuggestion = InterventionFamily.ShortPause,
                    selectionProbability = null,
                    reasonCode = AdaptiveReasonCode.MinimumEffectiveFriction,
                ),
                utilityByIntervention = emptyMap(),
            )
        }

        val validPlans = request.momentPlans.filter {
            it.enabled && AdaptiveModelValidator.isSafeAndValid(it)
        }
        val selectedPlan = selectMomentPlan(
            plans = validPlans,
            selectedCue = request.selectedCue,
            recentCompletedRehearsals = request.recentCompletedRehearsals,
        )
        val selectedPlanRecentlyRehearsed =
            selectedPlan != null &&
                request.recentCompletedRehearsals.any {
                    it.planId == selectedPlan.planId &&
                        it.planContentRevisionId == selectedPlan.contentRevisionId
                }
        val eligible = buildEligibleInterventions(
            request = request,
            hasMomentPlan = selectedPlan != null,
        )
        if (eligible.isEmpty()) {
            return AdaptiveRecommendation(
                assignment = AdaptiveAssignment(
                    momentIntensity = MomentIntensity.RepeatedAttempt,
                    assignmentMode = AssignmentMode.StableFallback,
                    eligibleInterventions = emptySet(),
                    assignedSuggestion = null,
                    selectionProbability = null,
                    reasonCode = AdaptiveReasonCode.StableFallback,
                ),
                utilityByIntervention = emptyMap(),
            )
        }

        val stableEligible = StableRepeatedOrder.filter { it in eligible }
        if (stableEligible.size == 1) {
            val only = stableEligible.single()
            return AdaptiveRecommendation(
                assignment = AdaptiveAssignment(
                    momentIntensity = MomentIntensity.RepeatedAttempt,
                    assignmentMode = AssignmentMode.AdaptiveSuggestion,
                    eligibleInterventions = eligible,
                    assignedSuggestion = only,
                    selectionProbability = null,
                    reasonCode = AdaptiveReasonCode.OnlyEligibleIntervention,
                    momentPlanId = selectedPlan?.planId.takeIf {
                        only == InterventionFamily.MomentPlan
                    },
                    momentPlanUpdatedAtMillis = selectedPlan?.updatedAtMillis.takeIf {
                        only == InterventionFamily.MomentPlan
                    },
                    assignedPlanContentRevisionId = selectedPlan?.contentRevisionId.takeIf {
                        only == InterventionFamily.MomentPlan
                    },
                ),
                utilityByIntervention = emptyMap(),
            )
        }

        if (request.preferences.randomisedExplorationEnabled) {
            val explorationDraw = randomisationSource.nextDouble()
            require(explorationDraw >= 0.0 && explorationDraw < 1.0) {
                "RandomisationSource.nextDouble() must return a value in [0, 1)."
            }
            if (explorationDraw < RandomisedExplorationRate) {
                val assigned = stableEligible[randomisationSource.nextInt(stableEligible.size)]
                return AdaptiveRecommendation(
                    assignment = AdaptiveAssignment(
                        momentIntensity = MomentIntensity.RepeatedAttempt,
                        assignmentMode = AssignmentMode.RandomisedSuggestion,
                        eligibleInterventions = eligible,
                        assignedSuggestion = assigned,
                        selectionProbability = 1.0 / stableEligible.size,
                        reasonCode = AdaptiveReasonCode.RandomisedExploration,
                        momentPlanId = selectedPlan?.planId.takeIf {
                            assigned == InterventionFamily.MomentPlan
                        },
                        momentPlanUpdatedAtMillis = selectedPlan?.updatedAtMillis.takeIf {
                            assigned == InterventionFamily.MomentPlan
                        },
                        assignedPlanContentRevisionId = selectedPlan?.contentRevisionId.takeIf {
                            assigned == InterventionFamily.MomentPlan
                        },
                    ),
                    utilityByIntervention = emptyMap(),
                )
            }
        }

        val momentPlanMatchesCue =
            request.selectedCue != null &&
                selectedPlan?.momentCue == request.selectedCue
        val preferredPlanMatchesCue =
            momentPlanMatchesCue && selectedPlan.preferredForCue
        val utility = stableEligible.associateWith { intervention ->
            AdaptiveUtilityPolicy.score(
                intervention = intervention,
                selectedCue = request.selectedCue,
                history = request.history,
                recentActualSelections = request.recentActualSelections,
                momentPlanMatchesCue =
                    intervention == InterventionFamily.MomentPlan &&
                        momentPlanMatchesCue,
                momentPlanIsPreferredForCue =
                    intervention == InterventionFamily.MomentPlan &&
                        preferredPlanMatchesCue,
            )
        }
        val assigned = highestUtility(
            utility = utility,
            momentPlanMatchesCue = momentPlanMatchesCue,
        )
        val reasonCode = adaptiveReasonCode(
            assigned = assigned,
            utility = utility,
            request = request,
            momentPlanMatchesCue = momentPlanMatchesCue,
            selectedPlanRecentlyRehearsed = selectedPlanRecentlyRehearsed,
        )

        return AdaptiveRecommendation(
            assignment = AdaptiveAssignment(
                momentIntensity = MomentIntensity.RepeatedAttempt,
                assignmentMode = AssignmentMode.AdaptiveSuggestion,
                eligibleInterventions = eligible,
                assignedSuggestion = assigned,
                selectionProbability = null,
                reasonCode = reasonCode,
                momentPlanId = selectedPlan?.planId.takeIf {
                    assigned == InterventionFamily.MomentPlan
                },
                momentPlanUpdatedAtMillis = selectedPlan?.updatedAtMillis.takeIf {
                    assigned == InterventionFamily.MomentPlan
                },
                assignedPlanContentRevisionId = selectedPlan?.contentRevisionId.takeIf {
                    assigned == InterventionFamily.MomentPlan
                },
            ),
            utilityByIntervention = utility,
        )
    }

    private fun buildEligibleInterventions(
        request: AdaptiveRecommendationRequest,
        hasMomentPlan: Boolean,
    ): Set<InterventionFamily> = buildSet {
        if (!request.preferences.personalSuggestionsEnabled) {
            return@buildSet
        }
        if (
            request.preferences.gameSuggestionsEnabled &&
            InterventionFamily.PivotGame in request.productEligibleInterventions
        ) {
            add(InterventionFamily.PivotGame)
        }
        if (
            request.preferences.readingSuggestionsEnabled &&
            InterventionFamily.PivotReading in request.productEligibleInterventions
        ) {
            add(InterventionFamily.PivotReading)
        }
        if (
            request.preferences.momentPlanSuggestionsEnabled &&
            hasMomentPlan &&
            InterventionFamily.MomentPlan in request.productEligibleInterventions
        ) {
            add(InterventionFamily.MomentPlan)
        }
    }

    private fun selectMomentPlan(
        plans: List<MomentPlan>,
        selectedCue: MomentCue?,
        recentCompletedRehearsals: List<MomentPlanRehearsal>,
    ): MomentPlan? = plans.sortedWith(
        compareBy<MomentPlan> {
            val cueMatched = selectedCue != null && it.momentCue == selectedCue
            val general = it.momentCue == null
            val recentlyRehearsed = recentCompletedRehearsals.any { rehearsal ->
                rehearsal.planId == it.planId &&
                    rehearsal.planContentRevisionId == it.contentRevisionId
            }
            when {
                cueMatched && it.preferredForCue -> 0
                cueMatched && recentlyRehearsed -> 1
                cueMatched -> 2
                general && it.preferredForCue -> 3
                general && recentlyRehearsed -> 4
                else -> 5
            }
        }.thenByDescending {
            it.updatedAtMillis
        }.thenBy {
            it.planId
        },
    ).firstOrNull()

    private fun highestUtility(
        utility: Map<InterventionFamily, AdaptiveUtilityBreakdown>,
        momentPlanMatchesCue: Boolean,
        transform: (AdaptiveUtilityBreakdown) -> Double = { it.finalUtility },
    ): InterventionFamily = utility.entries.sortedWith(
        compareByDescending<Map.Entry<InterventionFamily, AdaptiveUtilityBreakdown>> {
            transform(it.value)
        }.thenBy {
            stableTieOrder(
                intervention = it.key,
                momentPlanMatchesCue = momentPlanMatchesCue,
            )
        },
    ).first().key

    private fun adaptiveReasonCode(
        assigned: InterventionFamily,
        utility: Map<InterventionFamily, AdaptiveUtilityBreakdown>,
        request: AdaptiveRecommendationRequest,
        momentPlanMatchesCue: Boolean,
        selectedPlanRecentlyRehearsed: Boolean,
    ): AdaptiveReasonCode {
        if (
            assigned == InterventionFamily.MomentPlan &&
            selectedPlanRecentlyRehearsed
        ) {
            return if (momentPlanMatchesCue) {
                AdaptiveReasonCode.CueMatchedRecentlyRehearsedPlan
            } else {
                AdaptiveReasonCode.RecentlyRehearsedPlan
            }
        }
        if (assigned == InterventionFamily.MomentPlan && momentPlanMatchesCue) {
            return AdaptiveReasonCode.CueMatchedMomentPlan
        }
        val withoutFatigue = highestUtility(
            utility = utility,
            momentPlanMatchesCue = momentPlanMatchesCue,
            transform = { score -> score.finalUtility + score.fatiguePenalty },
        )
        if (withoutFatigue != assigned) {
            return AdaptiveReasonCode.InterventionFatigueRotation
        }
        val withoutReceptivity = highestUtility(
            utility = utility,
            momentPlanMatchesCue = momentPlanMatchesCue,
            transform = { score -> score.finalUtility + score.receptivityPenalty },
        )
        if (withoutReceptivity != assigned) {
            return AdaptiveReasonCode.TimingReceptivity
        }
        val selectedHistory = request.history.filter {
            it.isFinalised && it.actualIntervention == assigned
        }
        if (selectedHistory.any {
                it.feedbackCode == FeedbackCode.Helped ||
                    it.feedbackCode == FeedbackCode.HelpedALittle
            }
        ) {
            return AdaptiveReasonCode.RecentHelpfulFeedback
        }
        if (selectedHistory.any {
                it.engagementOutcome ==
                    com.impulsive.app.backend.domain.model.adaptive.EngagementOutcome.Completed
            }
        ) {
            return AdaptiveReasonCode.RecentCompletionPattern
        }
        return AdaptiveReasonCode.InsufficientEvidenceExploration
    }

    private fun stableTieOrder(
        intervention: InterventionFamily,
        momentPlanMatchesCue: Boolean,
    ): Int = when {
        intervention == InterventionFamily.MomentPlan && momentPlanMatchesCue -> 0
        intervention == InterventionFamily.PivotGame -> 1
        intervention == InterventionFamily.PivotReading -> 2
        intervention == InterventionFamily.MomentPlan -> 3
        else -> 4
    }

    companion object {
        const val RandomisedExplorationRate = 0.25

        private val StableRepeatedOrder = listOf(
            InterventionFamily.PivotGame,
            InterventionFamily.PivotReading,
            InterventionFamily.MomentPlan,
        )
    }
}
