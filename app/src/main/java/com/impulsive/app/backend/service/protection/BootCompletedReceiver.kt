package com.impulsive.app.backend.service.protection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import com.impulsive.app.backend.data.repository.ProtectionSetupRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Restarts protection after device boot and after app updates. The class name
// predates the second trigger and is kept to avoid manifest churn.
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        // MY_PACKAGE_REPLACED fires after every app update or reinstall, which
        // kills the monitor process. Without this, protection stays silently off
        // until the user next opens Impulsive. BOOT_COMPLETED and
        // MY_PACKAGE_REPLACED are documented background foreground-service
        // start exemptions. Android can still reject particular foreground-
        // service types or invalid configurations, so the controller continues
        // to expose and record the actual start result.
        val startOrigin =
            when (action) {
                Intent.ACTION_BOOT_COMPLETED ->
                    ProtectionServiceStartOrigin
                        .BootCompleted

                Intent.ACTION_MY_PACKAGE_REPLACED ->
                    ProtectionServiceStartOrigin
                        .PackageReplaced

                else ->
                    return
        }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val appContext = context.applicationContext
                val setup = ProtectionSetupRepository(appContext).state.first()
                val protectionConfigured =
                    setup.appProtectionMonitorEnabled && setup.selectedBlockedAppPackageNames.isNotEmpty() ||
                    setup.websiteProtectionEnabled
                if (protectionConfigured) {
                    ProtectionServiceController.start(
                        context = appContext,
                        origin = startOrigin,
                    )
                    ProtectionWatchdogScheduler.ensureScheduled(appContext)
                }
                com.impulsive.app.backend.service.journal.FeedbackPromptScheduler(appContext)
                    .scheduleDailyNudge()

                WorkManager
                    .getInstance(appContext)
                    .cancelUniqueWork(
                        "feedback_reading_daily",
                    )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
