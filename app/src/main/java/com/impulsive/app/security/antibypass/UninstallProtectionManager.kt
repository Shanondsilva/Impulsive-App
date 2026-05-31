package com.impulsive.app.security.antibypass

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Platform wrapper for user-consented uninstall friction.
 */
class UninstallProtectionManager(context: Context) {
    private val appContext = context.applicationContext
    private val devicePolicyManager: DevicePolicyManager =
        requireNotNull(appContext.getSystemService(DevicePolicyManager::class.java)) {
            "DevicePolicyManager is not available on this device."
        }

    val adminComponent: ComponentName = ComponentName(
        appContext,
        ImpulsiveDeviceAdminReceiver::class.java,
    )

    fun isActive(): Boolean = devicePolicyManager.isAdminActive(adminComponent)

    fun buildEnableIntent(): Intent {
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Impulsive can add one extra system step before the app is removed. " +
                    "This is meant to protect you during weak moments. You can turn it off later.",
            )
        }
    }

    fun disableOwnAdmin() {
        if (isActive()) {
            devicePolicyManager.removeActiveAdmin(adminComponent)
        }
    }

    fun buildSecuritySettingsIntent(): Intent {
        return Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
