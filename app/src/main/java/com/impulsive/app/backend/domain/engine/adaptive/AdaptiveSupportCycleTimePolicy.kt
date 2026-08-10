package com.impulsive.app.backend.domain.engine.adaptive

import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycle
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycleStatus
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycleTransitionReason
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportStepOutcome
import kotlin.math.min

object AdaptiveSupportCycleTimePolicy {
    fun elapsedDurationMillis(
        startedAtElapsedRealtimeMillis: Long,
        endedAtElapsedRealtimeMillis: Long,
    ): Long {
        require(
            startedAtElapsedRealtimeMillis >=
                0L,
        ) {
            "Elapsed-realtime start must not be negative."
        }

        require(
            endedAtElapsedRealtimeMillis >=
                startedAtElapsedRealtimeMillis,
        ) {
            "Elapsed-realtime end must not precede its start."
        }

        return endedAtElapsedRealtimeMillis -
            startedAtElapsedRealtimeMillis
    }

    fun recordElapsedDuration(
        cycle: AdaptiveSupportCycle,
        elapsedDurationMillis: Long,
    ): AdaptiveSupportCycle {
        require(
            elapsedDurationMillis >= 0L,
        ) {
            "Elapsed support duration must not be negative."
        }

        if (
            cycle.isTerminal ||
            elapsedDurationMillis == 0L
        ) {
            return cycle
        }

        val currentStep =
            requireNotNull(
                cycle.currentStep,
            ) {
                "An active support cycle requires a current step before time is consumed."
            }

        require(
            !currentStep.outcome.isTerminal,
        ) {
            "A terminal support step cannot consume more cycle time."
        }

        val consumedIncrement =
            min(
                elapsedDurationMillis,
                min(
                    cycle.remainingDurationMillis,
                    currentStep.remainingDurationMillis,
                ),
            )

        val updatedCycleConsumption =
            cycle.consumedDurationMillis +
                consumedIncrement

        val updatedStepConsumption =
            currentStep.consumedDurationMillis +
                consumedIncrement

        val cycleBudgetExhausted =
            updatedCycleConsumption ==
                cycle.initialDurationMillis

        val stepBudgetExhausted =
            updatedStepConsumption ==
                currentStep.allottedDurationMillis

        return cycle.copy(
            consumedDurationMillis =
                updatedCycleConsumption,
            currentStep =
                currentStep.copy(
                    consumedDurationMillis =
                        updatedStepConsumption,
                    outcome =
                        if (
                            stepBudgetExhausted
                        ) {
                            AdaptiveSupportStepOutcome
                                .TimedOut
                        } else {
                            currentStep.outcome
                        },
                ),
            transitionReason =
                when {
                    cycleBudgetExhausted ->
                        AdaptiveSupportCycleTransitionReason
                            .BudgetExhausted

                    stepBudgetExhausted ->
                        AdaptiveSupportCycleTransitionReason
                            .StepBudgetExhausted

                    else ->
                        cycle.transitionReason
                },
            status =
                if (
                    cycleBudgetExhausted
                ) {
                    AdaptiveSupportCycleStatus
                        .TimedOut
                } else {
                    cycle.status
                },
        )
    }

    fun nextStepDurationMillis(
        cycle: AdaptiveSupportCycle,
        requestedDurationMillis: Long,
        minimumUsefulDurationMillis: Long,
    ): Long? {
        require(
            requestedDurationMillis > 0L,
        ) {
            "Requested step duration must be positive."
        }

        require(
            minimumUsefulDurationMillis > 0L,
        ) {
            "Minimum useful duration must be positive."
        }

        if (
            cycle.isTerminal ||
            requestedDurationMillis <
                minimumUsefulDurationMillis ||
            cycle.remainingDurationMillis <
                minimumUsefulDurationMillis
        ) {
            return null
        }

        return min(
            requestedDurationMillis,
            cycle.remainingDurationMillis,
        )
    }
}
