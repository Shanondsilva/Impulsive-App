package com.impulsive.app.backend.domain.engine.adaptive

import com.impulsive.app.backend.domain.model.adaptive.AdaptiveDecision
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveMomentLimits
import com.impulsive.app.backend.domain.model.adaptive.ImpulsiveDestination
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentPlan
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanActionType
import com.impulsive.app.backend.domain.model.adaptive.RepeatObservation
import java.util.UUID

data class AdaptiveValidationIssue(
    val field: String,
    val message: String,
)

object AdaptiveModelValidator {
    private val AndroidPackageName =
        Regex("""^[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+$""")

    fun validate(plan: MomentPlan): List<AdaptiveValidationIssue> = buildList {
        if (!plan.planId.isUuid()) {
            add(issue("planId", "must be a UUID"))
        }
        if (!plan.contentRevisionId.isUuid()) {
            add(issue("contentRevisionId", "must be a UUID"))
        }
        validateRequiredText(
            field = "title",
            value = plan.title,
            maximumCharacters = AdaptiveMomentLimits.PlanTitleCharacters,
            issues = this,
        )
        validateRequiredText(
            field = "actionText",
            value = plan.actionText,
            maximumCharacters = AdaptiveMomentLimits.PlanActionCharacters,
            issues = this,
        )
        validateRequiredText(
            field = "futureCueText",
            value = plan.futureCueText,
            maximumCharacters = AdaptiveMomentLimits.PlanFutureCueCharacters,
            issues = this,
        )
        if (plan.createdAtMillis < 0L) {
            add(issue("createdAtMillis", "must not be negative"))
        }
        if (plan.updatedAtMillis < plan.createdAtMillis) {
            add(issue("updatedAtMillis", "must not precede creation"))
        }
        if (plan.rehearsedAtMillis != null && plan.rehearsedAtMillis < plan.createdAtMillis) {
            add(issue("rehearsedAtMillis", "must not precede creation"))
        }

        when (plan.actionType) {
            MomentPlanActionType.TextOnly -> {
                if (plan.actionTarget != null) {
                    add(issue("actionTarget", "must be absent for a text-only action"))
                }
            }

            MomentPlanActionType.OpenImpulsiveDestination -> {
                val allowedTargets = ImpulsiveDestination.entries.map { it.storageValue }
                if (plan.actionTarget !in allowedTargets) {
                    add(issue("actionTarget", "must be an approved Impulsive destination"))
                }
            }

            MomentPlanActionType.LaunchSelectedApp -> {
                if (plan.actionTarget?.matches(AndroidPackageName) != true) {
                    add(issue("actionTarget", "must be a valid selected application package"))
                }
            }
        }
    }

    fun validate(plans: List<MomentPlan>): List<AdaptiveValidationIssue> = buildList {
        plans.forEachIndexed { index, plan ->
            validate(plan).forEach { planIssue ->
                add(
                    planIssue.copy(
                        field = "plans[$index].${planIssue.field}",
                    ),
                )
            }
        }
        if (plans.count { it.enabled } > AdaptiveMomentLimits.MaximumEnabledPlans) {
            add(issue("plans", "must not contain more than six enabled plans"))
        }
        if (plans.groupingBy { it.planId }.eachCount().any { it.value > 1 }) {
            add(issue("plans", "must not contain duplicate plan IDs"))
        }
        if (plans.any { it.preferredForCue && !it.enabled }) {
            add(issue("plans", "a preferred plan must be enabled"))
        }
        if (
            plans
                .filter { it.enabled && it.preferredForCue }
                .groupingBy { it.momentCue }
                .eachCount()
                .any { it.value > 1 }
        ) {
            add(issue("plans", "only one enabled plan may be preferred for each cue"))
        }
    }

