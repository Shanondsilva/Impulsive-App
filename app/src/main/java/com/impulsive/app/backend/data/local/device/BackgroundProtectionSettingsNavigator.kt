package com.impulsive.app.backend.data.local.device

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

sealed interface BackgroundProtectionSettingsLaunchResult {
    data object AppDetailsOpened : BackgroundProtectionSettingsLaunchResult
    data object OptimizationListOpened : BackgroundProtectionSettingsLaunchResult
    data object Failed : BackgroundProtectionSettingsLaunchResult
}

class BackgroundProtectionSettingsNavigator(
    private val context: Context,
) {
    fun isAllowed(): Boolean {
        val powerManager = context.getSystemService(PowerManager::class.java) ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun open(): BackgroundProtectionSettingsLaunchResult {

        val appDetailsIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (safelyStart(appDetailsIntent)) {
            return BackgroundProtectionSettingsLaunchResult.AppDetailsOpened
        }

        val optimizationIntent = Intent(
            Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (safelyStart(optimizationIntent)) {
            return BackgroundProtectionSettingsLaunchResult.OptimizationListOpened
        }

        return BackgroundProtectionSettingsLaunchResult.Failed
    }

    private fun safelyStart(intent: Intent): Boolean {
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
