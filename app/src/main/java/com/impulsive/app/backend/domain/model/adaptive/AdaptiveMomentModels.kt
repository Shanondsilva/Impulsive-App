package com.impulsive.app.backend.domain.model.adaptive

enum class InterventionFamily(val eligibilityBit: Int) {
    ShortPause(1 shl 0),
    PivotGame(1 shl 1),
    PivotReading(1 shl 2),
    MomentPlan(1 shl 3),
}

enum class MomentIntensity {
    FirstAttempt,
    RepeatedAttempt,
}

enum class AssignmentMode {
    MinimumFriction,
    RandomisedSuggestion,
    AdaptiveSuggestion,
    StableFallback,
    UserChosen,
}

enum class AdaptiveReasonCode {
    MinimumEffectiveFriction,
    CueMatchedMomentPlan,
    RecentlyRehearsedPlan,
    CueMatchedRecentlyRehearsedPlan,
    RecentHelpfulFeedback,
    RecentCompletionPattern,
    InterventionFatigueRotation,
    TimingReceptivity,
    InsufficientEvidenceExploration,
    RandomisedExploration,
    OnlyEligibleIntervention,
    StableFallback,
    UserOverride,
}

enum class MomentCue {
    Boredom,
    Stress,
    BeingAlone,
    Tiredness,
    AvoidingSomething,
    AutomaticHabit,
}

enum class FeedbackCode {
    Helped,
    HelpedALittle,
    DidNotHelp,
    WrongTiming,
    NotProvided,
}

enum class EngagementOutcome {
    Completed,
    Dismissed,
    StartedNotCompleted,
    NotStarted,
}

enum class RepeatObservation {
    NoRepeatDetected,
    RepeatDetected,
    NotFinalised,
}

enum class AdaptiveSourceKind {
    App,
    Website,
    ExplicitUserSupport,
}

enum class MomentPlanActionType {
    TextOnly,
    OpenImpulsiveDestination,
    LaunchSelectedApp,
}

enum class ImpulsiveDestination(val storageValue: String) {
    Focus("focus"),
    Journal("journal"),
    PivotGames("pivot_games"),
    ResetReading("reset_reading"),
}

object AdaptiveMomentLimits {
    const val MomentWindowMinutes = 20
    const val RecentEvidenceLimit = 30
    const val CueMatchedEvidenceMinimum = 4
    const val MaximumEnabledPlans = 6
    const val PlanTitleCharacters = 60
    const val PlanActionCharacters = 160
    const val PlanFutureCueCharacters = 180
}

data class MomentPlan(
    val planId: String,
    val title: String,
    val momentCue: MomentCue?,
    val actionText: String,
    val futureCueText: String,
    val actionType: MomentPlanActionType,
    val actionTarget: String?,
    val enabled: Boolean,
    val preferredForCue: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val rehearsedAtMillis: Long? = null,
    val contentRevisionId: String =
        com.impulsive.app.backend.domain.engine.adaptive.MomentPlanContentRevisionIds.Unspecified,
)

data class AdaptivePreferences(
    val personalSuggestionsEnabled: Boolean = true,
    val gameSuggestionsEnabled: Boolean = true,
    val readingSuggestionsEnabled: Boolean = true,
    val momentPlanSuggestionsEnabled: Boolean = true,
    val randomisedExplorationEnabled: Boolean = true,
    val privateScreenProtectionEnabled: Boolean = true,
    val historyRetentionPolicy:
        com.impulsive.app.backend.domain.engine.adaptive.AdaptiveHistoryRetentionPolicy =
        com.impulsive.app.backend.domain.engine.adaptive.AdaptiveHistoryRetentionPolicy.SixMonths,
    val pathShiftEnabled: Boolean = true,
)

data class AdaptiveOutcomeRecord(
    val decisionId: String,
    val actualIntervention: InterventionFamily?,
    val selectedCue: MomentCue?,
    val feedbackCode: FeedbackCode,
    val engagementOutcome: EngagementOutcome,
    val repeatObservation: RepeatObservation,
    val decisionAtMillis: Long,
    val observationFinalisedAtMillis: Long?,
) {
    val isFinalised: Boolean
        get() = observationFinalisedAtMillis != null &&
            repeatObservation != RepeatObservation.NotFinalised
}

data class AdaptiveAssignment(
    val momentIntensity: MomentIntensity,
    val assignmentMode: AssignmentMode,
    val eligibleInterventions: Set<InterventionFamily>,
    val assignedSuggestion: InterventionFamily?,
    val selectionProbability: Double?,
    val reasonCode: AdaptiveReasonCode,
    val momentPlanId: String? = null,
    val momentPlanUpdatedAtMillis: Long? = null,
    val assignedPlanContentRevisionId: String? = null,
    val actualPlanContentRevisionId: String? = null,
    val actualIntervention: InterventionFamily? = null,
    val userOverrodeSuggestion: Boolean = false,
) {
    val eligibleInterventionsMask: Int
        get() = eligibleInterventions.fold(0) { mask, family ->
            mask or family.eligibilityBit
        }

    fun recordActualChoice(choice: InterventionFamily): AdaptiveAssignment =
        copy(
            actualIntervention = choice,
            userOverrodeSuggestion = assignedSuggestion != null && choice != assignedSuggestion,
        )
}

data class AdaptiveDecision(
    val decisionId: String,
    val protectionIncidentToken: String,
    val sourceKind: AdaptiveSourceKind,
    val createdAtMillis: Long,
    val momentWindowStartedAtMillis: Long,
    val momentCue: MomentCue?,
    val baselineUrgeRating: Int?,
    val assignment: AdaptiveAssignment,
    val presentedAtMillis: Long? = null,
    val startedAtMillis: Long? = null,
    val completedAtMillis: Long? = null,
    val dismissedAtMillis: Long? = null,
    val feedbackCode: FeedbackCode = FeedbackCode.NotProvided,
    val feedbackUpdatedAtMillis: Long? = null,
    val repeatObservation: RepeatObservation = RepeatObservation.NotFinalised,
    val firstRepeatAtMillis: Long? = null,
    val observationDeadlineAtMillis: Long,
    val observationFinalisedAtMillis: Long? = null,
    val recommendationPolicyVersion: Int =
        com.impulsive.app.backend.domain.engine.adaptive.AdaptiveRecommendationPolicyVersion.Current,
    val assignedProtocolId: String? = null,
    val assignedProtocolVersion: Int? = null,
    val actualProtocolId: String? = null,
    val actualProtocolVersion: Int? = null,
    val eligibleMomentPlanCount: Int = 0,
)

data class MomentPlanUseRecord(
    val decisionId: String,
    val planId: String,
    val planUpdatedAtMillis: Long,
    val startedAtMillis: Long,
    val planContentRevisionId: String =
        com.impulsive.app.backend.domain.engine.adaptive.MomentPlanContentRevisionIds.Unspecified,
)
