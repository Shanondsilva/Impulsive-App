package com.impulsive.app.backend.service.protection

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

enum class ProtectionServiceStartOrigin {
    VisibleApp,
    Watchdog,
    BootCompleted,
    PackageReplaced,
}

enum class ProtectionServiceStartBlockReason {
    BackgroundStartNotExempt,
    VisibleOverlayRequired,
}

internal data class ProtectionServiceStartEnvironment(
    val sdkInt: Int,
    val targetSdkInt: Int,
    val origin: ProtectionServiceStartOrigin,
    val hasSystemAlertWindowPermission: Boolean,
    val hasVisibleOverlay: Boolean,
    val isIgnoringBatteryOptimizations: Boolean,
)

internal sealed interface ProtectionServiceStartDecision {
    data object StartNow :
        ProtectionServiceStartDecision

    data class RequireUserAction(
        val reason:
            ProtectionServiceStartBlockReason,
    ) : ProtectionServiceStartDecision
}

internal fun decideProtectionServiceStart(
    environment: ProtectionServiceStartEnvironment,
): ProtectionServiceStartDecision {
    return when (environment.origin) {
        ProtectionServiceStartOrigin.VisibleApp,
        ProtectionServiceStartOrigin.BootCompleted,
        ProtectionServiceStartOrigin.PackageReplaced,
        ->
            ProtectionServiceStartDecision.StartNow

        ProtectionServiceStartOrigin.Watchdog ->
            decideWatchdogStart(environment)
    }
}

private fun decideWatchdogStart(
    environment: ProtectionServiceStartEnvironment,
): ProtectionServiceStartDecision {
    if (environment.sdkInt < Build.VERSION_CODES.S) {
        return ProtectionServiceStartDecision.StartNow
    }

    if (environment.isIgnoringBatteryOptimizations) {
        return ProtectionServiceStartDecision.StartNow
    }

    if (!environment.hasSystemAlertWindowPermission) {
        return ProtectionServiceStartDecision
            .RequireUserAction(
                ProtectionServiceStartBlockReason
                    .BackgroundStartNotExempt,
            )
    }

    val visibleOverlayRequired =
        environment.sdkInt >=
            Build.VERSION_CODES.VANILLA_ICE_CREAM &&
            environment.targetSdkInt >=
            Build.VERSION_CODES.VANILLA_ICE_CREAM

    if (
        visibleOverlayRequired &&
        !environment.hasVisibleOverlay
    ) {
        return ProtectionServiceStartDecision
            .RequireUserAction(
                ProtectionServiceStartBlockReason
                    .VisibleOverlayRequired,
            )
    }

    return ProtectionServiceStartDecision.StartNow
}

internal fun createProtectionServiceStartEnvironment(
    context: Context,
    origin: ProtectionServiceStartOrigin,
): ProtectionServiceStartEnvironment {
    val appContext =
        context.applicationContext

    val hasOverlayPermission =
        Settings.canDrawOverlays(appContext)

    val hasVisibleOverlay =
        hasOverlayPermission &&
            ProtectionInterruptionOverlay
                .isShowing(appContext)

    val powerManager =
        appContext.getSystemService(
            PowerManager::class.java,
        )

    val ignoresBatteryOptimizations =
        powerManager
            ?.isIgnoringBatteryOptimizations(
                appContext.packageName,
            ) == true

    return ProtectionServiceStartEnvironment(
        sdkInt = Build.VERSION.SDK_INT,
        targetSdkInt =
            appContext.applicationInfo
                .targetSdkVersion,
        origin = origin,
        hasSystemAlertWindowPermission =
            hasOverlayPermission,
        hasVisibleOverlay =
            hasVisibleOverlay,
        isIgnoringBatteryOptimizations =
            ignoresBatteryOptimizations,
    )
}
