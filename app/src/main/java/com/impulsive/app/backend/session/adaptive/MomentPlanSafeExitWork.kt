package com.impulsive.app.backend.session.adaptive

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveDecisionRepository
import com.impulsive.app.backend.session.progress.SafeExitRecordingCoordinator
import com.impulsive.app.backend.session.progress.SafeExitRecordingResult
import com.impulsive.app.backend.session.progress.SafeExitWorkEnqueueReceipt
import com.impulsive.app.backend.session.progress.toSafeExitWorkEnqueueReceipt
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

internal fun interface MomentPlanSafeExitReconciliationScheduler {
    fun request(
        decisionId: String,
    ): SafeExitWorkEnqueueReceipt?
}

internal sealed interface MomentPlanSafeExitReconciliationResult {
    data object Recorded :
        MomentPlanSafeExitReconciliationResult

    data object Duplicate :
        MomentPlanSafeExitReconciliationResult

    data object Rejected :
        MomentPlanSafeExitReconciliationResult

    data object RetryableFailure :
        MomentPlanSafeExitReconciliationResult

    data object MissingDecision :
        MomentPlanSafeExitReconciliationResult

    data object NotReady :
        MomentPlanSafeExitReconciliationResult

    data object Ineligible :
        MomentPlanSafeExitReconciliationResult
}

internal class MomentPlanSafeExitReconciler(
    private val decisions:
        AdaptiveDecisionRepository,
    private val recorder:
        MomentPlanWalkAwayRecorder,
) {
    suspend fun reconcile(
        decisionId: String,
    ): MomentPlanSafeExitReconciliationResult {
        val decision =
            decisions.getById(
                decisionId,
            )
                ?: return MomentPlanSafeExitReconciliationResult.MissingDecision

        val eligibleFamily =
            decision.assignment
                .actualIntervention ==
                InterventionFamily.MomentPlan &&
                !decision.assignment
                    .momentPlanId
                    .isNullOrBlank()

        if (
            !eligibleFamily ||
            decision.dismissedAtMillis != null
        ) {
            return MomentPlanSafeExitReconciliationResult
                .Ineligible
        }

        if (
            decision.startedAtMillis == null ||
            decision.completedAtMillis == null
        ) {
            return MomentPlanSafeExitReconciliationResult
                .NotReady
        }

        return when (
            recorder
                .recordExplicitWalkAway(
                    decision,
                )
        ) {
            is SafeExitRecordingResult.Recorded ->
                MomentPlanSafeExitReconciliationResult
                    .Recorded

            is SafeExitRecordingResult.Duplicate ->
                MomentPlanSafeExitReconciliationResult
                    .Duplicate

            is SafeExitRecordingResult.Rejected ->
                MomentPlanSafeExitReconciliationResult
                    .Rejected

            SafeExitRecordingResult.RetryableFailure ->
                MomentPlanSafeExitReconciliationResult
                    .RetryableFailure

            null ->
                MomentPlanSafeExitReconciliationResult
                    .Ineligible
        }
    }
}

internal class WorkManagerMomentPlanSafeExitReconciliationScheduler(
    context: Context,
) : MomentPlanSafeExitReconciliationScheduler {
    private val workManager =
        WorkManager.getInstance(
            context.applicationContext,
        )

    override fun request(
        decisionId: String,
    ): SafeExitWorkEnqueueReceipt? {
        val safeDecisionId =
            decisionId.trim()

        if (
            safeDecisionId.isEmpty()
        ) {
            return null
        }

        return try {
            val request =
                OneTimeWorkRequestBuilder<
                    MomentPlanSafeExitReconciliationWorker
                >()
                    .setInputData(
                        workDataOf(
                            MomentPlanSafeExitWork
                                .DecisionIdKey to
                                safeDecisionId,
                        ),
                    )
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        30,
                        TimeUnit.SECONDS,
                    )
                    .addTag(
                        MomentPlanSafeExitWork
                            .Tag,
                    )
                    .build()

            workManager
                .enqueueUniqueWork(
                    MomentPlanSafeExitWork
                        .uniqueWorkName(
                            safeDecisionId,
                        ),
                    ExistingWorkPolicy
                        .APPEND_OR_REPLACE,
                    request,
                )
                .toSafeExitWorkEnqueueReceipt()
        } catch (
            _: Exception,
        ) {
            null
        }
    }
}

object MomentPlanSafeExitWork {
    const val Tag =
        "moment-plan-safe-exit-reconciliation"

    internal const val DecisionIdKey =
        "moment_plan_decision_id"

    internal const val MaximumNotReadyRetries =
        3

    fun uniqueWorkName(
        decisionId: String,
    ): String {
        return "moment-plan-safe-exit:$decisionId"
    }
}

class MomentPlanSafeExitReconciliationWorker(
    appContext: Context,
    workerParameters:
        WorkerParameters,
) : CoroutineWorker(
    appContext,
    workerParameters,
) {
    override suspend fun doWork():
        Result {
        val decisionId =
            inputData.getString(
                MomentPlanSafeExitWork
                    .DecisionIdKey,
            )
                ?.trim()
                .orEmpty()

        if (
            decisionId.isEmpty()
        ) {
            return Result.failure()
        }

        return try {
            when (
                MomentPlanSafeExitReconciler(
                    decisions =
                    AdaptivePhase4Dependencies
                        .decisions(
                            applicationContext,
                        ),
                    recorder =
                    MomentPlanSafeExitRecorder(
                        SafeExitRecordingCoordinator(
                            applicationContext,
                        ),
                    ),
                )
                    .reconcile(
                        decisionId,
                    )
            ) {
                MomentPlanSafeExitReconciliationResult.Recorded,
                MomentPlanSafeExitReconciliationResult.Duplicate,
                MomentPlanSafeExitReconciliationResult.Rejected,
                MomentPlanSafeExitReconciliationResult.Ineligible,
                ->
                    Result.success()

                MomentPlanSafeExitReconciliationResult.RetryableFailure ->
                    Result.retry()

                MomentPlanSafeExitReconciliationResult.MissingDecision,
                MomentPlanSafeExitReconciliationResult.NotReady,
                ->
                    if (
                        runAttemptCount <
                        MomentPlanSafeExitWork
                            .MaximumNotReadyRetries
                    ) {
                        Result.retry()
                    } else {
                        Result.failure()
                    }
            }
        } catch (
            cancellation:
                CancellationException,
        ) {
            throw cancellation
        } catch (
            _: Exception,
        ) {
            Result.retry()
        }
    }
}
