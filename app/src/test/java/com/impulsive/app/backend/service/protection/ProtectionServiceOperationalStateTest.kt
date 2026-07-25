package com.impulsive.app.backend.service.protection

import org.junit.Assert.assertEquals
import org.junit.Test

class ProtectionServiceOperationalStateTest {
    @Test
    fun markStartingExposesOriginSdkAndTimestamp() {
        ProtectionServiceOperationalStateStore.markStarting(
            origin = ProtectionServiceStartOrigin.VisibleApp,
            sdkInt = 34,
            updatedAtElapsedRealtimeMillis = 1_001L,
        )

        assertEquals(
            ProtectionServiceOperationalState.Starting(
                origin = ProtectionServiceStartOrigin.VisibleApp,
                sdkInt = 34,
                updatedAtElapsedRealtimeMillis = 1_001L,
            ),
            ProtectionServiceOperationalStateStore.state.value,
        )
    }

    @Test
    fun markUserActionRequiredExposesReasonOriginSdkAndTimestamp() {
        ProtectionServiceOperationalStateStore.markUserActionRequired(
            origin = ProtectionServiceStartOrigin.Watchdog,
            reason = ProtectionServiceRecoveryReason.VisibleOverlayRequired,
            sdkInt = 35,
            updatedAtElapsedRealtimeMillis = 2_002L,
        )

        assertEquals(
            ProtectionServiceOperationalState.UserActionRequired(
                origin = ProtectionServiceStartOrigin.Watchdog,
                reason = ProtectionServiceRecoveryReason.VisibleOverlayRequired,
                sdkInt = 35,
                updatedAtElapsedRealtimeMillis = 2_002L,
            ),
            ProtectionServiceOperationalStateStore.state.value,
        )
    }

    @Test
    fun markFailedExposesRetryableFailureAndWatchdogOrigin() {
        ProtectionServiceOperationalStateStore.markFailed(
            origin = ProtectionServiceStartOrigin.Watchdog,
            reason = ProtectionServiceRecoveryReason.RetryableStartFailure,
            sdkInt = 36,
            updatedAtElapsedRealtimeMillis = 3_003L,
        )

        assertEquals(
            ProtectionServiceOperationalState.Failed(
                origin = ProtectionServiceStartOrigin.Watchdog,
                reason = ProtectionServiceRecoveryReason.RetryableStartFailure,
                sdkInt = 36,
                updatedAtElapsedRealtimeMillis = 3_003L,
            ),
            ProtectionServiceOperationalStateStore.state.value,
        )
    }

    @Test
    fun markHealthyChangesStateToHealthy() {
        ProtectionServiceOperationalStateStore.markStopped()

        ProtectionServiceOperationalStateStore.markHealthy(
            sdkInt = 35,
            updatedAtElapsedRealtimeMillis = 4_004L,
        )

        assertEquals(
            ProtectionServiceOperationalState.Healthy(
                origin = null,
                sdkInt = 35,
                updatedAtElapsedRealtimeMillis = 4_004L,
            ),
            ProtectionServiceOperationalStateStore.state.value,
        )
    }

    @Test
    fun healthyStatePreservesMostRecentStartOrigin() {
        ProtectionServiceOperationalStateStore.markStarting(
            origin = ProtectionServiceStartOrigin.PackageReplaced,
            sdkInt = 35,
            updatedAtElapsedRealtimeMillis = 5_005L,
        )

        ProtectionServiceOperationalStateStore.markHealthy(
            sdkInt = 35,
            updatedAtElapsedRealtimeMillis = 5_006L,
        )

        assertEquals(
            ProtectionServiceOperationalState.Healthy(
                origin = ProtectionServiceStartOrigin.PackageReplaced,
                sdkInt = 35,
                updatedAtElapsedRealtimeMillis = 5_006L,
            ),
            ProtectionServiceOperationalStateStore.state.value,
        )
    }

    @Test
    fun markStoppedExposesStopped() {
        ProtectionServiceOperationalStateStore.markStopped()

        assertEquals(
            ProtectionServiceOperationalState.Stopped,
            ProtectionServiceOperationalStateStore.state.value,
        )
    }
}
