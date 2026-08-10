package com.impulsive.app.backend.session.adaptive

import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycle
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentCue
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveDecisionRepository
import com.impulsive.app.backend.domain.repository.adaptive.PersistedAdaptiveSupportCycle
import kotlinx.coroutines.CancellationException

/**
 * Derives the deterministic attempt identity for one support-cycle alternative
 * operation.
 *
 * The identity is a pure function of two durable facts -- the cycle and the
 * decision that owned it when the user made the request -- so every retry of
 * the same user action derives the same value without any stored token.
 *
 * The raw identity is never persisted. It is only input to the existing
 * SHA-256 [AdaptiveFollowUpIncidentTokenFactory], which is what keeps cycle
 * identifiers out of the stored incident token.
 */
internal object AdaptiveSupportAlternativeAttemptIdentityFactory {
    fun create(
        cycleId: String,
        previousDecisionId: String,
    ): String {
        require(cycleId.isNotBlank()) {
            "Support-cycle ID must not be blank."
        }
        require(previousDecisionId.isNotBlank()) {
            "Previous decision ID must not be blank."
        }

        return buildString {
            append("support-cycle-alternative-v1")
            append('\u0000')
            append(cycleId)
            append('\u0000')
            append(previousDecisionId)
        }
    }
}

internal data class AdaptiveSupportAlternativeRequest(
    val cycleId: String,
    val previousDecisionId: String,
    val intervention: InterventionFamily,
    val momentPlanId: String? = null,
    val selectedCue: MomentCue? = null,
    val urgeRating: Int? = null,
) {
    init {
        require(cycleId.isNotBlank()) {
            "Support-cycle ID must not be blank."
        }
        require(previousDecisionId.isNotBlank()) {
            "Previous decision ID must not be blank."
        }
    }
}

internal sealed interface AdaptiveSupportAlternativeResult {
    data class Ready(
        val decisionId: String,
        val routeRequest: AdaptiveRouteRequest?,
        val cycle: PersistedAdaptiveSupportCycle,
    ) : AdaptiveSupportAlternativeResult

    /** The second explicit rejection ended the cycle; no further support is offered. */
    data class CycleEnded(
        val cycle: AdaptiveSupportCycle,
    ) : AdaptiveSupportAlternativeResult

    data object PreviousDecisionNotStarted : AdaptiveSupportAlternativeResult
    data object IneligibleChoice : AdaptiveSupportAlternativeResult
    data object InvalidMomentPlan : AdaptiveSupportAlternativeResult

    data class OwnerMismatch(
        val actualDecisionId: String,
    ) : AdaptiveSupportAlternativeResult

    data object NoActiveCycle : AdaptiveSupportAlternativeResult
    data object RevisionConflict : AdaptiveSupportAlternativeResult
    data object InvalidPersistedState : AdaptiveSupportAlternativeResult
    data object Expired : AdaptiveSupportAlternativeResult
    data object PersistenceFailure : AdaptiveSupportAlternativeResult
}

/**
 * Composes the complete "choose another support" operation across the two
 * persistence systems: the DataStore support cycle and the Room decision
 * history.
 *
 * The operation has no transaction spanning both stores, so it is instead made
 * safe to repeat. Every retry derives the same deterministic follow-up identity,
 * checks for an already-created follow-up before touching the cycle, and treats
 * an already-terminal step as continuation. The result is that duplicate taps,
 * concurrent callers, partial completion and process death all converge on one
 * follow-up decision and one durable rejection count.
 */
