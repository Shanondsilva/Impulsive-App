package com.impulsive.app.backend.data.restore

import com.impulsive.app.backend.data.local.entity.AdaptiveDecisionEntity
import com.impulsive.app.backend.data.local.entity.AdaptivePreferenceEntity
import com.impulsive.app.backend.data.local.entity.MomentPlanEntity
import com.impulsive.app.backend.data.local.entity.MomentPlanRehearsalEntity
import com.impulsive.app.backend.data.local.entity.PathShiftCycleEntity
import com.impulsive.app.backend.data.local.entity.ProtectionCoachSuggestionEntity
import com.impulsive.app.backend.domain.engine.adaptive.AdaptiveModelValidator
import com.impulsive.app.backend.domain.engine.adaptive.AdaptiveHistoryRetentionPolicy
import com.impulsive.app.backend.domain.engine.adaptive.LegacyMomentPlanContentRevisionFactory
import com.impulsive.app.backend.domain.engine.adaptive.AdaptiveRecommendationPolicyVersion
import com.impulsive.app.backend.domain.engine.adaptive.InterventionProtocolId
import com.impulsive.app.backend.domain.engine.adaptive.InterventionProtocolRegistry
import com.impulsive.app.backend.domain.engine.adaptive.InterventionProtocolVersion
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveAssignment
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveDecision
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveReasonCode
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSourceKind
import com.impulsive.app.backend.domain.model.adaptive.AssignmentMode
import com.impulsive.app.backend.domain.model.adaptive.FeedbackCode
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentCue
import com.impulsive.app.backend.domain.model.adaptive.MomentIntensity
import com.impulsive.app.backend.domain.model.adaptive.MomentPlan
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanActionType
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanRehearsalMode
import com.impulsive.app.backend.domain.model.adaptive.RepeatObservation
import com.impulsive.app.backend.domain.pathshift.PathShiftCycleStatus
import com.impulsive.app.backend.domain.pathshift.PathShiftEvidenceStrength
import com.impulsive.app.backend.domain.pathshift.PathShiftForecastPolicyVersion
import com.impulsive.app.backend.domain.protectioncoach.ProtectionCoachOnboardingReason
import com.impulsive.app.backend.domain.protectioncoach.ProtectionCoachSuggestionStatus
import com.impulsive.app.backend.domain.protectioncoach.ProtectionCoachSuggestionType
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class ValidatedAdaptiveRestorePayload(
    val plans: List<MomentPlanEntity>,
    val preferences: AdaptivePreferenceEntity,
    val decisions: List<AdaptiveDecisionEntity>,
    val rehearsals: List<MomentPlanRehearsalEntity>,
    val pathShiftCycles: List<PathShiftCycleEntity>,
    val protectionCoachSuggestions: List<ProtectionCoachSuggestionEntity>,
    val protectionMonitorTransitionCompleted: Boolean,
    val suggestedSetupReviewed: Boolean,
    val onboardingColdStartPriorUsed: Boolean,
)

/**
 * Optional payload extension shared by automatic and encrypted cloud recovery.
 *
 * The outer restore schema remains version 1 so historical bundles keep
 * importing. Once [JsonKey] exists, every adaptive section is mandatory and
 * validated before Room is touched. Open rehearsals are restored as dismissed:
 * a practice screen is transient navigation state and must never be resumed
 * after a device/account restore.
 */
internal object AdaptiveRestorePayloadCodec {
    const val JsonKey = "adaptiveData"
    internal const val CurrentFormatVersion = 4
    private const val PathShiftFormatVersion = 3
    private const val PreviousFormatVersion = 2
    private const val LegacyFormatVersion = 1
    private const val MaximumPlans = 1_000
    private const val MaximumDecisions = 100_000
    private const val MaximumRehearsals = 100_000
    private const val MaximumPathShiftCycles = 10_000
    private const val MaximumProtectionCoachSuggestions = 10_000
    private const val MaximumText = 1_024
    private val KnownEligibilityMask =
        InterventionFamily.entries.fold(0) { mask, family ->
            mask or family.eligibilityBit
        }

    fun encode(
        plans: List<MomentPlanEntity>,
        preferences: AdaptivePreferenceEntity?,
        decisions: List<AdaptiveDecisionEntity>,
        rehearsals: List<MomentPlanRehearsalEntity>,
        pathShiftCycles: List<PathShiftCycleEntity> = emptyList(),
        protectionCoachSuggestions: List<ProtectionCoachSuggestionEntity> = emptyList(),
        protectionMonitorTransitionCompleted: Boolean = false,
        suggestedSetupReviewed: Boolean = false,
        onboardingColdStartPriorUsed: Boolean = false,
    ): JSONObject {
        val plansById = plans.associateBy(MomentPlanEntity::planId)
        return JSONObject()
        .put("formatVersion", CurrentFormatVersion)
        .put("momentPlans", plans.toJsonArray(::encodePlan))
        .put(
            "preferences",
            encodePreferences(preferences ?: AdaptivePreferenceEntity()),
        )
        .put("decisions", decisions.toJsonArray { encodeDecision(it, plansById) })
        .put("rehearsals", rehearsals.toJsonArray(::encodeRehearsal))
        .put("pathShiftCycles", pathShiftCycles.toJsonArray(::encodePathShiftCycle))
        .put(
            "protectionCoachSuggestions",
            protectionCoachSuggestions.toJsonArray(::encodeProtectionCoachSuggestion),
        )
        .put("protectionMonitorTransitionCompleted", protectionMonitorTransitionCompleted)
        .put("suggestedSetupReviewed", suggestedSetupReviewed)
        .put("onboardingColdStartPriorUsed", onboardingColdStartPriorUsed)
    }

