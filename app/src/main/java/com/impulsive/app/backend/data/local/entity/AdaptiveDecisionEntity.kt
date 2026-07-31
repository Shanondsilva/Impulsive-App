package com.impulsive.app.backend.data.local.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "adaptive_decisions",
    indices = [
        Index(
            value = ["protectionIncidentToken"],
            unique = true,
            name = "index_adaptive_decisions_protectionIncidentToken",
        ),
        Index(
            value = ["createdAtMillis"],
            name = "index_adaptive_decisions_createdAtMillis",
        ),
        Index(
            value = ["observationFinalisedAtMillis", "observationDeadlineAtMillis"],
            name =
                "index_adaptive_decisions_observationFinalisedAtMillis_" +
                    "observationDeadlineAtMillis",
        ),
        Index(
            value = ["actualIntervention", "observationFinalisedAtMillis", "createdAtMillis"],
            name =
                "index_adaptive_decisions_actualIntervention_" +
                    "observationFinalisedAtMillis_createdAtMillis",
        ),
        Index(
            value = ["momentCue", "observationFinalisedAtMillis", "createdAtMillis"],
            name =
                "index_adaptive_decisions_momentCue_" +
                    "observationFinalisedAtMillis_createdAtMillis",
        ),
        Index(
            value = ["momentPlanId"],
            name = "index_adaptive_decisions_momentPlanId",
        ),
        Index(
            value = ["momentPlanId", "momentPlanUpdatedAtMillis", "startedAtMillis"],
            name =
                "index_adaptive_decisions_momentPlanId_" +
                    "momentPlanUpdatedAtMillis_startedAtMillis",
        ),
        Index(
            value = ["momentPlanId", "actualPlanContentRevisionId", "startedAtMillis"],
            name =
                "index_adaptive_decisions_momentPlanId_" +
                    "actualPlanContentRevisionId_startedAtMillis",
        ),
    ],
)
data class AdaptiveDecisionEntity(
    @PrimaryKey
    val decisionId: String,
    val protectionIncidentToken: String,
    val sourceKind: String,
    val createdAtMillis: Long,
    val momentWindowStartedAtMillis: Long,
    val momentIntensity: String,
    val momentCue: String?,
    val baselineUrgeRating: Int?,
    val assignmentMode: String,
    val eligibleInterventionsMask: Int,
    val assignedSuggestion: String?,
    val actualIntervention: String?,
    val selectionProbability: Double?,
    val reasonCode: String,
    val momentPlanId: String?,
    val momentPlanUpdatedAtMillis: Long?,
    val userOverrodeSuggestion: Boolean,
    val presentedAtMillis: Long?,
    val startedAtMillis: Long?,
    val completedAtMillis: Long?,
    val dismissedAtMillis: Long?,
    val feedbackCode: String,
    val feedbackUpdatedAtMillis: Long?,
    val repeatDetectedWithin20Minutes: Boolean?,
    val firstRepeatAtMillis: Long?,
    val observationDeadlineAtMillis: Long,
    val observationFinalisedAtMillis: Long?,
    @ColumnInfo(defaultValue = "1")
    val recommendationPolicyVersion: Int = 1,
    val assignedProtocolId: String? = null,
    val assignedProtocolVersion: Int? = null,
    val actualProtocolId: String? = null,
    val actualProtocolVersion: Int? = null,
    val assignedPlanContentRevisionId: String? = null,
    val actualPlanContentRevisionId: String? = null,
    @ColumnInfo(defaultValue = "0")
    val eligibleMomentPlanCount: Int = 0,
)
