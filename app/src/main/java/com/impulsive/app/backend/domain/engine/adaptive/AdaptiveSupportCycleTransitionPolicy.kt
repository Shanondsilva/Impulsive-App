package com.impulsive.app.backend.domain.engine.adaptive

import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycle
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycleStatus
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycleStep
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycleTransitionReason
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportStepOutcome
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.score.ScoreGameType

enum class AdaptiveSupportCycleTransitionRejection {
    CycleTerminal,
    StepAlreadyInProgress,
    NoCurrentStep,
    CurrentStepAlreadyTerminal,
    CurrentStepNotTerminal,
    InsufficientUsefulBudget,
    InvalidTerminalOutcome,
}

sealed interface AdaptiveSupportCycleTransitionResult {
    data class Applied(
        val cycle: AdaptiveSupportCycle,
    ) : AdaptiveSupportCycleTransitionResult

    data class Rejected(
        val reason:
            AdaptiveSupportCycleTransitionRejection,
    ) : AdaptiveSupportCycleTransitionResult
}

enum class AdaptiveSupportCycleStepResolution {
    CompletedAndContinue,
    CompletedAndEndCycle,
    FailedAndContinue,
    FailedAndEndCycle,
    AbandonedAndContinue,
    AbandonedAndEndCycle,
    AlternativeRequested,
    CancelledAndEndCycle,
}

object AdaptiveSupportCycleTransitionPolicy {
    fun finishCycleAfterResolvedStep(
        cycle: AdaptiveSupportCycle,
        requestedTerminalStatus: AdaptiveSupportCycleStatus,
    ): AdaptiveSupportCycleTransitionResult {
        if (cycle.isTerminal) {
            return AdaptiveSupportCycleTransitionResult.Rejected(
                AdaptiveSupportCycleTransitionRejection.CycleTerminal,
            )
        }
        val step = cycle.currentStep ?: return AdaptiveSupportCycleTransitionResult.Rejected(
            AdaptiveSupportCycleTransitionRejection.NoCurrentStep,
        )
        if (!step.outcome.isTerminal) {
            return AdaptiveSupportCycleTransitionResult.Rejected(
                AdaptiveSupportCycleTransitionRejection.CurrentStepNotTerminal,
            )
        }
        val valid = when (requestedTerminalStatus) {
            AdaptiveSupportCycleStatus.Completed ->
                step.outcome == AdaptiveSupportStepOutcome.Completed
            AdaptiveSupportCycleStatus.Failed ->
                step.outcome == AdaptiveSupportStepOutcome.Failed ||
                    step.outcome == AdaptiveSupportStepOutcome.TimedOut
            AdaptiveSupportCycleStatus.Abandoned ->
                step.outcome == AdaptiveSupportStepOutcome.Abandoned
            AdaptiveSupportCycleStatus.TimedOut ->
                step.outcome == AdaptiveSupportStepOutcome.TimedOut &&
                    cycle.remainingDurationMillis == 0L
            AdaptiveSupportCycleStatus.Cancelled ->
                step.outcome == AdaptiveSupportStepOutcome.Cancelled
            AdaptiveSupportCycleStatus.Active -> false
        }
        if (!valid) {
            return AdaptiveSupportCycleTransitionResult.Rejected(
                AdaptiveSupportCycleTransitionRejection.InvalidTerminalOutcome,
            )
        }
        val reason = when (requestedTerminalStatus) {
            AdaptiveSupportCycleStatus.Completed ->
                AdaptiveSupportCycleTransitionReason.InterventionCompleted
            AdaptiveSupportCycleStatus.Failed ->
                AdaptiveSupportCycleTransitionReason.InterventionFailed
            AdaptiveSupportCycleStatus.Abandoned ->
                AdaptiveSupportCycleTransitionReason.InterventionAbandoned
            AdaptiveSupportCycleStatus.TimedOut ->
                AdaptiveSupportCycleTransitionReason.BudgetExhausted
            AdaptiveSupportCycleStatus.Cancelled ->
                AdaptiveSupportCycleTransitionReason.CycleCancelled
            AdaptiveSupportCycleStatus.Active -> error("Validated above")
        }
        return AdaptiveSupportCycleTransitionResult.Applied(
            cycle.copy(status = requestedTerminalStatus, transitionReason = reason),
        )
    }

