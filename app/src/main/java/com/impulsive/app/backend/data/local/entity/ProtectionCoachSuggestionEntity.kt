package com.impulsive.app.backend.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "protection_coach_suggestions",
    indices = [
        Index(
            value = ["status", "expiresAtMillis"],
            name = "index_protection_coach_suggestions_status_expiresAtMillis",
        ),
        Index(
            value = [
                "suggestionType",
                "status",
                "broadWindowStartMinute",
                "broadWindowEndMinute",
            ],
            name =
                "index_protection_coach_suggestions_type_status_broadWindow",
        ),
        Index(
            value = ["createdAtMillis"],
            name = "index_protection_coach_suggestions_createdAtMillis",
        ),
        Index(
            value = ["relatedMomentPlanId", "relatedMomentPlanContentRevisionId"],
            name = "index_protection_coach_suggestions_relatedMomentPlan",
        ),
    ],
)
data class ProtectionCoachSuggestionEntity(
    @PrimaryKey
    val suggestionId: String,
    val policyVersion: Int,
    val suggestionType: String,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
    val status: String,
    val presentedAtMillis: Long?,
    val acceptedAtMillis: Long?,
    val dismissedAtMillis: Long?,
    val suppressedAtMillis: Long?,
    val evidenceWindowStartedAtMillis: Long?,
    val evidenceWindowEndedAtMillis: Long?,
    val evidenceProtectedMomentCount: Int,
    val evidenceDistinctDayCount: Int,
    val broadWindowStartMinute: Int?,
    val broadWindowEndMinute: Int?,
    val suggestedStartMinute: Int?,
    val suggestedEndMinute: Int?,
    val acceptedStartMinute: Int?,
    val acceptedEndMinute: Int?,
    val onboardingReasonCode: String?,
    val relatedMomentPlanId: String?,
    val relatedMomentPlanContentRevisionId: String?,
)
