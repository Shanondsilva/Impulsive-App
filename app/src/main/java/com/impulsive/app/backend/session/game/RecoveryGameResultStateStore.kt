package com.impulsive.app.backend.session.game

import androidx.lifecycle.SavedStateHandle
import com.impulsive.app.backend.domain.game.RecoveryGameLaunchContext
import com.impulsive.app.backend.domain.model.score.ScoreGameType
import com.impulsive.app.backend.domain.model.score.ScoreSessionOutcome
import com.impulsive.app.backend.domain.model.score.ScoreSessionRecord
import java.io.Serializable
import java.time.LocalDateTime

/**
 * Presentation-only recovery state for a support-cycle game result screen.
 *
 * The support-cycle coordinator remains authoritative for cycle and step state.
 * This snapshot contains only enough stable data to reconstruct a result screen
 * after Android process death.
 */
class RecoveryGameResultStateStore(
    private val savedStateHandle: SavedStateHandle,
) {
    fun save(snapshot: RecoveryGameResultSnapshot) {
        savedStateHandle[SnapshotKey] = snapshot
    }

    fun restore(
        launchContext: RecoveryGameLaunchContext,
        expectedGameType: ScoreGameType,
    ): RecoveryGameResultSnapshot? {
        val launch =
            launchContext as? RecoveryGameLaunchContext.SupportCycle
                ?: return null

        if (launch.gameType != expectedGameType) {
            return null
        }

        val snapshot =
            savedStateHandle.get<RecoveryGameResultSnapshot>(SnapshotKey)
                ?: return null

        return snapshot.takeIf {
            it.version == CurrentVersion &&
                it.cycleId == launch.cycleId &&
                it.decisionId == launch.decisionId &&
                it.gameTypeId == launch.gameType.id &&
                it.gameTypeId == expectedGameType.id
        }
    }

    fun clear() {
        savedStateHandle.remove<RecoveryGameResultSnapshot>(SnapshotKey)
    }

    companion object {
        const val CurrentVersion: Int = 1
        const val SnapshotKey: String = "recoveryGameResultSnapshot"
    }
}

/**
 * Common saved state shared by every supported recovery-game result.
 *
 * Enum values are stored by stable name or ID so this object remains composed
 * only of Serializable primitive-like values and nested snapshots.
 */
data class RecoveryGameResultSnapshot(
    val version: Int = RecoveryGameResultStateStore.CurrentVersion,
    val cycleId: String,
    val decisionId: String,
    val gameTypeId: String,
    val supportOutcomeName: String,
    val supportElapsedDurationMillis: Long,
    val activeSessionId: Long,
    val sessionStartedAtIso: String,
    val urgeBeforeRating: Int?,
    val urgeAfterRating: Int?,
    val lastRecordedSession: ScoreSessionSnapshot?,
    val payload: RecoveryGameResultPayload,
) : Serializable {
    fun supportOutcomeOrNull(): SupportCycleGameTerminalOutcome? =
        SupportCycleGameTerminalOutcome.entries.firstOrNull {
            it.name == supportOutcomeName
        }

    fun sessionStartedAtOrNull(): LocalDateTime? =
        runCatching {
            LocalDateTime.parse(sessionStartedAtIso)
        }.getOrNull()
}

/**
 * Stable, game-specific result presentation data.
 *
 * Do not add active gameplay objects to these payloads.
 */
sealed class RecoveryGameResultPayload : Serializable {
    data class Reflex(
        val score: Int,
        val combo: Int,
        val lives: Int,
        val historyPersonalBest: Int,
        val historyPrevious: Int,
        val historyBestReactionMs: Int?,
        val historyBestCombo: Int,
        val resultScore: Int,
        val resultPreviousBest: Int,
        val resultPreviousScore: Int,
        val resultBestReactionMs: Int?,
        val resultMaxCombo: Int,
        val resultHits: Int,
        val resultMisses: Int,
        val resultDifficulty: Int,
        val resultGameOver: Boolean,
        val resultDurationSec: Int,
        val resultValidCompletion: Boolean,
    ) : RecoveryGameResultPayload()

    data class RhythmTiles(
        val selectedSongId: String,
        val score: Int,
        val combo: Int,
        val lives: Int,
        val historyPersonalBest: Int,
        val historyPrevious: Int,
        val historyBestReactionMs: Int?,
        val historyBestCombo: Int,
        val resultScore: Int,
        val resultPreviousBest: Int,
        val resultPreviousScore: Int,
        val resultMaxCombo: Int,
        val resultHits: Int,
        val resultMisses: Int,
        val resultLoopsCompleted: Int,
        val resultGameOver: Boolean,
        val resultDurationSec: Int,
        val resultValidCompletion: Boolean,
    ) : RecoveryGameResultPayload()

    data class BlockCascade(
        val secondsPlayed: Int,
        val linesCleared: Int,
        val validMoves: Int,
        val completed: Boolean,
        val failed: Boolean,
        val failureReason: String?,
    ) : RecoveryGameResultPayload()

    data class Snake(
        val historyPersonalBest: Int,
        val historyPreviousScore: Int?,
        val resultScore: Int,
        val resultFruitsEaten: Int,
        val resultPreviousBest: Int,
        val resultPreviousScore: Int?,
        val resultDurationSec: Int,
        val resultElapsedDurationMillis: Long,
        val resultEndReasonName: String,
        val resultValidCompletion: Boolean,
    ) : RecoveryGameResultPayload()

    data class SkylineReset(
        val floorsBuilt: Int,
        val perfectCount: Int,
        val secondsPlayed: Int,
        val completed: Boolean,
        val failed: Boolean,
        val controlPointsBanked: Int?,
        val resultRecorded: Boolean,
        val perfectPointsBanked: Boolean,
    ) : RecoveryGameResultPayload()
}

/**
 * Serializable representation of ScoreSessionRecord.
 *
 * LocalDateTime and enum values are encoded as strings and reconstructed
 * strictly. Corrupt or unknown values return null rather than silently
 * producing a misleading record.
 */
data class ScoreSessionSnapshot(
    val id: Long,
    val gameTypeId: String,
    val score: Int,
    val startedAtIso: String,
    val completedAtIso: String,
    val durationSec: Int,
    val urgeBefore: Int?,
    val urgeAfter: Int?,
    val outcomeId: String,
    val validCompletion: Boolean,
) : Serializable {
    fun toRecordOrNull(): ScoreSessionRecord? =
        runCatching {
            val gameType =
                ScoreGameType.entries.firstOrNull {
                    it.id == gameTypeId
                } ?: return null

            val outcome =
                ScoreSessionOutcome.entries.firstOrNull {
                    it.id == outcomeId
                } ?: return null

            ScoreSessionRecord(
                id = id,
                gameType = gameType,
                score = score,
                startedAt = LocalDateTime.parse(startedAtIso),
                completedAt = LocalDateTime.parse(completedAtIso),
                durationSec = durationSec,
                urgeBefore = urgeBefore,
                urgeAfter = urgeAfter,
                outcome = outcome,
                validCompletion = validCompletion,
            )
        }.getOrNull()

    companion object {
        fun from(
            record: ScoreSessionRecord,
        ): ScoreSessionSnapshot =
            ScoreSessionSnapshot(
                id = record.id,
                gameTypeId = record.gameType.id,
                score = record.score,
                startedAtIso = record.startedAt.toString(),
                completedAtIso = record.completedAt.toString(),
                durationSec = record.durationSec,
                urgeBefore = record.urgeBefore,
                urgeAfter = record.urgeAfter,
                outcomeId = record.outcome.id,
                validCompletion = record.validCompletion,
            )
    }
}
