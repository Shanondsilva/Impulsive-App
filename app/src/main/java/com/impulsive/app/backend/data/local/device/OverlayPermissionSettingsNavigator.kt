package com.impulsive.app.backend.data.local.device

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

sealed interface OverlayPermissionSettingsLaunchResult {
    data object OverlaySettingsOpened : OverlayPermissionSettingsLaunchResult
    data object AppDetailsOpened : OverlayPermissionSettingsLaunchResult
    data object Failed : OverlayPermissionSettingsLaunchResult
}

class OverlayPermissionSettingsNavigator(
    private val context: Context,
) {
    fun isAllowed(): Boolean =
        Settings.canDrawOverlays(context)

    fun open(): OverlayPermissionSettingsLaunchResult {
        val overlaySettingsIntent =
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    data = Uri.parse("package:${context.packageName}")
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        if (safelyStart(overlaySettingsIntent)) {
            return OverlayPermissionSettingsLaunchResult.OverlaySettingsOpened
        }

        val appDetailsIntent =
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}"),
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        if (safelyStart(appDetailsIntent)) {
            return OverlayPermissionSettingsLaunchResult.AppDetailsOpened
        }

        return OverlayPermissionSettingsLaunchResult.Failed
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