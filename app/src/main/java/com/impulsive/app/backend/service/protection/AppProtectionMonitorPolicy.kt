package com.impulsive.app.backend.service.protection

internal fun shouldMonitorProtectedApps(
    appProtectionEnabled: Boolean,
    selectedPackages: Set<String>,
    usageAccessGranted: Boolean,
): Boolean =
    appProtectionEnabled &&
        selectedPackages.isNotEmpty() &&
        usageAccessGranted

internal fun shouldRecoverProtectionService(
    appProtectionEnabled: Boolean,
    selectedPackages: Set<String>,
    usageAccessGranted: Boolean,
    websiteProtectionEnabled: Boolean,
): Boolean =
    websiteProtectionEnabled ||
        shouldMonitorProtectedApps(
            appProtectionEnabled = appProtectionEnabled,
            selectedPackages = selectedPackages,
            usageAccessGranted = usageAccessGranted,
        )

internal fun shouldBypassGenericAppInterceptionForWebsiteProtection(
    foregroundPackage: String,
    websiteProtectionEnabled: Boolean,
    websiteProtectedPackages: Set<String>,
): Boolean =
    websiteProtectionEnabled &&
        foregroundPackage in websiteProtectedPackages
