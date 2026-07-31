package com.impulsive.app.backend.service.protection

import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.impulsive.app.backend.data.local.preferences.ProtectionRecoveryNoticeDataSource
import com.impulsive.app.backend.data.repository.ProtectionSetupRepository
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Last-line safety net for the protection monitor.
 *
 * Periodic work is best effort and may run later than its configured interval.
 * Android can also reject a foreground-service start from a background worker.
 * When that happens, the worker asks the user to open Impulsive rather than
 * silently reporting a successful restart.
 */
class ProtectionWatchdogWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val appContext = applicationContext

        val setup = ProtectionSetupRepository(appContext).state.first()

        val protectionConfigured =
            setup.configurationDrivenAppProtectionConsented &&
                setup.selectedBlockedAppPackageNames.isNotEmpty() ||
                setup.websiteProtectionEnabled

        if (!protectionConfigured) {
            ProtectionServiceOperationalStateStore
                .markStopped()
            clearRecoveryNotice(appContext)
            return Result.success()
        }

        val healthBeforeStart = ProtectionMonitorHealthRegistry.snapshot()

        val nowElapsedRealtimeMillis = SystemClock.elapsedRealtime()

        if (
            isProtectionMonitorHeartbeatFresh(
                snapshot = healthBeforeStart,
                nowElapsedRealtimeMillis = nowElapsedRealtimeMillis,
                maxAgeMillis =
                    ProtectionMonitorHealthRegistry.HealthyHeartbeatMaxAgeMillis,
            )
        ) {
            ProtectionServiceOperationalStateStore
                .markHealthy(
                    sdkInt = Build.VERSION.SDK_INT,
                    updatedAtElapsedRealtimeMillis =
                        nowElapsedRealtimeMillis,
                )
            clearRecoveryNotice(appContext)
            return Result.success()
        }

        val baselineGeneration = healthBeforeStart.healthyGeneration

        val startResult = ProtectionServiceController.start(
            context = appContext,
            origin =
                ProtectionServiceStartOrigin
                    .Watchdog,
        )

        val startConfirmed =
            if (startResult == ProtectionServiceStartResult.Requested) {
                ProtectionMonitorHealthRegistry.awaitHealthyAfter(
                    baselineGeneration = baselineGeneration,
                )
            } else {
                null
            }

        if (
            startResult ==
                ProtectionServiceStartResult.Requested &&
            startConfirmed != true
        ) {
            ProtectionServiceOperationalStateStore
                .markFailed(
                    origin =
                        ProtectionServiceStartOrigin
                            .Watchdog,
                    reason =
                        ProtectionServiceRecoveryReason
                            .HeartbeatNotConfirmed,
                    sdkInt = Build.VERSION.SDK_INT,
                    updatedAtElapsedRealtimeMillis =
                        SystemClock.elapsedRealtime(),
                )
        }

        return when (
            decideProtectionWatchdogAction(
                protectionConfigured = true,
                startResult = startResult,
                startConfirmed = startConfirmed,
            )
        ) {
            ProtectionWatchdogDecision.NoProtectionConfigured -> {
                clearRecoveryNotice(appContext)

                Result.success()
            }

            ProtectionWatchdogDecision.StartAccepted -> {
                clearRecoveryNotice(appContext)

                Result.success()
            }

            ProtectionWatchdogDecision.UserActionRequired -> {
                showRecoveryNoticeIfDue(
                    context = appContext,
                    nowMillis = System.currentTimeMillis(),
                )

                Result.success()
            }

            ProtectionWatchdogDecision.RetryWorker -> {
                showRecoveryNoticeIfDue(
                    context = appContext,
                    nowMillis = System.currentTimeMillis(),
                )

                Result.retry()
            }
        }
    }

    private suspend fun showRecoveryNoticeIfDue(
        context: Context,
        nowMillis: Long,
    ) {
        val noticeDataSource = ProtectionRecoveryNoticeDataSource(context)

        val lastShownAtMillis = noticeDataSource.lastShownAtMillis.first()

        if (
            !ProtectionRecoveryNoticePolicy.shouldShow(
                lastShownAtMillis = lastShownAtMillis,
                nowMillis = nowMillis,
            )
        ) {
            return
        }

        val posted = ProtectionNotificationHelper(
            context,
        ).showProtectionRecoveryNotification()

        if (posted) {
            noticeDataSource.markShown(nowMillis)
        }
    }

    private suspend fun clearRecoveryNotice(
        context: Context,
    ) {
        ProtectionNotificationHelper(
            context,
        ).cancelProtectionRecoveryNotification()

        ProtectionRecoveryNoticeDataSource(
            context,
        ).clear()
    }
}

object ProtectionWatchdogScheduler {
    private const val WorkName = "protection_watchdog_periodic"
    private const val IntervalMinutes = 15L

    /**
     * Idempotent. KEEP preserves an already scheduled job, so calling this on
     * every app open and boot does not reset the periodic interval.
     */
    fun ensureScheduled(context: Context) {
        val request = PeriodicWorkRequestBuilder<ProtectionWatchdogWorker>(
            IntervalMinutes,
            TimeUnit.MINUTES,
        ).build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            WorkName,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
