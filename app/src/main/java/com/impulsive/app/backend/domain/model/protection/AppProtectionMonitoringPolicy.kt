package com.impulsive.app.backend.domain.model.protection

enum class AppProtectionStatus {
    Active,
    NoApps,
    PermissionMissing,
    ServiceUnavailable,
    LegacyTransitionRequired,
}

data class AppProtectionStatusRequest(
    val selectedProtectedAppCount: Int,
    val usageAccessGranted: Boolean,
    val interruptionPermissionGranted: Boolean,
    val backgroundActivityAllowed: Boolean,
    val notificationPermissionGranted: Boolean,
    val serviceHealthy: Boolean,
    val legacyMonitorEnabled: Boolean,
    val transitionCompleted: Boolean,
)

object AppProtectionMonitoringPolicy {
    fun shouldMonitor(
        selectedPackages: Set<String>,
        usageAccessGranted: Boolean,
        legacyMonitorEnabled: Boolean,
        transitionCompleted: Boolean,
    ): Boolean =
        selectedPackages.isNotEmpty() &&
            usageAccessGranted &&
            (legacyMonitorEnabled || transitionCompleted)

    fun status(request: AppProtectionStatusRequest): AppProtectionStatus = when {
        request.selectedProtectedAppCount == 0 -> AppProtectionStatus.NoApps
        !request.legacyMonitorEnabled && !request.transitionCompleted ->
            AppProtectionStatus.LegacyTransitionRequired
        !request.usageAccessGranted ||
            !request.interruptionPermissionGranted ||
            !request.backgroundActivityAllowed ||
            !request.notificationPermissionGranted -> AppProtectionStatus.PermissionMissing
        !request.serviceHealthy -> AppProtectionStatus.ServiceUnavailable
        else -> AppProtectionStatus.Active
    }
}
