package com.impulsive.app.backend.domain.engine.adaptive

import com.impulsive.app.backend.domain.model.adaptive.AdaptiveDecision
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveReasonCode
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily

data class AdaptiveReplayScenario(
    val scenarioId: String,
    val request: AdaptiveRecommendationRequest,
    val recordedAssignedFamily: InterventionFamily?,
    val recordedReason: AdaptiveReasonCode,
    val recordedPolicyVersion: Int,
    val deterministicDraw: Double = 0.99,
    val deterministicIndex: Int = 0,
) {
    init {
        require(scenarioId.matches(Regex("[a-z0-9_-]{3,64}")))
        require(deterministicDraw >= 0.0 && deterministicDraw < 1.0)
        require(deterministicIndex >= 0)
    }
}

sealed interface AdaptiveReplayResult {
    data class Compared(
        val difference: AdaptiveReplayDifference,
    ) : AdaptiveReplayResult

    data class InsufficientContext(
        val scenarioId: String,
    ) : AdaptiveReplayResult
}

data class AdaptiveReplayDifference(
    val scenarioId: String,
    val recordedAssignedFamily: InterventionFamily?,
    val replayedAssignedFamily: InterventionFamily?,
    val recordedReason: AdaptiveReasonCode,
    val replayedReason: AdaptiveReasonCode,
    val recordedPolicyVersion: Int,
    val candidatePolicyVersion: Int,
    val selectionDiffers: Boolean,
)

data class AdaptiveReplayAggregate(
    val replayableScenarios: Int,
    val insufficientContextScenarios: Int,
    val familyDifferencePercentage: Double,
    val reasonCodeDistribution: Map<AdaptiveReasonCode, Int>,
    val interventionFamilyDistribution: Map<InterventionFamily, Int>,
)

fun interface AdaptiveReplayPolicy {
    fun replay(scenario: AdaptiveReplayScenario):
        com.impulsive.app.backend.domain.model.adaptive.AdaptiveAssignment
}

class CurrentAdaptiveReplayPolicy : AdaptiveReplayPolicy {
    override fun replay(
        scenario: AdaptiveReplayScenario,
    ): com.impulsive.app.backend.domain.model.adaptive.AdaptiveAssignment {
        val random = object : RandomisationSource {
            override fun nextDouble(): Double = scenario.deterministicDraw
            override fun nextInt(bound: Int): Int =
                scenario.deterministicIndex.coerceAtMost(bound - 1)
        }
        return AdaptiveRecommendationPolicy(random)
            .recommend(scenario.request)
            .assignment
    }
}

class AdaptivePolicyReplayEngine(
    private val candidatePolicyVersion: Int,
    private val candidatePolicy: AdaptiveReplayPolicy,
) {
    init {
        require(candidatePolicyVersion > 0)
    }

    fun replay(scenario: AdaptiveReplayScenario): AdaptiveReplayResult {
        val replayed = candidatePolicy.replay(scenario)
        return AdaptiveReplayResult.Compared(
            AdaptiveReplayDifference(
                scenarioId = scenario.scenarioId,
                recordedAssignedFamily = scenario.recordedAssignedFamily,
                replayedAssignedFamily = replayed.assignedSuggestion,
                recordedReason = scenario.recordedReason,
                replayedReason = replayed.reasonCode,
                recordedPolicyVersion = scenario.recordedPolicyVersion,
                candidatePolicyVersion = candidatePolicyVersion,
                selectionDiffers =
                    scenario.recordedAssignedFamily != replayed.assignedSuggestion,
            ),
        )
    }

    fun aggregate(results: List<AdaptiveReplayResult>): AdaptiveReplayAggregate {
        val compared = results.mapNotNull {
            (it as? AdaptiveReplayResult.Compared)?.difference
        }
        val insufficient = results.size - compared.size
        return AdaptiveReplayAggregate(
            replayableScenarios = compared.size,
            insufficientContextScenarios = insufficient,
            familyDifferencePercentage =
                if (compared.isEmpty()) {
                    0.0
                } else {
                    compared.count(AdaptiveReplayDifference::selectionDiffers) *
                        100.0 / compared.size
                },
            reasonCodeDistribution = compared
                .groupingBy(AdaptiveReplayDifference::replayedReason)
                .eachCount(),
            interventionFamilyDistribution = compared
                .mapNotNull(AdaptiveReplayDifference::replayedAssignedFamily)
                .groupingBy { it }
                .eachCount(),
        )
    }
}

data class AdaptiveHistoricalReplayContext(
    val request: AdaptiveRecommendationRequest,
    val allPolicyInputsReconstructedExactly: Boolean,
)

object AdaptiveHistoricalReplayReconstructor {
    fun reconstruct(
        decision: AdaptiveDecision,
        context: AdaptiveHistoricalReplayContext?,
    ): AdaptiveReplayResult {
        if (
            context == null ||
            !context.allPolicyInputsReconstructedExactly ||
            context.request.momentIntensity != decision.assignment.momentIntensity ||
            context.request.selectedCue != decision.momentCue ||
            context.request.productEligibleInterventions !=
            decision.assignment.eligibleInterventions
        ) {
            return AdaptiveReplayResult.InsufficientContext(decision.decisionId)
        }
        return AdaptivePolicyReplayEngine(
            candidatePolicyVersion = AdaptiveRecommendationPolicyVersion.Current,
            candidatePolicy = CurrentAdaptiveReplayPolicy(),
        ).replay(
            AdaptiveReplayScenario(
                scenarioId = decision.decisionId,
                request = context.request,
                recordedAssignedFamily = decision.assignment.assignedSuggestion,
                recordedReason = decision.assignment.reasonCode,
                recordedPolicyVersion = decision.recommendationPolicyVersion,
            ),
        )
    }
}
