package com.impulsive.app.frontend.screens.safebrowse

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class SafeBrowsePassActiveDisplayText(
    val title: String,
    val planLabel: String,
    val timingLabel: String?,
    val supportingText: String,
    val stateDescription: String,
)

internal data class SafeBrowsePassExpiredDisplayText(
    val title: String,
    val timingLabel: String?,
    val supportingText: String,
    val stateDescription: String,
)

internal fun formatSafeBrowsePassDateTime(
    epochMillis: Long,
    zoneId: ZoneId =
        ZoneId.systemDefault(),
    locale: Locale =
        Locale.getDefault(),
): String? {
    if (epochMillis <= 0L) {
        return null
    }

    return Instant
        .ofEpochMilli(epochMillis)
        .atZone(zoneId)
        .format(
            DateTimeFormatter.ofPattern(
                "d MMM uuuu, h:mm a",
                locale,
            ),
        )
}

internal fun safeBrowsePassActiveDisplayText(
    access:
        SafeBrowsePassScreenAccessState.Active,
    formattedExpiry:
        String?,
): SafeBrowsePassActiveDisplayText =
    when (access.planStatus) {
        SafeBrowsePassActivePlanStatus.Prepaid -> {
            val timing =
                formattedExpiry
                    ?.let { value ->
                        "Access verified until $value."
                    }

            val support =
                if (access.topUpPending) {
                    "Your top-up is pending. Your current Pass remains active."
                } else {
                    "Your prepaid access remains available until the verified end time."
                }

            SafeBrowsePassActiveDisplayText(
                title =
                    "Your Safe Browse Pass is active",
                planLabel =
                    "30-day prepaid Pass",
                timingLabel =
                    timing,
                supportingText =
                    support,
                stateDescription =
                    buildString {
                        append(
                            "Safe Browse Pass active. Prepaid plan.",
                        )

                        formattedExpiry
                            ?.let { value ->
                                append(
                                    " Access verified until $value.",
                                )
                            }

                        if (access.topUpPending) {
                            append(
                                " Top-up pending.",
                            )
                        }
                    },
            )
        }

        SafeBrowsePassActivePlanStatus
            .AutoRenewing -> {
            val timing =
                formattedExpiry
                    ?.let { value ->
                        "Current access is verified until $value."
                    }

            SafeBrowsePassActiveDisplayText(
                title =
                    "Your Safe Browse Pass is active",
                planLabel =
                    "Auto-renewing Pass",
                timingLabel =
                    timing,
                supportingText =
                    "Manage renewal or cancellation in Google Play.",
                stateDescription =
                    buildString {
                        append(
                            "Safe Browse Pass active. Auto-renewing plan.",
                        )

                        formattedExpiry
                            ?.let { value ->
                                append(
                                    " Current access verified until $value.",
                                )
                            }
                    },
            )
        }

        SafeBrowsePassActivePlanStatus
            .CancelledUntilExpiry -> {
            val timing =
                formattedExpiry
                    ?.let { value ->
                        "Access ends on $value."
                    }

            SafeBrowsePassActiveDisplayText(
                title =
                    "Your Safe Browse Pass remains active",
                planLabel =
                    "Subscription cancelled",
                timingLabel =
                    timing,
                supportingText =
                    "You can manage or resubscribe through Google Play.",
                stateDescription =
                    buildString {
                        append(
                            "Safe Browse Pass active. Subscription cancelled.",
                        )

                        formattedExpiry
                            ?.let { value ->
                                append(
                                    " Access ends on $value.",
                                )
                            }
                    },
            )
        }
    }

internal fun safeBrowsePassExpiredDisplayText(
    access:
        SafeBrowsePassScreenAccessState.Expired,
    formattedExpiry:
        String?,
): SafeBrowsePassExpiredDisplayText {
    val timing =
        formattedExpiry
            ?.let { value ->
                "Ended on $value."
            }

    val supporting =
        if (access.wasPrepaid) {
            "Choose another prepaid Pass or a monthly plan below."
        } else {
            "Choose a new plan below or restore an existing purchase."
        }

    val planDescription =
        if (access.wasPrepaid) {
            "Previous prepaid Pass expired."
        } else {
            "Previous subscription expired."
        }

    return SafeBrowsePassExpiredDisplayText(
        title =
            "Your Safe Browse Pass has ended",
        timingLabel =
            timing,
        supportingText =
            supporting,
        stateDescription =
            buildString {
                append(planDescription)

                formattedExpiry
                    ?.let { value ->
                        append(
                            " Ended on $value.",
                        )
                    }
            },
    )
}