    fun decodeIfPresent(
        payload: JSONObject,
        restoredAtMillis: Long,
    ): ValidatedAdaptiveRestorePayload? {
        if (!payload.has(JsonKey)) return null
        val adaptive = requiredObject(payload, JsonKey)
        val formatVersion = requiredInt(adaptive, "formatVersion")
        require(
                formatVersion == LegacyFormatVersion ||
                formatVersion == PreviousFormatVersion ||
                formatVersion == PathShiftFormatVersion ||
                formatVersion == CurrentFormatVersion,
        ) {
            "Unsupported adaptive restore payload version"
        }
        require(adaptive.has("momentPlans"))
        require(adaptive.has("preferences"))
        require(adaptive.has("decisions"))
        require(adaptive.has("rehearsals"))
        if (formatVersion >= PathShiftFormatVersion) {
            require(adaptive.has("pathShiftCycles"))
        }
        if (formatVersion == CurrentFormatVersion) {
            require(adaptive.has("protectionCoachSuggestions"))
        }

        val plansJson = requiredArray(adaptive, "momentPlans")
        val decisionsJson = requiredArray(adaptive, "decisions")
        val rehearsalsJson = requiredArray(adaptive, "rehearsals")
        val pathShiftCyclesJson = if (formatVersion >= PathShiftFormatVersion) {
            requiredArray(adaptive, "pathShiftCycles")
        } else {
            JSONArray()
        }
        val protectionCoachJson = if (formatVersion == CurrentFormatVersion) {
            requiredArray(adaptive, "protectionCoachSuggestions")
        } else {
            JSONArray()
        }
        require(plansJson.length() <= MaximumPlans) { "Too many Moment Plans" }
        require(decisionsJson.length() <= MaximumDecisions) { "Too many adaptive decisions" }
        require(rehearsalsJson.length() <= MaximumRehearsals) { "Too many rehearsals" }
        require(pathShiftCyclesJson.length() <= MaximumPathShiftCycles) {
            "Too many PathShift cycles"
        }
        require(protectionCoachJson.length() <= MaximumProtectionCoachSuggestions) {
            "Too many Protection Coach suggestions"
        }

        val plans = plansJson.mapObjects { decodePlan(it, formatVersion) }
        require(AdaptiveModelValidator.validate(plans.map { it.toDomain() }).isEmpty()) {
            "Invalid Moment Plan restore data"
        }

        val preferences = decodePreferences(
            requiredObject(adaptive, "preferences"),
            formatVersion,
        )
        val plansById = plans.associateBy(MomentPlanEntity::planId)
        val decisions = decisionsJson.mapObjects {
            decodeDecision(it, formatVersion, plansById)
        }
        require(decisions.map { it.toDomain() }.all {
            AdaptiveModelValidator.validate(it).isEmpty()
        }) {
            "Invalid adaptive decision restore data"
        }
        val rehearsals = rehearsalsJson.mapObjects { json ->
            decodeRehearsal(json, restoredAtMillis, formatVersion)
        }
        val pathShiftCycles = pathShiftCyclesJson.mapObjects { json ->
            decodePathShiftCycle(json, plans.associateBy(MomentPlanEntity::planId))
        }
        val protectionCoachSuggestions = protectionCoachJson.mapObjects {
            decodeProtectionCoachSuggestion(it)
        }

        require(plans.distinctBy { it.planId }.size == plans.size) {
            "Duplicate Moment Plan ID"
        }
        require(decisions.distinctBy { it.decisionId }.size == decisions.size) {
            "Duplicate adaptive decision ID"
        }
        require(rehearsals.distinctBy { it.rehearsalId }.size == rehearsals.size) {
            "Duplicate rehearsal ID"
        }
        require(decisions.distinctBy { it.protectionIncidentToken }.size == decisions.size) {
            "Duplicate adaptive incident identity"
        }
        require(pathShiftCycles.distinctBy { it.cycleId }.size == pathShiftCycles.size) {
            "Duplicate PathShift cycle ID"
        }
        require(
            protectionCoachSuggestions.distinctBy { it.suggestionId }.size ==
                protectionCoachSuggestions.size,
        ) {
            "Duplicate Protection Coach suggestion ID"
        }
        require(pathShiftCycles.count { it.status == PathShiftCycleStatus.Active.name } <= 1) {
            "Multiple active PathShift cycles"
        }

        return ValidatedAdaptiveRestorePayload(
            plans = plans,
            preferences = preferences,
            decisions = decisions,
            rehearsals = rehearsals,
            pathShiftCycles = pathShiftCycles,
            protectionCoachSuggestions = protectionCoachSuggestions.filterNot {
                it.status in setOf("Prepared", "Presented") &&
                    it.expiresAtMillis <= restoredAtMillis
            } + protectionCoachSuggestions.filter {
                it.status in setOf("Prepared", "Presented") &&
                    it.expiresAtMillis <= restoredAtMillis
            }.map { it.copy(status = ProtectionCoachSuggestionStatus.Expired.name) },
            protectionMonitorTransitionCompleted =
                if (formatVersion == CurrentFormatVersion) {
                    requiredBoolean(adaptive, "protectionMonitorTransitionCompleted")
                } else {
                    false
                },
            suggestedSetupReviewed =
                if (formatVersion == CurrentFormatVersion) {
                    requiredBoolean(adaptive, "suggestedSetupReviewed")
                } else {
                    false
                },
            onboardingColdStartPriorUsed =
                if (formatVersion == CurrentFormatVersion) {
                    requiredBoolean(adaptive, "onboardingColdStartPriorUsed")
                } else {
                    false
                },
        )
    }

    private fun encodePlan(plan: MomentPlanEntity): JSONObject = JSONObject()
        .put("planId", plan.planId)
        .put("title", plan.title)
        .putNullable("momentCue", plan.momentCue)
        .put("actionText", plan.actionText)
        .put("futureCueText", plan.futureCueText)
        .put("actionType", plan.actionType)
        .putNullable("actionTarget", plan.actionTarget)
        .put("enabled", plan.enabled)
        .put("preferredForCue", plan.preferredForCue)
        .put("createdAtMillis", plan.createdAtMillis)
        .put("updatedAtMillis", plan.updatedAtMillis)
        .putNullable("rehearsedAtMillis", plan.rehearsedAtMillis)
        .put(
            "contentRevisionId",
            plan.contentRevisionId.takeIf { it.isNotBlank() }
                ?: LegacyMomentPlanContentRevisionFactory.create(
                    plan.planId,
                    plan.updatedAtMillis,
                ),
        )

