package com.impulsive.app.debug.adaptive

import com.impulsive.app.BuildConfig
import com.impulsive.app.backend.domain.engine.adaptive.AdaptiveRecommendationPolicyVersion
import com.impulsive.app.backend.domain.engine.adaptive.AdaptiveRecommendationRequest
import com.impulsive.app.backend.domain.engine.adaptive.AdaptiveReplayScenario
import com.impulsive.app.backend.domain.model.adaptive.AdaptivePreferences
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveReasonCode
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveOutcomeRecord
import com.impulsive.app.backend.domain.model.adaptive.EngagementOutcome
import com.impulsive.app.backend.domain.model.adaptive.FeedbackCode
import com.impulsive.app.backend.domain.model.adaptive.MomentCue
import com.impulsive.app.backend.domain.model.adaptive.MomentIntensity
import com.impulsive.app.backend.domain.model.adaptive.MomentPlan
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanActionType
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanRehearsal
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanRehearsalMode
import com.impulsive.app.backend.domain.model.adaptive.RepeatObservation

object AdaptiveReplayDebugScenarios {
    fun deterministicFixtures(): List<AdaptiveReplayScenario> {
        check(BuildConfig.DEBUG)
        return listOf(
            fixture("first-attempt", MomentIntensity.FirstAttempt),
            fixture("repeated-attempt"),
            fixture(
                "cue-matched-plan",
                cue = MomentCue.Stress,
                plans = listOf(plan()),
            ),
            fixture(
                "recently-rehearsed-plan",
                plans = listOf(plan()),
                rehearsals = listOf(rehearsal()),
            ),
            fixture(
                "intervention-fatigue",
                recentSelections = List(4) { InterventionFamily.PivotGame },
            ),
            fixture(
                "wrong-timing",
                history = listOf(
                    outcome(FeedbackCode.WrongTiming),
                ),
            ),
            fixture("insufficient-evidence"),
            fixture("randomised-exploration", draw = 0.0),
            fixture(
                "disabled-families",
                preferences = AdaptivePreferences(
                    gameSuggestionsEnabled = false,
                    readingSuggestionsEnabled = false,
                    momentPlanSuggestionsEnabled = false,
                ),
            ),
            fixture("no-valid-plans"),
        )
    }

    private fun fixture(
        id: String,
        intensity: MomentIntensity = MomentIntensity.RepeatedAttempt,
        cue: MomentCue? = null,
        draw: Double = 0.99,
        preferences: AdaptivePreferences = AdaptivePreferences(),
        plans: List<MomentPlan> = emptyList(),
        rehearsals: List<MomentPlanRehearsal> = emptyList(),
        history: List<AdaptiveOutcomeRecord> = emptyList(),
        recentSelections: List<InterventionFamily> = emptyList(),
    ) = AdaptiveReplayScenario(
        scenarioId = id,
        request = AdaptiveRecommendationRequest(
            momentIntensity = intensity,
            selectedCue = cue,
            preferences = preferences,
            momentPlans = plans,
            recentCompletedRehearsals = rehearsals,
            history = history,
            recentActualSelections = recentSelections,
        ),
        recordedAssignedFamily =
            if (intensity == MomentIntensity.FirstAttempt) {
                InterventionFamily.ShortPause
            } else {
                InterventionFamily.PivotGame
            },
        recordedReason =
            if (intensity == MomentIntensity.FirstAttempt) {
                AdaptiveReasonCode.MinimumEffectiveFriction
            } else {
                AdaptiveReasonCode.InsufficientEvidenceExploration
            },
        recordedPolicyVersion = AdaptiveRecommendationPolicyVersion.Current,
        deterministicDraw = draw,
    )

    private fun plan() = MomentPlan(
        planId = PlanId,
        title = "Synthetic plan",
        momentCue = MomentCue.Stress,
        actionText = "Pause briefly",
        futureCueText = "Synthetic future cue",
        actionType = MomentPlanActionType.TextOnly,
        actionTarget = null,
        enabled = true,
        preferredForCue = true,
        createdAtMillis = 1L,
        updatedAtMillis = 2L,
        contentRevisionId = RevisionId,
    )

    private fun rehearsal() = MomentPlanRehearsal(
        rehearsalId = RehearsalId,
        planId = PlanId,
        planUpdatedAtMillisAtStart = 2L,
        mode = MomentPlanRehearsalMode.Guided,
        startedAtMillis = 3L,
        completedAtMillis = 4L,
        planContentRevisionId = RevisionId,
    )

    private fun outcome(feedback: FeedbackCode) = AdaptiveOutcomeRecord(
        decisionId = DecisionId,
        actualIntervention = InterventionFamily.PivotGame,
        selectedCue = null,
        feedbackCode = feedback,
        engagementOutcome = EngagementOutcome.Completed,
        repeatObservation = RepeatObservation.NoRepeatDetected,
        decisionAtMillis = 1L,
        observationFinalisedAtMillis = 2L,
    )

    private const val PlanId = "00000000-0000-0000-0000-000000007001"
    private const val RevisionId = "00000000-0000-0000-0000-000000007002"
    private const val RehearsalId = "00000000-0000-0000-0000-000000007003"
    private const val DecisionId = "00000000-0000-0000-0000-000000007004"
}
