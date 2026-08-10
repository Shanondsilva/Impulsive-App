package com.impulsive.app.backend.session.adaptive

import com.impulsive.app.backend.domain.game.RecoveryGameLaunchContext
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportStepOutcome
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.repository.adaptive.PersistedAdaptiveSupportCycle

/**
 * Safe destination for an already-active support cycle owned by another
 * adaptive decision.
 */
sealed interface AdaptiveSupportCycleResumeTarget {
    /**
     * Return to the existing adaptive moment so its current lifecycle can be
     * continued through the normal chooser.
     */
    data class AdaptiveMoment(
        val decisionId: String,
    ) : AdaptiveSupportCycleResumeTarget

    /**
     * Resume an intervention that already has a concrete production route.
     */
    data class Route(
        val request: AdaptiveRouteRequest,
    ) : AdaptiveSupportCycleResumeTarget
}

/**
 * Converts the authoritative resume target into the existing navigation
 * request model.
 *
 * The resulting decision ID always belongs to the existing active cycle.
 */
fun AdaptiveSupportCycleResumeTarget.toRouteRequest(): AdaptiveRouteRequest =
    when (this) {
        is AdaptiveSupportCycleResumeTarget.AdaptiveMoment ->
            AdaptiveRouteRequest(
                decisionId = decisionId,
                kind = AdaptiveRouteKind.AdaptiveMoment,
            )

        is AdaptiveSupportCycleResumeTarget.Route -> request
    }

/**
 * Converts one authoritative persisted support-cycle state into a safe resume
 * target.
 *
 * This policy never creates a standalone game and never uses information from
 * the conflicting new decision.
 */
object AdaptiveSupportCycleResumePolicy {
    /**
     * Determines whether the AdaptiveGame destination must resume an existing
     * step instead of starting a new game step.
     *
     * In-progress work must always resume first. A terminal game result must also
     * resume so its result screen and final user choice remain available.
     *
     * Terminal non-game steps do not have a game result screen and must not block
     * starting the next authoritative game step.
     */
    fun requiresResumeBeforeStartingGame(
        state: PersistedAdaptiveSupportCycle,
    ): Boolean {
        val step = state.cycle.currentStep ?: return false

        if (step.outcome == AdaptiveSupportStepOutcome.InProgress) {
            return true
        }

        if (step.intervention != InterventionFamily.PivotGame) {
            return false
        }

        return when (step.outcome) {
            AdaptiveSupportStepOutcome.Completed,
            AdaptiveSupportStepOutcome.Failed,
            AdaptiveSupportStepOutcome.Abandoned,
            AdaptiveSupportStepOutcome.TimedOut,
            -> true

            AdaptiveSupportStepOutcome.InProgress -> true

            AdaptiveSupportStepOutcome.Cancelled -> false
        }
    }

    fun target(
        state: PersistedAdaptiveSupportCycle,
    ): AdaptiveSupportCycleResumeTarget {
        val cycle = state.cycle

        val adaptiveMoment = AdaptiveSupportCycleResumeTarget.AdaptiveMoment(
            decisionId = cycle.decisionId,
        )

        if (cycle.isTerminal) {
            return adaptiveMoment
        }

        val step = cycle.currentStep ?: return adaptiveMoment

        return when (step.intervention) {
            InterventionFamily.PivotGame -> {
                val gameType = step.gameType ?: return adaptiveMoment

                val maximumDurationMillis = when (step.outcome) {
                    /*
                     * An in-progress game resumes only its remaining step allocation.
                     */
                    AdaptiveSupportStepOutcome.InProgress ->
                        minOf(
                            step.remainingDurationMillis,
                            cycle.remainingDurationMillis,
                        )

                    /*
                     * These outcomes have a recoverable game result presentation.
                     * The result screen uses the remaining cycle budget because the
                     * resolved step's own remaining allocation is no longer the active
                     * gameplay limit.
                     */
                    AdaptiveSupportStepOutcome.Completed,
                    AdaptiveSupportStepOutcome.Failed,
                    AdaptiveSupportStepOutcome.Abandoned,
                    AdaptiveSupportStepOutcome.TimedOut,
                    -> cycle.remainingDurationMillis

                    /*
                     * Cancelled is not a recoverable game result. The game bridge
                     * deliberately rejects Cancelled steps.
                     */
                    AdaptiveSupportStepOutcome.Cancelled -> return adaptiveMoment
                }

                if (maximumDurationMillis <= 0L) {
                    return adaptiveMoment
                }

                AdaptiveSupportCycleResumeTarget.Route(
                    AdaptiveMomentRoutingPolicy.forSupportCycleGame(
                        RecoveryGameLaunchContext.SupportCycle(
                            cycleId = cycle.cycleId,
                            decisionId = cycle.decisionId,
                            gameType = gameType,
                            maxDurationMillis = maximumDurationMillis,
                        ),
                    ),
                )
            }

            InterventionFamily.PivotReading ->
                if (step.outcome == AdaptiveSupportStepOutcome.InProgress) {
                    AdaptiveSupportCycleResumeTarget.Route(
                        AdaptiveRouteRequest(
                            decisionId = cycle.decisionId,
                            kind = AdaptiveRouteKind.Reading,
                        ),
                    )
                } else {
                    adaptiveMoment
                }

            InterventionFamily.MomentPlan ->
                if (step.outcome == AdaptiveSupportStepOutcome.InProgress) {
                    AdaptiveSupportCycleResumeTarget.Route(
                        AdaptiveRouteRequest(
                            decisionId = cycle.decisionId,
                            kind = AdaptiveRouteKind.MomentPlan,
                        ),
                    )
                } else {
                    adaptiveMoment
                }

            /*
             * Short Pause has no separate navigation destination. Returning to
             * the owning adaptive moment allows its existing timer and
             * lifecycle state to resume.
             */
            InterventionFamily.ShortPause -> adaptiveMoment
        }
    }
}
