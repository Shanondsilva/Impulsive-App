package com.impulsive.app.backend.domain.engine.adaptive

import com.impulsive.app.backend.domain.model.adaptive.AdaptiveDecision
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveReasonCode
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentIntensity

data class AdaptiveDecisionExplanation(
    val decisionId: String,
    val whySuggested: String,
    val factorsUsed: List<String>,
    val factorsNotUsed: List<String>,
    val recommendationPolicyVersion: Int,
    val historicalProtocolDisplay: String?,
)

object AdaptiveDecisionExplanationBuilder {
    fun build(decision: AdaptiveDecision): AdaptiveDecisionExplanation =
        AdaptiveDecisionExplanation(
            decisionId = decision.decisionId,
            whySuggested = reasonExplanation(decision.assignment.reasonCode),
            factorsUsed = applicableFactors(decision),
            factorsNotUsed = FactorsNeverUsed,
            recommendationPolicyVersion = decision.recommendationPolicyVersion,
            historicalProtocolDisplay = historicalProtocolDisplay(decision),
        )

    private fun applicableFactors(decision: AdaptiveDecision): List<String> = buildList {
        add(
            when (decision.assignment.momentIntensity) {
                MomentIntensity.FirstAttempt -> "This was the first protected moment."
                MomentIntensity.RepeatedAttempt -> "This was a repeated protected moment."
            },
        )
        if (decision.assignment.eligibleInterventions.isNotEmpty()) {
            add(
                "Enabled support families: " +
                    decision.assignment.eligibleInterventions
                        .sortedBy(InterventionFamily::ordinal)
                        .joinToString { it.genericName() } +
                    ".",
            )
        }
        decision.momentCue?.let {
            add("The cue you selected: ${it.name.toConsumerWords()}.")
        }
        when (decision.assignment.reasonCode) {
            AdaptiveReasonCode.RecentCompletionPattern ->
                add("Recent completion history.")
            AdaptiveReasonCode.RecentHelpfulFeedback ->
                add("Recent feedback you chose to provide.")
            AdaptiveReasonCode.TimingReceptivity ->
                add("Recent timing feedback.")
            AdaptiveReasonCode.RecentlyRehearsedPlan,
            AdaptiveReasonCode.CueMatchedRecentlyRehearsedPlan ->
                add("Recent practice of this exact Moment Plan version.")
            AdaptiveReasonCode.InterventionFatigueRotation,
            AdaptiveReasonCode.RandomisedExploration ->
                add("Recent suggestion rotation.")
            else -> Unit
        }
    }

    private fun reasonExplanation(reason: AdaptiveReasonCode): String = when (reason) {
        AdaptiveReasonCode.MinimumEffectiveFriction ->
            "The first step stays simple."
        AdaptiveReasonCode.CueMatchedMomentPlan ->
            "This plan matched the cue you selected."
        AdaptiveReasonCode.RecentlyRehearsedPlan,
        AdaptiveReasonCode.CueMatchedRecentlyRehearsedPlan ->
            "You practised this version recently."
        AdaptiveReasonCode.RecentHelpfulFeedback,
        AdaptiveReasonCode.RecentCompletionPattern,
        AdaptiveReasonCode.TimingReceptivity ->
            "This option fitted some of your recent recorded moments."
        AdaptiveReasonCode.RandomisedExploration,
        AdaptiveReasonCode.InterventionFatigueRotation ->
            "Impulsive occasionally varies suggestions."
        AdaptiveReasonCode.OnlyEligibleIntervention ->
            "This was the available enabled support option."
        AdaptiveReasonCode.UserOverride ->
            "You chose a different available support option."
        AdaptiveReasonCode.InsufficientEvidenceExploration,
        AdaptiveReasonCode.StableFallback ->
            "There was not enough history yet, so Impulsive used a stable option."
    }

    private fun historicalProtocolDisplay(decision: AdaptiveDecision): String? {
        val id = decision.assignedProtocolId ?: return null
        val version = decision.assignedProtocolVersion ?: return null
        val historical = runCatching {
            InterventionProtocolRegistry.historical(
                InterventionProtocolId(id),
                InterventionProtocolVersion(version),
            )
        }.getOrNull() ?: return "Historical personal support"
        return if (historical.executableContract == null) {
            "${historical.consumerDisplayName} (historical version)"
        } else {
            historical.consumerDisplayName
        }
    }

    private fun InterventionFamily.genericName(): String = when (this) {
        InterventionFamily.ShortPause -> "Short Pause"
        InterventionFamily.PivotGame -> "Pivot Games"
        InterventionFamily.PivotReading -> "Reset Reading"
        InterventionFamily.MomentPlan -> "Moment Plans"
    }

    private fun String.toConsumerWords(): String =
        replace(Regex("([a-z])([A-Z])"), "$1 $2")

    val FactorsNeverUsed: List<String> = listOf(
        "The protected app or website identity.",
        "A URL or domain.",
        "Journal content.",
        "Your account email.",
        "A cloud behavioural profile.",
    )
}
