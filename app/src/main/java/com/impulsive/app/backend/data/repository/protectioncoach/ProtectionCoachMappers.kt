package com.impulsive.app.backend.data.repository.protectioncoach

import com.impulsive.app.backend.data.local.entity.ProtectionCoachSuggestionEntity
import com.impulsive.app.backend.domain.protectioncoach.ProtectionCoachEvidence
import com.impulsive.app.backend.domain.protectioncoach.ProtectionCoachOnboardingReason
import com.impulsive.app.backend.domain.protectioncoach.ProtectionCoachSuggestion
import com.impulsive.app.backend.domain.protectioncoach.ProtectionCoachSuggestionId
import com.impulsive.app.backend.domain.protectioncoach.ProtectionCoachSuggestionStatus
import com.impulsive.app.backend.domain.protectioncoach.ProtectionCoachSuggestionType

internal fun ProtectionCoachSuggestion.toEntity(): ProtectionCoachSuggestionEntity =
    ProtectionCoachSuggestionEntity(
        suggestionId = suggestionId.value,
        policyVersion = policyVersion,
        suggestionType = suggestionType.name,
        createdAtMillis = createdAtMillis,
        expiresAtMillis = expiresAtMillis,
        status = status.name,
        presentedAtMillis = presentedAtMillis,
        acceptedAtMillis = acceptedAtMillis,
        dismissedAtMillis = dismissedAtMillis,
        suppressedAtMillis = suppressedAtMillis,
        evidenceWindowStartedAtMillis = evidence.evidenceWindowStartedAtMillis,
        evidenceWindowEndedAtMillis = evidence.evidenceWindowEndedAtMillis,
        evidenceProtectedMomentCount = evidence.protectedMomentCount,
        evidenceDistinctDayCount = evidence.distinctDayCount,
        broadWindowStartMinute = evidence.broadWindowStartMinute,
        broadWindowEndMinute = evidence.broadWindowEndMinute,
        suggestedStartMinute = suggestedStartMinute,
        suggestedEndMinute = suggestedEndMinute,
        acceptedStartMinute = acceptedStartMinute,
        acceptedEndMinute = acceptedEndMinute,
        onboardingReasonCode = evidence.onboardingReason?.name,
        relatedMomentPlanId = relatedMomentPlanId,
        relatedMomentPlanContentRevisionId = relatedMomentPlanContentRevisionId,
    )

internal fun ProtectionCoachSuggestionEntity.toDomain(): ProtectionCoachSuggestion =
    ProtectionCoachSuggestion(
        suggestionId = ProtectionCoachSuggestionId(suggestionId),
        policyVersion = policyVersion,
        suggestionType = ProtectionCoachSuggestionType.valueOf(suggestionType),
        createdAtMillis = createdAtMillis,
        expiresAtMillis = expiresAtMillis,
        status = ProtectionCoachSuggestionStatus.valueOf(status),
        presentedAtMillis = presentedAtMillis,
        acceptedAtMillis = acceptedAtMillis,
        dismissedAtMillis = dismissedAtMillis,
        suppressedAtMillis = suppressedAtMillis,
        evidence = ProtectionCoachEvidence(
            evidenceWindowStartedAtMillis = evidenceWindowStartedAtMillis,
            evidenceWindowEndedAtMillis = evidenceWindowEndedAtMillis,
            protectedMomentCount = evidenceProtectedMomentCount,
            distinctDayCount = evidenceDistinctDayCount,
            broadWindowStartMinute = broadWindowStartMinute,
            broadWindowEndMinute = broadWindowEndMinute,
            onboardingReason = onboardingReasonCode?.let(ProtectionCoachOnboardingReason::valueOf),
        ),
        suggestedStartMinute = suggestedStartMinute,
        suggestedEndMinute = suggestedEndMinute,
        acceptedStartMinute = acceptedStartMinute,
        acceptedEndMinute = acceptedEndMinute,
        relatedMomentPlanId = relatedMomentPlanId,
        relatedMomentPlanContentRevisionId = relatedMomentPlanContentRevisionId,
    )
