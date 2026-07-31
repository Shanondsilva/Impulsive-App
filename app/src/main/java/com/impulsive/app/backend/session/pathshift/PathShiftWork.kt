package com.impulsive.app.backend.session.pathshift

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.impulsive.app.backend.session.adaptive.AdaptiveClock
import com.impulsive.app.backend.session.adaptive.SystemAdaptiveClock
import java.util.concurrent.TimeUnit

class WorkManagerPathShiftWorkScheduler(
    context: Context,
    private val clock: AdaptiveClock = SystemAdaptiveClock,
) : PathShiftWorkScheduler {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun schedule(cycleId: String, finaliseAtMillis: Long): Boolean = try {
        require(cycleId.isNotBlank())
        val input = Data.Builder()
            .putString(PathShiftFinalisationWorker.InputCycleId, cycleId)
            .build()
        val request = OneTimeWorkRequestBuilder<PathShiftFinalisationWorker>()
            .setInputData(input)
            .setInitialDelay(
                (finaliseAtMillis - clock.nowMillis()).coerceAtLeast(0L),
                TimeUnit.MILLISECONDS,
            )
            .addTag(PathShiftWork.Tag)
            .build()
        workManager.enqueueUniqueWork(
            PathShiftWork.uniqueName(cycleId),
            ExistingWorkPolicy.KEEP,
            request,
        )
        true
    } catch (_: Throwable) {
        false
    }

    override fun cancel(cycleId: String): Boolean = try {
        workManager.cancelUniqueWork(PathShiftWork.uniqueName(cycleId))
        true
    } catch (_: Throwable) {
        false
    }

    override fun cancelAll(): Boolean = try {
        workManager.cancelAllWorkByTag(PathShiftWork.Tag)
        true
    } catch (_: Throwable) {
        false
    }
}

object PathShiftWork {
    const val Tag = "path-shift-finalisation"
    fun uniqueName(cycleId: String): String = "path-shift-finalisation-$cycleId"
}

class PathShiftFinalisationWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val cycleId = inputData.getString(InputCycleId)
            ?.takeIf { it.isNotBlank() }
            ?: return Result.success()
        return when (
            PathShiftDependencies.finaliser(applicationContext).finalise(cycleId)
        ) {
            PathShiftFinalisationResult.Finalised,
            PathShiftFinalisationResult.AlreadyFinalised,
            PathShiftFinalisationResult.Missing,
            -> Result.success()
            PathShiftFinalisationResult.NotDue,
            PathShiftFinalisationResult.PersistenceFailure,
            -> Result.retry()
        }
    }

    companion object {
        const val InputCycleId = "cycleId"
    }
}