    private fun encodePreferences(value: AdaptivePreferenceEntity): JSONObject = JSONObject()
        .put("personalSuggestionsEnabled", value.personalSuggestionsEnabled)
        .put("gameSuggestionsEnabled", value.gameSuggestionsEnabled)
        .put("readingSuggestionsEnabled", value.readingSuggestionsEnabled)
        .put("momentPlanSuggestionsEnabled", value.momentPlanSuggestionsEnabled)
        .put("randomisedExplorationEnabled", value.randomisedExplorationEnabled)
        .put("privateScreenProtectionEnabled", value.privateScreenProtectionEnabled)
        .put("historyRetentionPolicy", value.historyRetentionPolicy)
        .put("pathShiftEnabled", true)
        .put("updatedAtMillis", value.updatedAtMillis)

    private fun encodePathShiftCycle(value: PathShiftCycleEntity): JSONObject = JSONObject()
        .put("cycleId", value.cycleId)
        .put("createdAtMillis", value.createdAtMillis)
        .put("lookbackStartedAtMillis", value.lookbackStartedAtMillis)
        .put("lookbackEndedAtMillis", value.lookbackEndedAtMillis)
        .put("forecastWindowStartedAtMillis", value.forecastWindowStartedAtMillis)
        .put("forecastWindowEndsAtMillis", value.forecastWindowEndsAtMillis)
        .put("forecastPolicyVersion", value.forecastPolicyVersion)
        .put("evidenceStrength", value.evidenceStrength)
        .put("inputProtectedMomentCount", value.inputProtectedMomentCount)
        .put("inputDistinctDayCount", value.inputDistinctDayCount)
        .put("estimatedLowerCount", value.estimatedLowerCount)
        .put("estimatedUpperCount", value.estimatedUpperCount)
        .putNullable("commonWindowStartMinute", value.commonWindowStartMinute)
        .putNullable("commonWindowEndMinute", value.commonWindowEndMinute)
        .putNullable("preparedPlanId", value.preparedPlanId)
        .putNullable(
            "preparedPlanContentRevisionId",
            value.preparedPlanContentRevisionId,
        )
        .putNullable("preparedAtMillis", value.preparedAtMillis)
        .putNullable("reviewFinalisedAtMillis", value.reviewFinalisedAtMillis)
        .put("observedProtectedMomentCount", value.observedProtectedMomentCount)
        .put("preparedPlanSelectedCount", value.preparedPlanSelectedCount)
        .put("preparedPlanStartedCount", value.preparedPlanStartedCount)
        .put("preparedPlanCompletedCount", value.preparedPlanCompletedCount)
        .put("preparedPlanDismissedCount", value.preparedPlanDismissedCount)
        .put("wrongTimingCount", value.wrongTimingCount)
        .put("repeatDetectedCount", value.repeatDetectedCount)
        .put("status", value.status)
        .putNullable("cancelledAtMillis", value.cancelledAtMillis)

    private fun encodeProtectionCoachSuggestion(
        value: ProtectionCoachSuggestionEntity,
    ): JSONObject = JSONObject()
        .put("suggestionId", value.suggestionId)
        .put("policyVersion", value.policyVersion)
        .put("suggestionType", value.suggestionType)
        .put("createdAtMillis", value.createdAtMillis)
        .put("expiresAtMillis", value.expiresAtMillis)
        .put("status", value.status)
        .putNullable("presentedAtMillis", value.presentedAtMillis)
        .putNullable("acceptedAtMillis", value.acceptedAtMillis)
        .putNullable("dismissedAtMillis", value.dismissedAtMillis)
        .putNullable("suppressedAtMillis", value.suppressedAtMillis)
        .putNullable("evidenceWindowStartedAtMillis", value.evidenceWindowStartedAtMillis)
        .putNullable("evidenceWindowEndedAtMillis", value.evidenceWindowEndedAtMillis)
        .put("evidenceProtectedMomentCount", value.evidenceProtectedMomentCount)
        .put("evidenceDistinctDayCount", value.evidenceDistinctDayCount)
        .putNullable("broadWindowStartMinute", value.broadWindowStartMinute)
        .putNullable("broadWindowEndMinute", value.broadWindowEndMinute)
        .putNullable("suggestedStartMinute", value.suggestedStartMinute)
        .putNullable("suggestedEndMinute", value.suggestedEndMinute)
        .putNullable("acceptedStartMinute", value.acceptedStartMinute)
        .putNullable("acceptedEndMinute", value.acceptedEndMinute)
        .putNullable("onboardingReasonCode", value.onboardingReasonCode)
        .putNullable("relatedMomentPlanId", value.relatedMomentPlanId)
        .putNullable(
            "relatedMomentPlanContentRevisionId",
            value.relatedMomentPlanContentRevisionId,
        )

    private fun encodeDecision(
        value: AdaptiveDecisionEntity,
        plansById: Map<String, MomentPlanEntity>,
    ): JSONObject {
        val assignedProtocol = value.protocolForExport(
            value.assignedSuggestion,
            plansById,
            value.assignedProtocolId,
            value.assignedProtocolVersion,
        )
        val actualProtocol = value.protocolForExport(
            value.actualIntervention,
            plansById,
            value.actualProtocolId,
            value.actualProtocolVersion,
        )
        return JSONObject()
        .put("decisionId", value.decisionId)
        .put("sourceKind", value.sourceKind)
        .put("createdAtMillis", value.createdAtMillis)
        .put("momentWindowStartedAtMillis", value.momentWindowStartedAtMillis)
        .put("momentIntensity", value.momentIntensity)
        .putNullable("momentCue", value.momentCue)
        .putNullable("baselineUrgeRating", value.baselineUrgeRating)
        .put("assignmentMode", value.assignmentMode)
        .put("eligibleInterventionsMask", value.eligibleInterventionsMask)
        .putNullable("assignedSuggestion", value.assignedSuggestion)
        .putNullable("actualIntervention", value.actualIntervention)
        .putNullable("selectionProbability", value.selectionProbability)
        .put("reasonCode", value.reasonCode)
        .putNullable("momentPlanId", value.momentPlanId)
        .putNullable("momentPlanUpdatedAtMillis", value.momentPlanUpdatedAtMillis)
        .putNullable(
            "assignedPlanContentRevisionId",
            value.assignedPlanContentRevisionId
                ?: value.legacyPlanRevisionFor(InterventionFamily.MomentPlan, assigned = true),
        )
        .putNullable(
            "actualPlanContentRevisionId",
            value.actualPlanContentRevisionId
                ?: value.legacyPlanRevisionFor(InterventionFamily.MomentPlan, assigned = false),
        )
        .put("userOverrodeSuggestion", value.userOverrodeSuggestion)
        .putNullable("presentedAtMillis", value.presentedAtMillis)
        .putNullable("startedAtMillis", value.startedAtMillis)
        .putNullable("completedAtMillis", value.completedAtMillis)
        .putNullable("dismissedAtMillis", value.dismissedAtMillis)
        .put("feedbackCode", value.feedbackCode)
        .putNullable("feedbackUpdatedAtMillis", value.feedbackUpdatedAtMillis)
        .putNullable("repeatDetectedWithin20Minutes", value.repeatDetectedWithin20Minutes)
        .putNullable("firstRepeatAtMillis", value.firstRepeatAtMillis)
        .put("observationDeadlineAtMillis", value.observationDeadlineAtMillis)
        .putNullable("observationFinalisedAtMillis", value.observationFinalisedAtMillis)
        .put("recommendationPolicyVersion", value.recommendationPolicyVersion)
        .putNullable("assignedProtocolId", assignedProtocol?.first)
        .putNullable("assignedProtocolVersion", assignedProtocol?.second)
        .putNullable("actualProtocolId", actualProtocol?.first)
        .putNullable("actualProtocolVersion", actualProtocol?.second)
        .put("eligibleMomentPlanCount", value.eligibleMomentPlanCount)
    }

