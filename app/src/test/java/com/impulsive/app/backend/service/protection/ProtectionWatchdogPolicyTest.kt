package com.impulsive.app.backend.service.protection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionWatchdogPolicyTest {
    @Test
    fun noConfiguredProtection() {
        assertEquals(
            ProtectionWatchdogDecision.NoProtectionConfigured,
            decideProtectionWatchdogAction(
                protectionConfigured = false,
                startResult = null,
                startConfirmed = null,
            ),
        )
    }

    @Test
    fun requestedStartWithConfirmationIsAccepted() {
        assertEquals(
            ProtectionWatchdogDecision.StartAccepted,
            decideProtectionWatchdogAction(
                protectionConfigured = true,
                startResult = ProtectionServiceStartResult.Requested,
                startConfirmed = true,
            ),
        )
    }

    @Test
    fun requestedStartWithoutConfirmationRetries() {
        assertEquals(
            ProtectionWatchdogDecision.RetryWorker,
            decideProtectionWatchdogAction(
                protectionConfigured = true,
                startResult = ProtectionServiceStartResult.Requested,
                startConfirmed = false,
            ),
        )
    }

    @Test
    fun requestedStartWithMissingConfirmationRetries() {
        assertEquals(
            ProtectionWatchdogDecision.RetryWorker,
            decideProtectionWatchdogAction(
                protectionConfigured = true,
                startResult = ProtectionServiceStartResult.Requested,
                startConfirmed = null,
            ),
        )
    }

    @Test
    fun androidBlockedBackgroundStart() {
        assertEquals(
            ProtectionWatchdogDecision.UserActionRequired,
            decideProtectionWatchdogAction(
                protectionConfigured = true,
                startResult = ProtectionServiceStartResult.BackgroundStartBlocked,
                startConfirmed = null,
            ),
        )
    }

    @Test
    fun visibleOverlayPolicyBlockRequiresUserAction() {
        assertEquals(
            ProtectionWatchdogDecision.UserActionRequired,
            decideProtectionWatchdogAction(
                protectionConfigured = true,
                startResult =
                    ProtectionServiceStartResult.PolicyBlocked(
                        ProtectionServiceStartBlockReason.VisibleOverlayRequired,
                    ),
                startConfirmed = null,
            ),
        )
    }

    @Test
    fun backgroundExemptionPolicyBlockRequiresUserAction() {
        assertEquals(
            ProtectionWatchdogDecision.UserActionRequired,
            decideProtectionWatchdogAction(
                protectionConfigured = true,
                startResult =
                    ProtectionServiceStartResult.PolicyBlocked(
                        ProtectionServiceStartBlockReason.BackgroundStartNotExempt,
                    ),
                startConfirmed = null,
            ),
        )
    }

    @Test
    fun permanentStartFailure() {
        assertEquals(
            ProtectionWatchdogDecision.UserActionRequired,
            decideProtectionWatchdogAction(
                protectionConfigured = true,
                startResult = ProtectionServiceStartResult.PermanentFailure,
                startConfirmed = null,
            ),
        )
    }

    @Test
    fun retryableStartFailure() {
        assertEquals(
            ProtectionWatchdogDecision.RetryWorker,
            decideProtectionWatchdogAction(
                protectionConfigured = true,
                startResult = ProtectionServiceStartResult.RetryableFailure,
                startConfirmed = null,
            ),
        )
    }

    @Test
    fun missingResultFailsSafely() {
        assertEquals(
            ProtectionWatchdogDecision.RetryWorker,
            decideProtectionWatchdogAction(
                protectionConfigured = true,
                startResult = null,
                startConfirmed = null,
            ),
        )
    }

    @Test
    fun firstRecoveryNoticeIsAllowed() {
        assertTrue(
            ProtectionRecoveryNoticePolicy.shouldShow(
                lastShownAtMillis = null,
                nowMillis = 1_000L,
            ),
        )
    }

    @Test
    fun noticeIsSuppressedInsideCooldown() {
        assertFalse(
            ProtectionRecoveryNoticePolicy.shouldShow(
                lastShownAtMillis = 1_000L,
                nowMillis =
                    1_000L +
                        ProtectionRecoveryNoticePolicy.CooldownMillis -
                        1L,
            ),
        )
    }

    @Test
    fun noticeIsAllowedAtCooldownBoundary() {
        assertTrue(
            ProtectionRecoveryNoticePolicy.shouldShow(
                lastShownAtMillis = 1_000L,
                nowMillis =
                    1_000L +
                        ProtectionRecoveryNoticePolicy.CooldownMillis,
            ),
        )
    }

    @Test
    fun clockRollbackDoesNotSuppressForever() {
        assertTrue(
            ProtectionRecoveryNoticePolicy.shouldShow(
                lastShownAtMillis = 10_000L,
                nowMillis = 5_000L,
            ),
        )
    }

    @Test
    fun invalidNonPositiveTimestamp() {
        assertTrue(
            ProtectionRecoveryNoticePolicy.shouldShow(
                lastShownAtMillis = 0L,
                nowMillis = 1_000L,
            ),
        )
    }

    @Test
    fun recoveryNotificationIsAllowedWhenAvailable() {
        assertTrue(
            canPostProtectionRecoveryNotification(
                InterruptionNotificationStatus.Available,
            ),
        )
    }

    @Test
    fun recoveryNotificationIsAllowedWhenChannelPriorityIsLowered() {
        assertTrue(
            canPostProtectionRecoveryNotification(
                InterruptionNotificationStatus.ChannelNotHighPriority,
            ),
        )
    }

    @Test
    fun recoveryNotificationIsUnavailableWithoutRuntimePermission() {
        assertFalse(
            canPostProtectionRecoveryNotification(
                InterruptionNotificationStatus.RuntimePermissionMissing,
            ),
        )
    }

    @Test
    fun recoveryNotificationIsUnavailableWhenAppNotificationsAreDisabled() {
        assertFalse(
            canPostProtectionRecoveryNotification(
                InterruptionNotificationStatus.AppNotificationsDisabled,
            ),
        )
    }

    @Test
    fun recoveryNotificationIsUnavailableWhenChannelIsDisabled() {
        assertFalse(
            canPostProtectionRecoveryNotification(
                InterruptionNotificationStatus.ChannelDisabled,
            ),
        )
    }

    @Test
    fun freshActiveHeartbeatIsAccepted() {
        val snapshot = ProtectionMonitorHealthSnapshot(
            healthyGeneration = 3L,
            lastHeartbeatElapsedRealtimeMillis = 10_000L,
            active = true,
        )

        assertTrue(
            isProtectionMonitorHeartbeatFresh(
                snapshot = snapshot,
                nowElapsedRealtimeMillis = 100_000L,
                maxAgeMillis = 90_000L,
            ),
        )
    }

    @Test
    fun staleHeartbeatIsRejected() {
        val snapshot = ProtectionMonitorHealthSnapshot(
            healthyGeneration = 3L,
            lastHeartbeatElapsedRealtimeMillis = 10_000L,
            active = true,
        )

        assertFalse(
            isProtectionMonitorHeartbeatFresh(
                snapshot = snapshot,
                nowElapsedRealtimeMillis = 100_001L,
                maxAgeMillis = 90_000L,
            ),
        )
    }

    @Test
    fun inactiveHeartbeatIsRejected() {
        assertFalse(
            isProtectionMonitorHeartbeatFresh(
                snapshot = ProtectionMonitorHealthSnapshot(
                    healthyGeneration = 3L,
                    lastHeartbeatElapsedRealtimeMillis = 99_999L,
                    active = false,
                ),
                nowElapsedRealtimeMillis = 100_000L,
                maxAgeMillis = 90_000L,
            ),
        )
    }

    @Test
    fun missingHeartbeatIsRejected() {
        assertFalse(
            isProtectionMonitorHeartbeatFresh(
                snapshot = ProtectionMonitorHealthSnapshot(
                    healthyGeneration = 0L,
                    lastHeartbeatElapsedRealtimeMillis = 0L,
                    active = true,
                ),
                nowElapsedRealtimeMillis = 100_000L,
                maxAgeMillis = 90_000L,
            ),
        )
    }

    @Test
    fun futureHeartbeatIsRejected() {
        assertFalse(
            isProtectionMonitorHeartbeatFresh(
                snapshot = ProtectionMonitorHealthSnapshot(
                    healthyGeneration = 3L,
                    lastHeartbeatElapsedRealtimeMillis = 100_001L,
                    active = true,
                ),
                nowElapsedRealtimeMillis = 100_000L,
                maxAgeMillis = 90_000L,
            ),
        )
    }

    @Test
    fun newerActiveGenerationConfirmsStartup() {
        assertTrue(
            hasConfirmedProtectionMonitorHeartbeat(
                baselineGeneration = 3L,
                snapshot = ProtectionMonitorHealthSnapshot(
                    healthyGeneration = 4L,
                    lastHeartbeatElapsedRealtimeMillis = 10_000L,
                    active = true,
                ),
            ),
        )
    }

    @Test
    fun sameGenerationDoesNotConfirmStartup() {
        assertFalse(
            hasConfirmedProtectionMonitorHeartbeat(
                baselineGeneration = 3L,
                snapshot = ProtectionMonitorHealthSnapshot(
                    healthyGeneration = 3L,
                    lastHeartbeatElapsedRealtimeMillis = 10_000L,
                    active = true,
                ),
            ),
        )
    }

    @Test
    fun newerInactiveGenerationDoesNotConfirmStartup() {
        assertFalse(
            hasConfirmedProtectionMonitorHeartbeat(
                baselineGeneration = 3L,
                snapshot = ProtectionMonitorHealthSnapshot(
                    healthyGeneration = 4L,
                    lastHeartbeatElapsedRealtimeMillis = 10_000L,
                    active = false,
                ),
            ),
        )
    }
}
