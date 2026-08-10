package com.impulsive.app.frontend.screens.safebrowse

import androidx.compose.runtime.Immutable

@Immutable
sealed interface SafeBrowseAccessState {
    data object SetupPending : SafeBrowseAccessState

    data object Locked : SafeBrowseAccessState

    data class Active(
        val remainingSeconds: Long,
        val passActive: Boolean = false,
    ) : SafeBrowseAccessState

    data object Expired : SafeBrowseAccessState

    data class Error(
        val message: String,
    ) : SafeBrowseAccessState
}

@Immutable
data class SafeBrowseUiState(
    val accessState: SafeBrowseAccessState,
    val rewardedUnlockAvailable: Boolean,
    val browserOpeningAvailable: Boolean,
    val passPurchaseAvailable: Boolean,
    val passPriceLabel: String?,
)

internal val SafeBrowseSetupPendingUiState =
    SafeBrowseUiState(
        accessState = SafeBrowseAccessState.SetupPending,
        rewardedUnlockAvailable = false,
        browserOpeningAvailable = false,
        passPurchaseAvailable = false,
        passPriceLabel = null,
    )

internal enum class SafeBrowseAction {
    WatchRewardedAd,
    OpenBrowser,
    Retry,
}

@Immutable
internal data class SafeBrowsePresentation(
    val title: String,
    val body: String,
    val primaryActionLabel: String,
    val primaryAction: SafeBrowseAction?,
    val primaryActionEnabled: Boolean,
    val secondaryActionLabel: String? = null,
    val secondaryActionEnabled: Boolean = false,
    val stateDescription: String,
)

@Immutable
internal data class SafeBrowseHomePresentation(
    val supportingText: String,
    val stateDescription: String,
)

internal fun SafeBrowseUiState.toPresentation(): SafeBrowsePresentation =
    when (val access = accessState) {
        SafeBrowseAccessState.SetupPending ->
            SafeBrowsePresentation(
                title = "Safe Browse is being prepared",
                body = "The secure browser, access timer and optional ad unlock will be connected in the next implementation phase.",
                primaryActionLabel = "Watch ad to unlock 2 hours",
                primaryAction = null,
                primaryActionEnabled = false,
                secondaryActionLabel = "Open Safe Browse",
                secondaryActionEnabled = false,
                stateDescription = "Safe Browse setup is not complete",
            )

        SafeBrowseAccessState.Locked ->
            SafeBrowsePresentation(
                title = "Unlock Safe Browse",
                body = "Watch one optional rewarded ad to unlock Safe Browse for 2 hours.",
                primaryActionLabel = "Watch ad to unlock 2 hours",
                primaryAction = SafeBrowseAction.WatchRewardedAd,
                primaryActionEnabled = rewardedUnlockAvailable,
                stateDescription = if (rewardedUnlockAvailable) {
                    "Safe Browse is locked. An ad can unlock access."
                } else {
                    "Safe Browse is locked. Ad unlock is unavailable."
                },
            )

        is SafeBrowseAccessState.Active -> {
            val passIsActive = access.passActive

            SafeBrowsePresentation(
                title = if (passIsActive) {
                    "Safe Browse Pass is active"
                } else {
                    "You can browse safely"
                },
                body = if (passIsActive) {
                    "Open Safe Browse without watching ads."
                } else {
                    "Your Safe Browse time pauses when you leave."
                },
                primaryActionLabel = "Open Safe Browse",
                primaryAction = SafeBrowseAction.OpenBrowser,
                primaryActionEnabled = browserOpeningAvailable,
                stateDescription = if (passIsActive) {
                    "Ad-free Safe Browse is available"
                } else {
                    "Safe Browse is available for " +
                        formatSafeBrowseRemainingTime(access.remainingSeconds)
                },
            )
        }

        SafeBrowseAccessState.Expired ->
            SafeBrowsePresentation(
                title = "Time is up",
                body = "Unlock another Safe Browse session when you are ready.",
                primaryActionLabel = "Watch ad to unlock 2 hours",
                primaryAction = SafeBrowseAction.WatchRewardedAd,
                primaryActionEnabled = rewardedUnlockAvailable,
                stateDescription = "Safe Browse access has expired",
            )

        is SafeBrowseAccessState.Error ->
            SafeBrowsePresentation(
                title = "Safe Browse is unavailable",
                body = access.message.trim().takeIf(String::isNotEmpty)
                    ?: "Safe Browse could not be loaded. Try again shortly.",
                primaryActionLabel = "Try again",
                primaryAction = SafeBrowseAction.Retry,
                primaryActionEnabled = true,
                stateDescription = "Safe Browse could not be loaded",
            )
    }

internal fun SafeBrowseUiState.toHomePresentation(): SafeBrowseHomePresentation =
    when (val access = accessState) {
        SafeBrowseAccessState.SetupPending ->
            SafeBrowseHomePresentation(
                supportingText = "Safe browsing setup is being completed",
                stateDescription = "Safe Browse is not available yet",
            )

        SafeBrowseAccessState.Locked ->
            SafeBrowseHomePresentation(
                supportingText = "Watch an ad to unlock 2 hours",
                stateDescription = "Safe Browse is locked",
            )

        is SafeBrowseAccessState.Active ->
            if (access.passActive) {
                SafeBrowseHomePresentation(
                    supportingText = "Ad-free Safe Browse",
                    stateDescription = "Safe Browse Pass is active",
                )
            } else {
                SafeBrowseHomePresentation(
                    supportingText = formatSafeBrowseRemainingTime(access.remainingSeconds) +
                        " remaining",
                    stateDescription = "Safe Browse is available",
                )
            }

        SafeBrowseAccessState.Expired ->
            SafeBrowseHomePresentation(
                supportingText = "Unlock another safe session",
                stateDescription = "Safe Browse access has expired",
            )

        is SafeBrowseAccessState.Error ->
            SafeBrowseHomePresentation(
                supportingText = "Safe Browse is temporarily unavailable",
                stateDescription = "Safe Browse could not be loaded",
            )
    }

internal fun formatSafeBrowseRemainingTime(remainingSeconds: Long): String {
    val safeSeconds = remainingSeconds.coerceAtLeast(0L)

    val hours = safeSeconds / 3_600L
    val minutes = (safeSeconds % 3_600L) / 60L

    return when {
        hours > 0L && minutes > 0L -> "${hours}h ${minutes}m"
        hours > 0L -> "${hours}h"
        minutes > 0L -> "${minutes}m"
        else -> "Less than 1 min"
    }
}