    private fun encodeRehearsal(value: MomentPlanRehearsalEntity): JSONObject = JSONObject()
        .put("rehearsalId", value.rehearsalId)
        .put("planId", value.planId)
        .put("planUpdatedAtMillisAtStart", value.planUpdatedAtMillisAtStart)
        .put("mode", value.mode)
        .put("startedAtMillis", value.startedAtMillis)
        .putNullable("completedAtMillis", value.completedAtMillis)
        .putNullable("dismissedAtMillis", value.dismissedAtMillis)
        .put(
            "planContentRevisionId",
            value.planContentRevisionId.takeIf { it.isNotBlank() }
                ?: LegacyMomentPlanContentRevisionFactory.create(
                    value.planId,
                    value.planUpdatedAtMillisAtStart,
                ),
        )

    private fun decodePlan(
        json: JSONObject,
        formatVersion: Int,
    ): MomentPlanEntity {
        val planId = requiredUuid(json, "planId")
        val updatedAtMillis = requiredLong(json, "updatedAtMillis")
        return MomentPlanEntity(
        planId = planId,
        title = requiredString(json, "title"),
        momentCue = nullableEnum<MomentCue>(json, "momentCue")?.name,
        actionText = requiredString(json, "actionText"),
        futureCueText = requiredString(json, "futureCueText"),
        actionType = requiredEnum<MomentPlanActionType>(json, "actionType").name,
        actionTarget = nullableString(json, "actionTarget"),
        enabled = requiredBoolean(json, "enabled"),
        preferredForCue = requiredBoolean(json, "preferredForCue"),
        createdAtMillis = requiredLong(json, "createdAtMillis"),
        updatedAtMillis = updatedAtMillis,
        rehearsedAtMillis = nullableLong(json, "rehearsedAtMillis"),
        contentRevisionId = if (formatVersion == LegacyFormatVersion) {
            nullableUuid(json, "contentRevisionId")
                ?: LegacyMomentPlanContentRevisionFactory.create(planId, updatedAtMillis)
        } else {
            requiredUuid(json, "contentRevisionId")
        },
    )
    }

    private fun decodePreferences(
        json: JSONObject,
        formatVersion: Int,
    ): AdaptivePreferenceEntity {
        val updatedAtMillis = requiredLong(json, "updatedAtMillis")
        require(updatedAtMillis >= 0L) { "Invalid adaptive preference timestamp" }
        if (
            formatVersion >=
            PathShiftFormatVersion
        ) {
            requiredBoolean(
                json,
                "pathShiftEnabled",
            )
        }
        return AdaptivePreferenceEntity(
            personalSuggestionsEnabled = requiredBoolean(json, "personalSuggestionsEnabled"),
            gameSuggestionsEnabled = requiredBoolean(json, "gameSuggestionsEnabled"),
            readingSuggestionsEnabled = requiredBoolean(json, "readingSuggestionsEnabled"),
            momentPlanSuggestionsEnabled = requiredBoolean(json, "momentPlanSuggestionsEnabled"),
            randomisedExplorationEnabled = requiredBoolean(json, "randomisedExplorationEnabled"),
            privateScreenProtectionEnabled =
                if (formatVersion >= PreviousFormatVersion) {
                    requiredBoolean(json, "privateScreenProtectionEnabled")
                } else {
                    true
                },
            historyRetentionPolicy =
                if (formatVersion >= PreviousFormatVersion) {
                    requiredEnum<AdaptiveHistoryRetentionPolicy>(
                        json,
                        "historyRetentionPolicy",
                    ).name
                } else {
                    AdaptiveHistoryRetentionPolicy.SixMonths.name
                },
            updatedAtMillis = updatedAtMillis,
            pathShiftEnabled =
                true,
        )
    }

