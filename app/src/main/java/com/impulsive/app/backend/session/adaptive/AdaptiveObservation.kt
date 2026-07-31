package com.impulsive.app.backend.session.adaptive

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveDecisionRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

interface AdaptiveObservationScheduler {
    fun schedule(decisionId: String, deadlineAtMillis: Long): Boolean

    fun cancelAll(): Boolean
}

class WorkManagerAdaptiveObservationScheduler(
    context: Context,
    private val clock: AdaptiveClock = SystemAdaptiveClock,
) : AdaptiveObservationScheduler {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun schedule(decisionId: String, deadlineAtMillis: Long): Boolean =
        try {
            val delayMillis = (deadlineAtMillis - clock.nowMillis()).coerceAtLeast(0L)
            val input = Data.Builder()
                .putString(AdaptiveObservationFinalizerWorker.InputDecisionId, decisionId)
                .build()
            val request = OneTimeWorkRequestBuilder<AdaptiveObservationFinalizerWorker>()
                .setInputData(input)
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .addTag(AdaptiveObservationWork.Tag)
                .build()
            workManager.enqueueUniqueWork(
                AdaptiveObservationWork.uniqueName(decisionId),
                ExistingWorkPolicy.KEEP,
                request,
            )
            true
        } catch (error: Throwable) {
            AndroidAdaptiveSafeLogger.failure("schedule adaptive observation", error)
            false
        }

    override fun cancelAll(): Boolean = try {
        workManager.cancelAllWorkByTag(AdaptiveObservationWork.Tag)
        true
    } catch (error: Throwable) {
        AndroidAdaptiveSafeLogger.failure("cancel adaptive observation", error)
        false
    }
}

object AdaptiveObservationWork {
    const val Tag = "adaptive-observation"

    fun uniqueName(decisionId: String): String =
        "adaptive-observation-$decisionId"
}

class AdaptiveObservationFinalizer(
    private val decisions: AdaptiveDecisionRepository,
    private val clock: AdaptiveClock,
    private val logger: AdaptiveSafeLogger = AndroidAdaptiveSafeLogger,
) {
    suspend fun finalise(
        decisionId: String,
    ): AdaptiveFinalisationResult = try {
        val decision = decisions.getById(decisionId)
            ?: return AdaptiveFinalisationResult.Missing
        if (decision.observationFinalisedAtMillis != null) {
            return AdaptiveFinalisationResult.AlreadyFinalised
        }
        val now = clock.nowMillis()
        if (now < decision.observationDeadlineAtMillis) {
            return AdaptiveFinalisationResult.NotDue
        }
        if (decisions.finaliseOnce(decisionId, now)) {
            AdaptiveFinalisationResult.Finalised
        } else {
            val latest = decisions.getById(decisionId)
            if (latest == null) {
                AdaptiveFinalisationResult.Missing
            } else if (latest.observationFinalisedAtMillis != null) {
                AdaptiveFinalisationResult.AlreadyFinalised
            } else {
                AdaptiveFinalisationResult.PersistenceFailure
            }
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        logger.failure("finalise adaptive observation", error)
        AdaptiveFinalisationResult.PersistenceFailure
    }
}

class AdaptiveObservationFinalizerWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val decisionId = inputData.getString(InputDecisionId)
            ?.takeIf { it.isNotBlank() }
            ?: return Result.success()
        return when (
            AdaptivePhase4Dependencies
                .observationFinalizer(applicationContext)
                .finalise(decisionId)
        ) {
            AdaptiveFinalisationResult.Finalised,
            AdaptiveFinalisationResult.AlreadyFinalised,
            AdaptiveFinalisationResult.Missing,
            -> Result.success()

            AdaptiveFinalisationResult.NotDue,
            AdaptiveFinalisationResult.PersistenceFailure,
            -> Result.retry()
        }
    }

    companion object {
        const val InputDecisionId = "decisionId"
    }
}

class AdaptiveObservationRecovery(
    private val decisions: AdaptiveDecisionRepository,
    private val finalizer: AdaptiveObservationFinalizer,
    private val scheduler: AdaptiveObservationScheduler,
    private val clock: AdaptiveClock,
    private val logger: AdaptiveSafeLogger = AndroidAdaptiveSafeLogger,
) {
    suspend fun recover(limit: Int = RecoveryLimit): AdaptiveRecoveryResult {
        var finalised = 0
        var rescheduled = 0
        var failed = 0
        try {
            val now = clock.nowMillis()
            decisions.getOpenObservationDeadlines(now, limit).forEach { decision ->
                when (finalizer.finalise(decision.decisionId)) {
                    AdaptiveFinalisationResult.Finalised,
                    AdaptiveFinalisationResult.AlreadyFinalised,
                    AdaptiveFinalisationResult.Missing,
                    -> finalised++

                    AdaptiveFinalisationResult.NotDue,
                    AdaptiveFinalisationResult.PersistenceFailure,
                    -> failed++
                }
            }
            decisions.getFutureOpenObservationDeadlines(now, limit).forEach { decision ->
                if (scheduler.schedule(decision.decisionId, decision.observationDeadlineAtMillis)) {
                    rescheduled++
                } else {
                    failed++
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            logger.failure("recover adaptive observations", error)
            failed++
        }
        return AdaptiveRecoveryResult(
            finalisedCount = finalised,
            rescheduledCount = rescheduled,
            failedCount = failed,
        )
    }

    private companion object {
        const val RecoveryLimit = Int.MAX_VALUE
    }
}
