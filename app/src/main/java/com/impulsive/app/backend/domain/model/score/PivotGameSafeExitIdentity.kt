package com.impulsive.app.backend.domain.model.score

object PivotGameSafeExitIdentity {
    private val SupportedGameTypes =
        setOf(
            ScoreGameType.ReflexOverride,
            ScoreGameType.RhythmTiles,
            ScoreGameType.Snake,
        )

    fun sourceId(
        record:
            ScoreSessionRecord,
    ): String? {
        if (
            record.outcome !=
                ScoreSessionOutcome.WalkedAway ||
            record.gameType !in
                SupportedGameTypes
        ) {
            return null
        }

        return "${record.gameType.id}:${record.id}"
    }

    fun sourceKey(
        record:
            ScoreSessionRecord,
    ): String? {
        val sourceId =
            sourceId(
                record,
            )
                ?: return null

        return "${SafeExitSource.PivotGame.storageValue}:$sourceId"
    }

    fun isSupported(
        gameType:
            ScoreGameType,
    ): Boolean {
        return gameType in
            SupportedGameTypes
    }
}