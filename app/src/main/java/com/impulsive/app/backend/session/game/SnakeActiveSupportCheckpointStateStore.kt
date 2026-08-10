package com.impulsive.app.backend.session.game

import androidx.lifecycle.SavedStateHandle
import com.impulsive.app.backend.domain.game.RecoveryGameLaunchContext
import com.impulsive.app.backend.domain.model.score.ScoreGameType
import java.io.Serializable

/**
 * Support time already consumed by an *active* Snake round.
 *
 * The Snake board is deliberately transient across process death — the player
 * gets a fresh Ready board — but time taken from an authoritative support-cycle
 * allocation must not be handed back. Only scalar identity and a duration are
 * stored here; no gameplay state of any kind belongs in this object.
 */
internal data class SnakeActiveSupportCheckpoint(
    val version: Int,
    val cycleId: String,
    val decisionId: String,
    val gameTypeId: String,
    val consumedSupportMillis: Long,
) : Serializable

internal class SnakeActiveSupportCheckpointStateStore(
    private val savedStateHandle: SavedStateHandle,
) {
    fun save(
        launch: RecoveryGameLaunchContext.SupportCycle,
        consumedSupportMillis: Long,
    ) {
        require(launch.gameType == ScoreGameType.Snake) {
            "checkpoint is Snake-only"
        }
        require(consumedSupportMillis >= 0L) {
            "consumedSupportMillis must not be negative"
        }

        savedStateHandle[CheckpointKey] = SnakeActiveSupportCheckpoint(
            version = CurrentVersion,
            cycleId = launch.cycleId,
            decisionId = launch.decisionId,
            gameTypeId = ScoreGameType.Snake.id,
            consumedSupportMillis = consumedSupportMillis,
        )
    }

    /** Consumed support time, or null when nothing trustworthy is stored. */
    fun restore(launchContext: RecoveryGameLaunchContext): Long? {
        val launch = launchContext as? RecoveryGameLaunchContext.SupportCycle ?: return null

        if (launch.gameType != ScoreGameType.Snake) return null

        val checkpoint =
            savedStateHandle.get<SnakeActiveSupportCheckpoint>(CheckpointKey) ?: return null

        return checkpoint
            .takeIf {
                it.version == CurrentVersion &&
                    it.cycleId == launch.cycleId &&
                    it.decisionId == launch.decisionId &&
                    it.gameTypeId == ScoreGameType.Snake.id &&
                    it.consumedSupportMillis >= 0L
            }
            ?.consumedSupportMillis
    }

    fun clear() {
        savedStateHandle.remove<SnakeActiveSupportCheckpoint>(CheckpointKey)
    }

    companion object {
        const val CurrentVersion: Int = 1
        const val CheckpointKey: String = "snakeActiveSupportCheckpoint"
    }
}
