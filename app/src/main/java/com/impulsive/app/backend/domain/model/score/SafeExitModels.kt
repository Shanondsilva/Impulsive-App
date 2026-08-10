package com.impulsive.app.backend.domain.model.score

import java.time.LocalDateTime

const val SAFE_EXIT_CONTROL_POINT_BONUS: Int = 80

enum class SafeExitSource(val storageValue: String) {
    PivotGame("pivot_game"),
    ResetReading("reset_reading"),
    MomentPlan("moment_plan"),
}

enum class SafeExitAction {
    WalkAway,
    Done,
    NotNow,
    LeaveThisApp,
}

/**
 * Candidate for a Safe Exit record.
 *
 * Later integration adapters supply source IDs with these stable formats:
 * Pivot game: "<gameTypeId>:<scoreSessionId>".
 * Reset Reading: the ResetReadSessionRecord ID converted to a string.
 * Moment Plan: the AdaptiveDecision decisionId.
 */
data class SafeExitCandidate(
    val source: SafeExitSource,
    val sourceId: String,
    val action: SafeExitAction,
    val completedAt: LocalDateTime,
    val validCompletion: Boolean,
)

data class SafeExitRecord(
    val sourceKey: String,
    val source: SafeExitSource,
    val sourceId: String,
    val completedAt: LocalDateTime,
) {
    val controlPoints: Int
        get() = SAFE_EXIT_CONTROL_POINT_BONUS
}

enum class SafeExitRejectionReason {
    BlankSourceId,
    InvalidCompletion,
    NonWalkAwayAction,
}

sealed interface SafeExitEvaluation {
    data class Accepted(val record: SafeExitRecord) : SafeExitEvaluation
    data class Rejected(val reason: SafeExitRejectionReason) : SafeExitEvaluation
}

object SafeExitPolicy {
    fun evaluate(candidate: SafeExitCandidate): SafeExitEvaluation {
        val trimmedSourceId = candidate.sourceId.trim()
        if (trimmedSourceId.isEmpty()) {
            return SafeExitEvaluation.Rejected(SafeExitRejectionReason.BlankSourceId)
        }
        if (!candidate.validCompletion) {
            return SafeExitEvaluation.Rejected(SafeExitRejectionReason.InvalidCompletion)
        }
        if (candidate.action != SafeExitAction.WalkAway) {
            return SafeExitEvaluation.Rejected(SafeExitRejectionReason.NonWalkAwayAction)
        }
        return SafeExitEvaluation.Accepted(
            SafeExitRecord(
                sourceKey = "${candidate.source.storageValue}:$trimmedSourceId",
                source = candidate.source,
                sourceId = trimmedSourceId,
                completedAt = candidate.completedAt,
            ),
        )
    }
}