package com.impulsive.app.backend.domain.model.score

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PivotGameSafeExitIdentityTest {
    @Test
    fun supportedWalkAwayBuildsStableSourceIdAndKey() {
        val record =
            record(
                id = 42L,
                gameType = ScoreGameType.ReflexOverride,
                outcome = ScoreSessionOutcome.WalkedAway,
            )

        assertEquals(
            "REFLEX_OVERRIDE:42",
            PivotGameSafeExitIdentity.sourceId(record),
        )
        assertEquals(
            "pivot_game:REFLEX_OVERRIDE:42",
            PivotGameSafeExitIdentity.sourceKey(record),
        )
    }

    @Test
    fun identityDoesNotDependOnValidCompletion() {
        val record =
            record(
                validCompletion = false,
            )

        assertEquals(
            "RHYTHM_TILES:1",
            PivotGameSafeExitIdentity.sourceId(record),
        )
    }

    @Test
    fun unsupportedOrNonWalkAwayRecordsDoNotProduceKeys() {
        listOf(
            ScoreGameType.BlockCascade,
            ScoreGameType.SkylineReset,
            ScoreGameType.FocusSession,
            ScoreGameType.Unknown,
        ).forEach { gameType ->
            assertNull(
                PivotGameSafeExitIdentity
                    .sourceKey(
                        record(
                            gameType = gameType,
                        ),
                    ),
            )
        }

        assertNull(
            PivotGameSafeExitIdentity
                .sourceKey(
                    record(
                        outcome = ScoreSessionOutcome.Completed,
                    ),
                ),
        )
    }
    @Test
    fun supportedTypesAreExplicit() {
        assertTrue(
            PivotGameSafeExitIdentity
                .isSupported(
                    ScoreGameType.ReflexOverride,
                ),
        )
        assertTrue(
            PivotGameSafeExitIdentity
                .isSupported(
                    ScoreGameType.RhythmTiles,
                ),
        )
        assertFalse(
            PivotGameSafeExitIdentity
                .isSupported(
                    ScoreGameType.FocusSession,
                ),
        )
    }

    private fun record(
        id: Long = 1L,
        gameType: ScoreGameType = ScoreGameType.RhythmTiles,
        outcome: ScoreSessionOutcome = ScoreSessionOutcome.WalkedAway,
        validCompletion: Boolean = true,
    ): ScoreSessionRecord {
        return ScoreSessionRecord(
            id = id,
            gameType = gameType,
            score = 0,
            startedAt = Now.minusMinutes(2),
            completedAt = Now.minusMinutes(1),
            durationSec = 60,
            outcome = outcome,
            validCompletion = validCompletion,
        )
    }

    private companion object {
        val Now: LocalDateTime =
            LocalDateTime.of(2026, 8, 3, 12, 0)
    }
}