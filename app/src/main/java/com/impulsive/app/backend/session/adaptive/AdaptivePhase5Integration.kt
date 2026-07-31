package com.impulsive.app.backend.session.adaptive

import androidx.annotation.StringRes
import com.impulsive.app.R
import com.impulsive.app.backend.domain.engine.adaptive.InterventionProtocolRegistry
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveDecision
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveReasonCode
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSourceKind
import com.impulsive.app.backend.domain.model.adaptive.ImpulsiveDestination
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentPlan
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanActionType
import com.impulsive.app.backend.domain.model.adaptive.MomentIntensity
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveDecisionRepository
import com.impulsive.app.backend.domain.repository.adaptive.MomentPlanRepository
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.firstOrNull

enum class AdaptiveProtectionSource {
    MonitoredApplication,
    VpnWebsite,
}

data class AdaptiveIncidentSignal(
    val source: AdaptiveProtectionSource,
    val incidentStartedAtMillis: Long,
    /**
     * Used only as one-way digest input. It is never returned, logged, routed,
     * or persisted in the adaptive tables.
     */
    val ephemeralSourceIdentity: String,
)

object AdaptiveIncidentTokenFactory {
    fun create(signal: AdaptiveIncidentSignal): String {
        require(signal.incidentStartedAtMillis >= 0L)
        require(signal.ephemeralSourceIdentity.isNotBlank())
        val digestInput = buildString {
            append("adaptive-incident-v1")
            append('\u0000')
            append(signal.source.name)
            append('\u0000')
            append(signal.incidentStartedAtMillis)
            append('\u0000')
            append(signal.ephemeralSourceIdentity)
        }.toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(digestInput)
        return "ai1_" + digest.joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }
}

data class AdaptiveIncidentHandoff(
    val decisionId: String?,
    val duplicate: Boolean,
    val fallbackRequired: Boolean,
)

