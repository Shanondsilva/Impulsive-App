package com.impulsive.app.backend.data.local.device

import android.app.AppOpsManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings

sealed interface UsageAccessSettingsLaunchResult {
    data object PackageHintOpened : UsageAccessSettingsLaunchResult
    data object GeneralListOpened : UsageAccessSettingsLaunchResult
    data object Failed : UsageAccessSettingsLaunchResult
}

class UsageAccessPermissionChecker(
    private val context: Context,
) {
    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Opens Android's Usage Access settings.
     *
     * Android does not guarantee an app-specific Usage Access deep link for
     * ACTION_USAGE_ACCESS_SETTINGS.
     *
     * A package URI is attempted first as a best-effort OEM hint. If that
     * launch fails, the general Usage Access list is opened.
     *
     * Callers must be prepared for the user to land on the full app list and
     * should clearly instruct them to find Impulsive.
     */
    fun openUsageAccessSettings(): UsageAccessSettingsLaunchResult {
        val packageHintIntent =
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        if (safelyStart(packageHintIntent)) {
            return UsageAccessSettingsLaunchResult.PackageHintOpened
        }

        val generalIntent =
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        if (safelyStart(generalIntent)) {
            return UsageAccessSettingsLaunchResult.GeneralListOpened
        }

        return UsageAccessSettingsLaunchResult.Failed
    }

    private fun safelyStart(
        intent: Intent,
    ): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (exception: ActivityNotFoundException) {
            false
        } catch (exception: SecurityException) {
            false
        }
    }
}