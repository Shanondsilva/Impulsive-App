package com.impulsive.app.backend.data.repository.adaptive

import com.impulsive.app.backend.data.local.entity.AdaptiveDecisionEntity
import com.impulsive.app.backend.data.local.entity.AdaptivePreferenceEntity
import com.impulsive.app.backend.data.local.entity.MomentPlanEntity
import com.impulsive.app.backend.domain.engine.adaptive.AdaptiveModelValidator
import com.impulsive.app.backend.domain.engine.adaptive.AdaptiveHistoryRetentionPolicy
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveAssignment
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveDecision
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveOutcomeRecord
import com.impulsive.app.backend.domain.model.adaptive.AdaptivePreferences
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveReasonCode
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSourceKind
import com.impulsive.app.backend.domain.model.adaptive.AssignmentMode
import com.impulsive.app.backend.domain.model.adaptive.EngagementOutcome
import com.impulsive.app.backend.domain.model.adaptive.FeedbackCode
import com.impulsive.app.backend.domain.model.adaptive.FamiliarStepEvidenceRecord
import com.impulsive.app.backend.domain.model.adaptive.FamiliarStepRouteIdentity
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentCue
import com.impulsive.app.backend.domain.model.adaptive.MomentIntensity
import com.impulsive.app.backend.domain.model.adaptive.MomentPlan
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanActionType
import com.impulsive.app.backend.domain.model.adaptive.RepeatObservation

internal fun AdaptiveDecision.toEntity(): AdaptiveDecisionEntity {
    val issues = AdaptiveModelValidator.validate(this)
    require(issues.isEmpty()) {
        issues.joinToString(
            prefix = "Invalid adaptive decision: ",
            separator = "; ",
        ) { issue -> "${issue.field} ${issue.message}" }
    }
    require(
        assignment.actualIntervention != InterventionFamily.MomentPlan ||
            !assignment.momentPlanId.isNullOrBlank(),
    ) {
        "Invalid adaptive decision: momentPlanId must identify the chosen Moment Plan."
    }
    return AdaptiveDecisionEntity(
        decisionId = decisionId,
        protectionIncidentToken = protectionIncidentToken,
        sourceKind = sourceKind.name,
        createdAtMillis = createdAtMillis,
        momentWindowStartedAtMillis = momentWindowStartedAtMillis,
        momentIntensity = assignment.momentIntensity.name,
        momentCue = momentCue?.name,
        baselineUrgeRating = baselineUrgeRating,
        assignmentMode = assignment.assignmentMode.name,
        eligibleInterventionsMask = assignment.eligibleInterventionsMask,
        assignedSuggestion = assignment.assignedSuggestion?.name,
        actualIntervention = assignment.actualIntervention?.name,
        selectionProbability = assignment.selectionProbability,
        reasonCode = assignment.reasonCode.name,
        momentPlanId = assignment.momentPlanId,
        momentPlanUpdatedAtMillis = assignment.momentPlanUpdatedAtMillis,
        assignedPlanContentRevisionId = assignment.assignedPlanContentRevisionId,
        actualPlanContentRevisionId = assignment.actualPlanContentRevisionId,
        userOverrodeSuggestion = assignment.userOverrodeSuggestion,
        presentedAtMillis = presentedAtMillis,
        startedAtMillis = startedAtMillis,
        completedAtMillis = completedAtMillis,
        dismissedAtMillis = dismissedAtMillis,
        feedbackCode = feedbackCode.name,
        feedbackUpdatedAtMillis = feedbackUpdatedAtMillis,
        repeatDetectedWithin20Minutes = when (repeatObservation) {
            RepeatObservation.NoRepeatDetected -> false
            RepeatObservation.RepeatDetected -> true
            RepeatObservation.NotFinalised -> null
        },
        firstRepeatAtMillis = firstRepeatAtMillis,
        observationDeadlineAtMillis = observationDeadlineAtMillis,
        observationFinalisedAtMillis = observationFinalisedAtMillis,
        recommendationPolicyVersion = recommendationPolicyVersion,
        assignedProtocolId = assignedProtocolId,
        assignedProtocolVersion = assignedProtocolVersion,
        actualProtocolId = actualProtocolId,
        actualProtocolVersion = actualProtocolVersion,
        eligibleMomentPlanCount = eligibleMomentPlanCount,
    )
}

