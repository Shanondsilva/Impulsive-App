package com.impulsive.app.backend.session.progress

import com.impulsive.app.backend.domain.model.score.SafeExitProgressSnapshot
import com.impulsive.app.backend.domain.model.score.ScoreGameType
import com.impulsive.app.backend.domain.model.score.ScoreRange
import com.impulsive.app.backend.domain.model.score.ScoreSessionOutcome
import com.impulsive.app.backend.domain.model.score.ScoreSessionRecord
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreProgressSignalsTest {
    @Test
    fun sessionLedgerAndRangeChangesRefreshSignalsWithFreshNow() =
        runBlocking {
            val selectedRange =
                MutableStateFlow(
                    ScoreRange.Week,
                )
            val sessions =
                MutableStateFlow<List<ScoreSessionRecord>>(
                    emptyList(),
                )
            val ledgerRevision =
                MutableStateFlow(
                    0L,
                )
            var currentNow =
                LocalDateTime.of(
                    2026,
                    8,
                    3,
                    10,
                    0,
                )
            var nowProviderCalls =
                0
            val callbackPivotKeys =
                mutableListOf<Set<String>>()
            val emissions =
                Channel<ScoreAndSafeExitSignals>(
                    Channel.UNLIMITED,
                )

            val job =
                launch(
                    start = CoroutineStart.UNDISPATCHED,
                ) {
                    observeScoreAndSafeExitSignals(
                        selectedRange =
                            selectedRange,
                        sessions =
                            sessions,
                        ledgerChanges =
                            ledgerRevision.map { Unit },
                        observeSafeExitProgress = { _, _, pivotKeys ->
                            callbackPivotKeys += pivotKeys
                            flowOf(
                                SafeExitProgressSnapshot(
                                    ledgerSafeExitCount = pivotKeys.size,
                                    persistedPivotSourceKeys = pivotKeys,
                                ),
                            )
                        },
                        nowProvider = {
                            nowProviderCalls += 1
                            currentNow
                        },
                    ).collect { signal ->
                        emissions.send(
                            signal,
                        )
                    }
                }

            try {
                val initial =
                    withTimeout(5_000) {
                        emissions.receive()
                    }

                assertEquals(
                    LocalDateTime.of(2026, 8, 3, 10, 0),
                    initial.now,
                )

                currentNow =
                    LocalDateTime.of(
                        2026,
                        8,
                        3,
                        10,
                        5,
                    )
                val session =
                    record(
                        id = 7001L,
                        completedAt = currentNow.minusMinutes(1),
                    )
                sessions.value =
                    listOf(
                        session,
                    )

                val sessionRefresh =
                    withTimeout(5_000) {
                        emissions.receive()
                    }

                assertEquals(
                    LocalDateTime.of(2026, 8, 3, 10, 5),
                    sessionRefresh.now,
                )
                assertEquals(
                    listOf(session),
                    sessionRefresh.sessions,
                )
                assertEquals(
                    setOf(
                        "pivot_game:REFLEX_OVERRIDE:7001",
                    ),
                    callbackPivotKeys.last(),
                )

                currentNow =
                    LocalDateTime.of(
                        2026,
                        8,
                        3,
                        10,
                        10,
                    )
                ledgerRevision.value = 1L

                val ledgerRefresh =
                    withTimeout(5_000) {
                        emissions.receive()
                    }

                assertEquals(
                    LocalDateTime.of(2026, 8, 3, 10, 10),
                    ledgerRefresh.now,
                )

                currentNow =
                    LocalDateTime.of(
                        2026,
                        8,
                        3,
                        10,
                        15,
                    )
                selectedRange.value =
                    ScoreRange.Month

                val rangeRefresh =
                    withTimeout(5_000) {
                        emissions.receive()
                    }

                assertEquals(
                    LocalDateTime.of(2026, 8, 3, 10, 15),
                    rangeRefresh.now,
                )
                assertEquals(
                    ScoreRange.Month,
                    rangeRefresh.selectedRange,
                )
                assertTrue(
                    nowProviderCalls >= 4,
                )
                assertTrue(
                    callbackPivotKeys.size >= 4,
                )
            } finally {
                job.cancel()
                job.join()
                emissions.close()
            }
        }

    private fun record(
        id: Long,
        completedAt: LocalDateTime,
    ): ScoreSessionRecord {
        return ScoreSessionRecord(
            id = id,
            gameType = ScoreGameType.ReflexOverride,
            score = 0,
            startedAt = completedAt.minusMinutes(2),
            completedAt = completedAt,
            durationSec = 60,
            outcome = ScoreSessionOutcome.WalkedAway,
            validCompletion = true,
        )
    }
}