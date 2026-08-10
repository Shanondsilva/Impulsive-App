package com.impulsive.app.backend.session.adaptive

import com.impulsive.app.backend.domain.engine.adaptive.FamiliarStepMatchInput
import com.impulsive.app.backend.domain.engine.adaptive.FamiliarStepMatcher
import com.impulsive.app.backend.domain.engine.adaptive.FamiliarStepQualificationPolicy
import com.impulsive.app.backend.domain.engine.adaptive.InterventionProtocolRegistry
import com.impulsive.app.backend.domain.model.adaptive.FamiliarStepCandidate
import com.impulsive.app.backend.domain.model.adaptive.FamiliarStepEvidenceSufficiency
import com.impulsive.app.backend.domain.model.adaptive.FamiliarStepExplanationCategory
import com.impulsive.app.backend.domain.model.adaptive.FamiliarStepMatchResult
import com.impulsive.app.backend.domain.model.adaptive.FamiliarStepNoMatchReason
import com.impulsive.app.backend.domain.model.adaptive.FamiliarStepRouteIdentity
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentIntensity
import com.impulsive.app.backend.domain.model.adaptive.MomentPlan
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveDecisionRepository
import com.impulsive.app.backend.domain.repository.adaptive.AdaptivePreferenceRepository
import com.impulsive.app.backend.domain.repository.adaptive.MomentPlanRepository
import kotlinx.coroutines.flow.first

enum class FamiliarStepCommand {
    Start,
    AnotherSupport,
    LeaveThisMoment,
}

sealed interface FamiliarStepSessionState {
    data class FamiliarStepAvailable(
        val routeRecommendation: AdaptiveRouteRequest?,
        val routeIdentity: FamiliarStepRouteIdentity,
        val comparableCount: Int,
        val favourableCount: Int,
        val explanationCategory: FamiliarStepExplanationCategory,
        val evidenceSufficiency: FamiliarStepEvidenceSufficiency,
        val explanation: FamiliarStepExplanation,
        val startCommand: FamiliarStepCommand = FamiliarStepCommand.Start,
        val anotherSupportCommand: FamiliarStepCommand = FamiliarStepCommand.AnotherSupport,
        val leaveThisMomentCommand: FamiliarStepCommand = FamiliarStepCommand.LeaveThisMoment,
    ) : FamiliarStepSessionState

    data class Unavailable(val reason: FamiliarStepNoMatchReason) : FamiliarStepSessionState
}

sealed interface FamiliarStepStartResult {
    data class Ready(
        val routeRequest: AdaptiveRouteRequest?,
        val supportCycle: AdaptiveSupportCycleCommandResult,
    ) : FamiliarStepStartResult

    data class ResumeExistingCycle(
        val routeRequest: AdaptiveRouteRequest,
    ) : FamiliarStepStartResult

    data class Unavailable(val reason: FamiliarStepNoMatchReason) : FamiliarStepStartResult
    data class LifecycleRejected(val result: AdaptiveLifecycleResult) : FamiliarStepStartResult
    data object SupportCycleUnavailable : FamiliarStepStartResult
}

/**
 * The only runtime boundary that invokes [FamiliarStepMatcher]. All evidence is
 * derived from the existing finalised decision ledger and discarded after use.
 */