    fun startStep(
        cycle: AdaptiveSupportCycle,
        intervention: InterventionFamily,
        gameType: ScoreGameType? = null,
        requestedDurationMillis: Long,
        minimumUsefulDurationMillis: Long,
    ): AdaptiveSupportCycleTransitionResult {
        if (
            cycle.isTerminal
        ) {
            return AdaptiveSupportCycleTransitionResult
                .Rejected(
                    AdaptiveSupportCycleTransitionRejection
                        .CycleTerminal,
                )
        }

        if (
            cycle.currentStep
                ?.outcome ==
                AdaptiveSupportStepOutcome.InProgress
        ) {
            return AdaptiveSupportCycleTransitionResult
                .Rejected(
                    AdaptiveSupportCycleTransitionRejection
                        .StepAlreadyInProgress,
                )
        }

        val stepDuration =
            AdaptiveSupportCycleTimePolicy
                .nextStepDurationMillis(
                    cycle =
                        cycle,
                    requestedDurationMillis =
                        requestedDurationMillis,
                    minimumUsefulDurationMillis =
                        minimumUsefulDurationMillis,
                )
                ?: return AdaptiveSupportCycleTransitionResult
                    .Rejected(
                        AdaptiveSupportCycleTransitionRejection
                            .InsufficientUsefulBudget,
                    )

        val previousSequence =
            cycle.currentStep
                ?.sequence
                ?: 0

        require(
            previousSequence <
                Int.MAX_VALUE,
        ) {
            "Support-cycle step sequence is exhausted."
        }

        val step =
            AdaptiveSupportCycleStep(
                sequence =
                    previousSequence +
                        1,
                intervention =
                    intervention,
                gameType =
                    gameType,
                startedAtCycleConsumedDurationMillis =
                    cycle.consumedDurationMillis,
                allottedDurationMillis =
                    stepDuration,
                consumedDurationMillis =
                    0L,
                outcome =
                    AdaptiveSupportStepOutcome
                        .InProgress,
            )

        return AdaptiveSupportCycleTransitionResult
            .Applied(
                cycle.copy(
                    currentStep =
                        step,
                    transitionReason =
                        AdaptiveSupportCycleTransitionReason
                            .InterventionStarted,
                    status =
                        AdaptiveSupportCycleStatus
                            .Active,
                ),
            )
    }

