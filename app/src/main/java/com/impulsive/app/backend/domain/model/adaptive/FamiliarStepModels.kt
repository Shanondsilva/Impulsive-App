package com.impulsive.app.backend.domain.model.adaptive

data class FamiliarStepRouteIdentity(
    val intervention: InterventionFamily,
    val protocolId: String,
    val protocolVersion: Int,
    val momentPlanId: String? = null,
    val momentPlanContentRevisionId: String? = null,
) {
    init {
        require(protocolId.isNotBlank())
        require(protocolVersion > 0)
        require((momentPlanId == null) == (momentPlanContentRevisionId == null))
        require(
            intervention == InterventionFamily.MomentPlan || momentPlanId == null,
        )
        if (intervention == InterventionFamily.MomentPlan) {
            require(!momentPlanId.isNullOrBlank())
            require(!momentPlanContentRevisionId.isNullOrBlank())
        }
    }
}

data class FamiliarStepEvidenceRecord(
    val decisionId: String,
    val routeIdentity: FamiliarStepRouteIdentity,
    val momentCue: MomentCue?,
    val feedbackCode: FeedbackCode,
    val engagementOutcome: EngagementOutcome,
    val repeatObservation: RepeatObservation,
    val decisionAtMillis: Long,
    val finalisedAtMillis: Long,
) {
    init {
        require(decisionId.isNotBlank())
        require(decisionAtMillis >= 0L)
        require(finalisedAtMillis >= decisionAtMillis)
    }
}

data class FamiliarStepCandidate(
    val routeIdentity: FamiliarStepRouteIdentity,
    val comparableCount: Int,
    val favourableCount: Int,
    val matchedCue: MomentCue?,
    val mostRecentFavourableAtMillis: Long,
)

enum class FamiliarStepNoMatchReason {
    FirstAttempt,
    PersonalSuggestionsDisabled,
    InsufficientEvidence,
    NoEligibleRoute,
    StaleProtocol,
    StalePlanRevision,
    NoFavourableMajority,
    PrivacyUnsafeEvidence,
    UnsupportedIntervention,
}

sealed interface FamiliarStepMatchResult {
    data class Match(val candidate: FamiliarStepCandidate) : FamiliarStepMatchResult
    data class NoMatch(val reason: FamiliarStepNoMatchReason) : FamiliarStepMatchResult
}

enum class FamiliarStepExplanationCategory { CueMatchedObservedPattern, BroadObservedPattern }
enum class FamiliarStepEvidenceSufficiency { Qualified }
