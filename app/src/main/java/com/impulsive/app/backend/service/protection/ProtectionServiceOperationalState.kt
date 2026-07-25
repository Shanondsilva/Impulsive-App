package com.impulsive.app.backend.service.protection

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ProtectionServiceRecoveryReason {
    BackgroundStartNotExempt,
    VisibleOverlayRequired,
    AndroidRejectedBackgroundStart,
    PermanentStartFailure,
    RetryableStartFailure,
    HeartbeatNotConfirmed,
}

sealed interface ProtectionServiceOperationalState {
    data object Unknown :
        ProtectionServiceOperationalState

    data object Stopped :
        ProtectionServiceOperationalState

    data class Starting(
        val origin: ProtectionServiceStartOrigin,
        val sdkInt: Int,
        val updatedAtElapsedRealtimeMillis: Long,
    ) : ProtectionServiceOperationalState

    data class Healthy(
        val origin:
            ProtectionServiceStartOrigin?,
        val sdkInt: Int,
        val updatedAtElapsedRealtimeMillis: Long,
    ) : ProtectionServiceOperationalState

    data class UserActionRequired(
        val origin: ProtectionServiceStartOrigin,
        val reason: ProtectionServiceRecoveryReason,
        val sdkInt: Int,
        val updatedAtElapsedRealtimeMillis: Long,
    ) : ProtectionServiceOperationalState

    data class Failed(
        val origin: ProtectionServiceStartOrigin,
        val reason: ProtectionServiceRecoveryReason,
        val sdkInt: Int,
        val updatedAtElapsedRealtimeMillis: Long,
    ) : ProtectionServiceOperationalState
}

object ProtectionServiceOperationalStateStore {
    private val mutableState =
        MutableStateFlow<
            ProtectionServiceOperationalState
        >(
            ProtectionServiceOperationalState
                .Unknown,
        )

    val state:
        StateFlow<
            ProtectionServiceOperationalState
        > =
        mutableState.asStateFlow()

    fun markStarting(
        origin: ProtectionServiceStartOrigin,
        sdkInt: Int,
        updatedAtElapsedRealtimeMillis: Long,
    ) {
        mutableState.value =
            ProtectionServiceOperationalState
                .Starting(
                    origin = origin,
                    sdkInt = sdkInt,
                    updatedAtElapsedRealtimeMillis =
                        updatedAtElapsedRealtimeMillis,
                )
    }

    fun markHealthy(
        sdkInt: Int,
        updatedAtElapsedRealtimeMillis: Long,
    ) {
        val previousOrigin =
            when (
                val previous =
                    mutableState.value
            ) {
                is ProtectionServiceOperationalState
                    .Starting ->
                    previous.origin

                is ProtectionServiceOperationalState
                    .Healthy ->
                    previous.origin

                is ProtectionServiceOperationalState
                    .UserActionRequired ->
                    previous.origin

                is ProtectionServiceOperationalState
                    .Failed ->
                    previous.origin

                ProtectionServiceOperationalState
                    .Unknown,
                ProtectionServiceOperationalState
                    .Stopped,
                ->
                    null
            }

        mutableState.value =
            ProtectionServiceOperationalState
                .Healthy(
                    origin = previousOrigin,
                    sdkInt = sdkInt,
                    updatedAtElapsedRealtimeMillis =
                        updatedAtElapsedRealtimeMillis,
                )
    }

    fun markUserActionRequired(
        origin: ProtectionServiceStartOrigin,
        reason: ProtectionServiceRecoveryReason,
        sdkInt: Int,
        updatedAtElapsedRealtimeMillis: Long,
    ) {
        mutableState.value =
            ProtectionServiceOperationalState
                .UserActionRequired(
                    origin = origin,
                    reason = reason,
                    sdkInt = sdkInt,
                    updatedAtElapsedRealtimeMillis =
                        updatedAtElapsedRealtimeMillis,
                )
    }

    fun markFailed(
        origin: ProtectionServiceStartOrigin,
        reason: ProtectionServiceRecoveryReason,
        sdkInt: Int,
        updatedAtElapsedRealtimeMillis: Long,
    ) {
        mutableState.value =
            ProtectionServiceOperationalState
                .Failed(
                    origin = origin,
                    reason = reason,
                    sdkInt = sdkInt,
                    updatedAtElapsedRealtimeMillis =
                        updatedAtElapsedRealtimeMillis,
                )
    }

    fun markStopped() {
        mutableState.value =
            ProtectionServiceOperationalState
                .Stopped
    }
}