class AdaptiveProtectionBridge(
    private val coordinator: AdaptiveMomentCoordinator,
    private val decisions: AdaptiveDecisionRepository? = null,
    private val momentPlans: MomentPlanRepository? = null,
) {
    suspend fun recognise(signal: AdaptiveIncidentSignal): AdaptiveIncidentHandoff {
        val result = try {
            coordinator.coordinate(
                AdaptiveProtectionIncidentRequest(
                    incidentToken = AdaptiveIncidentTokenFactory.create(signal),
                    sourceKind = when (signal.source) {
                        AdaptiveProtectionSource.MonitoredApplication -> AdaptiveSourceKind.App
                        AdaptiveProtectionSource.VpnWebsite -> AdaptiveSourceKind.Website
                    },
                    detectedAtMillis = signal.incidentStartedAtMillis,
                    currentlyAllowedInterventions = setOf(
                        InterventionFamily.ShortPause,
                        InterventionFamily.PivotGame,
                        InterventionFamily.PivotReading,
                        InterventionFamily.MomentPlan,
                    ),
                    gameProductEligible = true,
                    readingProductEligible = true,
                    momentPlansProductEligible = true,
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return AdaptiveIncidentHandoff(null, duplicate = false, fallbackRequired = true)
        }
        val decisionId = result.presentation.decisionId.takeIf { result.persisted }
        val decisionRepository = decisions
        if (
            decisionId != null &&
            result.presentation.momentIntensity == MomentIntensity.FirstAttempt &&
            decisionRepository != null
        ) {
            val alternatives = buildSet {
                add(InterventionFamily.PivotGame)
                add(InterventionFamily.PivotReading)
                if (momentPlans?.observeEnabled()?.firstOrNull()?.isNotEmpty() == true) {
                    add(InterventionFamily.MomentPlan)
                }
            }
            runCatching {
                decisionRepository.addEligibleInterventions(decisionId, alternatives)
            }
        }
        return AdaptiveIncidentHandoff(
            decisionId = decisionId,
            duplicate = result.duplicateIncident,
            fallbackRequired = !result.persisted || result.presentation.decisionId == null,
        )
    }
}

object AdaptiveWhyThisCopy {
    @StringRes
    fun resource(reason: AdaptiveReasonCode): Int = when (reason) {
        AdaptiveReasonCode.MinimumEffectiveFriction ->
            R.string.adaptive_why_minimum_friction
        AdaptiveReasonCode.OnlyEligibleIntervention ->
            R.string.adaptive_why_only_eligible
        AdaptiveReasonCode.CueMatchedMomentPlan ->
            R.string.adaptive_why_cue_plan
        AdaptiveReasonCode.RecentlyRehearsedPlan,
        AdaptiveReasonCode.CueMatchedRecentlyRehearsedPlan ->
            R.string.adaptive_why_recently_rehearsed
        AdaptiveReasonCode.RecentHelpfulFeedback,
        AdaptiveReasonCode.RecentCompletionPattern,
        AdaptiveReasonCode.TimingReceptivity ->
            R.string.adaptive_why_recent_pattern
        AdaptiveReasonCode.InsufficientEvidenceExploration,
        AdaptiveReasonCode.RandomisedExploration ->
            R.string.adaptive_why_randomised
        AdaptiveReasonCode.InterventionFatigueRotation ->
            R.string.adaptive_why_fatigue
        AdaptiveReasonCode.StableFallback ->
            R.string.adaptive_why_fallback
        AdaptiveReasonCode.UserOverride ->
            R.string.adaptive_why_override
    }

    fun forReason(reason: AdaptiveReasonCode): String = when (reason) {
        AdaptiveReasonCode.MinimumEffectiveFriction ->
            "A short pause keeps the first step simple."
        AdaptiveReasonCode.OnlyEligibleIntervention ->
            "This is the support option currently available."
        AdaptiveReasonCode.CueMatchedMomentPlan ->
            "This plan matches the moment you selected."
        AdaptiveReasonCode.RecentlyRehearsedPlan,
        AdaptiveReasonCode.CueMatchedRecentlyRehearsedPlan ->
            "You practised this plan recently, so it may be easier to remember right now."
        AdaptiveReasonCode.RecentHelpfulFeedback,
        AdaptiveReasonCode.RecentCompletionPattern,
        AdaptiveReasonCode.TimingReceptivity ->
            "Your recent on-device history gave this option a slightly stronger score."
        AdaptiveReasonCode.InsufficientEvidenceExploration,
        AdaptiveReasonCode.RandomisedExploration ->
            "Impulsive occasionally varies suggestions so it can avoid repeating the same option every time."
        AdaptiveReasonCode.InterventionFatigueRotation ->
            "A different option is being suggested because another one was used recently."
        AdaptiveReasonCode.StableFallback ->
            "A simple support option is available while personal suggestions are unavailable."
        AdaptiveReasonCode.UserOverride ->
            "You chose a different available support option."
    }
}

enum class AdaptiveRouteKind {
    Game,
    Reading,
    MomentPlan,
    Focus,
    Journal,
    ExternalApplication,
    Feedback,
}

data class AdaptiveRouteRequest(
    val decisionId: String,
    val kind: AdaptiveRouteKind,
    val opaqueTarget: String? = null,
)

object AdaptiveMomentRoutingPolicy {
    fun forChoice(
        decisionId: String,
        intervention: InterventionFamily,
    ): AdaptiveRouteRequest? {
        if (
            intervention != InterventionFamily.MomentPlan &&
            InterventionProtocolRegistry.resolveForFamily(intervention) == null
        ) {
            return null
        }
        return when (intervention) {
            InterventionFamily.ShortPause -> null
            InterventionFamily.PivotGame ->
                AdaptiveRouteRequest(decisionId, AdaptiveRouteKind.Game)
            InterventionFamily.PivotReading ->
                AdaptiveRouteRequest(decisionId, AdaptiveRouteKind.Reading)
            InterventionFamily.MomentPlan ->
                AdaptiveRouteRequest(decisionId, AdaptiveRouteKind.MomentPlan)
        }
    }

    fun forPlanAction(decisionId: String, plan: MomentPlan): AdaptiveRouteRequest? {
        InterventionProtocolRegistry.resolveForPlan(plan) ?: return null
        return when (plan.actionType) {
            MomentPlanActionType.TextOnly -> null
            MomentPlanActionType.OpenImpulsiveDestination -> when (
                ImpulsiveDestination.entries.firstOrNull {
                    it.storageValue == plan.actionTarget
                }
            ) {
                ImpulsiveDestination.Focus ->
                    AdaptiveRouteRequest(decisionId, AdaptiveRouteKind.Focus)
                ImpulsiveDestination.Journal ->
                    AdaptiveRouteRequest(decisionId, AdaptiveRouteKind.Journal)
                ImpulsiveDestination.PivotGames ->
                    AdaptiveRouteRequest(decisionId, AdaptiveRouteKind.Game)
                ImpulsiveDestination.ResetReading ->
                    AdaptiveRouteRequest(decisionId, AdaptiveRouteKind.Reading)
                null -> null
            }
            MomentPlanActionType.LaunchSelectedApp ->
                plan.actionTarget?.takeIf(::isValidAndroidPackage)?.let {
                    AdaptiveRouteRequest(
                        decisionId = decisionId,
                        kind = AdaptiveRouteKind.ExternalApplication,
                        opaqueTarget = it,
                    )
                }
        }
    }

    private fun isValidAndroidPackage(value: String): Boolean =
        value.matches(
            Regex("""^[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+$"""),
        )
}

data class AdaptiveMomentLoadedData(
    val decision: AdaptiveDecision,
    val availablePlans: List<MomentPlan>,
) {
    val selectedPlan: MomentPlan?
        get() = decision.assignment.momentPlanId?.let { id ->
            availablePlans.firstOrNull { it.planId == id && it.enabled }
        } ?: availablePlans.firstOrNull { it.enabled }
}