internal fun AdaptiveDecisionEntity.toDomain(): AdaptiveDecision {
    require(baselineUrgeRating == null || baselineUrgeRating in 0..10) {
        "Adaptive decision contains an invalid urge rating."
    }
    require(
        selectionProbability == null ||
            selectionProbability.isFinite() &&
            selectionProbability > 0.0 &&
            selectionProbability <= 1.0,
    ) {
        "Adaptive decision contains an invalid selection probability."
    }
    require(completedAtMillis == null || dismissedAtMillis == null) {
        "Adaptive decision cannot be both completed and dismissed."
    }
    val eligible = InterventionFamily.entries.filterTo(linkedSetOf()) { family ->
        eligibleInterventionsMask and family.eligibilityBit != 0
    }
    val knownMask = InterventionFamily.entries.fold(0) { mask, family ->
        mask or family.eligibilityBit
    }
    require(eligibleInterventionsMask and knownMask.inv() == 0) {
        "Adaptive decision contains unknown eligible-intervention bits."
    }
    return AdaptiveDecision(
        decisionId = decisionId,
        protectionIncidentToken = protectionIncidentToken,
        sourceKind = enumValue<AdaptiveSourceKind>(sourceKind, "sourceKind"),
        createdAtMillis = createdAtMillis,
        momentWindowStartedAtMillis = momentWindowStartedAtMillis,
        momentCue = momentCue?.let { enumValue<MomentCue>(it, "momentCue") },
        baselineUrgeRating = baselineUrgeRating,
        assignment = AdaptiveAssignment(
            momentIntensity = enumValue<MomentIntensity>(
                momentIntensity,
                "momentIntensity",
            ),
            assignmentMode = enumValue<AssignmentMode>(
                assignmentMode,
                "assignmentMode",
            ),
            eligibleInterventions = eligible,
            assignedSuggestion = assignedSuggestion?.let {
                enumValue<InterventionFamily>(it, "assignedSuggestion")
            },
            selectionProbability = selectionProbability,
            reasonCode = enumValue<AdaptiveReasonCode>(reasonCode, "reasonCode"),
            momentPlanId = momentPlanId,
            momentPlanUpdatedAtMillis = momentPlanUpdatedAtMillis,
            assignedPlanContentRevisionId = assignedPlanContentRevisionId,
            actualPlanContentRevisionId = actualPlanContentRevisionId,
            actualIntervention = actualIntervention?.let {
                enumValue<InterventionFamily>(it, "actualIntervention")
            },
            userOverrodeSuggestion = userOverrodeSuggestion,
        ),
        presentedAtMillis = presentedAtMillis,
        startedAtMillis = startedAtMillis,
        completedAtMillis = completedAtMillis,
        dismissedAtMillis = dismissedAtMillis,
        feedbackCode = enumValue<FeedbackCode>(feedbackCode, "feedbackCode"),
        feedbackUpdatedAtMillis = feedbackUpdatedAtMillis,
        repeatObservation = repeatDetectedWithin20Minutes.toRepeatObservation(),
        firstRepeatAtMillis = firstRepeatAtMillis,
        observationDeadlineAtMillis = observationDeadlineAtMillis,
        observationFinalisedAtMillis = observationFinalisedAtMillis,
        recommendationPolicyVersion = recommendationPolicyVersion,
        assignedProtocolId = assignedProtocolId,
        assignedProtocolVersion = assignedProtocolVersion,
        actualProtocolId = actualProtocolId,
        actualProtocolVersion = actualProtocolVersion,
        eligibleMomentPlanCount = eligibleMomentPlanCount,
    )
}

internal fun AdaptiveDecisionEntity.toOutcomeRecord(): AdaptiveOutcomeRecord =
    AdaptiveOutcomeRecord(
        decisionId = decisionId,
        actualIntervention = actualIntervention?.let {
            enumValue<InterventionFamily>(it, "actualIntervention")
        },
        selectedCue = momentCue?.let { enumValue<MomentCue>(it, "momentCue") },
        feedbackCode = enumValue<FeedbackCode>(feedbackCode, "feedbackCode"),
        engagementOutcome = when {
            completedAtMillis != null -> EngagementOutcome.Completed
            dismissedAtMillis != null -> EngagementOutcome.Dismissed
            startedAtMillis != null -> EngagementOutcome.StartedNotCompleted
            else -> EngagementOutcome.NotStarted
        },
        repeatObservation = repeatDetectedWithin20Minutes.toRepeatObservation(),
        decisionAtMillis = createdAtMillis,
        observationFinalisedAtMillis = observationFinalisedAtMillis,
    )