    fun validate(decision: AdaptiveDecision): List<AdaptiveValidationIssue> = buildList {
        if (!decision.decisionId.isUuid()) {
            add(issue("decisionId", "must be a UUID"))
        }
        if (decision.protectionIncidentToken.isBlank()) {
            add(issue("protectionIncidentToken", "must not be blank"))
        }
        if (!AdaptiveRecommendationPolicyVersion.isValid(decision.recommendationPolicyVersion)) {
            add(issue("recommendationPolicyVersion", "must be positive"))
        }
        if (decision.eligibleMomentPlanCount < 0) {
            add(issue("eligibleMomentPlanCount", "must not be negative"))
        }
        validateProtocolPair(
            "assignedProtocol",
            decision.assignedProtocolId,
            decision.assignedProtocolVersion,
            this,
        )
        validateProtocolPair(
            "actualProtocol",
            decision.actualProtocolId,
            decision.actualProtocolVersion,
            this,
        )
        if (decision.createdAtMillis < 0L) {
            add(issue("createdAtMillis", "must not be negative"))
        }
        if (decision.momentWindowStartedAtMillis > decision.createdAtMillis) {
            add(issue("momentWindowStartedAtMillis", "must not follow decision creation"))
        }
        if (decision.baselineUrgeRating != null && decision.baselineUrgeRating !in 0..10) {
            add(issue("baselineUrgeRating", "must be between 0 and 10"))
        }
        val probability = decision.assignment.selectionProbability
        if (probability != null && (probability <= 0.0 || probability > 1.0 || !probability.isFinite())) {
            add(issue("selectionProbability", "must be greater than 0 and at most 1"))
        }
        val suggestion = decision.assignment.assignedSuggestion
        if (suggestion != null && suggestion !in decision.assignment.eligibleInterventions) {
            add(issue("assignedSuggestion", "must be in the eligible set"))
        }
        if (decision.completedAtMillis != null && decision.dismissedAtMillis != null) {
            add(issue("outcome", "completion and dismissal cannot both be recorded"))
        }
        if (decision.completedAtMillis != null && decision.startedAtMillis == null) {
            add(issue("completedAtMillis", "completion requires a recorded start"))
        }
        if (
            decision.assignment.assignedSuggestion == InterventionFamily.MomentPlan &&
            decision.assignment.momentPlanId.isNullOrBlank()
        ) {
            add(issue("momentPlanId", "must identify the assigned Moment Plan"))
        }
        validateTimestampOrder(decision, this)
        if (
            decision.observationFinalisedAtMillis != null &&
            decision.repeatObservation == RepeatObservation.NotFinalised
        ) {
            add(issue("repeatObservation", "must be resolved when observation is finalised"))
        }
        if (
            decision.observationFinalisedAtMillis == null &&
            decision.repeatObservation != RepeatObservation.NotFinalised
        ) {
            add(issue("observationFinalisedAtMillis", "must be present for a resolved repeat observation"))
        }
    }

    fun isSafeAndValid(plan: MomentPlan): Boolean = validate(plan).isEmpty()

    private fun validateTimestampOrder(
        decision: AdaptiveDecision,
        issues: MutableList<AdaptiveValidationIssue>,
    ) {
        val createdAt = decision.createdAtMillis
        val presentedAt = decision.presentedAtMillis
        val startedAt = decision.startedAtMillis
        val completedAt = decision.completedAtMillis
        val dismissedAt = decision.dismissedAtMillis

        if (presentedAt != null && presentedAt < createdAt) {
            issues += issue("presentedAtMillis", "must not precede creation")
        }
        if (startedAt != null && startedAt < (presentedAt ?: createdAt)) {
            issues += issue("startedAtMillis", "must not precede presentation")
        }
        if (completedAt != null && completedAt < (startedAt ?: createdAt)) {
            issues += issue("completedAtMillis", "must not precede start")
        }
        if (dismissedAt != null && dismissedAt < (presentedAt ?: createdAt)) {
            issues += issue("dismissedAtMillis", "must not precede presentation")
        }
        if (
            decision.feedbackUpdatedAtMillis != null &&
            decision.feedbackUpdatedAtMillis < (presentedAt ?: createdAt)
        ) {
            issues += issue("feedbackUpdatedAtMillis", "must not precede presentation")
        }
        if (decision.firstRepeatAtMillis != null && decision.firstRepeatAtMillis < createdAt) {
            issues += issue("firstRepeatAtMillis", "must not precede creation")
        }
        if (decision.observationDeadlineAtMillis < createdAt) {
            issues += issue("observationDeadlineAtMillis", "must not precede creation")
        }
        if (
            decision.observationFinalisedAtMillis != null &&
            decision.observationFinalisedAtMillis < createdAt
        ) {
            issues += issue("observationFinalisedAtMillis", "must not precede creation")
        }
    }

    private fun validateRequiredText(
        field: String,
        value: String,
        maximumCharacters: Int,
        issues: MutableList<AdaptiveValidationIssue>,
    ) {
        if (value.isBlank()) {
            issues += issue(field, "must not be blank")
        }
        if (value.characterCount() > maximumCharacters) {
            issues += issue(field, "must not exceed $maximumCharacters characters")
        }
    }

    private fun validateProtocolPair(
        field: String,
        id: String?,
        version: Int?,
        issues: MutableList<AdaptiveValidationIssue>,
    ) {
        if ((id == null) != (version == null)) {
            issues += issue(field, "ID and version must be stored together")
            return
        }
        if (id != null && runCatching { InterventionProtocolId(id) }.isFailure) {
            issues += issue(field, "ID must be a stable protocol identifier")
        }
        if (version != null && version <= 0) {
            issues += issue(field, "version must be positive")
        }
    }

    private fun String.characterCount(): Int = codePointCount(0, length)

    private fun String.isUuid(): Boolean =
        runCatching { UUID.fromString(this) }.isSuccess

    private fun issue(field: String, message: String): AdaptiveValidationIssue =
        AdaptiveValidationIssue(field = field, message = message)
}
