package com.impulsive.app.backend.session.adaptive

import com.impulsive.app.backend.domain.engine.adaptive.AdaptiveModelValidator
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveDecision
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveMomentLimits
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSourceKind
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentCue
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveDecisionRepository
import com.impulsive.app.backend.domain.repository.adaptive.MomentPlanRepository
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException

object AdaptiveFollowUpIncidentTokenFactory {
    fun create(
        previousDecisionId: String,
        attemptIdentity: String,
    ): String {
        require(previousDecisionId.isNotBlank())
        require(attemptIdentity.isNotBlank())
        val input = buildString {
            append("adaptive-follow-up-v1")
            append('\u0000')
            append(previousDecisionId)
            append('\u0000')
            append(attemptIdentity)
        }.toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(input)
        return "afu1_" + digest.joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }
}

data class AdaptiveFollowUpRequest(
    val previousDecisionId: String,
    val intervention: InterventionFamily,
    val momentPlanId: String? = null,
    val selectedCue: MomentCue? = null,
    val urgeRating: Int? = null,
)

sealed interface AdaptiveFollowUpResult {
    data class Ready(
        val decisionId: String,
        val routeRequest: AdaptiveRouteRequest?,
    ) : AdaptiveFollowUpResult

    data object PreviousDecisionNotStarted : AdaptiveFollowUpResult
    data object IneligibleChoice : AdaptiveFollowUpResult
    data object InvalidMomentPlan : AdaptiveFollowUpResult
    data object PersistenceFailure : AdaptiveFollowUpResult
}

/**
 * Creates a separate decision only after an explicit request to use another
 * support option. Reading this class, recomposition, and Back do not create one.
 */
class AdaptiveFollowUpSupport(
    private val coordinator: AdaptiveMomentCoordinator,
    private val decisions: AdaptiveDecisionRepository,
    private val momentPlans: MomentPlanRepository,
    private val lifecycle: AdaptiveDecisionLifecycle,
    private val clock: AdaptiveClock,
    private val attemptIdSource: AdaptiveIdSource = AdaptiveIdSource {
        UUID.randomUUID().toString()
    },
) {
    suspend fun chooseAnother(
        request: AdaptiveFollowUpRequest,
    ): AdaptiveFollowUpResult {
        try {
            val previous = decisions.getById(request.previousDecisionId)
                ?: return AdaptiveFollowUpResult.PersistenceFailure
            if (previous.startedAtMillis == null) {
                return AdaptiveFollowUpResult.PreviousDecisionNotStarted
            }
            if (request.intervention !in previous.assignment.eligibleInterventions) {
                return AdaptiveFollowUpResult.IneligibleChoice
            }
            if (request.urgeRating?.let { it !in 0..10 } == true) {
                return AdaptiveFollowUpResult.PersistenceFailure
            }
            if (
                request.intervention != InterventionFamily.MomentPlan &&
                request.momentPlanId != null
            ) {
                return AdaptiveFollowUpResult.InvalidMomentPlan
            }
            val validatedPlanId = if (request.intervention == InterventionFamily.MomentPlan) {
                validatePlan(request) ?: return AdaptiveFollowUpResult.InvalidMomentPlan
            } else {
                null
            }
            val actionAtMillis = previous.followUpTimestamp(clock.nowMillis())
                ?: return AdaptiveFollowUpResult.PersistenceFailure
            val incidentToken = AdaptiveFollowUpIncidentTokenFactory.create(
                previousDecisionId = previous.decisionId,
                attemptIdentity = attemptIdSource.newId(),
            )
            val coordination = coordinator.coordinate(
                AdaptiveProtectionIncidentRequest(
                    incidentToken = incidentToken,
                    sourceKind = AdaptiveSourceKind.ExplicitUserSupport,
                    detectedAtMillis = actionAtMillis,
                    currentlyAllowedInterventions =
                        previous.assignment.eligibleInterventions,
                    confirmedCue = request.selectedCue,
                    baselineUrgeRating = request.urgeRating,
                    gameProductEligible = true,
                    readingProductEligible = true,
                    momentPlansProductEligible = true,
                    recordsProtectionRepeat = false,
                ),
            )
            val followUpId = coordination.presentation.decisionId
                ?.takeIf { coordination.persisted && it != previous.decisionId }
                ?: return AdaptiveFollowUpResult.PersistenceFailure
            val planForChoice =
                if (request.intervention == InterventionFamily.MomentPlan) {
                    coordination.presentation.selectedMomentPlanId ?: validatedPlanId
                } else {
                    null
                }

            // Explicitly selected options remain available even when personal
            // suggestion preferences are off; those preferences control suggestions.
            if (!decisions.addEligibleInterventions(followUpId, setOf(request.intervention))) {
                return AdaptiveFollowUpResult.PersistenceFailure
            }
            when (lifecycle.markPresented(followUpId, actionAtMillis)) {
                AdaptiveLifecycleResult.Applied,
                AdaptiveLifecycleResult.Idempotent,
                AdaptiveLifecycleResult.SchedulingFailure -> Unit
                else -> return AdaptiveFollowUpResult.PersistenceFailure
            }
            when (
                lifecycle.recordActualChoice(
                    decisionId = followUpId,
                    intervention = request.intervention,
                    momentPlanId = planForChoice,
                )
            ) {
                AdaptiveLifecycleResult.Applied,
                AdaptiveLifecycleResult.Idempotent -> Unit
                AdaptiveLifecycleResult.IneligibleChoice ->
                    return AdaptiveFollowUpResult.IneligibleChoice
                AdaptiveLifecycleResult.InvalidMomentPlan ->
                    return AdaptiveFollowUpResult.InvalidMomentPlan
                else -> return AdaptiveFollowUpResult.PersistenceFailure
            }
            return AdaptiveFollowUpResult.Ready(
                decisionId = followUpId,
                routeRequest = AdaptiveMomentRoutingPolicy.forChoice(
                    decisionId = followUpId,
                    intervention = request.intervention,
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return AdaptiveFollowUpResult.PersistenceFailure
        }
    }

    private suspend fun validatePlan(request: AdaptiveFollowUpRequest): String? {
        val planId = request.momentPlanId?.takeIf { it.isNotBlank() } ?: return null
        val plan = momentPlans.getById(planId)
        return planId.takeIf {
            plan != null &&
                plan.enabled &&
                AdaptiveModelValidator.isSafeAndValid(plan)
        }
    }

    private fun AdaptiveDecision.followUpTimestamp(nowMillis: Long): Long? {
        if (nowMillis < createdAtMillis) return null
        val windowDuration = AdaptiveMomentLimits.MomentWindowMinutes * 60_000L
        val latestInsideWindow = momentWindowStartedAtMillis + windowDuration - 1L
        return nowMillis.coerceAtMost(latestInsideWindow).coerceAtLeast(createdAtMillis)
    }

}