internal fun AdaptiveDecisionEntity.toFamiliarStepEvidenceRecord(): FamiliarStepEvidenceRecord? {
    val intervention = actualIntervention?.let {
        enumValue<InterventionFamily>(it, "actualIntervention")
    } ?: return null
    val protocolId = actualProtocolId?.takeIf { it.isNotBlank() } ?: return null
    val protocolVersion = actualProtocolVersion?.takeIf { it > 0 } ?: return null
    val finalisedAt = observationFinalisedAtMillis ?: return null
    val planId = momentPlanId.takeIf { intervention == InterventionFamily.MomentPlan }
    val planRevision = actualPlanContentRevisionId
        .takeIf { intervention == InterventionFamily.MomentPlan }
    if (intervention == InterventionFamily.MomentPlan && (planId == null || planRevision == null)) {
        return null
    }
    return FamiliarStepEvidenceRecord(
        decisionId = decisionId,
        routeIdentity = FamiliarStepRouteIdentity(
            intervention = intervention,
            protocolId = protocolId,
            protocolVersion = protocolVersion,
            momentPlanId = planId,
            momentPlanContentRevisionId = planRevision,
        ),
        momentCue = momentCue?.let { enumValue<MomentCue>(it, "momentCue") },
        feedbackCode = enumValue<FeedbackCode>(feedbackCode, "feedbackCode"),
        engagementOutcome = when {
            completedAtMillis != null -> EngagementOutcome.Completed
            dismissedAtMillis != null -> EngagementOutcome.Dismissed
            startedAtMillis != null -> EngagementOutcome.StartedNotCompleted
            else -> EngagementOutcome.NotStarted
        },
        repeatObservation = repeatDetectedWithin20Minutes.toRepeatObservation(),
        decisionAtMillis = createdAtMillis,
        finalisedAtMillis = finalisedAt,
    )
}

internal fun MomentPlan.toEntity(): MomentPlanEntity {
    val issues = AdaptiveModelValidator.validate(this)
    require(issues.isEmpty()) {
        issues.joinToString(
            prefix = "Invalid Moment Plan: ",
            separator = "; ",
        ) { issue -> "${issue.field} ${issue.message}" }
    }
    return MomentPlanEntity(
        planId = planId,
        title = title,
        momentCue = momentCue?.name,
        actionText = actionText,
        futureCueText = futureCueText,
        actionType = actionType.name,
        actionTarget = actionTarget,
        enabled = enabled,
        preferredForCue = preferredForCue,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
        rehearsedAtMillis = rehearsedAtMillis,
        contentRevisionId = contentRevisionId,
    )
}

internal fun MomentPlanEntity.toDomain(): MomentPlan = MomentPlan(
    planId = planId,
    title = title,
    momentCue = momentCue?.let { enumValue<MomentCue>(it, "momentCue") },
    actionText = actionText,
    futureCueText = futureCueText,
    actionType = enumValue<MomentPlanActionType>(actionType, "actionType"),
    actionTarget = actionTarget,
    enabled = enabled,
    preferredForCue = preferredForCue,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
    rehearsedAtMillis = rehearsedAtMillis,
    contentRevisionId = contentRevisionId,
)

internal fun AdaptivePreferences.toEntity(
    updatedAtMillis: Long,
): AdaptivePreferenceEntity = AdaptivePreferenceEntity(
    personalSuggestionsEnabled = personalSuggestionsEnabled,
    gameSuggestionsEnabled = gameSuggestionsEnabled,
    readingSuggestionsEnabled = readingSuggestionsEnabled,
    momentPlanSuggestionsEnabled = momentPlanSuggestionsEnabled,
    randomisedExplorationEnabled = randomisedExplorationEnabled,
    privateScreenProtectionEnabled = privateScreenProtectionEnabled,
    historyRetentionPolicy = historyRetentionPolicy.name,
    pathShiftEnabled = true,
    updatedAtMillis = updatedAtMillis,
)

internal fun AdaptivePreferenceEntity.toDomain(): AdaptivePreferences {
    require(id == AdaptivePreferenceEntity.SingleRowId) {
        "Adaptive preferences must use the single settings row."
    }
    return AdaptivePreferences(
        personalSuggestionsEnabled = personalSuggestionsEnabled,
        gameSuggestionsEnabled = gameSuggestionsEnabled,
        readingSuggestionsEnabled = readingSuggestionsEnabled,
        momentPlanSuggestionsEnabled = momentPlanSuggestionsEnabled,
        randomisedExplorationEnabled = randomisedExplorationEnabled,
        privateScreenProtectionEnabled = privateScreenProtectionEnabled,
        historyRetentionPolicy = enumValue<AdaptiveHistoryRetentionPolicy>(
            historyRetentionPolicy,
            "adaptive history retention policy",
        ),
        pathShiftEnabled = true,
    )
}

private inline fun <reified T : Enum<T>> enumValue(
    stored: String,
    field: String,
): T = enumValues<T>().firstOrNull { it.name == stored }
    ?: throw IllegalArgumentException("Unknown $field value.")

private fun Boolean?.toRepeatObservation(): RepeatObservation = when (this) {
    true -> RepeatObservation.RepeatDetected
    false -> RepeatObservation.NoRepeatDetected
    null -> RepeatObservation.NotFinalised
}
