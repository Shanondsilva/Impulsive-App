package com.impulsive.app.backend.session.adaptive

import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.score.SafeExitCandidate
import com.impulsive.app.backend.domain.model.score.SafeExitRecord
import com.impulsive.app.backend.domain.model.score.SafeExitSource
import com.impulsive.app.backend.session.progress.SafeExitCandidateRecorder
import com.impulsive.app.backend.session.progress.SafeExitRecordingResult
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentPlanSafeExitRecorderTest {
    @Test
    fun completedMomentPlanBuildsExactCanonicalCandidate() =
        runBlocking {
            val recorder =
                FakeRecorder(
                    mutableListOf(
                        SafeExitRecordingResult
                            .Recorded(
                                safeExitRecord(),
                            ),
                    ),
                )

            MomentPlanSafeExitRecorder(
                recorder,
                Utc,
            )
                .recordExplicitWalkAway(
                    momentPlanDecision(),
                )

            val candidate =
                recorder
                    .candidates
                    .single()

            assertEquals(
                SafeExitSource.MomentPlan,
                candidate.source,
            )

            assertEquals(
                DecisionId,
                candidate.sourceId,
            )

            assertEquals(
                com.impulsive.app.backend.domain.model.score.SafeExitAction.WalkAway,
                candidate.action,
            )

            assertTrue(
                candidate.validCompletion,
            )

            assertEquals(
                CompletedAt,
                candidate.completedAt,
            )
        }

    @Test
    fun dismissedMomentPlanReturnsNull() {
        assertNull(
            MomentPlanSafeExitCandidateFactory
                .createOrNull(
                    momentPlanDecision(
                        dismissed =
                            CompletedMillis + 1_000L,
                    ),
                    Utc,
                ),
        )
    }

    @Test
    fun startedButIncompleteMomentPlanReturnsNull() {
        assertNull(
            MomentPlanSafeExitCandidateFactory
                .createOrNull(
                    momentPlanDecision(
                        completed =
                            null,
                    ),
                    Utc,
                ),
        )
    }

    @Test
    fun completedShortPauseReturnsNull() {
        assertNull(
            MomentPlanSafeExitCandidateFactory
                .createOrNull(
                    momentPlanDecision(
                        actual =
                            InterventionFamily.ShortPause,
                    ),
                    Utc,
                ),
        )
    }

    @Test
    fun completedMomentPlanWithoutMomentPlanIdReturnsNull() {
        assertNull(
            MomentPlanSafeExitCandidateFactory
                .createOrNull(
                    momentPlanDecision(
                        planId =
                            null,
                    ),
                    Utc,
                ),
        )
    }

    @Test
    fun blankDecisionIdReturnsNull() {
        assertNull(
            MomentPlanSafeExitCandidateFactory
                .createOrNull(
                    momentPlanDecision(
                        id =
                            " ",
                    ),
                    Utc,
                ),
        )
    }

    @Test
    fun retryableFailureIsRetriedExactlyOnce() =
        runBlocking {
            val recorder =
                FakeRecorder(
                    mutableListOf(
                        SafeExitRecordingResult
                            .RetryableFailure,
                        SafeExitRecordingResult
                            .Recorded(
                                safeExitRecord(),
                            ),
                    ),
                )

            val result =
                MomentPlanSafeExitRecorder(
                    recorder,
                    Utc,
                )
                    .recordExplicitWalkAway(
                        momentPlanDecision(),
                    )

            assertEquals(
                SafeExitRecordingResult
                    .Recorded(
                        safeExitRecord(),
                    ),
                result,
            )

            assertEquals(
                2,
                recorder
                    .candidates
                    .size,
            )
        }

    @Test
    fun duplicateIsNotRetried() =
        runBlocking {
            val recorder =
                FakeRecorder(
                    mutableListOf(
                        SafeExitRecordingResult
                            .Duplicate(
                                safeExitRecord(),
                            ),
                    ),
                )

            val result =
                MomentPlanSafeExitRecorder(
                    recorder,
                    Utc,
                )
                    .recordExplicitWalkAway(
                        momentPlanDecision(),
                    )

            assertEquals(
                SafeExitRecordingResult
                    .Duplicate(
                        safeExitRecord(),
                    ),
                result,
            )

            assertEquals(
                1,
                recorder
                    .candidates
                    .size,
            )
        }

    private fun momentPlanDecision(
        id: String = DecisionId,
        actual: InterventionFamily? = InterventionFamily.MomentPlan,
        planId: String? = PlanId,
        started: Long? = StartedMillis,
        completed: Long? = CompletedMillis,
        dismissed: Long? = null,
    ) = decision(
        id = id,
        eligible = setOf(InterventionFamily.MomentPlan),
        assigned = InterventionFamily.MomentPlan,
        actual = actual,
        planId = planId,
        presented = StartedMillis - 1_000L,
        started = started,
        completed = completed,
        dismissed = dismissed,
    )

    private fun safeExitRecord(): SafeExitRecord {
        return SafeExitRecord(
            sourceKey =
                "moment_plan:$DecisionId",
            source =
                SafeExitSource.MomentPlan,
            sourceId =
                DecisionId,
            completedAt =
                CompletedAt,
        )
    }

    private class FakeRecorder(
        private val results:
            MutableList<SafeExitRecordingResult>,
    ) : SafeExitCandidateRecorder {
        val candidates =
            mutableListOf<SafeExitCandidate>()

        override suspend fun record(
            candidate: SafeExitCandidate,
        ): SafeExitRecordingResult {
            candidates +=
                candidate

            return results.removeFirst()
        }
    }

    private companion object {
        const val DecisionId =
            "decision-moment-plan-1"
        const val PlanId =
            "plan-1"
        const val StartedMillis =
            1_775_216_000_000L
        const val CompletedMillis =
            1_775_216_120_000L
        val Utc: ZoneId =
            ZoneId.of("UTC")
        val CompletedAt: LocalDateTime =
            LocalDateTime.ofInstant(
                Instant.ofEpochMilli(
                    CompletedMillis,
                ),
                Utc,
            )
    }
}