    private fun decodePathShiftCycle(
        json: JSONObject,
        plansById: Map<String, MomentPlanEntity>,
    ): PathShiftCycleEntity {
        val cycleId = requiredUuid(json, "cycleId")
        val createdAt = requiredLong(json, "createdAtMillis")
        val lookbackStart = requiredLong(json, "lookbackStartedAtMillis")
        val lookbackEnd = requiredLong(json, "lookbackEndedAtMillis")
        val forecastStart = requiredLong(json, "forecastWindowStartedAtMillis")
        val forecastEnd = requiredLong(json, "forecastWindowEndsAtMillis")
        val policyVersion = requiredInt(json, "forecastPolicyVersion")
        val evidence = requiredEnum<PathShiftEvidenceStrength>(json, "evidenceStrength")
        val inputCount = requiredInt(json, "inputProtectedMomentCount")
        val distinctDays = requiredInt(json, "inputDistinctDayCount")
        val lower = requiredInt(json, "estimatedLowerCount")
        val upper = requiredInt(json, "estimatedUpperCount")
        val commonStart = nullableInt(json, "commonWindowStartMinute")
        val commonEnd = nullableInt(json, "commonWindowEndMinute")
        val preparedPlanId = nullableUuid(json, "preparedPlanId")
        val preparedRevision = nullableUuid(json, "preparedPlanContentRevisionId")
        val preparedAt = nullableLong(json, "preparedAtMillis")
        val reviewAt = nullableLong(json, "reviewFinalisedAtMillis")
        val observed = requiredInt(json, "observedProtectedMomentCount")
        val selected = requiredInt(json, "preparedPlanSelectedCount")
        val started = requiredInt(json, "preparedPlanStartedCount")
        val completed = requiredInt(json, "preparedPlanCompletedCount")
        val dismissed = requiredInt(json, "preparedPlanDismissedCount")
        val wrongTiming = requiredInt(json, "wrongTimingCount")
        val repeat = requiredInt(json, "repeatDetectedCount")
        val status = requiredEnum<PathShiftCycleStatus>(json, "status")
        val cancelledAt = nullableLong(json, "cancelledAtMillis")

        require(createdAt >= 0L)
        require(lookbackEnd > lookbackStart)
        require(forecastEnd > forecastStart)
        require(policyVersion == PathShiftForecastPolicyVersion.Current)
        require(evidence != PathShiftEvidenceStrength.Insufficient)
        require(inputCount >= 0 && distinctDays >= 0)
        require(lower >= 0 && upper >= lower)
        require((commonStart == null) == (commonEnd == null))
        require(commonStart == null || commonStart in 0 until 24 * 60)
        require(commonEnd == null || commonEnd in 1..24 * 60)
        require((preparedPlanId == null) == (preparedRevision == null))
        require(preparedAt == null || preparedPlanId != null)
        require(preparedPlanId == null || plansById.containsKey(preparedPlanId))
        require(
            listOf(
                observed,
                selected,
                started,
                completed,
                dismissed,
                wrongTiming,
                repeat,
            ).all { it >= 0 },
        )
        require(selected <= observed)
        require(started <= selected)
        require(completed <= started)
        require(dismissed <= started)
        require(wrongTiming <= observed)
        require(repeat <= observed)
        when (status) {
            PathShiftCycleStatus.Active -> {
                require(reviewAt == null && cancelledAt == null)
            }
            PathShiftCycleStatus.Finalised -> {
                require(reviewAt != null && reviewAt >= forecastEnd)
                require(cancelledAt == null)
            }
            PathShiftCycleStatus.Cancelled -> {
                require(reviewAt == null && cancelledAt != null)
            }
        }
        return PathShiftCycleEntity(
            cycleId = cycleId,
            createdAtMillis = createdAt,
            lookbackStartedAtMillis = lookbackStart,
            lookbackEndedAtMillis = lookbackEnd,
            forecastWindowStartedAtMillis = forecastStart,
            forecastWindowEndsAtMillis = forecastEnd,
            forecastPolicyVersion = policyVersion,
            evidenceStrength = evidence.name,
            inputProtectedMomentCount = inputCount,
            inputDistinctDayCount = distinctDays,
            estimatedLowerCount = lower,
            estimatedUpperCount = upper,
            commonWindowStartMinute = commonStart,
            commonWindowEndMinute = commonEnd,
            preparedPlanId = preparedPlanId,
            preparedPlanContentRevisionId = preparedRevision,
            preparedAtMillis = preparedAt,
            reviewFinalisedAtMillis = reviewAt,
            observedProtectedMomentCount = observed,
            preparedPlanSelectedCount = selected,
            preparedPlanStartedCount = started,
            preparedPlanCompletedCount = completed,
            preparedPlanDismissedCount = dismissed,
            wrongTimingCount = wrongTiming,
            repeatDetectedCount = repeat,
            status = status.name,
            cancelledAtMillis = cancelledAt,
        )
    }

    private fun decodeProtectionCoachSuggestion(
        json: JSONObject,
    ): ProtectionCoachSuggestionEntity {
        val suggestionId = requiredUuid(json, "suggestionId")
        val policyVersion = requiredInt(json, "policyVersion")
        val suggestionType = requiredEnum<ProtectionCoachSuggestionType>(
            json,
            "suggestionType",
        ).name
        val status = requiredEnum<ProtectionCoachSuggestionStatus>(
            json,
            "status",
        ).name
        val onboardingReason = nullableEnum<ProtectionCoachOnboardingReason>(
            json,
            "onboardingReasonCode",
        )?.name
        val createdAt = requiredLong(json, "createdAtMillis")
        val expiresAt = requiredLong(json, "expiresAtMillis")
        val evidenceCount = requiredInt(json, "evidenceProtectedMomentCount")
        val distinctDayCount = requiredInt(json, "evidenceDistinctDayCount")
        val broadStart = nullableMinute(json, "broadWindowStartMinute")
        val broadEnd = nullableMinute(json, "broadWindowEndMinute")
        val suggestedStart = nullableMinute(json, "suggestedStartMinute")
        val suggestedEnd = nullableMinute(json, "suggestedEndMinute")
        val acceptedStart = nullableMinute(json, "acceptedStartMinute")
        val acceptedEnd = nullableMinute(json, "acceptedEndMinute")
        val acceptedAt = nullableLong(json, "acceptedAtMillis")
        val dismissedAt = nullableLong(json, "dismissedAtMillis")
        val suppressedAt = nullableLong(json, "suppressedAtMillis")

        require(policyVersion > 0)
        require(expiresAt > createdAt)
        require(evidenceCount >= 0)
        require(distinctDayCount >= 0)
        require(listOfNotNull(acceptedAt, dismissedAt, suppressedAt).size <= 1)
        require(status != ProtectionCoachSuggestionStatus.Accepted.name || acceptedAt != null)
        require(status != ProtectionCoachSuggestionStatus.AcceptedWithEdits.name ||
            (acceptedAt != null && acceptedStart != null && acceptedEnd != null))
        require(status != ProtectionCoachSuggestionStatus.Dismissed.name || dismissedAt != null)
        require(status != ProtectionCoachSuggestionStatus.Suppressed.name || suppressedAt != null)

        return ProtectionCoachSuggestionEntity(
            suggestionId = suggestionId,
            policyVersion = policyVersion,
            suggestionType = suggestionType,
            createdAtMillis = createdAt,
            expiresAtMillis = expiresAt,
            status = status,
            presentedAtMillis = nullableLong(json, "presentedAtMillis"),
            acceptedAtMillis = acceptedAt,
            dismissedAtMillis = dismissedAt,
            suppressedAtMillis = suppressedAt,
            evidenceWindowStartedAtMillis = nullableLong(json, "evidenceWindowStartedAtMillis"),
            evidenceWindowEndedAtMillis = nullableLong(json, "evidenceWindowEndedAtMillis"),
            evidenceProtectedMomentCount = evidenceCount,
            evidenceDistinctDayCount = distinctDayCount,
            broadWindowStartMinute = broadStart,
            broadWindowEndMinute = broadEnd,
            suggestedStartMinute = suggestedStart,
            suggestedEndMinute = suggestedEnd,
            acceptedStartMinute = acceptedStart,
            acceptedEndMinute = acceptedEnd,
            onboardingReasonCode = onboardingReason,
            relatedMomentPlanId = nullableUuid(json, "relatedMomentPlanId"),
            relatedMomentPlanContentRevisionId =
                nullableUuid(json, "relatedMomentPlanContentRevisionId"),
        )
    }

