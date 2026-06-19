package com.impulsive.app.backend.service.protection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.impulsive.app.backend.data.repository.ProtectionSetupRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val appContext = context.applicationContext
                val setup = ProtectionSetupRepository(appContext).state.first()
                if (setup.selectedBlockedAppPackageNames.isNotEmpty()) {
                    ProtectionServiceController.start(appContext)
                }
                com.impulsive.app.backend.service.journal.FeedbackPromptScheduler(appContext)
                    .scheduleDailyNudge()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
