package com.impulsive.app.backend.session.adaptive

import com.impulsive.app.backend.domain.model.adaptive.AdaptiveDecision
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycle
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycleStatus
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycleTransitionReason
import com.impulsive.app.backend.domain.model.adaptive.FeedbackCode
import com.impulsive.app.backend.domain.model.adaptive.RepeatObservation
import com.impulsive.app.backend.session.game.SupportCycleGameTerminalOutcome

enum class AdaptiveSupportCycleOutcomeKind {
    Completed,
    Dismissed,
    StartedNotCompleted,
    NotStarted,
    AlternativeRequested,
    Failed,
    Abandoned,
    TimedOut,
    Cancelled,
    WrongTiming,
    HelpfulFeedback,
    PoorFeedback,
    RepeatDetected,
}

object AdaptiveSupportCycleOutcomeMapper {
    fun fromCycle(cycle: AdaptiveSupportCycle): AdaptiveSupportCycleOutcomeKind = when {
        cycle.transitionReason == AdaptiveSupportCycleTransitionReason.UserRequestedAlternative ->
            AdaptiveSupportCycleOutcomeKind.AlternativeRequested
        cycle.status == AdaptiveSupportCycleStatus.Completed ->
            AdaptiveSupportCycleOutcomeKind.Completed
        cycle.status == AdaptiveSupportCycleStatus.Failed ->
            AdaptiveSupportCycleOutcomeKind.Failed
        cycle.status == AdaptiveSupportCycleStatus.Abandoned ->
            AdaptiveSupportCycleOutcomeKind.Abandoned
        cycle.status == AdaptiveSupportCycleStatus.TimedOut ->
            AdaptiveSupportCycleOutcomeKind.TimedOut
        cycle.status == AdaptiveSupportCycleStatus.Cancelled ->
            AdaptiveSupportCycleOutcomeKind.Cancelled
        else -> AdaptiveSupportCycleOutcomeKind.StartedNotCompleted
    }

    fun fromDecision(decision: AdaptiveDecision): Set<AdaptiveSupportCycleOutcomeKind> = buildSet {
        add(
            when {
                decision.completedAtMillis != null -> AdaptiveSupportCycleOutcomeKind.Completed
                decision.dismissedAtMillis != null -> AdaptiveSupportCycleOutcomeKind.Dismissed
                decision.startedAtMillis != null -> AdaptiveSupportCycleOutcomeKind.StartedNotCompleted
                else -> AdaptiveSupportCycleOutcomeKind.NotStarted
            },
        )
        when (decision.feedbackCode) {
            FeedbackCode.Helped,
            FeedbackCode.HelpedALittle,
            -> add(AdaptiveSupportCycleOutcomeKind.HelpfulFeedback)
            FeedbackCode.DidNotHelp -> add(AdaptiveSupportCycleOutcomeKind.PoorFeedback)
            FeedbackCode.WrongTiming -> add(AdaptiveSupportCycleOutcomeKind.WrongTiming)
            FeedbackCode.NotProvided,
            -> Unit
        }
        if (decision.repeatObservation == RepeatObservation.RepeatDetected) {
            add(AdaptiveSupportCycleOutcomeKind.RepeatDetected)
        }
    }
}

class AdaptiveSupportCycleDecisionOutcomeCoordinator(
    private val outcomes: AdaptiveOutcomeCoordinator,
) {
    suspend fun recordTerminal(
        decisionId: String,
        outcome: SupportCycleGameTerminalOutcome,
    ): AdaptiveOutcomeResult = when (outcome) {
        SupportCycleGameTerminalOutcome.Completed -> outcomes.complete(decisionId)
        SupportCycleGameTerminalOutcome.Failed,
        SupportCycleGameTerminalOutcome.Abandoned,
        SupportCycleGameTerminalOutcome.TimedOut,
        -> outcomes.dismiss(decisionId)
    }
}