    private fun decodeDecision(
        json: JSONObject,
        formatVersion: Int,
        plansById: Map<String, MomentPlanEntity>,
    ): AdaptiveDecisionEntity {
        val decisionId = requiredUuid(json, "decisionId")
        val mask = requiredInt(json, "eligibleInterventionsMask")
        require(mask > 0 && mask and KnownEligibilityMask == mask) {
            "Invalid eligible intervention mask"
        }
        val assigned = nullableEnum<InterventionFamily>(json, "assignedSuggestion")
        val actual = nullableEnum<InterventionFamily>(json, "actualIntervention")
        val planId = nullableUuid(json, "momentPlanId")
        val planRevision = nullableLong(json, "momentPlanUpdatedAtMillis")
        require(
            (assigned != InterventionFamily.MomentPlan && actual != InterventionFamily.MomentPlan) ||
                (planId != null && planRevision != null),
        ) {
            "Moment Plan decisions require a plan ID and revision"
        }
        val legacyPlanContentRevision = if (planId != null && planRevision != null) {
            LegacyMomentPlanContentRevisionFactory.create(planId, planRevision)
        } else {
            null
        }
        val recommendationPolicyVersion = if (formatVersion == LegacyFormatVersion) {
            nullableInt(json, "recommendationPolicyVersion")
                ?: AdaptiveRecommendationPolicyVersion.Current
        } else {
            requiredInt(json, "recommendationPolicyVersion")
        }
        require(AdaptiveRecommendationPolicyVersion.isValid(recommendationPolicyVersion)) {
            "Invalid recommendation policy version"
        }
        val storedAssignedProtocol = validatedHistoricalProtocol(
            nullableString(json, "assignedProtocolId"),
            nullableInt(json, "assignedProtocolVersion"),
        )
        val storedActualProtocol = validatedHistoricalProtocol(
            nullableString(json, "actualProtocolId"),
            nullableInt(json, "actualProtocolVersion"),
        )
        val assignedProtocol = storedAssignedProtocol
            ?: legacyDefaultProtocol(formatVersion, assigned, planId, plansById)
        val actualProtocol = storedActualProtocol
            ?: legacyDefaultProtocol(formatVersion, actual, planId, plansById)
        require(assignedProtocol == null || assignedProtocol.family == assigned) {
            "Assigned protocol is incompatible with the assigned intervention family"
        }
        require(actualProtocol == null || actualProtocol.family == actual) {
            "Actual protocol is incompatible with the actual intervention family"
        }
        val eligibleMomentPlanCount = if (formatVersion == LegacyFormatVersion) {
            nullableInt(json, "eligibleMomentPlanCount") ?: 0
        } else {
            requiredInt(json, "eligibleMomentPlanCount")
        }
        require(eligibleMomentPlanCount >= 0) { "Invalid eligible Moment Plan count" }

        return AdaptiveDecisionEntity(
            decisionId = decisionId,
            protectionIncidentToken = "restored:$decisionId",
            sourceKind = requiredEnum<AdaptiveSourceKind>(json, "sourceKind").name,
            createdAtMillis = requiredLong(json, "createdAtMillis"),
            momentWindowStartedAtMillis = requiredLong(json, "momentWindowStartedAtMillis"),
            momentIntensity = requiredEnum<MomentIntensity>(json, "momentIntensity").name,
            momentCue = nullableEnum<MomentCue>(json, "momentCue")?.name,
            baselineUrgeRating = nullableInt(json, "baselineUrgeRating"),
            assignmentMode = requiredEnum<AssignmentMode>(json, "assignmentMode").name,
            eligibleInterventionsMask = mask,
            assignedSuggestion = assigned?.name,
            actualIntervention = actual?.name,
            selectionProbability = nullableDouble(json, "selectionProbability"),
            reasonCode = requiredEnum<AdaptiveReasonCode>(json, "reasonCode").name,
            momentPlanId = planId,
            momentPlanUpdatedAtMillis = planRevision,
            userOverrodeSuggestion = requiredBoolean(json, "userOverrodeSuggestion"),
            presentedAtMillis = nullableLong(json, "presentedAtMillis"),
            startedAtMillis = nullableLong(json, "startedAtMillis"),
            completedAtMillis = nullableLong(json, "completedAtMillis"),
            dismissedAtMillis = nullableLong(json, "dismissedAtMillis"),
            feedbackCode = requiredEnum<FeedbackCode>(json, "feedbackCode").name,
            feedbackUpdatedAtMillis = nullableLong(json, "feedbackUpdatedAtMillis"),
            repeatDetectedWithin20Minutes =
                nullableBoolean(json, "repeatDetectedWithin20Minutes"),
            firstRepeatAtMillis = nullableLong(json, "firstRepeatAtMillis"),
            observationDeadlineAtMillis = requiredLong(json, "observationDeadlineAtMillis"),
            observationFinalisedAtMillis = nullableLong(json, "observationFinalisedAtMillis"),
            assignedPlanContentRevisionId = decodePlanContentRevision(
                json = json,
                name = "assignedPlanContentRevisionId",
                formatVersion = formatVersion,
                family = assigned,
                legacyRevision = legacyPlanContentRevision,
            ),
            actualPlanContentRevisionId = decodePlanContentRevision(
                json = json,
                name = "actualPlanContentRevisionId",
                formatVersion = formatVersion,
                family = actual,
                legacyRevision = legacyPlanContentRevision,
            ),
            recommendationPolicyVersion = recommendationPolicyVersion,
            assignedProtocolId = assignedProtocol?.protocolId?.value,
            assignedProtocolVersion = assignedProtocol?.recordedVersion?.value,
            actualProtocolId = actualProtocol?.protocolId?.value,
            actualProtocolVersion = actualProtocol?.recordedVersion?.value,
            eligibleMomentPlanCount = eligibleMomentPlanCount,
        )
    }