    fun resolveCurrentStep(
        cycle: AdaptiveSupportCycle,
        resolution:
            AdaptiveSupportCycleStepResolution,
    ): AdaptiveSupportCycleTransitionResult {
        if (
            cycle.isTerminal
        ) {
            return AdaptiveSupportCycleTransitionResult
                .Rejected(
                    AdaptiveSupportCycleTransitionRejection
                        .CycleTerminal,
                )
        }

        val currentStep =
            cycle.currentStep
                ?: return AdaptiveSupportCycleTransitionResult
                    .Rejected(
                        AdaptiveSupportCycleTransitionRejection
                            .NoCurrentStep,
                    )

        if (
            currentStep.outcome.isTerminal
        ) {
            return AdaptiveSupportCycleTransitionResult
                .Rejected(
                    AdaptiveSupportCycleTransitionRejection
                        .CurrentStepAlreadyTerminal,
                )
        }

        val stepOutcome =
            when (
                resolution
            ) {
                AdaptiveSupportCycleStepResolution
                    .CompletedAndContinue,
                AdaptiveSupportCycleStepResolution
                    .CompletedAndEndCycle,
                ->
                    AdaptiveSupportStepOutcome
                        .Completed

                AdaptiveSupportCycleStepResolution
                    .FailedAndContinue,
                AdaptiveSupportCycleStepResolution
                    .FailedAndEndCycle,
                ->
                    AdaptiveSupportStepOutcome
                        .Failed

                AdaptiveSupportCycleStepResolution
                    .AbandonedAndContinue,
                AdaptiveSupportCycleStepResolution
                    .AbandonedAndEndCycle,
                AdaptiveSupportCycleStepResolution
                    .AlternativeRequested,
                ->
                    AdaptiveSupportStepOutcome
                        .Abandoned

                AdaptiveSupportCycleStepResolution
                    .CancelledAndEndCycle,
                ->
                    AdaptiveSupportStepOutcome
                        .Cancelled
            }

        /*
         * An explicit alternative request is the only transition that advances
         * this durable cycle-level counter. The first accepted request leaves
         * the cycle active; the second is authoritatively terminal, and both
         * the counter and the terminal status are produced by this single pure
         * transition so the coordinator commits them in one repository write.
         */
        val isSecondAlternativeRequest =
            resolution ==
                AdaptiveSupportCycleStepResolution
                    .AlternativeRequested &&
                cycle.alternativeRequestCount >=
                1

        val alternativeRequestCount =
            if (
                resolution ==
                AdaptiveSupportCycleStepResolution
                    .AlternativeRequested
            ) {
                cycle.alternativeRequestCount +
                    1
            } else {
                cycle.alternativeRequestCount
            }

        val transitionReason =
            when (
                resolution
            ) {
                AdaptiveSupportCycleStepResolution
                    .CompletedAndContinue,
                AdaptiveSupportCycleStepResolution
                    .CompletedAndEndCycle,
                ->
                    AdaptiveSupportCycleTransitionReason
                        .InterventionCompleted

                AdaptiveSupportCycleStepResolution
                    .FailedAndContinue,
                AdaptiveSupportCycleStepResolution
                    .FailedAndEndCycle,
                ->
                    AdaptiveSupportCycleTransitionReason
                        .InterventionFailed

                AdaptiveSupportCycleStepResolution
                    .AbandonedAndContinue,
                AdaptiveSupportCycleStepResolution
                    .AbandonedAndEndCycle,
                ->
                    AdaptiveSupportCycleTransitionReason
                        .InterventionAbandoned

                /*
                 * The terminal second request reports InterventionAbandoned so
                 * AdaptiveSupportCycleOutcomeMapper -- which prioritises
                 * UserRequestedAlternative ahead of status -- maps the finished
                 * cycle to Abandoned rather than to a merely-continuing
                 * AlternativeRequested. The durable count records why it ended.
                 */
                AdaptiveSupportCycleStepResolution
                    .AlternativeRequested,
                ->
                    if (
                        isSecondAlternativeRequest
                    ) {
                        AdaptiveSupportCycleTransitionReason
                            .InterventionAbandoned
                    } else {
                        AdaptiveSupportCycleTransitionReason
                            .UserRequestedAlternative
                    }

                AdaptiveSupportCycleStepResolution
                    .CancelledAndEndCycle,
                ->
                    AdaptiveSupportCycleTransitionReason
                        .CycleCancelled
            }

        val status =
            when (
                resolution
            ) {
                AdaptiveSupportCycleStepResolution
                    .CompletedAndContinue,
                AdaptiveSupportCycleStepResolution
                    .FailedAndContinue,
                AdaptiveSupportCycleStepResolution
                    .AbandonedAndContinue,
                ->
                    AdaptiveSupportCycleStatus
                        .Active

                /*
                 * A first request continues the cycle so another intervention
                 * can be offered. A second one terminates it atomically.
                 */
                AdaptiveSupportCycleStepResolution
                    .AlternativeRequested,
                ->
                    if (
                        isSecondAlternativeRequest
                    ) {
                        AdaptiveSupportCycleStatus
                            .Abandoned
                    } else {
                        AdaptiveSupportCycleStatus
                            .Active
                    }

                AdaptiveSupportCycleStepResolution
                    .CompletedAndEndCycle,
                ->
                    AdaptiveSupportCycleStatus
                        .Completed

                AdaptiveSupportCycleStepResolution
                    .FailedAndEndCycle,
                ->
                    AdaptiveSupportCycleStatus
                        .Failed

                AdaptiveSupportCycleStepResolution
                    .AbandonedAndEndCycle,
                ->
                    AdaptiveSupportCycleStatus
                        .Abandoned

                AdaptiveSupportCycleStepResolution
                    .CancelledAndEndCycle,
                ->
                    AdaptiveSupportCycleStatus
                        .Cancelled
            }

        return AdaptiveSupportCycleTransitionResult
            .Applied(
                cycle.copy(
                    currentStep =
                        currentStep.copy(
                            outcome =
                                stepOutcome,
                        ),
                    alternativeRequestCount =
                        alternativeRequestCount,
                    transitionReason =
                        transitionReason,
                    status =
                        status,
                ),
            )
    }
}
