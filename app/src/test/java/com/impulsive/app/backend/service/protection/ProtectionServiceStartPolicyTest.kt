package com.impulsive.app.backend.service.protection

import org.junit.Assert.assertEquals
import org.junit.Test

class ProtectionServiceStartPolicyTest {
    @Test
    fun visibleUiStartsOnApi34Through36() {
        for (sdkInt in 34..36) {
            assertEquals(
                "API $sdkInt",
                ProtectionServiceStartDecision.StartNow,
                decision(
                    sdkInt = sdkInt,
                    origin = ProtectionServiceStartOrigin.VisibleApp,
                ),
            )
        }
    }

    @Test
    fun bootStartsOnApi34Through36() {
        for (sdkInt in 34..36) {
            assertEquals(
                "API $sdkInt",
                ProtectionServiceStartDecision.StartNow,
                decision(
                    sdkInt = sdkInt,
                    origin = ProtectionServiceStartOrigin.BootCompleted,
                ),
            )
        }
    }

    @Test
    fun packageReplacedStartsOnApi34Through36() {
        for (sdkInt in 34..36) {
            assertEquals(
                "API $sdkInt",
                ProtectionServiceStartDecision.StartNow,
                decision(
                    sdkInt = sdkInt,
                    origin = ProtectionServiceStartOrigin.PackageReplaced,
                ),
            )
        }
    }

    @Test
    fun api34WatchdogWithOverlayPermissionStartsWithoutVisibleOverlay() {
        assertEquals(
            ProtectionServiceStartDecision.StartNow,
            decision(
                sdkInt = 34,
                hasSystemAlertWindowPermission = true,
            ),
        )
    }

    @Test
    fun api34WatchdogWithoutExemptionRequiresUserAction() {
        assertEquals(
            ProtectionServiceStartDecision.RequireUserAction(
                ProtectionServiceStartBlockReason.BackgroundStartNotExempt,
            ),
            decision(sdkInt = 34),
        )
    }

    @Test
    fun api35WatchdogWithPermissionButNoVisibleOverlayRequiresUserAction() {
        assertEquals(
            ProtectionServiceStartDecision.RequireUserAction(
                ProtectionServiceStartBlockReason.VisibleOverlayRequired,
            ),
            decision(
                sdkInt = 35,
                hasSystemAlertWindowPermission = true,
            ),
        )
    }

    @Test
    fun api36WatchdogWithPermissionButNoVisibleOverlayRequiresUserAction() {
        assertEquals(
            ProtectionServiceStartDecision.RequireUserAction(
                ProtectionServiceStartBlockReason.VisibleOverlayRequired,
            ),
            decision(
                sdkInt = 36,
                hasSystemAlertWindowPermission = true,
            ),
        )
    }

    @Test
    fun api35WatchdogWithVisibleOverlayStarts() {
        assertEquals(
            ProtectionServiceStartDecision.StartNow,
            decision(
                sdkInt = 35,
                hasSystemAlertWindowPermission = true,
                hasVisibleOverlay = true,
            ),
        )
    }

    @Test
    fun api36WatchdogWithVisibleOverlayStarts() {
        assertEquals(
            ProtectionServiceStartDecision.StartNow,
            decision(
                sdkInt = 36,
                hasSystemAlertWindowPermission = true,
                hasVisibleOverlay = true,
            ),
        )
    }

    @Test
    fun api34BatteryExemptionStartsWithoutOverlayPermission() {
        assertEquals(
            ProtectionServiceStartDecision.StartNow,
            decision(
                sdkInt = 34,
                isIgnoringBatteryOptimizations = true,
            ),
        )
    }

    @Test
    fun api35BatteryExemptionStartsWithoutOverlayPermission() {
        assertEquals(
            ProtectionServiceStartDecision.StartNow,
            decision(
                sdkInt = 35,
                isIgnoringBatteryOptimizations = true,
            ),
        )
    }

    @Test
    fun api36BatteryExemptionStartsWithoutOverlayPermission() {
        assertEquals(
            ProtectionServiceStartDecision.StartNow,
            decision(
                sdkInt = 36,
                isIgnoringBatteryOptimizations = true,
            ),
        )
    }

    @Test
    fun api35WatchdogWithoutOverlayPermissionRequiresUserAction() {
        assertEquals(
            ProtectionServiceStartDecision.RequireUserAction(
                ProtectionServiceStartBlockReason.BackgroundStartNotExempt,
            ),
            decision(sdkInt = 35),
        )
    }

    @Test
    fun api36WatchdogWithoutOverlayPermissionRequiresUserAction() {
        assertEquals(
            ProtectionServiceStartDecision.RequireUserAction(
                ProtectionServiceStartBlockReason.BackgroundStartNotExempt,
            ),
            decision(sdkInt = 36),
        )
    }

    @Test
    fun api35Target34KeepsOverlayPermissionExemption() {
        assertEquals(
            ProtectionServiceStartDecision.StartNow,
            decision(
                sdkInt = 35,
                targetSdkInt = 34,
                hasSystemAlertWindowPermission = true,
            ),
        )
    }

    @Test
    fun preApi31WatchdogStartsWithoutExemption() {
        assertEquals(
            ProtectionServiceStartDecision.StartNow,
            decision(sdkInt = 30),
        )
    }

    private fun decision(
        sdkInt: Int,
        targetSdkInt: Int = 36,
        origin: ProtectionServiceStartOrigin = ProtectionServiceStartOrigin.Watchdog,
        hasSystemAlertWindowPermission: Boolean = false,
        hasVisibleOverlay: Boolean = false,
        isIgnoringBatteryOptimizations: Boolean = false,
    ): ProtectionServiceStartDecision =
        decideProtectionServiceStart(
            ProtectionServiceStartEnvironment(
                sdkInt = sdkInt,
                targetSdkInt = targetSdkInt,
                origin = origin,
                hasSystemAlertWindowPermission = hasSystemAlertWindowPermission,
                hasVisibleOverlay = hasVisibleOverlay,
                isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
            ),
        )
}
