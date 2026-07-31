package com.impulsive.app.backend.service.protection

import com.impulsive.app.backend.data.local.preferences.WebsiteProtectionIncidentPhase
import com.impulsive.app.backend.domain.model.protection.AppProtectionMonitoringPolicy

internal fun shouldMonitorProtectedApps(
    appProtectionEnabled: Boolean,
    selectedPackages: Set<String>,
    usageAccessGranted: Boolean,
    transitionCompleted: Boolean = false,
): Boolean =
    AppProtectionMonitoringPolicy.shouldMonitor(
        selectedPackages = selectedPackages,
        usageAccessGranted = usageAccessGranted,
        legacyMonitorEnabled = appProtectionEnabled,
        transitionCompleted = transitionCompleted,
    )

internal fun shouldRecoverProtectionService(
    appProtectionEnabled: Boolean,
    selectedPackages: Set<String>,
    usageAccessGranted: Boolean,
    websiteProtectionEnabled: Boolean,
    transitionCompleted: Boolean = false,
): Boolean =
    websiteProtectionEnabled ||
        shouldMonitorProtectedApps(
            appProtectionEnabled = appProtectionEnabled,
            selectedPackages = selectedPackages,
            usageAccessGranted = usageAccessGranted,
            transitionCompleted = transitionCompleted,
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
