package com.impulsive.app.backend.domain.game

import com.impulsive.app.backend.domain.model.score.ScoreGameType

sealed interface RecoveryGameLaunchContext {
    data object Standalone : RecoveryGameLaunchContext

    data class SupportCycle(
        val cycleId: String,
        val decisionId: String,
        val gameType: ScoreGameType,
        val maxDurationMillis: Long,
    ) : RecoveryGameLaunchContext {
        init {
            require(cycleId.isNotBlank())
            require(decisionId.isNotBlank())
            require(gameType != ScoreGameType.Unknown)
            require(maxDurationMillis > 0L)
        }
    }
}

fun RecoveryGameLaunchContext.boundedDurationMillis(
    standaloneDefaultDurationMillis: Long,
): Long {
    require(standaloneDefaultDurationMillis > 0L)
    return when (this) {
        RecoveryGameLaunchContext.Standalone -> standaloneDefaultDurationMillis
        is RecoveryGameLaunchContext.SupportCycle ->
            minOf(standaloneDefaultDurationMillis, maxDurationMillis)
    }
}
