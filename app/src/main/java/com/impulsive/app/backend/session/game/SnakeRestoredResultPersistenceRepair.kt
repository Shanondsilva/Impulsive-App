package com.impulsive.app.backend.session.game

import com.impulsive.app.backend.domain.game.SnakeGameHistory
import com.impulsive.app.backend.domain.game.SnakeGameResult
import com.impulsive.app.backend.domain.model.score.ScoreSessionRecord
import kotlin.coroutines.cancellation.CancellationException

internal fun interface SnakeRestoredScoreWriter {
    suspend fun record(record: ScoreSessionRecord)
}

internal fun interface SnakeRestoredHistoryWriter {
    suspend fun save(history: SnakeGameHistory)
}

/**
 * Re-applies the persistence a restored Result screen implies.
 *
 * A stable Result snapshot can survive process death while the asynchronous
 * score and history writes launched right after it did not finish. This repair
 * closes that window.
 *
 * It is a repair, not a new user action: it deliberately does not go through
 * [PivotGameSessionCommitCoordinator], so it cannot trigger Safe Exit, schedule
 * reconciliation, award anything, or create a second session. The score store
 * replaces by session ID, which makes re-recording naturally idempotent.
 */
internal class SnakeRestoredResultPersistenceRepair(
    private val scoreWriter: SnakeRestoredScoreWriter,
    private val historyWriter: SnakeRestoredHistoryWriter,
) {
    suspend fun repair(
        result: SnakeGameResult,
        history: SnakeGameHistory,
        record: ScoreSessionRecord,
    ): Boolean = try {
        scoreWriter.record(record)

        // An invalid attempt never became official history, so leave it alone.
        if (result.validCompletion) {
            historyWriter.save(history)
        }

        true
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        false
    }
}
