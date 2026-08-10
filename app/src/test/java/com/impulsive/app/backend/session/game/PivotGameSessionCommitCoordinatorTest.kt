package com.impulsive.app.backend.session.game

import com.impulsive.app.backend.domain.model.score.SafeExitRecord
import com.impulsive.app.backend.domain.model.score.SafeExitSource
import com.impulsive.app.backend.domain.model.score.ScoreGameType
import com.impulsive.app.backend.domain.model.score.ScoreSessionOutcome
import com.impulsive.app.backend.domain.model.score.ScoreSessionRecord
import com.impulsive.app.backend.session.progress.SafeExitRecordingResult
import java.time.LocalDateTime
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PivotGameSessionCommitCoordinatorTest {
    @Test
    fun walkAwayPersistsThenSchedulesThenAttemptsImmediateInsert() =
        runBlocking {
            val events =
                mutableListOf<String>()

            val result =
                coordinator(
                    events =
                        events,
                )
                    .commit(
                        record(),
                    )

            assertEquals(
                listOf(
                    "persist",
                    "schedule",
                    "immediate",
                ),
                events,
            )

            assertTrue(
                result
                    .reconciliationScheduled,
            )

            assertEquals(
                SafeExitRecordingResult
                    .Recorded(
                        safeExitRecord(),
                    ),
                result
                    .immediateSafeExitResult,
            )
        }

    @Test
    fun nonWalkAwayPersistsWithoutSchedulingOrSafeExitRecording() =
        runBlocking {
            val events =
                mutableListOf<String>()

            val result =
                coordinator(
                    events =
                        events,
                )
                    .commit(
                        record(
                            outcome =
                                ScoreSessionOutcome
                                    .Completed,
                        ),
                    )

            assertEquals(
                listOf(
                    "persist",
                ),
                events,
            )

            assertFalse(
                result
                    .reconciliationScheduled,
            )

            assertEquals(
                null,
                result
                    .immediateSafeExitResult,
            )
        }

    @Test
    fun cancellationDuringScorePersistenceStillRequestsDurableReconciliation() {
        val events =
            mutableListOf<String>()

        val coordinator =
            PivotGameSessionCommitCoordinator(
                scoreSessionWriter =
                    PivotGameScoreSessionWriter {
                        events +=
                            "persist"

                        throw CancellationException(
                            "view model cleared",
                        )
                    },
                immediateSafeExitRecorder =
                    PivotGameWalkAwayRecorder {
                        events +=
                            "immediate"

                        SafeExitRecordingResult
                            .Recorded(
                                safeExitRecord(),
                            )
                    },
                reconciliationScheduler =
                    PivotGameSafeExitReconciliationScheduler {
                        events +=
                            "schedule"
                        true
                    },
            )

        assertThrows(
            CancellationException::class.java,
        ) {
            runBlocking {
                coordinator.commit(
                    record(),
                )
            }
        }

        assertEquals(
            listOf(
                "persist",
                "schedule",
            ),
            events,
        )
    }

    @Test
    fun ordinaryScorePersistenceFailureStillRequestsReconciliationButDoesNotInsertImmediately() {
        val events =
            mutableListOf<String>()

        val coordinator =
            PivotGameSessionCommitCoordinator(
                scoreSessionWriter =
                    PivotGameScoreSessionWriter {
                        events +=
                            "persist"

                        throw IllegalStateException(
                            "score store unavailable",
                        )
                    },
                immediateSafeExitRecorder =
                    PivotGameWalkAwayRecorder {
                        events +=
                            "immediate"

                        SafeExitRecordingResult
                            .Recorded(
                                safeExitRecord(),
                            )
                    },
                reconciliationScheduler =
                    PivotGameSafeExitReconciliationScheduler {
                        events +=
                            "schedule"
                        true
                    },
            )

        assertThrows(
            IllegalStateException::class.java,
        ) {
            runBlocking {
                coordinator.commit(
                    record(),
                )
            }
        }

        assertEquals(
            listOf(
                "persist",
                "schedule",
            ),
            events,
        )
    }

    @Test
    fun schedulerFailureDoesNotPreventImmediateInsertionAfterSuccessfulScoreWrite() =
        runBlocking {
            val events =
                mutableListOf<String>()

            val coordinator =
                coordinator(
                    events =
                        events,
                    schedulerResult =
                        false,
                )

            val result =
                coordinator.commit(
                    record(),
                )

            assertFalse(
                result
                    .reconciliationScheduled,
            )

            assertEquals(
                SafeExitRecordingResult
                    .Recorded(
                        safeExitRecord(),
                    ),
                result
                    .immediateSafeExitResult,
            )

            assertEquals(
                listOf(
                    "persist",
                    "schedule",
                    "immediate",
                ),
                events,
            )
        }

    @Test
    fun immediateRetryableFailureRemainsRecoverableBecauseDurableWorkWasAlreadyScheduled() =
        runBlocking {
            val events =
                mutableListOf<String>()

            val coordinator =
                coordinator(
                    events =
                        events,
                    immediateResult =
                        SafeExitRecordingResult
                            .RetryableFailure,
                )

            val result =
                coordinator.commit(
                    record(),
                )

            assertTrue(
                result
                    .reconciliationScheduled,
            )

            assertEquals(
                SafeExitRecordingResult
                    .RetryableFailure,
                result
                    .immediateSafeExitResult,
            )

            assertEquals(
                listOf(
                    "persist",
                    "schedule",
                    "immediate",
                ),
                events,
            )
        }

    private fun coordinator(
        events:
            MutableList<String>,
        schedulerResult:
            Boolean = true,
        immediateResult:
            SafeExitRecordingResult =
                SafeExitRecordingResult
                    .Recorded(
                        safeExitRecord(),
                    ),
    ): PivotGameSessionCommitCoordinator {
        return PivotGameSessionCommitCoordinator(
            scoreSessionWriter =
                PivotGameScoreSessionWriter {
                    events +=
                        "persist"
                },
            immediateSafeExitRecorder =
                PivotGameWalkAwayRecorder {
                    events +=
                        "immediate"

                    immediateResult
                },
            reconciliationScheduler =
                PivotGameSafeExitReconciliationScheduler {
                    events +=
                        "schedule"

                    schedulerResult
                },
        )
    }

    private fun record(
        outcome:
            ScoreSessionOutcome =
                ScoreSessionOutcome
                    .WalkedAway,
    ): ScoreSessionRecord {
        return ScoreSessionRecord(
            id =
                8_001L,
            gameType =
                ScoreGameType.ReflexOverride,
            score =
                500,
            startedAt =
                CompletedAt.minusSeconds(
                    60,
                ),
            completedAt =
                CompletedAt,
            durationSec =
                60,
            outcome =
                outcome,
            validCompletion =
                true,
        )
    }

    private fun safeExitRecord():
        SafeExitRecord {
        return SafeExitRecord(
            sourceKey =
                "pivot_game:REFLEX_OVERRIDE:8001",
            source =
                SafeExitSource.PivotGame,
            sourceId =
                "REFLEX_OVERRIDE:8001",
            completedAt =
                CompletedAt,
        )
    }

    private companion object {
        val CompletedAt:
            LocalDateTime =
            LocalDateTime.of(
                2026,
                8,
                3,
                9,
                0,
            )
    }
}