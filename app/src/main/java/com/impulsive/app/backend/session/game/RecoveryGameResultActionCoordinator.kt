package com.impulsive.app.backend.session.game

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the support-cycle behaviour shared by every recovery-game result screen.
 *
 * Game ViewModels remain responsible only for their score, history, payload,
 * and UI state. They must not independently reproduce support-cycle action
 * sequencing.
 */
class RecoveryGameResultActionCoordinator(
    private val runtime: RecoveryGameSupportCycleRuntime,
    private val clearResultState: () -> Unit,
) {
    private val actionMutex = Mutex()

    /**
     * Ends an active game before a result has been produced.
     */
    suspend fun abandon(
        elapsedDurationMillis: Long,
    ): Boolean = finish(
        outcome = SupportCycleGameTerminalOutcome.Abandoned,
        elapsedDurationMillis = elapsedDurationMillis,
    )

    /**
     * Confirms that the current result step may remain resolved while the
     * navigation layer starts another game in the same cycle.
     *
     * The snapshot is deliberately retained until navigation begins.
     */
    suspend fun continueWithAnotherGame(
        outcome: SupportCycleGameTerminalOutcome,
        elapsedDurationMillis: Long,
    ): Boolean = actionMutex.withLock {
        runtime.resolveForContinuation(
            outcome = outcome,
            elapsedDurationMillis = elapsedDurationMillis,
        ).allowsContinuation
    }

    /**
     * Creates and binds the next authoritative step for a replay.
     *
     * The previous result snapshot is cleared only after the new step has
     * successfully been created and rebound.
     */
    suspend fun prepareReplay(
        outcome: SupportCycleGameTerminalOutcome,
        elapsedDurationMillis: Long,
        requestedDurationMillis: Long,
    ): Long? = actionMutex.withLock {
        val continuation = runtime.resolveForContinuation(
            outcome = outcome,
            elapsedDurationMillis = elapsedDurationMillis,
        )

        if (!continuation.allowsContinuation) {
            return@withLock null
        }

        val duration = runtime.prepareReplay(
            standaloneDurationMillis = requestedDurationMillis,
        ) ?: return@withLock null

        clearResultState()

        duration
    }

    /**
     * Finishes the cycle using the already-authoritative result outcome.
     *
     * Walk Away and Exit must pass the same outcome that originally resolved
     * the current step. They must not replace Failed, TimedOut, or Abandoned
     * with Completed.
     */
    suspend fun finish(
        outcome: SupportCycleGameTerminalOutcome,
        elapsedDurationMillis: Long,
    ): Boolean = actionMutex.withLock {
        val report = runtime.resolveAndEnd(
            outcome = outcome,
            elapsedDurationMillis = elapsedDurationMillis,
        )

        if (!report.allowsExit) {
            return@withLock false
        }

        clearResultState()

        true
    }
}
