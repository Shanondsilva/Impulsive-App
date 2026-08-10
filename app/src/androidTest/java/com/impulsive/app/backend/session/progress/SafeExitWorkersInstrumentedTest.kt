package com.impulsive.app.backend.session.progress

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.impulsive.app.backend.data.local.database.AppDatabase
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveAssignment
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveDecision
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveReasonCode
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSourceKind
import com.impulsive.app.backend.domain.model.adaptive.AssignmentMode
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentIntensity
import com.impulsive.app.backend.domain.model.tasks.ResetReadSessionRecord
import com.impulsive.app.backend.session.adaptive.AdaptivePhase4Dependencies
import com.impulsive.app.backend.session.adaptive.MomentPlanSafeExitReconciliationWorker
import com.impulsive.app.backend.session.adaptive.MomentPlanSafeExitWork
import com.impulsive.app.backend.session.tasks.ResetReadSafeExitReconciliationWorker
import com.impulsive.app.backend.session.tasks.ResetReadSafeExitWorkDataCodec
import java.time.LocalDateTime
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SafeExitWorkersInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase

    @Before
    fun setUp() =
        runBlocking {
            context =
                ApplicationProvider
                    .getApplicationContext()
            database =
                AppDatabase
                    .getInstance(
                        context,
                    )
            database
                .safeExitDao()
                .clearAllForRestore()
            AdaptivePhase4Dependencies
                .decisions(
                    context,
                )
                .clearLearningHistory()
        }

    @After
    fun tearDown() =
        runBlocking {
            database
                .safeExitDao()
                .clearAllForRestore()
            AdaptivePhase4Dependencies
                .decisions(
                    context,
                )
                .clearLearningHistory()
        }

    @Test
    fun resetReadingWorkerRecordsSelfContainedRequestWithoutSourceDataStore() =
        runBlocking {
            val sessionId =
                System.currentTimeMillis()
                    .coerceAtLeast(
                        1L,
                    )
            val data =
                ResetReadSafeExitWorkDataCodec
                    .encode(
                        resetReadSession(
                            id = sessionId,
                        ),
                    )
            val sourceKey =
                "reset_reading:$sessionId"

            val workerOne =
                resetReadWorker(
                    data,
                )

            assertEquals(
                ListenableWorker.Result.success(),
                workerOne.doWork(),
            )
            assertNotNull(
                database
                    .safeExitDao()
                    .getBySourceKey(
                        sourceKey,
                    ),
            )

            val workerTwo =
                resetReadWorker(
                    data,
                )

            assertEquals(
                ListenableWorker.Result.success(),
                workerTwo.doWork(),
            )
            assertEquals(
                1,
                database
                    .safeExitDao()
                    .count(),
            )
        }

    @Test
    fun resetReadingWorkerRejectsMalformedInput() =
        runBlocking {
            val worker =
                resetReadWorker(
                    Data.EMPTY,
                )

            assertEquals(
                ListenableWorker.Result.failure(),
                worker.doWork(),
            )
            assertEquals(
                0,
                database
                    .safeExitDao()
                    .count(),
            )
        }

    @Test
    fun momentPlanWorkerRecordsCompletedDecisionIdempotently() =
        runBlocking {
            val decisionId =
                UUID
                    .randomUUID()
                    .toString()
            val decision =
                completedMomentPlanDecision(
                    decisionId = decisionId,
                )
            assertTrue(
                AdaptivePhase4Dependencies
                    .decisions(
                        context,
                    )
                    .insertOnce(
                        decision,
                    ),
            )
            val data =
                workDataOf(
                    MomentPlanSafeExitWork.DecisionIdKey to
                        decisionId,
                )
            val sourceKey =
                "moment_plan:$decisionId"

            val workerOne =
                momentPlanWorker(
                    data,
                )

            assertEquals(
                ListenableWorker.Result.success(),
                workerOne.doWork(),
            )
            assertNotNull(
                database
                    .safeExitDao()
                    .getBySourceKey(
                        sourceKey,
                    ),
            )

            val workerTwo =
                momentPlanWorker(
                    data,
                )

            assertEquals(
                ListenableWorker.Result.success(),
                workerTwo.doWork(),
            )
            assertEquals(
                1,
                database
                    .safeExitDao()
                    .count(),
            )
        }

    @Test
    fun momentPlanWorkerRejectsBlankInput() =
        runBlocking {
            val worker =
                momentPlanWorker(
                    workDataOf(
                        MomentPlanSafeExitWork.DecisionIdKey to
                            "   ",
                    ),
                )

            assertEquals(
                ListenableWorker.Result.failure(),
                worker.doWork(),
            )
        }

    private fun resetReadWorker(
        data: Data,
    ): ResetReadSafeExitReconciliationWorker {
        return TestListenableWorkerBuilder<
            ResetReadSafeExitReconciliationWorker
        >(
            context,
        )
            .setInputData(
                data,
            )
            .build()
    }

    private fun momentPlanWorker(
        data: Data,
    ): MomentPlanSafeExitReconciliationWorker {
        return TestListenableWorkerBuilder<
            MomentPlanSafeExitReconciliationWorker
        >(
            context,
        )
            .setInputData(
                data,
            )
            .build()
    }

    private fun resetReadSession(
        id: Long,
    ): ResetReadSessionRecord {
        val completedAt =
            LocalDateTime.of(
                2026,
                8,
                3,
                12,
                0,
            )
        return ResetReadSessionRecord(
            id = id,
            articleId = "worker-reset-read",
            articleTitle = "Worker reset read",
            startedAt = completedAt.minusMinutes(2),
            completedAt = completedAt,
            selectedDurationSeconds = 90,
            requiredDurationSeconds = 90,
            secondsSpent = 90,
            selectedOptionIndex = 0,
            validCompletion = true,
            answerText = "Done",
        )
    }

    private fun completedMomentPlanDecision(
        decisionId: String,
    ): AdaptiveDecision {
        val completedAtMillis =
            1_722_685_600_000L

        return AdaptiveDecision(
            decisionId = decisionId,
            protectionIncidentToken =
                "worker-$decisionId",
            sourceKind =
                AdaptiveSourceKind.ExplicitUserSupport,
            createdAtMillis =
                completedAtMillis - 2_000L,
            momentWindowStartedAtMillis =
                completedAtMillis - 2_000L,
            momentCue =
                null,
            baselineUrgeRating =
                5,
            assignment =
                AdaptiveAssignment(
                    momentIntensity =
                        MomentIntensity.FirstAttempt,
                    assignmentMode =
                        AssignmentMode.UserChosen,
                    eligibleInterventions =
                        setOf(
                            InterventionFamily.MomentPlan,
                        ),
                    assignedSuggestion =
                        InterventionFamily.MomentPlan,
                    selectionProbability =
                        null,
                    reasonCode =
                        AdaptiveReasonCode.UserOverride,
                    momentPlanId =
                        "plan-$decisionId",
                    momentPlanUpdatedAtMillis =
                        completedAtMillis - 3_000L,
                    actualIntervention =
                        InterventionFamily.MomentPlan,
                    userOverrodeSuggestion =
                        false,
                ),
            presentedAtMillis =
                completedAtMillis - 1_500L,
            startedAtMillis =
                completedAtMillis - 1_000L,
            completedAtMillis =
                completedAtMillis,
            dismissedAtMillis =
                null,
            observationDeadlineAtMillis =
                completedAtMillis + 1_200_000L,
        )
    }
}