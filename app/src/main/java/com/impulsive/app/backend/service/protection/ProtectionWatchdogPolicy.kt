package com.impulsive.app.backend.service.protection

sealed interface ProtectionServiceStartResult {
    data object Requested :
        ProtectionServiceStartResult

    data class PolicyBlocked(
        val reason:
            ProtectionServiceStartBlockReason,
    ) : ProtectionServiceStartResult

    data object BackgroundStartBlocked :
        ProtectionServiceStartResult

    data object PermanentFailure :
        ProtectionServiceStartResult

    data object RetryableFailure :
        ProtectionServiceStartResult
}

internal sealed interface ProtectionWatchdogDecision {
    data object NoProtectionConfigured :
        ProtectionWatchdogDecision

    data object StartAccepted :
        ProtectionWatchdogDecision

    data object UserActionRequired :
        ProtectionWatchdogDecision

    data object RetryWorker :
        ProtectionWatchdogDecision
}

internal fun decideProtectionWatchdogAction(
    protectionConfigured: Boolean,
    startResult: ProtectionServiceStartResult?,
    startConfirmed: Boolean?,
): ProtectionWatchdogDecision {
    if (!protectionConfigured) {
        return ProtectionWatchdogDecision
            .NoProtectionConfigured
    }

    return when (startResult) {
        ProtectionServiceStartResult.Requested ->
            if (startConfirmed == true) {
                ProtectionWatchdogDecision.StartAccepted
            } else {
                ProtectionWatchdogDecision.RetryWorker
            }

        is ProtectionServiceStartResult.PolicyBlocked,
        ProtectionServiceStartResult.BackgroundStartBlocked,
        ProtectionServiceStartResult.PermanentFailure ->
            ProtectionWatchdogDecision.UserActionRequired

        ProtectionServiceStartResult.RetryableFailure,
        null ->
            ProtectionWatchdogDecision.RetryWorker
    }
}

internal fun canPostProtectionRecoveryNotification(
    status: InterruptionNotificationStatus,
): Boolean =
    when (status) {
        InterruptionNotificationStatus.Available,
        InterruptionNotificationStatus.ChannelNotHighPriority,
        -> true

        InterruptionNotificationStatus.RuntimePermissionMissing,
        InterruptionNotificationStatus.AppNotificationsDisabled,
        InterruptionNotificationStatus.ChannelDisabled,
        -> false
    }

internal object ProtectionRecoveryNoticePolicy {
    const val CooldownMillis: Long =
        12L * 60L * 60L * 1000L

    fun shouldShow(
        lastShownAtMillis: Long?,
        nowMillis: Long,
    ): Boolean {
        val lastShown = lastShownAtMillis
            ?: return true

        if (lastShown <= 0L) {
            return true
        }

        if (nowMillis < lastShown) {
            return true
        }

        return nowMillis - lastShown >=
            CooldownMillis
    }
}
