package com.impulsive.app.backend.session.adaptive

import android.util.Log
import com.impulsive.app.backend.domain.engine.adaptive.AdaptiveRecommendationPolicy
import com.impulsive.app.backend.domain.engine.adaptive.AdaptiveRecommendationPolicyVersion
import com.impulsive.app.backend.domain.engine.adaptive.AdaptiveRecommendationRequest
import com.impulsive.app.backend.domain.engine.adaptive.InterventionProtocolRegistry
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveAssignment
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveDecision
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveMomentLimits
import com.impulsive.app.backend.domain.model.adaptive.AdaptivePreferences
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveReasonCode
import com.impulsive.app.backend.domain.model.adaptive.AssignmentMode
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentIntensity
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveDecisionRepository
import com.impulsive.app.backend.domain.repository.adaptive.AdaptivePreferenceRepository
import com.impulsive.app.backend.domain.repository.adaptive.MomentPlanRepository
import com.impulsive.app.backend.domain.repository.adaptive.MomentPlanRehearsalRepository
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

class AdaptiveMomentCoordinator(
    private val decisions: AdaptiveDecisionRepository,
    private val preferences: AdaptivePreferenceRepository,
    private val momentPlans: MomentPlanRepository,
    private val recommendationPolicy: AdaptiveRecommendationPolicy,
    private val clock: AdaptiveClock,
    private val rehearsals: MomentPlanRehearsalRepository? = null,
    private val idSource: AdaptiveIdSource = AdaptiveIdSource {
        UUID.randomUUID().toString()
    },
    private val logger: AdaptiveSafeLogger = AndroidAdaptiveSafeLogger,
) {
    suspend fun coordinate(
        request: AdaptiveProtectionIncidentRequest,
    ): AdaptiveMomentCoordinationResult {
        val now = clock.nowMillis()
        if (!request.isValid(now)) {
            return fallback(
                intensity = MomentIntensity.FirstAttempt,
                request = request,
                failure = AdaptiveMomentFailure.InvalidIncident,
            )
        }

        try {
            decisions.getByIncidentToken(request.incidentToken)?.let { existing ->
                return AdaptiveMomentCoordinationResult(
                    presentation = existing.toPresentation(),
                    persisted = true,
                    duplicateIncident = true,
                )
            }

            /*
             * The DAO uses an inclusive lower bound. Adding one millisecond makes
             * the approved boundary explicit: exactly twenty minutes starts a
             * new Moment Window; anything less remains inside the active window.
             */
            val windowLowerBound = request.detectedAtMillis.windowLowerBound()
            val previous = decisions.getLatestInsideMomentWindow(
                windowStartedAtMillis = windowLowerBound,
                nowMillis = request.detectedAtMillis,
            )
            val previousOpen = decisions.getLatestOpenInsideMomentWindow(
                windowStartedAtMillis = windowLowerBound,
                nowMillis = request.detectedAtMillis,
            )
            if (previousOpen != null && request.recordsProtectionRepeat) {
                decisions.markFirstRepeatOnce(
                    decisionId = previousOpen.decisionId,
                    firstRepeatAtMillis = request.detectedAtMillis,
                )
            }

            val intensity = if (previous == null) {
                MomentIntensity.FirstAttempt
            } else {
                MomentIntensity.RepeatedAttempt
            }
            val storedPreferences = preferences.get()
            val enabledPlans = momentPlans.observeEnabled().first()
            val eligibility = AdaptiveEligibilityBuilder.build(
                request = request,
                preferences = storedPreferences,
                plans = enabledPlans,
            )
            val assignment = recommendSafely(
                intensity = intensity,
                request = request,
                storedPreferences = storedPreferences,
                eligibility = eligibility,
            )
            val assignedProtocol = when (assignment.assignedSuggestion) {
                null -> null
                InterventionFamily.MomentPlan -> assignment.momentPlanId?.let { planId ->
                    eligibility.validEnabledMomentPlans
                        .firstOrNull {
                            it.planId == planId &&
                                it.contentRevisionId ==
                                assignment.assignedPlanContentRevisionId
                        }
                        ?.let(InterventionProtocolRegistry::resolveForPlan)
                }
                else -> InterventionProtocolRegistry.resolveForFamily(
                    checkNotNull(assignment.assignedSuggestion),
                )
            }
            val decision = AdaptiveDecision(
                decisionId = idSource.newId(),
                protectionIncidentToken = request.incidentToken,
                sourceKind = request.sourceKind,
                createdAtMillis = request.detectedAtMillis,
                momentWindowStartedAtMillis =
                    previous?.momentWindowStartedAtMillis ?: request.detectedAtMillis,
                momentCue = request.confirmedCue,
                baselineUrgeRating = request.baselineUrgeRating,
                assignment = assignment,
                // Phase 4 uses incident creation as the single observation reference.
                observationDeadlineAtMillis =
                    request.detectedAtMillis + AdaptiveMomentLimits.MomentWindowMinutes * 60_000L,
                recommendationPolicyVersion = AdaptiveRecommendationPolicyVersion.Current,
                assignedProtocolId = assignedProtocol?.protocolId?.value,
                assignedProtocolVersion = assignedProtocol?.version?.value,
                eligibleMomentPlanCount = eligibility.validEnabledMomentPlans.size,
            )
            val inserted = decisions.insertOnce(decision)
            if (!inserted) {
                val duplicate = decisions.getByIncidentToken(request.incidentToken)
                if (duplicate != null) {
                    return AdaptiveMomentCoordinationResult(
                        presentation = duplicate.toPresentation(),
                        persisted = true,
                        duplicateIncident = true,
                    )
                }
                logger.failure("insert adaptive decision", IllegalStateException())
                return fallback(
                    intensity = intensity,
                    request = request,
                    failure = AdaptiveMomentFailure.PersistenceUnavailable,
                )
            }
            return AdaptiveMomentCoordinationResult(
                presentation = decision.toPresentation(),
                persisted = true,
                duplicateIncident = false,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            logger.failure("coordinate adaptive moment", error)
            return fallback(
                intensity = MomentIntensity.FirstAttempt,
                request = request,
                failure = AdaptiveMomentFailure.PersistenceUnavailable,
            )
        }
    }

    private suspend fun recommendSafely(
        intensity: MomentIntensity,
        request: AdaptiveProtectionIncidentRequest,
        storedPreferences: AdaptivePreferences,
        eligibility: AdaptiveEligibility,
    ): AdaptiveAssignment = try {
        val history = decisions.getRecentFinalised(
            AdaptiveMomentLimits.RecentEvidenceLimit,
        )
        val recentPracticeCutoff =
            request.detectedAtMillis - RecentRehearsalDays * MillisPerDay
        val recentCompletedRehearsals = rehearsals
            ?.getRecentCompleted(RecentRehearsalLimit)
            .orEmpty()
            .filter {
                val completedAt = it.completedAtMillis
                completedAt != null &&
                    completedAt >= recentPracticeCutoff &&
                    completedAt <= request.detectedAtMillis
            }
        recommendationPolicy.recommend(
            AdaptiveRecommendationRequest(
                momentIntensity = intensity,
                selectedCue = request.confirmedCue,
                preferences = storedPreferences,
                momentPlans = eligibility.validEnabledMomentPlans,
                recentCompletedRehearsals = recentCompletedRehearsals,
                history = history,
                recentActualSelections = history.mapNotNull {
                    it.actualIntervention
                },
                productEligibleInterventions =
                    eligibility.productEligibleInterventions,
            ),
        ).assignment
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        logger.failure("select adaptive suggestion", error)
        stableFallbackAssignment(intensity)
    }

    private fun fallback(
        intensity: MomentIntensity,
        request: AdaptiveProtectionIncidentRequest,
        failure: AdaptiveMomentFailure,
    ): AdaptiveMomentCoordinationResult =
        AdaptiveMomentCoordinationResult(
            presentation = AdaptiveMomentPresentation(
                decisionId = null,
                momentIntensity = intensity,
                assignmentMode = AssignmentMode.StableFallback,
                assignedIntervention = null,
                selectedMomentPlanId = null,
                reasonCode = AdaptiveReasonCode.StableFallback,
                eligibleInterventions = emptySet(),
                confirmedCue = request.confirmedCue,
                baselineUrgeRating = request.baselineUrgeRating,
                stableFallback = true,
            ),
            persisted = false,
            duplicateIncident = false,
            failure = failure,
        )

    private fun stableFallbackAssignment(intensity: MomentIntensity) =
        AdaptiveAssignment(
            momentIntensity = intensity,
            assignmentMode = AssignmentMode.StableFallback,
            eligibleInterventions = emptySet(),
            assignedSuggestion = null,
            selectionProbability = null,
            reasonCode = AdaptiveReasonCode.StableFallback,
        )

    private fun AdaptiveDecision.toPresentation() = AdaptiveMomentPresentation(
        decisionId = decisionId,
        momentIntensity = assignment.momentIntensity,
        assignmentMode = assignment.assignmentMode,
        assignedIntervention = assignment.assignedSuggestion,
        selectedMomentPlanId = assignment.momentPlanId.takeIf {
            assignment.assignedSuggestion == InterventionFamily.MomentPlan
        },
        reasonCode = assignment.reasonCode,
        eligibleInterventions = assignment.eligibleInterventions,
        confirmedCue = momentCue,
        baselineUrgeRating = baselineUrgeRating,
        stableFallback = assignment.assignmentMode == AssignmentMode.StableFallback,
    )

    private fun AdaptiveProtectionIncidentRequest.isValid(nowMillis: Long): Boolean =
        incidentToken.isNotBlank() &&
            incidentToken.length <= MaximumIncidentTokenCharacters &&
            detectedAtMillis >= 0L &&
            detectedAtMillis <= nowMillis &&
            baselineUrgeRating?.let { it in 0..10 } != false

    private fun Long.windowLowerBound(): Long {
        val duration = AdaptiveMomentLimits.MomentWindowMinutes * 60_000L
        return if (this < duration) 0L else this - duration + 1L
    }

    private companion object {
        const val MaximumIncidentTokenCharacters = 200
        const val RecentRehearsalDays = 14L
        const val MillisPerDay = 86_400_000L
        const val RecentRehearsalLimit = 100
    }
}

fun interface AdaptiveSafeLogger {
    fun failure(operation: String, error: Throwable)
}

object AndroidAdaptiveSafeLogger : AdaptiveSafeLogger {
    override fun failure(operation: String, error: Throwable) {
        Log.w(
            "AdaptiveMoment",
            "$operation failed (${error.javaClass.simpleName})",
        )
    }
}
