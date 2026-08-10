package com.impulsive.app.backend.session.game

import com.impulsive.app.backend.domain.game.SnakeRoundEndReason

/** Maps a terminal Snake round onto the support-cycle vocabulary. */
internal object SnakeGameSupportOutcomePolicy {
    fun terminalOutcome(
        endReason: SnakeRoundEndReason,
        validCompletion: Boolean,
        supportCycle: Boolean,
    ): SupportCycleGameTerminalOutcome = when (endReason) {
        /*
         * Inside a support cycle, consuming the whole allocation is always
         * TimedOut. The coordinator applies elapsed duration before resolving
         * the step, so a local "Completed" guess would contradict the
         * authoritative outcome when the allocation happens to be 90 seconds.
         */
        SnakeRoundEndReason.TimeLimit ->
            if (supportCycle) {
                SupportCycleGameTerminalOutcome.TimedOut
            } else {
                SupportCycleGameTerminalOutcome.Completed
            }

        SnakeRoundEndReason.BoardCleared -> SupportCycleGameTerminalOutcome.Completed

        SnakeRoundEndReason.SelfCollision ->
            if (validCompletion) {
                SupportCycleGameTerminalOutcome.Completed
            } else {
                SupportCycleGameTerminalOutcome.Abandoned
            }
    }
}
