package com.impulsive.app.backend.domain.game

import com.impulsive.app.backend.domain.model.score.ScoreGameType

enum class RecoveryGameId(
    val scoreGameType: ScoreGameType,
) {
    ReflexOverride(ScoreGameType.ReflexOverride),
    BlockCascade(ScoreGameType.BlockCascade),
    SkylineReset(ScoreGameType.SkylineReset),
}

data class RecoveryGameDefinition(
    val id: RecoveryGameId,
    val title: String,
    val description: String,
    val durationLabel: String,
    val chipLabel: String,
)

object RecoveryGameCatalog {
    val games: List<RecoveryGameDefinition> = listOf(
        RecoveryGameDefinition(
            id = RecoveryGameId.ReflexOverride,
            title = "Reflex Override",
            description = "Break autopilot with a fast control challenge.",
            durationLabel = "60 sec",
            chipLabel = "Fast control",
        ),
        RecoveryGameDefinition(
            id = RecoveryGameId.BlockCascade,
            title = "Block Cascade",
            description = "A time-boxed block round with a clear finish state.",
            durationLabel = "90 sec",
            chipLabel = "Visual focus",
        ),
        RecoveryGameDefinition(
            id = RecoveryGameId.SkylineReset,
            title = "Skyline Reset",
            description = "Stack a calm skyscraper, floor by floor, into the night.",
            durationLabel = "Open-ended",
            chipLabel = "Steady focus",
        ),
    )

    val scoreGameTypes: List<ScoreGameType>
        get() = games.map { it.id.scoreGameType }

    fun definitionFor(gameType: ScoreGameType): RecoveryGameDefinition? =
        games.firstOrNull { it.id.scoreGameType == gameType }

    fun displayNameFor(gameType: ScoreGameType): String =
        definitionFor(gameType)?.title ?: gameType.displayName
}