internal class AdaptiveSupportAlternativeCoordinator(
    private val supportCycles: AdaptiveSupportCycleCoordinator,
    private val followUpSupport: AdaptiveFollowUpSupport,
    private val decisions: AdaptiveDecisionRepository,
) {
    suspend fun chooseAlternative(
        request: AdaptiveSupportAlternativeRequest,
    ): AdaptiveSupportAlternativeResult {
        try {
            val previous = decisions.getById(request.previousDecisionId)
                ?: return AdaptiveSupportAlternativeResult.PersistenceFailure
            if (previous.startedAtMillis == null) {
                return AdaptiveSupportAlternativeResult.PreviousDecisionNotStarted
            }

            val attemptIdentity = AdaptiveSupportAlternativeAttemptIdentityFactory.create(
                cycleId = request.cycleId,
                previousDecisionId = request.previousDecisionId,
            )
            val incidentToken = AdaptiveFollowUpIncidentTokenFactory.create(
                previousDecisionId = request.previousDecisionId,
                attemptIdentity = attemptIdentity,
            )

            /*
             * The main duplicate-protection boundary. When a follow-up already
             * exists, an earlier invocation may have counted the rejection,
             * created the follow-up, handed the cycle over and started the next
             * support. Preparing the cycle again would be a stale operation, so
             * this retry goes straight to deterministic finalisation.
             */
            val existingFollowUp = decisions.getByIncidentToken(incidentToken)

            if (existingFollowUp == null) {
                when (
                    val prepared = supportCycles.prepareAlternativeChoice(
                        cycleId = request.cycleId,
                        expectedDecisionId = request.previousDecisionId,
                    )
                ) {
                    is AdaptiveSupportCycleAlternativePreparationResult.Continue -> Unit

                    /*
                     * Second rejection. No follow-up decision is created and no
                     * handoff is attempted.
                     */
                    is AdaptiveSupportCycleAlternativePreparationResult.CycleEnded ->
                        return AdaptiveSupportAlternativeResult.CycleEnded(prepared.cycle)

                    /*
                     * A concurrent caller may have created and handed off the
                     * follow-up between our token lookup and this owner check.
                     * Exactly one recheck resolves that race; there is no retry
                     * loop and prepareAlternativeChoice is never called again.
                     */
                    is AdaptiveSupportCycleAlternativePreparationResult.OwnerMismatch ->
                        if (decisions.getByIncidentToken(incidentToken) == null) {
                            return AdaptiveSupportAlternativeResult.OwnerMismatch(
                                actualDecisionId = prepared.actualDecisionId,
                            )
                        }

                    is AdaptiveSupportCycleAlternativePreparationResult.Rejected ->
                        return AdaptiveSupportAlternativeResult.PersistenceFailure

                    AdaptiveSupportCycleAlternativePreparationResult.NotFound,
                    AdaptiveSupportCycleAlternativePreparationResult.CycleMismatch,
                    ->
                        return AdaptiveSupportAlternativeResult.NoActiveCycle

                    AdaptiveSupportCycleAlternativePreparationResult.RevisionConflict ->
                        return AdaptiveSupportAlternativeResult.RevisionConflict
                    AdaptiveSupportCycleAlternativePreparationResult.InvalidPersistedState ->
                        return AdaptiveSupportAlternativeResult.InvalidPersistedState
                    AdaptiveSupportCycleAlternativePreparationResult.Expired ->
                        return AdaptiveSupportAlternativeResult.Expired
                    AdaptiveSupportCycleAlternativePreparationResult.PersistenceFailure ->
                        return AdaptiveSupportAlternativeResult.PersistenceFailure
                }
            }

            /*
             * Deterministic: an existing follow-up for this incident token is
             * recognised and reused rather than duplicated. A failure here never
             * rolls back an already-counted rejection -- the user did request an
             * alternative, and a later retry continues from the terminal step
             * without counting again.
             */
            val followUp = when (
                val created = followUpSupport.chooseAnotherWithAttemptIdentity(
                    request = AdaptiveFollowUpRequest(
                        previousDecisionId = request.previousDecisionId,
                        intervention = request.intervention,
                        momentPlanId = request.momentPlanId,
                        selectedCue = request.selectedCue,
                        urgeRating = request.urgeRating,
                    ),
                    attemptIdentity = attemptIdentity,
                )
            ) {
                is AdaptiveFollowUpResult.Ready -> created
                AdaptiveFollowUpResult.PreviousDecisionNotStarted ->
                    return AdaptiveSupportAlternativeResult.PreviousDecisionNotStarted
                AdaptiveFollowUpResult.IneligibleChoice ->
                    return AdaptiveSupportAlternativeResult.IneligibleChoice
                AdaptiveFollowUpResult.InvalidMomentPlan ->
                    return AdaptiveSupportAlternativeResult.InvalidMomentPlan
                AdaptiveFollowUpResult.PersistenceFailure ->
                    return AdaptiveSupportAlternativeResult.PersistenceFailure
            }

            /*
             * APP-003 lineage validation must see the actual persisted records,
             * never reconstructed ones.
             */
            val previousDecision = decisions.getById(request.previousDecisionId)
                ?: return AdaptiveSupportAlternativeResult.PersistenceFailure
            val followUpDecision = decisions.getById(followUp.decisionId)
                ?: return AdaptiveSupportAlternativeResult.PersistenceFailure

            return when (
                val handoff = supportCycles.handoffDecision(
                    cycleId = request.cycleId,
                    previousDecision = previousDecision,
                    followUpDecision = followUpDecision,
                )
            ) {
                /*
                 * Both mean the durable cycle is correctly owned by the follow-up.
                 * APP-003H established that AlreadyTransferred does not imply this
                 * caller performed the write, and callers must not infer that.
                 */
                is AdaptiveSupportCycleDecisionHandoffResult.Transferred ->
                    ready(followUpDecision.decisionId, followUp, handoff.state)
                is AdaptiveSupportCycleDecisionHandoffResult.AlreadyTransferred ->
                    ready(followUpDecision.decisionId, followUp, handoff.state)

                AdaptiveSupportCycleDecisionHandoffResult.OwnerMismatch ->
                    ownerMismatch(request.cycleId)

                /*
                 * Fail closed. After a Continue preparation this can only mean a
                 * concurrent mutation changed the step underneath us.
                 */
                AdaptiveSupportCycleDecisionHandoffResult.CurrentStepNotResolved,
                AdaptiveSupportCycleDecisionHandoffResult.InvalidDecisionLineage,
                ->
                    AdaptiveSupportAlternativeResult.PersistenceFailure

                AdaptiveSupportCycleDecisionHandoffResult.RevisionConflict ->
                    AdaptiveSupportAlternativeResult.RevisionConflict
                AdaptiveSupportCycleDecisionHandoffResult.NotFound,
                AdaptiveSupportCycleDecisionHandoffResult.CycleMismatch,
                ->
                    AdaptiveSupportAlternativeResult.NoActiveCycle
                AdaptiveSupportCycleDecisionHandoffResult.InvalidPersistedState ->
                    AdaptiveSupportAlternativeResult.InvalidPersistedState
                AdaptiveSupportCycleDecisionHandoffResult.Expired ->
                    AdaptiveSupportAlternativeResult.Expired
                AdaptiveSupportCycleDecisionHandoffResult.PersistenceFailure ->
                    AdaptiveSupportAlternativeResult.PersistenceFailure
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return AdaptiveSupportAlternativeResult.PersistenceFailure
        }
    }

    private fun ready(
        decisionId: String,
        followUp: AdaptiveFollowUpResult.Ready,
        state: PersistedAdaptiveSupportCycle,
    ): AdaptiveSupportAlternativeResult = AdaptiveSupportAlternativeResult.Ready(
        decisionId = decisionId,
        routeRequest = followUp.routeRequest,
        cycle = state,
    )

    /**
     * Reports the authoritative owner when it can still be read, rather than
     * asserting a stale one.
     */
    private suspend fun ownerMismatch(
        cycleId: String,
    ): AdaptiveSupportAlternativeResult {
        val actual = supportCycles.activeCycleOwner(cycleId)
            ?: return AdaptiveSupportAlternativeResult.PersistenceFailure
        return AdaptiveSupportAlternativeResult.OwnerMismatch(actualDecisionId = actual)
    }
}
