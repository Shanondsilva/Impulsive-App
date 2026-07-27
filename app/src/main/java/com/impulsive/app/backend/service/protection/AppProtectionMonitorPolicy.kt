package com.impulsive.app.backend.service.protection

import com.impulsive.app.backend.data.local.preferences.WebsiteProtectionIncidentPhase

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

internal fun canStartWebsiteInterruption(
    phase: WebsiteProtectionIncidentPhase,
): Boolean =
    phase ==
        WebsiteProtectionIncidentPhase.Friction ||
        phase ==
        WebsiteProtectionIncidentPhase.Cooldown

internal fun isWebsiteFallbackIncidentEligible(
    incidentMatches: Boolean,
    websiteProtectionEnabled: Boolean,
    sameBrowserForeground: Boolean,
    browserIsWebsiteProtected: Boolean,
    protectionPaused: Boolean,
    websiteProtectionAlwaysOn: Boolean,
    overlayShowing: Boolean,
    terminatingActionSelected: Boolean,
): Boolean =
    incidentMatches &&
        websiteProtectionEnabled &&
        sameBrowserForeground &&
        browserIsWebsiteProtected &&
        (!protectionPaused || websiteProtectionAlwaysOn) &&
        !overlayShowing &&
        !terminatingActionSelected
