package com.impulsive.app.backend.domain.protectioncoach

import com.impulsive.app.backend.domain.model.adaptive.AdaptiveReasonCode
import com.impulsive.app.backend.domain.model.adaptive.FeedbackCode
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily

data class OnboardingColdStartPriorState(
    val genuineRootProtectedMomentCount: Int = 0,
    val substantiveFeedbackCount: Int = 0,
    val evidenceQualityReachedEarlyPattern: Boolean = false,
)

data class OnboardingColdStartPriorRequest(
    val eligibleInterventions: Set<InterventionFamily>,
    val tiedCandidates: List<InterventionFamily>,
    val disabledInterventions: Set<InterventionFamily> = emptySet(),
    val preferredFamilies: Set<InterventionFamily>,
    val hasStrongRealEvidence: Boolean,
    val wrongTimingEvidencePresent: Boolean,
    val cueMatchedMomentPlanPresent: Boolean,
    val isRandomisedExploration: Boolean,
    val state: OnboardingColdStartPriorState,
)

data class OnboardingColdStartPriorResult(
    val selected: InterventionFamily?,
    val affectedTieBreak: Boolean,
    val explanation: String?,
    val reasonCode: AdaptiveReasonCode?,
)

object OnboardingColdStartPriorPolicy {
    fun select(request: OnboardingColdStartPriorRequest): OnboardingColdStartPriorResult {
        if (isExpired(request.state) ||
            request.isRandomisedExploration ||
            request.hasStrongRealEvidence ||
            request.wrongTimingEvidencePresent ||
            request.cueMatchedMomentPlanPresent ||
            request.tiedCandidates.size < 2
        ) {
            return OnboardingColdStartPriorResult(null, false, null, null)
        }
        val selected = request.tiedCandidates.firstOrNull {
            it in request.preferredFamilies &&
                it in request.eligibleInterventions &&
                it !in request.disabledInterventions
        } ?: return OnboardingColdStartPriorResult(null, false, null, null)

        return OnboardingColdStartPriorResult(
            selected = selected,
            affectedTieBreak = true,
            explanation = "This option matched a preference you chose during setup.",
            reasonCode = AdaptiveReasonCode.InsufficientEvidenceExploration,
        )
    }

    fun isExpired(state: OnboardingColdStartPriorState): Boolean =
        state.genuineRootProtectedMomentCount >= 10 ||
            state.substantiveFeedbackCount >= 3 ||
            state.evidenceQualityReachedEarlyPattern

    fun shouldCountFeedback(code: FeedbackCode): Boolean =
        code != FeedbackCode.NotProvided
}
