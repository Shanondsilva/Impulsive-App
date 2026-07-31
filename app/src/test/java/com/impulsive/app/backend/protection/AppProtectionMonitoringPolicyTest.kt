package com.impulsive.app.backend.protection

import com.impulsive.app.backend.domain.model.protection.AppProtectionMonitoringPolicy
import com.impulsive.app.backend.domain.model.protection.AppProtectionStatus
import com.impulsive.app.backend.domain.model.protection.AppProtectionStatusRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppProtectionMonitoringPolicyTest {
    @Test
    fun newUserWithAppsAndUsageAccessCanMonitorAfterExplicitSelection() {
        assertTrue(
            AppProtectionMonitoringPolicy.shouldMonitor(
                selectedPackages = setOf("local.only"),
                usageAccessGranted = true,
                legacyMonitorEnabled = true,
                transitionCompleted = false,
            ),
        )
    }

    @Test
    fun noAppsOrMissingUsageAccessMeansInactive() {
        assertFalse(
            AppProtectionMonitoringPolicy.shouldMonitor(
                selectedPackages = emptySet(),
                usageAccessGranted = true,
                legacyMonitorEnabled = true,
                transitionCompleted = false,
            ),
        )
        assertFalse(
            AppProtectionMonitoringPolicy.shouldMonitor(
                selectedPackages = setOf("local.only"),
                usageAccessGranted = false,
                legacyMonitorEnabled = true,
                transitionCompleted = false,
            ),
        )
    }

    @Test
    fun legacyOffWithAppsRequiresTransitionAndDoesNotSilentlyActivate() {
        assertFalse(
            AppProtectionMonitoringPolicy.shouldMonitor(
                selectedPackages = setOf("local.only"),
                usageAccessGranted = true,
                legacyMonitorEnabled = false,
                transitionCompleted = false,
            ),
        )
        assertEquals(
            AppProtectionStatus.LegacyTransitionRequired,
            AppProtectionMonitoringPolicy.status(
                AppProtectionStatusRequest(
                    selectedProtectedAppCount = 1,
                    usageAccessGranted = true,
                    interruptionPermissionGranted = true,
                    backgroundActivityAllowed = true,
                    notificationPermissionGranted = true,
                    serviceHealthy = true,
                    legacyMonitorEnabled = false,
                    transitionCompleted = false,
                ),
            ),
        )
    }

    @Test
    fun keepProtectionCompletesTransition() {
        assertTrue(
            AppProtectionMonitoringPolicy.shouldMonitor(
                selectedPackages = setOf("local.only"),
                usageAccessGranted = true,
                legacyMonitorEnabled = false,
                transitionCompleted = true,
            ),
        )
    }

    @Test
    fun activeRequiresPermissionsAndHealthyService() {
        assertEquals(
            AppProtectionStatus.PermissionMissing,
            AppProtectionMonitoringPolicy.status(
                AppProtectionStatusRequest(
                    selectedProtectedAppCount = 2,
                    usageAccessGranted = false,
                    interruptionPermissionGranted = true,
                    backgroundActivityAllowed = true,
                    notificationPermissionGranted = true,
                    serviceHealthy = true,
                    legacyMonitorEnabled = true,
                    transitionCompleted = false,
                ),
            ),
        )
        assertEquals(
            AppProtectionStatus.ServiceUnavailable,
            AppProtectionMonitoringPolicy.status(
                AppProtectionStatusRequest(
                    selectedProtectedAppCount = 2,
                    usageAccessGranted = true,
                    interruptionPermissionGranted = true,
                    backgroundActivityAllowed = true,
                    notificationPermissionGranted = true,
                    serviceHealthy = false,
                    legacyMonitorEnabled = true,
                    transitionCompleted = false,
                ),
            ),
        )
    }
}
