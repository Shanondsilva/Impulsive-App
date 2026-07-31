package com.impulsive.app.backend.session.adaptive

import com.impulsive.app.backend.domain.engine.adaptive.AdaptiveModelValidator
import com.impulsive.app.backend.domain.engine.adaptive.InterventionProtocolContract
import com.impulsive.app.backend.domain.engine.adaptive.InterventionProtocolRegistry
import com.impulsive.app.backend.domain.model.adaptive.FeedbackCode
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveDecisionRepository
import com.impulsive.app.backend.domain.repository.adaptive.MomentPlanRepository
import kotlinx.coroutines.CancellationException

class AdaptiveDecisionLifecycle(
    private val decisions: AdaptiveDecisionRepository,
    private val momentPlans: MomentPlanRepository,
    private val scheduler: AdaptiveObservationScheduler,
    private val clock: AdaptiveClock,
    private val logger: AdaptiveSafeLogger = AndroidAdaptiveSafeLogger,
) {
    suspend fun recordActualChoice(
        decisionId: String,
        intervention: InterventionFamily,
        momentPlanId: String? = null,
    ): AdaptiveLifecycleResult = safely("record actual choice") {
        val decision = decisions.getById(decisionId)
            ?: return@safely AdaptiveLifecycleResult.NotFound
        val existing = decision.assignment.actualIntervention
        if (existing != null) {
            val samePlan = existing != InterventionFamily.MomentPlan ||
                decision.assignment.momentPlanId == momentPlanId
            return@safely if (existing == intervention && samePlan) {
                AdaptiveLifecycleResult.Idempotent
            } else {
                AdaptiveLifecycleResult.ConflictingChoice
            }
        }
        if (intervention !in decision.assignment.eligibleInterventions) {
            return@safely AdaptiveLifecycleResult.IneligibleChoice
        }
        var validatedPlanRevision: Long? = null
        var validatedContentRevision: String? = null
        var validatedProtocol: InterventionProtocolContract? = null
        val validatedPlanId = if (intervention == InterventionFamily.MomentPlan) {
            val requestedId = momentPlanId?.takeIf { it.isNotBlank() }
                ?: return@safely AdaptiveLifecycleResult.InvalidMomentPlan
            val plan = momentPlans.getById(requestedId)
            if (
                plan == null ||
                !plan.enabled ||
                !AdaptiveModelValidator.isSafeAndValid(plan) ||
                (
                    decision.assignment.assignedSuggestion ==
                        InterventionFamily.MomentPlan &&
                        decision.assignment.momentPlanId != requestedId
                    )
            ) {
                return@safely AdaptiveLifecycleResult.InvalidMomentPlan
            }
            validatedPlanRevision = plan.updatedAtMillis
            validatedContentRevision = plan.contentRevisionId
            validatedProtocol = InterventionProtocolRegistry.resolveForPlan(plan)
                ?: return@safely AdaptiveLifecycleResult.InvalidMomentPlan
            requestedId
        } else {
            if (momentPlanId != null) {
                return@safely AdaptiveLifecycleResult.InvalidMomentPlan
            }
            validatedProtocol = InterventionProtocolRegistry.resolveForFamily(intervention)
                ?: return@safely AdaptiveLifecycleResult.IneligibleChoice
            null
        }
        val updated = decisions.recordActualChoiceOnce(
            decisionId = decisionId,
            intervention = intervention,
            momentPlanId = validatedPlanId,
            momentPlanUpdatedAtMillis = validatedPlanRevision,
            userOverrodeSuggestion =
                decision.assignment.assignedSuggestion != null &&
                    decision.assignment.assignedSuggestion != intervention,
            actualPlanContentRevisionId = validatedContentRevision,
            actualProtocolId = checkNotNull(validatedProtocol).protocolId.value,
            actualProtocolVersion = checkNotNull(validatedProtocol).version.value,
        )
        if (updated) {
            AdaptiveLifecycleResult.Applied
        } else {
            val latest = decisions.getById(decisionId)
                ?: return@safely AdaptiveLifecycleResult.NotFound
            if (
                latest.assignment.actualIntervention == intervention &&
                (
                    intervention != InterventionFamily.MomentPlan ||
                        latest.assignment.momentPlanId == validatedPlanId
                    )
            ) {
                AdaptiveLifecycleResult.Idempotent
            } else {
                AdaptiveLifecycleResult.ConflictingChoice
            }
        }
    }

    /**
     * Replaces a choice only while it is still pending. The immutable
     * recordActualChoice path remains the authority once an intervention starts.
     */
    suspend fun replacePendingActualChoice(
        decisionId: String,
        intervention: InterventionFamily,
        momentPlanId: String? = null,
    ): AdaptiveLifecycleResult = safely("replace pending actual choice") {
        val decision = decisions.getById(decisionId)
            ?: return@safely AdaptiveLifecycleResult.NotFound
        val existing = decision.assignment.actualIntervention
            ?: return@safely AdaptiveLifecycleResult.InvalidTransition
        if (intervention !in decision.assignment.eligibleInterventions) {
            return@safely AdaptiveLifecycleResult.IneligibleChoice
        }
        var validatedPlanRevision: Long? = null
        var validatedContentRevision: String? = null
        var validatedProtocol: InterventionProtocolContract? = null
        val validatedPlanId = if (intervention == InterventionFamily.MomentPlan) {
            val requestedId = momentPlanId?.takeIf { it.isNotBlank() }
                ?: return@safely AdaptiveLifecycleResult.InvalidMomentPlan
            val plan = momentPlans.getById(requestedId)
            if (
                plan == null ||
                !plan.enabled ||
                !AdaptiveModelValidator.isSafeAndValid(plan) ||
                (
                    decision.assignment.assignedSuggestion ==
                        InterventionFamily.MomentPlan &&
                        decision.assignment.momentPlanId != requestedId
                    )
            ) {
                return@safely AdaptiveLifecycleResult.InvalidMomentPlan
            }
            validatedPlanRevision = plan.updatedAtMillis
            validatedContentRevision = plan.contentRevisionId
            validatedProtocol = InterventionProtocolRegistry.resolveForPlan(plan)
                ?: return@safely AdaptiveLifecycleResult.InvalidMomentPlan
            requestedId
        } else {
            if (momentPlanId != null) {
                return@safely AdaptiveLifecycleResult.InvalidMomentPlan
            }
            validatedProtocol = InterventionProtocolRegistry.resolveForFamily(intervention)
                ?: return@safely AdaptiveLifecycleResult.IneligibleChoice
            null
        }
        val samePlan = intervention != InterventionFamily.MomentPlan ||
            (
                decision.assignment.momentPlanId == validatedPlanId &&
                    decision.assignment.actualPlanContentRevisionId == validatedContentRevision
                )
        if (existing == intervention && samePlan) {
            return@safely AdaptiveLifecycleResult.Idempotent
        }
        if (decision.startedAtMillis != null) {
            return@safely AdaptiveLifecycleResult.InvalidTransition
        }
        val updated = decisions.replacePendingActualChoice(
            decisionId = decisionId,
            intervention = intervention,
            momentPlanId = validatedPlanId,
            momentPlanUpdatedAtMillis = validatedPlanRevision,
            userOverrodeSuggestion =
                decision.assignment.assignedSuggestion != null &&
                    decision.assignment.assignedSuggestion != intervention,
            actualPlanContentRevisionId = validatedContentRevision,
            actualProtocolId = checkNotNull(validatedProtocol).protocolId.value,
            actualProtocolVersion = checkNotNull(validatedProtocol).version.value,
        )
        if (updated) {
            AdaptiveLifecycleResult.Applied
        } else {
            val latest = decisions.getById(decisionId)
                ?: return@safely AdaptiveLifecycleResult.NotFound
            val latestSamePlan = intervention != InterventionFamily.MomentPlan ||
                latest.assignment.momentPlanId == validatedPlanId
            if (
                latest.startedAtMillis == null &&
                latest.assignment.actualIntervention == intervention &&
                latestSamePlan
            ) {
                AdaptiveLifecycleResult.Idempotent
            } else {
                AdaptiveLifecycleResult.InvalidTransition
            }
        }
    }

    suspend fun markPresented(
        decisionId: String,
        timestamp: Long,
    ): AdaptiveLifecycleResult = safely("mark decision presented") {
        val decision = decisions.getById(decisionId)
            ?: return@safely AdaptiveLifecycleResult.NotFound
        if (!timestamp.isValidAfter(decision.createdAtMillis)) {
            return@safely AdaptiveLifecycleResult.InvalidTimestamp
        }
        val existing = decision.presentedAtMillis
        if (existing != null) {
            if (existing != timestamp) {
                return@safely AdaptiveLifecycleResult.InvalidTransition
            }
            return@safely if (
                scheduler.schedule(decisionId, decision.observationDeadlineAtMillis)
            ) {
                AdaptiveLifecycleResult.Idempotent
            } else {
                AdaptiveLifecycleResult.SchedulingFailure
            }
        }
        if (decision.completedAtMillis != null || decision.dismissedAtMillis != null) {
            return@safely AdaptiveLifecycleResult.InvalidTransition
        }
        if (!decisions.markPresentedOnce(decisionId, timestamp)) {
            return@safely AdaptiveLifecycleResult.InvalidTransition
        }
        if (scheduler.schedule(decisionId, decision.observationDeadlineAtMillis)) {
            AdaptiveLifecycleResult.Applied
        } else {
            AdaptiveLifecycleResult.SchedulingFailure
        }
    }

    suspend fun markStarted(
        decisionId: String,
        timestamp: Long,
    ): AdaptiveLifecycleResult = safely("mark decision started") {
        val decision = decisions.getById(decisionId)
            ?: return@safely AdaptiveLifecycleResult.NotFound
        val presentedAt = decision.presentedAtMillis
            ?: return@safely AdaptiveLifecycleResult.InvalidTransition
        if (decision.assignment.actualIntervention == null) {
            return@safely AdaptiveLifecycleResult.InvalidTransition
        }
        if (!timestamp.isValidAfter(presentedAt)) {
            return@safely AdaptiveLifecycleResult.InvalidTimestamp
        }
        decision.startedAtMillis?.let {
            return@safely if (it == timestamp) {
                AdaptiveLifecycleResult.Idempotent
            } else {
                AdaptiveLifecycleResult.InvalidTransition
            }
        }
        if (decision.completedAtMillis != null || decision.dismissedAtMillis != null) {
            return@safely AdaptiveLifecycleResult.InvalidTransition
        }
        if (decisions.markStartedOnce(decisionId, timestamp)) {
            AdaptiveLifecycleResult.Applied
        } else {
            AdaptiveLifecycleResult.InvalidTransition
        }
    }

    suspend fun markCompleted(
        decisionId: String,
        timestamp: Long,
    ): AdaptiveLifecycleResult = safely("mark decision completed") {
        val decision = decisions.getById(decisionId)
            ?: return@safely AdaptiveLifecycleResult.NotFound
        val startedAt = decision.startedAtMillis
            ?: return@safely AdaptiveLifecycleResult.InvalidTransition
        if (!timestamp.isValidAfter(startedAt)) {
            return@safely AdaptiveLifecycleResult.InvalidTimestamp
        }
        decision.completedAtMillis?.let {
            return@safely if (it == timestamp) {
                AdaptiveLifecycleResult.Idempotent
            } else {
                AdaptiveLifecycleResult.InvalidTransition
            }
        }
        if (decision.dismissedAtMillis != null) {
            return@safely AdaptiveLifecycleResult.InvalidTransition
        }
        if (decisions.markCompletedOnce(decisionId, timestamp)) {
            AdaptiveLifecycleResult.Applied
        } else {
            AdaptiveLifecycleResult.InvalidTransition
        }
    }

    /**
     * Both unstarted dismissal and started-but-incomplete dismissal use the
     * single persisted dismissedAtMillis field. Neither path fabricates a
     * completion.
     */
    suspend fun markDismissed(
        decisionId: String,
        timestamp: Long,
    ): AdaptiveLifecycleResult = safely("mark decision dismissed") {
        val decision = decisions.getById(decisionId)
            ?: return@safely AdaptiveLifecycleResult.NotFound
        val presentedAt = decision.presentedAtMillis
            ?: return@safely AdaptiveLifecycleResult.InvalidTransition
        val earliest = decision.startedAtMillis ?: presentedAt
        if (!timestamp.isValidAfter(earliest)) {
            return@safely AdaptiveLifecycleResult.InvalidTimestamp
        }
        decision.dismissedAtMillis?.let {
            return@safely if (it == timestamp) {
                AdaptiveLifecycleResult.Idempotent
            } else {
                AdaptiveLifecycleResult.InvalidTransition
            }
        }
        if (decision.completedAtMillis != null) {
            return@safely AdaptiveLifecycleResult.InvalidTransition
        }
        if (decisions.markDismissedOnce(decisionId, timestamp)) {
            AdaptiveLifecycleResult.Applied
        } else {
            AdaptiveLifecycleResult.InvalidTransition
        }
    }

    suspend fun updateFeedback(
        decisionId: String,
        feedbackCode: FeedbackCode,
        timestamp: Long,
    ): AdaptiveLifecycleResult = safely("update decision feedback") {
        val decision = decisions.getById(decisionId)
            ?: return@safely AdaptiveLifecycleResult.NotFound
        val earliest = decision.presentedAtMillis ?: decision.createdAtMillis
        if (!timestamp.isValidAfter(earliest)) {
            return@safely AdaptiveLifecycleResult.InvalidTimestamp
        }
        if (
            decision.feedbackCode == feedbackCode &&
            decision.feedbackUpdatedAtMillis == timestamp
        ) {
            return@safely AdaptiveLifecycleResult.Idempotent
        }
        if (decisions.updateFeedback(decisionId, feedbackCode, timestamp)) {
            AdaptiveLifecycleResult.Applied
        } else {
            AdaptiveLifecycleResult.NotFound
        }
    }

    private fun Long.isValidAfter(earliest: Long): Boolean =
        this >= earliest && this <= clock.nowMillis() && this >= 0L

    private suspend fun safely(
        operation: String,
        block: suspend () -> AdaptiveLifecycleResult,
    ): AdaptiveLifecycleResult = try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        logger.failure(operation, error)
        AdaptiveLifecycleResult.PersistenceFailure
    }

}