    private fun decodeRehearsal(
        json: JSONObject,
        restoredAtMillis: Long,
        formatVersion: Int,
    ): MomentPlanRehearsalEntity {
        val startedAt = requiredLong(json, "startedAtMillis")
        val completedAt = nullableLong(json, "completedAtMillis")
        val storedDismissedAt = nullableLong(json, "dismissedAtMillis")
        require(startedAt >= 0L) { "Invalid rehearsal start" }
        require(completedAt == null || completedAt >= startedAt) {
            "Rehearsal completion precedes start"
        }
        require(storedDismissedAt == null || storedDismissedAt >= startedAt) {
            "Rehearsal dismissal precedes start"
        }
        require(completedAt == null || storedDismissedAt == null) {
            "Rehearsal cannot be both completed and dismissed"
        }
        val dismissedAt = if (completedAt == null && storedDismissedAt == null) {
            maxOf(startedAt, restoredAtMillis)
        } else {
            storedDismissedAt
        }
        val planId = requiredUuid(json, "planId")
        val planUpdatedAtMillis = requiredLong(json, "planUpdatedAtMillisAtStart")
        return MomentPlanRehearsalEntity(
            rehearsalId = requiredUuid(json, "rehearsalId"),
            planId = planId,
            planUpdatedAtMillisAtStart = planUpdatedAtMillis,
            mode = requiredEnum<MomentPlanRehearsalMode>(json, "mode").name,
            startedAtMillis = startedAt,
            completedAtMillis = completedAt,
            dismissedAtMillis = dismissedAt,
            planContentRevisionId = if (formatVersion == LegacyFormatVersion) {
                nullableUuid(json, "planContentRevisionId")
                    ?: LegacyMomentPlanContentRevisionFactory.create(
                        planId,
                        planUpdatedAtMillis,
                    )
            } else {
                requiredUuid(json, "planContentRevisionId")
            },
        )
    }

    private fun decodePlanContentRevision(
        json: JSONObject,
        name: String,
        formatVersion: Int,
        family: InterventionFamily?,
        legacyRevision: String?,
    ): String? {
        val revision = if (formatVersion == LegacyFormatVersion) {
            nullableUuid(json, name)
                ?: legacyRevision.takeIf { family == InterventionFamily.MomentPlan }
        } else {
            nullableUuid(json, name)
        }
        require((family == InterventionFamily.MomentPlan) == (revision != null)) {
            "$name must exist only for a Moment Plan assignment"
        }
        return revision
    }

    private fun MomentPlanEntity.toDomain(): MomentPlan = MomentPlan(
        planId = planId,
        title = title,
        momentCue = momentCue?.let(MomentCue::valueOf),
        actionText = actionText,
        futureCueText = futureCueText,
        actionType = MomentPlanActionType.valueOf(actionType),
        actionTarget = actionTarget,
        enabled = enabled,
        preferredForCue = preferredForCue,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
        rehearsedAtMillis = rehearsedAtMillis,
        contentRevisionId = contentRevisionId,
    )

