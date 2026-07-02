package com.impulsive.app.backend.service.protection

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.firebase.crashlytics.FirebaseCrashlytics

object ProtectionServiceController {
    fun start(
        context: Context,
        showTemporaryNotification: Boolean = false,
    ) {
        val intent = Intent(context, AppMonitorService::class.java).apply {
            action = AppMonitorService.ActionStart
            putExtra(AppMonitorService.ExtraShowTemporaryProtectionNotification, showTemporaryNotification)
        }
        runCatching { ContextCompat.startForegroundService(context, intent) }
            .onFailure { error -> reportServiceStartFailure("start", error) }
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
        Log.e(Tag, "Failed to $operation AppMonitorService", error)
        FirebaseCrashlytics.getInstance().recordException(error)
    }

    private const val Tag = "ProtectionService"
}
