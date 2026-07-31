package com.impulsive.app.backend.session.adaptive

import com.impulsive.app.backend.domain.engine.adaptive.InterventionProtocolRegistry
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveDecision
import com.impulsive.app.backend.domain.model.adaptive.FeedbackCode
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveDecisionRepository
import java.util.concurrent.atomic.AtomicBoolean

enum class AdaptiveOutcomeResult {
    Applied,
    Idempotent,
    NotFound,
    NotStarted,
    NotTerminal,
    ConflictingTerminalState,
    InvalidTimestamp,
    RetryableFailure,
}

/**
 * A small Phase 6 boundary over the existing lifecycle. It adds terminal-state
 * reload/idempotency semantics without changing the Phase 4 persistence model
 * or observation scheduling.
 */
class AdaptiveOutcomeCoordinator(
    private val decisions: AdaptiveDecisionRepository,
    private val lifecycle: AdaptiveDecisionLifecycle,
    private val clock: AdaptiveClock,
) {
    suspend fun load(decisionId: String): AdaptiveDecision? =
        decisionId.takeIf { it.isNotBlank() }?.let { decisions.getById(it) }

    suspend fun complete(
        decisionId: String,
        timestamp: Long = clock.nowMillis(),
    ): AdaptiveOutcomeResult {
        val current = load(decisionId) ?: return AdaptiveOutcomeResult.NotFound
        if (current.completedAtMillis != null) return AdaptiveOutcomeResult.Idempotent
        if (current.dismissedAtMillis != null) {
            return AdaptiveOutcomeResult.ConflictingTerminalState
        }
        if (current.startedAtMillis == null) return AdaptiveOutcomeResult.NotStarted
        return lifecycle.markCompleted(decisionId, timestamp).toOutcomeResult()
    }

    suspend fun dismiss(
        decisionId: String,
        timestamp: Long = clock.nowMillis(),
    ): AdaptiveOutcomeResult {
        val current = load(decisionId) ?: return AdaptiveOutcomeResult.NotFound
        if (current.dismissedAtMillis != null) return AdaptiveOutcomeResult.Idempotent
        if (current.completedAtMillis != null) {
            return AdaptiveOutcomeResult.ConflictingTerminalState
        }
        if (current.startedAtMillis == null) return AdaptiveOutcomeResult.NotStarted
        return lifecycle.markDismissed(decisionId, timestamp).toOutcomeResult()
    }

    suspend fun submitFeedback(
        decisionId: String,
        feedbackCode: FeedbackCode,
        timestamp: Long = clock.nowMillis(),
    ): AdaptiveOutcomeResult {
        val current = load(decisionId) ?: return AdaptiveOutcomeResult.NotFound
        if (current.startedAtMillis == null) return AdaptiveOutcomeResult.NotStarted
        if (current.completedAtMillis == null && current.dismissedAtMillis == null) {
            return AdaptiveOutcomeResult.NotTerminal
        }
        val actual = current.assignment.actualIntervention
            ?: return AdaptiveOutcomeResult.NotTerminal
        if (!InterventionProtocolRegistry.supportsFeedback(actual)) {
            return AdaptiveOutcomeResult.NotTerminal
        }
        if (
            current.feedbackCode == feedbackCode &&
            current.feedbackUpdatedAtMillis != null
        ) {
            return AdaptiveOutcomeResult.Idempotent
        }
        return lifecycle.updateFeedback(
            decisionId = decisionId,
            feedbackCode = feedbackCode,
            timestamp = timestamp,
        ).toOutcomeResult()
    }

    private fun AdaptiveLifecycleResult.toOutcomeResult(): AdaptiveOutcomeResult = when (this) {
        AdaptiveLifecycleResult.Applied -> AdaptiveOutcomeResult.Applied
        AdaptiveLifecycleResult.Idempotent -> AdaptiveOutcomeResult.Idempotent
        AdaptiveLifecycleResult.NotFound -> AdaptiveOutcomeResult.NotFound
        AdaptiveLifecycleResult.InvalidTimestamp -> AdaptiveOutcomeResult.InvalidTimestamp
        AdaptiveLifecycleResult.InvalidTransition,
        AdaptiveLifecycleResult.ConflictingChoice,
        AdaptiveLifecycleResult.IneligibleChoice,
        AdaptiveLifecycleResult.InvalidMomentPlan,
        -> AdaptiveOutcomeResult.ConflictingTerminalState
        AdaptiveLifecycleResult.PersistenceFailure,
        AdaptiveLifecycleResult.SchedulingFailure,
        -> AdaptiveOutcomeResult.RetryableFailure
    }
}

object AdaptiveCompletionGate {
    const val ShortPauseDurationMillis = 30_000L
    const val ReadingMinimumSeconds = 90

    fun pauseRemainingMillis(startedAtMillis: Long, nowMillis: Long): Long =
        (ShortPauseDurationMillis - (nowMillis - startedAtMillis).coerceAtLeast(0L))
            .coerceAtLeast(0L)

    fun pauseFinished(startedAtMillis: Long, nowMillis: Long): Boolean =
        pauseRemainingMillis(startedAtMillis, nowMillis) == 0L

    fun gameCompleted(validCompletion: Boolean): Boolean = validCompletion

    fun readingCompleted(
        secondsSpent: Int,
        reachedEnd: Boolean,
        existingValidCompletion: Boolean,
    ): Boolean =
        secondsSpent >= ReadingMinimumSeconds &&
            reachedEnd &&
            existingValidCompletion
}

data class AdaptivePendingFeedbackSafety(
    val protectionOverlayVisible: Boolean,
    val activeInterventionRunning: Boolean,
    val appLockPending: Boolean,
)

class AdaptivePendingFeedbackCoordinator(
    private val decisions: AdaptiveDecisionRepository,
) {
    private val automaticPresentationClaimed = AtomicBoolean(false)

    suspend fun claimMostRecentEligible(
        safety: AdaptivePendingFeedbackSafety,
    ): AdaptiveDecision? {
        if (
            safety.protectionOverlayVisible ||
            safety.activeInterventionRunning ||
            safety.appLockPending
        ) {
            return null
        }
        val pending = decisions.getLatestPendingFeedback() ?: return null
        return pending.takeIf {
            automaticPresentationClaimed.compareAndSet(false, true)
        }
    }
}

class AdaptiveOutcomeOperationGuard {
    private val inFlight = AtomicBoolean(false)

    fun tryStart(): Boolean = inFlight.compareAndSet(false, true)

    fun clear() {
        inFlight.set(false)
    }
}