class FamiliarStepCoordinator(
    private val decisions: AdaptiveDecisionRepository,
    private val preferences: AdaptivePreferenceRepository,
    private val plans: MomentPlanRepository,
    private val lifecycle: AdaptiveDecisionLifecycle,
    private val supportCycles: AdaptiveSupportCycleCoordinator,
    private val clock: AdaptiveClock = SystemAdaptiveClock,
) {
    suspend fun state(decisionId: String): FamiliarStepSessionState {
        val decision = decisions.getById(decisionId)
            ?: return unavailable(FamiliarStepNoMatchReason.NoEligibleRoute)
        // This check deliberately precedes preference, plan and evidence reads.
        if (decision.assignment.momentIntensity == MomentIntensity.FirstAttempt) {
            return unavailable(FamiliarStepNoMatchReason.FirstAttempt)
        }

        val currentPreferences = preferences.get()
        if (!currentPreferences.personalSuggestionsEnabled) {
            return unavailable(FamiliarStepNoMatchReason.PersonalSuggestionsDisabled)
        }
        val enabledPlans = plans.observeEnabled().first()
        val match = FamiliarStepMatcher.match(
            FamiliarStepMatchInput(
                momentIntensity = decision.assignment.momentIntensity,
                personalSuggestionsEnabled = true,
                eligibleInterventions = decision.assignment.eligibleInterventions,
                currentMomentCue = decision.momentCue,
                evidence = decisions.getRecentFamiliarStepEvidence(
                    FamiliarStepQualificationPolicy.MaximumInspectedRecords,
                ),
                currentProtocolIdentities = InterventionProtocolRegistry.contracts
                    .mapTo(mutableSetOf()) { it.protocolId.value to it.version.value },
                currentMomentPlanRevisions = enabledPlans.associate {
                    it.planId to it.contentRevisionId
                },
            ),
        )
        return when (match) {
            is FamiliarStepMatchResult.NoMatch -> unavailable(match.reason)
            is FamiliarStepMatchResult.Match -> available(
                decisionId = decisionId,
                candidate = match.candidate,
                enabledPlans = enabledPlans,
            )
        }
    }

    /** Revalidates all evidence and route identities immediately before start. */
    suspend fun start(
        decisionId: String,
        expectedIdentity: FamiliarStepRouteIdentity,
    ): FamiliarStepStartResult {
        val latest = state(decisionId)
        if (latest !is FamiliarStepSessionState.FamiliarStepAvailable) {
            return FamiliarStepStartResult.Unavailable(
                (latest as FamiliarStepSessionState.Unavailable).reason,
            )
        }
        if (latest.routeIdentity != expectedIdentity) {
            return FamiliarStepStartResult.Unavailable(staleReason(expectedIdentity))
        }
        val decision = decisions.getById(decisionId)
            ?: return FamiliarStepStartResult.Unavailable(
                FamiliarStepNoMatchReason.NoEligibleRoute,
            )
        if (decision.presentedAtMillis == null) {
            when (val presented = lifecycle.markPresented(decisionId, clock.nowMillis())) {
                AdaptiveLifecycleResult.Applied,
                AdaptiveLifecycleResult.Idempotent,
                AdaptiveLifecycleResult.SchedulingFailure -> Unit
                else -> return FamiliarStepStartResult.LifecycleRejected(presented)
            }
        }
        val planId = expectedIdentity.momentPlanId
        val choice = when {
            decision.assignment.actualIntervention == null -> lifecycle.recordActualChoice(
                decisionId,
                expectedIdentity.intervention,
                planId,
            )
            decision.startedAtMillis == null -> lifecycle.replacePendingActualChoice(
                decisionId,
                expectedIdentity.intervention,
                planId,
            )
            decision.assignment.actualIntervention == expectedIdentity.intervention &&
                (expectedIdentity.intervention != InterventionFamily.MomentPlan ||
                    decision.assignment.momentPlanId == planId) -> AdaptiveLifecycleResult.Idempotent
            else -> AdaptiveLifecycleResult.ConflictingChoice
        }
        if (choice != AdaptiveLifecycleResult.Applied && choice != AdaptiveLifecycleResult.Idempotent) {
            return FamiliarStepStartResult.LifecycleRejected(choice)
        }
        val supportCycle = supportCycles.createOrRecover(decision)
        if (supportCycle is AdaptiveSupportCycleCommandResult.ActiveDecisionConflict) {
            return FamiliarStepStartResult.ResumeExistingCycle(
                routeRequest = AdaptiveSupportCycleResumePolicy
                    .target(supportCycle.state)
                    .toRouteRequest(),
            )
        }
        if (supportCycle !is AdaptiveSupportCycleCommandResult.Active &&
            supportCycle !is AdaptiveSupportCycleCommandResult.ExistingActive
        ) {
            return FamiliarStepStartResult.SupportCycleUnavailable
        }
        val cycleDecisionId = when (supportCycle) {
            is AdaptiveSupportCycleCommandResult.Active -> supportCycle.state.cycle.decisionId
            is AdaptiveSupportCycleCommandResult.ExistingActive -> supportCycle.state.cycle.decisionId
            is AdaptiveSupportCycleCommandResult.Terminal,
            is AdaptiveSupportCycleCommandResult.Rejected,
            is AdaptiveSupportCycleCommandResult.ActiveDecisionConflict,
            AdaptiveSupportCycleCommandResult.NotFound,
            AdaptiveSupportCycleCommandResult.CycleMismatch,
            AdaptiveSupportCycleCommandResult.RevisionConflict,
            AdaptiveSupportCycleCommandResult.InvalidPersistedState,
            AdaptiveSupportCycleCommandResult.Expired,
            AdaptiveSupportCycleCommandResult.PersistenceFailure ->
                return FamiliarStepStartResult.SupportCycleUnavailable
        }
        if (cycleDecisionId != decisionId) return FamiliarStepStartResult.SupportCycleUnavailable

        if (expectedIdentity.intervention == InterventionFamily.ShortPause) {
            val started = lifecycle.markStarted(decisionId, clock.nowMillis())
            if (started != AdaptiveLifecycleResult.Applied &&
                started != AdaptiveLifecycleResult.Idempotent
            ) {
                return FamiliarStepStartResult.LifecycleRejected(started)
            }
        }
        return FamiliarStepStartResult.Ready(latest.routeRecommendation, supportCycle)
    }

    private fun available(
        decisionId: String,
        candidate: FamiliarStepCandidate,
        enabledPlans: List<MomentPlan>,
    ): FamiliarStepSessionState {
        val identity = candidate.routeIdentity
        val route = if (identity.intervention == InterventionFamily.MomentPlan) {
            enabledPlans.firstOrNull {
                it.planId == identity.momentPlanId &&
                    it.contentRevisionId == identity.momentPlanContentRevisionId
            } ?: return unavailable(FamiliarStepNoMatchReason.StalePlanRevision)
            AdaptiveMomentRoutingPolicy.forChoice(decisionId, InterventionFamily.MomentPlan)
        } else {
            AdaptiveMomentRoutingPolicy.forChoice(decisionId, identity.intervention)
        }
        if (identity.intervention != InterventionFamily.ShortPause && route == null) {
            return unavailable(FamiliarStepNoMatchReason.NoEligibleRoute)
        }
        val explanation = FamiliarStepExplanationService.explain(candidate)
        return FamiliarStepSessionState.FamiliarStepAvailable(
            routeRecommendation = route,
            routeIdentity = identity,
            comparableCount = candidate.comparableCount,
            favourableCount = candidate.favourableCount,
            explanationCategory = if (candidate.matchedCue == null) {
                FamiliarStepExplanationCategory.BroadObservedPattern
            } else {
                FamiliarStepExplanationCategory.CueMatchedObservedPattern
            },
            evidenceSufficiency = FamiliarStepEvidenceSufficiency.Qualified,
            explanation = explanation,
        )
    }

    private fun staleReason(identity: FamiliarStepRouteIdentity) =
        if (identity.momentPlanId == null) FamiliarStepNoMatchReason.StaleProtocol
        else FamiliarStepNoMatchReason.StalePlanRevision

    private fun unavailable(reason: FamiliarStepNoMatchReason) =
        FamiliarStepSessionState.Unavailable(reason)
}
