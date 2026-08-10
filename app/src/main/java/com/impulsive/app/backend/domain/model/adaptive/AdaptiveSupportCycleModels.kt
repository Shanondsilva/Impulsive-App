package com.impulsive.app.backend.domain.model.adaptive

import com.impulsive.app.backend.domain.model.score.ScoreGameType

enum class AdaptiveSupportCycleStatus {
    Active,
    Completed,
    Abandoned,
    Failed,
    TimedOut,
    Cancelled,
    ;

    val isTerminal: Boolean
        get() =
            this != Active
}

enum class AdaptiveSupportCycleTransitionReason {
    Created,
    InterventionStarted,
    InterventionCompleted,
    InterventionFailed,
    InterventionAbandoned,
    UserRequestedAlternative,
    StepBudgetExhausted,
    BudgetExhausted,
    CycleCancelled,
    Restored,
}

enum class AdaptiveSupportStepOutcome {
    InProgress,
    Completed,
    Failed,
    Abandoned,
    TimedOut,
    Cancelled,
    ;

    val isTerminal: Boolean
        get() =
            this != InProgress
}

data class AdaptiveSupportCycleStep(
    val sequence: Int,
    val intervention: InterventionFamily,
    val gameType: ScoreGameType? = null,
    val startedAtCycleConsumedDurationMillis: Long,
    val allottedDurationMillis: Long,
    val consumedDurationMillis: Long = 0L,
    val outcome: AdaptiveSupportStepOutcome =
        AdaptiveSupportStepOutcome.InProgress,
) {
    init {
        require(
            sequence > 0,
        ) {
            "Support-cycle step sequence must be positive."
        }

        require(
            startedAtCycleConsumedDurationMillis >=
                0L,
        ) {
            "Step start consumption must not be negative."
        }

        require(
            allottedDurationMillis > 0L,
        ) {
            "Support-cycle step duration must be positive."
        }

        require(
            consumedDurationMillis in
                0L..allottedDurationMillis,
        ) {
            "Step consumption must remain inside its allotted duration."
        }

        require(
            outcome !=
                AdaptiveSupportStepOutcome.InProgress ||
                consumedDurationMillis <
                allottedDurationMillis,
        ) {
            "A fully consumed step cannot remain in progress."
        }

        require(
            outcome !=
                AdaptiveSupportStepOutcome.TimedOut ||
                consumedDurationMillis ==
                allottedDurationMillis,
        ) {
            "A timed-out step must consume its complete allocation."
        }

        when (
            intervention
        ) {
            InterventionFamily.PivotGame ->
                require(
                    gameType != null,
                ) {
                    "A Pivot Game support step requires a concrete game type."
                }

            InterventionFamily.ShortPause,
            InterventionFamily.PivotReading,
            InterventionFamily.MomentPlan,
            ->
                require(
                    gameType == null,
                ) {
                    "Only Pivot Game support steps may carry a game type."
                }
        }
    }

    val remainingDurationMillis: Long
        get() =
            allottedDurationMillis -
                consumedDurationMillis
}

/**
 * One bounded support budget for a single Moment.
 *
 * @property decisionId The adaptive decision **currently owning** this support
 * lifecycle. This is not permanently the decision that originally created the
 * cycle: after a validated explicit same-Moment follow-up, ownership transfers
 * to the follow-up decision while the cycle itself, its budget and its expiry
 * are unchanged. Adaptive decisions stay immutable historical records; this
 * field is the pointer used for recovery and navigation.
 * @property protectionIncidentToken The **root** protected-incident identity of
 * this cycle. It is never rewritten during a same-Moment decision handoff, so
 * it keeps pointing at the incident that opened the Moment even when a
 * follow-up decision (which carries its own private token) owns the cycle.
 * @property alternativeRequestCount The number of explicit
 * "this isn't helping" requests authoritatively applied to this cycle. It is
 * cycle-level control state because no other field can durably represent it:
 * [transitionReason] is overwritten as soon as the next intervention starts,
 * step sequences also advance for completions, failures and ordinary
 * abandonments, and only one [currentStep] is retained. The first accepted
 * request leaves the cycle Active at one; the second terminates it at two.
 */
