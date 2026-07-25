package com.impulsive.app.backend.service.protection

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.firebase.crashlytics.FirebaseCrashlytics

object ProtectionServiceController {
    fun start(
        context: Context,
        origin: ProtectionServiceStartOrigin,
        showTemporaryNotification: Boolean = false,
    ): ProtectionServiceStartResult {
        val appContext =
            context.applicationContext

        val environment =
            createProtectionServiceStartEnvironment(
                context = appContext,
                origin = origin,
            )

        val startDecision =
            decideProtectionServiceStart(
                environment,
            )

        if (
            startDecision is
                ProtectionServiceStartDecision
                    .RequireUserAction
        ) {
            ProtectionLog.warn(
                "Protection service recovery requires user action: " +
                    "origin=$origin, reason=${startDecision.reason}",
            )
            val recoveryReason =
                when (startDecision.reason) {
                    ProtectionServiceStartBlockReason
                        .BackgroundStartNotExempt ->
                        ProtectionServiceRecoveryReason
                            .BackgroundStartNotExempt

                    ProtectionServiceStartBlockReason
                        .VisibleOverlayRequired ->
                        ProtectionServiceRecoveryReason
                            .VisibleOverlayRequired
                }

            ProtectionServiceOperationalStateStore
                .markUserActionRequired(
                    origin = origin,
                    reason = recoveryReason,
                    sdkInt = environment.sdkInt,
                    updatedAtElapsedRealtimeMillis =
                        SystemClock.elapsedRealtime(),
                )

            return ProtectionServiceStartResult
                .PolicyBlocked(
                    startDecision.reason,
                )
        }

        val intent =
            Intent(
                appContext,
                AppMonitorService::class.java,
            ).apply {
                action = AppMonitorService.ActionStart
                putExtra(
                    AppMonitorService.ExtraShowTemporaryProtectionNotification,
                    showTemporaryNotification,
                )
            }

        ProtectionServiceOperationalStateStore
            .markStarting(
                origin = origin,
                sdkInt = environment.sdkInt,
                updatedAtElapsedRealtimeMillis =
                    SystemClock.elapsedRealtime(),
            )

        return try {
            ContextCompat.startForegroundService(
                appContext,
                intent,
            )

            ProtectionLog.debug("Protection service start requested: origin=$origin")

            ProtectionServiceStartResult.Requested
        } catch (error: Throwable) {
            reportServiceStartFailure(
                operation = "start",
                error = error,
            )

            val startResult =
                classifyStartFailure(error)

            when (startResult) {
                ProtectionServiceStartResult.BackgroundStartBlocked ->
                    ProtectionServiceOperationalStateStore
                        .markUserActionRequired(
                            origin = origin,
                            reason =
                                ProtectionServiceRecoveryReason
                                    .AndroidRejectedBackgroundStart,
                            sdkInt = environment.sdkInt,
                            updatedAtElapsedRealtimeMillis =
                                SystemClock.elapsedRealtime(),
                        )

                ProtectionServiceStartResult.PermanentFailure ->
                    ProtectionServiceOperationalStateStore
                        .markFailed(
                            origin = origin,
                            reason =
                                ProtectionServiceRecoveryReason
                                    .PermanentStartFailure,
                            sdkInt = environment.sdkInt,
                            updatedAtElapsedRealtimeMillis =
                                SystemClock.elapsedRealtime(),
                        )

                ProtectionServiceStartResult.RetryableFailure ->
                    ProtectionServiceOperationalStateStore
                        .markFailed(
                            origin = origin,
                            reason =
                                ProtectionServiceRecoveryReason
                                    .RetryableStartFailure,
                            sdkInt = environment.sdkInt,
                            updatedAtElapsedRealtimeMillis =
                                SystemClock.elapsedRealtime(),
                        )

                ProtectionServiceStartResult.Requested,
                is ProtectionServiceStartResult.PolicyBlocked,
                -> Unit
            }

            startResult
        }
    }

    fun cancelProtectionNotification(context: Context) {
        val intent = Intent(context, AppMonitorService::class.java).apply {
            action = AppMonitorService.ActionCancelProtectionNotification
        }
        runCatching { context.startService(intent) }
            .onFailure { error -> reportServiceStartFailure("cancel notification", error) }
    }

    fun stop(context: Context) {
        cancelProtectionNotification(context)

        val intent = Intent(context, AppMonitorService::class.java).apply {
            action = AppMonitorService.ActionStop
        }
        runCatching { context.startService(intent) }
            .onFailure { error -> reportServiceStartFailure("stop", error) }
    }

    private fun reportServiceStartFailure(operation: String, error: Throwable) {
        ProtectionLog.error(
            "Failed to $operation AppMonitorService " +
                "(exception=${error.javaClass.simpleName})",
        )
        Log.e(Tag, "Failed to $operation AppMonitorService", error)
        FirebaseCrashlytics.getInstance().recordException(error)
    }

    private fun classifyStartFailure(
        error: Throwable,
    ): ProtectionServiceStartResult {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            error is ForegroundServiceStartNotAllowedException
        ) {
            return ProtectionServiceStartResult.BackgroundStartBlocked
        }

        return when (error) {
            is SecurityException,
            is IllegalArgumentException ->
                ProtectionServiceStartResult.PermanentFailure

            else ->
                ProtectionServiceStartResult.RetryableFailure
        }
    }

    private const val Tag = "ProtectionService"
}
