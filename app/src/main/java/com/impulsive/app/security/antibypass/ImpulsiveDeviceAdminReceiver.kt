package com.impulsive.app.security.antibypass

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.impulsive.app.backend.data.local.preferences.ProtectionSetupPreferencesDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Device Admin receiver used only for user-consented uninstall friction.
 *
 * Do not add wipe, password reset, camera disable, lock, hidden-app, or trap behavior here.
 */
class ImpulsiveDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        syncUninstallProtectionState(context = context, enabled = true)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        syncUninstallProtectionState(context = context, enabled = false)
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Turning this off removes the extra pause before uninstalling Impulsive. " +
            "Your recovery tools will still work, but removing the app becomes easier."
    }

    private fun syncUninstallProtectionState(context: Context, enabled: Boolean) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ProtectionSetupPreferencesDataSource(context.applicationContext)
                    .setUninstallProtectionEnabled(enabled)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