    private fun AdaptiveDecisionEntity.toDomain(): AdaptiveDecision {
        val eligible = InterventionFamily.entries.filterTo(linkedSetOf()) {
            eligibleInterventionsMask and it.eligibilityBit != 0
        }
        val repeat = when {
            observationFinalisedAtMillis == null -> RepeatObservation.NotFinalised
            repeatDetectedWithin20Minutes == true -> RepeatObservation.RepeatDetected
            else -> RepeatObservation.NoRepeatDetected
        }
        return AdaptiveDecision(
            decisionId = decisionId,
            protectionIncidentToken = protectionIncidentToken,
            sourceKind = AdaptiveSourceKind.valueOf(sourceKind),
            createdAtMillis = createdAtMillis,
            momentWindowStartedAtMillis = momentWindowStartedAtMillis,
            momentCue = momentCue?.let(MomentCue::valueOf),
            baselineUrgeRating = baselineUrgeRating,
            assignment = AdaptiveAssignment(
                momentIntensity = MomentIntensity.valueOf(momentIntensity),
                assignmentMode = AssignmentMode.valueOf(assignmentMode),
                eligibleInterventions = eligible,
                assignedSuggestion = assignedSuggestion?.let(InterventionFamily::valueOf),
                selectionProbability = selectionProbability,
                reasonCode = AdaptiveReasonCode.valueOf(reasonCode),
                momentPlanId = momentPlanId,
                momentPlanUpdatedAtMillis = momentPlanUpdatedAtMillis,
                assignedPlanContentRevisionId = assignedPlanContentRevisionId,
                actualPlanContentRevisionId = actualPlanContentRevisionId,
                actualIntervention = actualIntervention?.let(InterventionFamily::valueOf),
                userOverrodeSuggestion = userOverrodeSuggestion,
            ),
            presentedAtMillis = presentedAtMillis,
            startedAtMillis = startedAtMillis,
            completedAtMillis = completedAtMillis,
            dismissedAtMillis = dismissedAtMillis,
            feedbackCode = FeedbackCode.valueOf(feedbackCode),
            feedbackUpdatedAtMillis = feedbackUpdatedAtMillis,
            repeatObservation = repeat,
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

    private inline fun <T> List<T>.toJsonArray(
        encode: (T) -> JSONObject,
    ): JSONArray = JSONArray().also { array -> forEach { array.put(encode(it)) } }

    private inline fun <T> JSONArray.mapObjects(
        decode: (JSONObject) -> T,
    ): List<T> = List(length()) { index -> decode(getJSONObject(index)) }

    private fun requiredObject(json: JSONObject, name: String): JSONObject =
        json.get(name) as? JSONObject
            ?: throw IllegalArgumentException("$name must be an object")

    private fun requiredArray(json: JSONObject, name: String): JSONArray =
        json.get(name) as? JSONArray
            ?: throw IllegalArgumentException("$name must be an array")

    private fun requiredString(json: JSONObject, name: String): String {
        val value = json.get(name) as? String
            ?: throw IllegalArgumentException("$name must be a string")
        require(value.length <= MaximumText) { "$name is too long" }
        return value
    }

    private fun nullableString(json: JSONObject, name: String): String? =
        if (!json.has(name) || json.isNull(name)) null else requiredString(json, name)

    private fun requiredBoolean(json: JSONObject, name: String): Boolean =
        json.get(name) as? Boolean
            ?: throw IllegalArgumentException("$name must be a boolean")

    private fun nullableBoolean(json: JSONObject, name: String): Boolean? =
        if (!json.has(name) || json.isNull(name)) null else requiredBoolean(json, name)

    private fun requiredLong(json: JSONObject, name: String): Long = when (val value = json.get(name)) {
        is Int -> value.toLong()
        is Long -> value
        else -> throw IllegalArgumentException("$name must be an integer")
    }

    private fun nullableLong(json: JSONObject, name: String): Long? =
        if (!json.has(name) || json.isNull(name)) null else requiredLong(json, name)

    private fun requiredInt(json: JSONObject, name: String): Int {
        val value = requiredLong(json, name)
        require(value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
        return value.toInt()
    }

    private fun nullableInt(json: JSONObject, name: String): Int? =
        if (!json.has(name) || json.isNull(name)) null else requiredInt(json, name)

    private fun nullableMinute(json: JSONObject, name: String): Int? =
        nullableInt(json, name)?.also {
            require(it in 0..1_439) { "$name must be a local minute" }
        }

    private fun nullableDouble(json: JSONObject, name: String): Double? {
        if (!json.has(name) || json.isNull(name)) return null
        val value = json.get(name)
        return when (value) {
            is Double -> value
            is Float -> value.toDouble()
            is Int -> value.toDouble()
            is Long -> value.toDouble()
            else -> throw IllegalArgumentException("$name must be numeric")
        }
    }

    private fun validatedHistoricalProtocol(
        id: String?,
        version: Int?,
    ): com.impulsive.app.backend.domain.engine.adaptive.HistoricalInterventionProtocol? {
        require((id == null) == (version == null)) {
            "Protocol ID and version must be restored together"
        }
        if (id == null || version == null) return null
        val protocolId = runCatching { InterventionProtocolId(id) }.getOrElse {
            throw IllegalArgumentException("Malformed protocol ID")
        }
        val protocolVersion = runCatching { InterventionProtocolVersion(version) }.getOrElse {
            throw IllegalArgumentException("Malformed protocol version")
        }
        return InterventionProtocolRegistry.historical(protocolId, protocolVersion)
            ?: throw IllegalArgumentException("Unsupported historical protocol ID")
    }

    private fun legacyDefaultProtocol(
        formatVersion: Int,
        family: InterventionFamily?,
        planId: String?,
        plansById: Map<String, MomentPlanEntity>,
    ): com.impulsive.app.backend.domain.engine.adaptive.HistoricalInterventionProtocol? {
        if (formatVersion != LegacyFormatVersion || family == null) return null
        val contract = when (family) {
            InterventionFamily.MomentPlan -> planId
                ?.let(plansById::get)
                ?.toDomain()
                ?.let(InterventionProtocolRegistry::resolveForPlan)
            else -> InterventionProtocolRegistry.resolveForFamily(family)
        } ?: return null
        return InterventionProtocolRegistry.historical(
            contract.protocolId,
            contract.version,
        )
    }

    private fun requiredUuid(json: JSONObject, name: String): String {
        val value = requiredString(json, name)
        val parsed = runCatching { UUID.fromString(value) }.getOrNull()
            ?: throw IllegalArgumentException("$name must be a UUID")
        require(parsed.toString() == value.lowercase()) { "$name must be a canonical UUID" }
        return parsed.toString()
    }

    private fun nullableUuid(json: JSONObject, name: String): String? =
        if (!json.has(name) || json.isNull(name)) null else requiredUuid(json, name)

    private inline fun <reified T : Enum<T>> requiredEnum(
        json: JSONObject,
        name: String,
    ): T = enumValueOf(requiredString(json, name))

    private inline fun <reified T : Enum<T>> nullableEnum(
        json: JSONObject,
        name: String,
    ): T? = nullableString(json, name)?.let(::enumValueOf)

    private fun JSONObject.putNullable(name: String, value: Any?): JSONObject =
        put(name, value ?: JSONObject.NULL)

    private fun AdaptiveDecisionEntity.legacyPlanRevisionFor(
        intervention: InterventionFamily,
        assigned: Boolean,
    ): String? {
        val stored = if (assigned) assignedSuggestion else actualIntervention
        if (stored != intervention.name) return null
        val planId = momentPlanId ?: return null
        val timestamp = momentPlanUpdatedAtMillis ?: return null
        return LegacyMomentPlanContentRevisionFactory.create(planId, timestamp)
    }

    private fun AdaptiveDecisionEntity.protocolForExport(
        familyName: String?,
        plansById: Map<String, MomentPlanEntity>,
        storedId: String?,
        storedVersion: Int?,
    ): Pair<String, Int>? {
        if (storedId != null && storedVersion != null) {
            return storedId to storedVersion
        }
        val family = familyName?.let { name ->
            runCatching { InterventionFamily.valueOf(name) }.getOrNull()
        } ?: return null
        val contract = when (family) {
            InterventionFamily.MomentPlan -> momentPlanId
                ?.let(plansById::get)
                ?.toDomain()
                ?.let(InterventionProtocolRegistry::resolveForPlan)
            else -> InterventionProtocolRegistry.resolveForFamily(family)
        } ?: return null
        return contract.protocolId.value to contract.version.value
    }
}