data class AdaptiveSupportCycle(
    val cycleId: String,
    val decisionId: String,
    val protectionIncidentToken: String,
    val initialDurationMillis: Long,
    val consumedDurationMillis: Long = 0L,
    val currentStep: AdaptiveSupportCycleStep? = null,
    val consecutiveGameAssignments: Int = 0,
    val alternativeRequestCount: Int = 0,
    val transitionReason:
        AdaptiveSupportCycleTransitionReason =
        AdaptiveSupportCycleTransitionReason.Created,
    val status:
        AdaptiveSupportCycleStatus =
        AdaptiveSupportCycleStatus.Active,
) {
    init {
        require(
            cycleId.isNotBlank(),
        ) {
            "Support-cycle ID must not be blank."
        }

        require(
            decisionId.isNotBlank(),
        ) {
            "Support-cycle decision ID must not be blank."
        }

        require(
            protectionIncidentToken.isNotBlank(),
        ) {
            "Support-cycle incident token must not be blank."
        }

        /*
         * The product contract is a single fixed protected duration, not a
         * default that callers may vary. Enforcing it in the model means an
         * obsolete shorter cycle persisted by an earlier build also fails to
         * reconstruct, and is cleared by the repository's invalid-state path
         * rather than silently gaining or losing support time.
         */
        require(
            initialDurationMillis ==
                AdaptiveSupportCycleTiming.TotalDurationMillis,
        ) {
            "Protected Support Cycles must use the fixed 90-second duration."
        }

        require(
            consumedDurationMillis in
                0L..initialDurationMillis,
        ) {
            "Consumed duration must remain inside the cycle budget."
        }

        require(
            consecutiveGameAssignments >= 0,
        ) {
            "Consecutive game assignments must not be negative."
        }

        require(
            alternativeRequestCount in
                0..2,
        ) {
            "Support-cycle alternative request count must be between zero and two."
        }

        require(
            status !=
                AdaptiveSupportCycleStatus.Active ||
                alternativeRequestCount < 2,
        ) {
            "An active support cycle cannot remain active after a second alternative request."
        }

        require(
            status !=
                AdaptiveSupportCycleStatus.Active ||
                consumedDurationMillis <
                initialDurationMillis,
        ) {
            "An exhausted support cycle cannot remain active."
        }

        require(
            status !=
                AdaptiveSupportCycleStatus.TimedOut ||
                consumedDurationMillis ==
                initialDurationMillis,
        ) {
            "A timed-out support cycle must have exhausted its budget."
        }

        require(
            !status.isTerminal ||
                currentStep == null ||
                currentStep.outcome.isTerminal,
        ) {
            "A terminal support cycle cannot retain an in-progress step."
        }

        currentStep?.let { step ->
            require(
                step.startedAtCycleConsumedDurationMillis <=
                    consumedDurationMillis,
            ) {
                "A step cannot begin after the cycle's current consumption."
            }

            require(
                step.startedAtCycleConsumedDurationMillis ==
                    consumedDurationMillis -
                    step.consumedDurationMillis,
            ) {
                "Cycle and current-step consumption must describe one timeline."
            }

            require(
                step.startedAtCycleConsumedDurationMillis <=
                    initialDurationMillis,
            ) {
                "A step cannot begin beyond the cycle budget."
            }

            require(
                step.allottedDurationMillis <=
                    initialDurationMillis -
                    step.startedAtCycleConsumedDurationMillis,
            ) {
                "A step allocation cannot exceed the remaining cycle budget at start."
            }
        }
    }

    val remainingDurationMillis: Long
        get() =
            initialDurationMillis -
                consumedDurationMillis

    val isTerminal: Boolean
        get() =
            status.isTerminal
}
