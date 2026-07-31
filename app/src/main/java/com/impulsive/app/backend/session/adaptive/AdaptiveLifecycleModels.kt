package com.impulsive.app.backend.session.adaptive

import com.impulsive.app.backend.domain.model.adaptive.AdaptiveReasonCode
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSourceKind
import com.impulsive.app.backend.domain.model.adaptive.AssignmentMode
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentCue
import com.impulsive.app.backend.domain.model.adaptive.MomentIntensity

fun interface AdaptiveClock {
    fun nowMillis(): Long
}

object SystemAdaptiveClock : AdaptiveClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

fun interface AdaptiveIdSource {
    fun newId(): String
}

data class AdaptiveProtectionIncidentRequest(
    val incidentToken: String,
    val sourceKind: AdaptiveSourceKind,
    val detectedAtMillis: Long,
    val currentlyAllowedInterventions: Set<InterventionFamily>,
    val confirmedCue: MomentCue? = null,
    val baselineUrgeRating: Int? = null,
    val gameProductEligible: Boolean = false,
    val readingProductEligible: Boolean = false,
    val momentPlansProductEligible: Boolean = false,
    val recordsProtectionRepeat: Boolean = true,
)

data class AdaptiveMomentPresentation(
    val decisionId: String?,
    val momentIntensity: MomentIntensity,
    val assignmentMode: AssignmentMode,
    val assignedIntervention: InterventionFamily?,
    val selectedMomentPlanId: String?,
    val reasonCode: AdaptiveReasonCode,
    val eligibleInterventions: Set<InterventionFamily>,
    val confirmedCue: MomentCue?,
    val baselineUrgeRating: Int?,
    val stableFallback: Boolean,
)

data class AdaptiveMomentCoordinationResult(
    val presentation: AdaptiveMomentPresentation,
    val persisted: Boolean,
    val duplicateIncident: Boolean,
    val failure: AdaptiveMomentFailure? = null,
)

enum class AdaptiveMomentFailure {
    InvalidIncident,
    PersistenceUnavailable,
    RecommendationUnavailable,
}

enum class AdaptiveLifecycleResult {
    Applied,
    Idempotent,
    NotFound,
    InvalidTimestamp,
    InvalidTransition,
    IneligibleChoice,
    InvalidMomentPlan,
    ConflictingChoice,
    PersistenceFailure,
    SchedulingFailure,
}

enum class AdaptiveFinalisationResult {
    Finalised,
    AlreadyFinalised,
    Missing,
    NotDue,
    PersistenceFailure,
}

data class AdaptiveRecoveryResult(
    val finalisedCount: Int,
    val rescheduledCount: Int,
    val failedCount: Int,
)
