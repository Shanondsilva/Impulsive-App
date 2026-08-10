package com.impulsive.app.backend.session.adaptive

import com.impulsive.app.backend.domain.model.adaptive.AdaptiveDecision
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.score.SafeExitRecord
import com.impulsive.app.backend.domain.model.score.SafeExitSource
import com.impulsive.app.backend.session.progress.SafeExitRecordingResult
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class MomentPlanSafeExitReconciliationTest {
    @Test
    fun missingDecisionReturnsMissingDecision() =
        runBlocking {
            val recorder =
                FakeWalkAwayRecorder(
                    SafeExitRecordingResult
                        .Recorded(
                            safeExitRecord(),
                        ),
                )

            val result =
                MomentPlanSafeExitReconciler(
                    decisions = FakeDecisionRepository(),
                    recorder = recorder,
                )
                    .reconcile(
                        DecisionId,
                    )

            assertEquals(
                MomentPlanSafeExitReconciliationResult
                    .MissingDecision,
                result,
            )

            assertEquals(
                emptyList<String>(),
                recorder.decisionIds,
            )
        }

    @Test
    fun incompleteEligibleDecisionReturnsNotReady() =
        runBlocking {
            val repository =
                repositoryWith(
                    momentPlanDecision(
                        completed = null,
                    ),
                )

            val result =
                MomentPlanSafeExitReconciler(
                    decisions = repository,
                    recorder = FakeWalkAwayRecorder(
                        SafeExitRecordingResult
                            .Recorded(
                                safeExitRecord(),
                            ),
                    ),
                )
                    .reconcile(
                        DecisionId,
                    )

            assertEquals(
                MomentPlanSafeExitReconciliationResult.NotReady,
                result,
            )
        }

    @Test
    fun dismissedDecisionReturnsIneligible() =
        runBlocking {
            val result =
                reconcilerFor(
                    momentPlanDecision(
                        dismissed = CompletedMillis + 1_000L,
                    ),
                )
                    .reconcile(
                        DecisionId,
                    )

            assertEquals(
                MomentPlanSafeExitReconciliationResult.Ineligible,
                result,
            )
        }

    @Test
    fun completedNonMomentPlanDecisionReturnsIneligible() =
        runBlocking {
            val result =
                reconcilerFor(
                    momentPlanDecision(
                        actual = InterventionFamily.ShortPause,
                    ),
                )
                    .reconcile(
                        DecisionId,
                    )

            assertEquals(
                MomentPlanSafeExitReconciliationResult.Ineligible,
                result,
            )
        }

    @Test
    fun completedMomentPlanRoutesToRecorder() =
        runBlocking {
            val recorder =
                FakeWalkAwayRecorder(
                    SafeExitRecordingResult
                        .Recorded(
                            safeExitRecord(),
                        ),
                )

            val result =
                MomentPlanSafeExitReconciler(
                    decisions = repositoryWith(momentPlanDecision()),
                    recorder = recorder,
                )
                    .reconcile(
                        DecisionId,
                    )

            assertEquals(
                MomentPlanSafeExitReconciliationResult.Recorded,
                result,
            )

            assertEquals(
                listOf(DecisionId),
                recorder.decisionIds,
            )
        }

    @Test
    fun recordedMapsToRecorded() =
        runBlocking {
            val result =
                reconcilerFor(
                    momentPlanDecision(),
                    SafeExitRecordingResult
                        .Recorded(
                            safeExitRecord(),
                        ),
                )
                    .reconcile(
                        DecisionId,
                    )

            assertEquals(
                MomentPlanSafeExitReconciliationResult.Recorded,
                result,
            )
        }

    @Test
    fun duplicateMapsToDuplicate() =
        runBlocking {
            val result =
                reconcilerFor(
                    momentPlanDecision(),
                    SafeExitRecordingResult
                        .Duplicate(
                            safeExitRecord(),
                        ),
                )
                    .reconcile(
                        DecisionId,
                    )

            assertEquals(
                MomentPlanSafeExitReconciliationResult.Duplicate,
                result,
            )
        }

    @Test
    fun retryableFailureMapsToRetryableFailure() =
        runBlocking {
            val result =
                reconcilerFor(
                    momentPlanDecision(),
                    SafeExitRecordingResult
                        .RetryableFailure,
                )
                    .reconcile(
                        DecisionId,
                    )

            assertEquals(
                MomentPlanSafeExitReconciliationResult.RetryableFailure,
                result,
            )
        }

    @Test
    fun recorderReceivesOnlyRequestedDecisionId() =
        runBlocking {
            val recorder =
                FakeWalkAwayRecorder(
                    SafeExitRecordingResult
                        .Recorded(
                            safeExitRecord(
                                RequestedDecisionId,
                            ),
                        ),
                )

            MomentPlanSafeExitReconciler(
                decisions = repositoryWith(
                    momentPlanDecision(),
                    momentPlanDecision(
                        id = RequestedDecisionId,
                    ),
                ),
                recorder = recorder,
            )
                .reconcile(
                    RequestedDecisionId,
                )

            assertEquals(
                listOf(RequestedDecisionId),
                recorder.decisionIds,
            )
        }

    private fun reconcilerFor(
        decision: AdaptiveDecision,
        result: SafeExitRecordingResult =
            SafeExitRecordingResult.Recorded(
                safeExitRecord(),
            ),
    ): MomentPlanSafeExitReconciler {
        return MomentPlanSafeExitReconciler(
            decisions = repositoryWith(decision),
            recorder = FakeWalkAwayRecorder(result),
        )
    }

    private fun repositoryWith(
        vararg decisions: AdaptiveDecision,
    ): FakeDecisionRepository {
        return FakeDecisionRepository().apply {
            stored += decisions
        }
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
        eligible = setOf(InterventionFamily.MomentPlan, InterventionFamily.ShortPause),
        assigned = InterventionFamily.MomentPlan,
        actual = actual,
        planId = planId,
        presented = StartedMillis - 1_000L,
        started = started,
        completed = completed,
        dismissed = dismissed,
    )

    private fun safeExitRecord(
        id: String = DecisionId,
    ): SafeExitRecord {
        return SafeExitRecord(
            sourceKey =
                "moment_plan:$id",
            source =
                SafeExitSource.MomentPlan,
            sourceId =
                id,
            completedAt =
                CompletedAt,
        )
    }

    private class FakeWalkAwayRecorder(
        private val result:
            SafeExitRecordingResult,
    ) : MomentPlanWalkAwayRecorder {
        val decisionIds =
            mutableListOf<String>()

        override suspend fun recordExplicitWalkAway(
            decision: AdaptiveDecision,
        ): SafeExitRecordingResult {
            decisionIds +=
                decision.decisionId

            return result
        }
    }

    private companion object {
        const val DecisionId =
            "decision-moment-plan-1"
        const val RequestedDecisionId =
            "decision-moment-plan-2"
        const val PlanId =
            "plan-1"
        const val StartedMillis =
            1_775_216_000_000L
        const val CompletedMillis =
            1_775_216_120_000L
        val CompletedAt: LocalDateTime =
            LocalDateTime.of(
                2026,
                4,
                1,
                12,
                2,
            )
    }
}