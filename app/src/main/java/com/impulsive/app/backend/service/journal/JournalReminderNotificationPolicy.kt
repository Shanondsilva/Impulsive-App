package com.impulsive.app.backend.service.journal

internal enum class JournalReminderVisibility {
    Private,
    Secret,
}

internal data class JournalReminderNotificationDecision(
    val title: String,
    val body: String,
    val visibility:
        JournalReminderVisibility,
    val publicTitle: String?,
    val publicBody: String?,
)

internal fun resolveJournalReminderNotification(
    hideSensitiveNotifications:
        Boolean,
    currentTitle:
        String,
    currentPreview:
        String,
): JournalReminderNotificationDecision {
    val genericTitle =
        "Impulsive"

    val genericBody =
        "Open Impulsive for your journal reminder."

    if (hideSensitiveNotifications) {
        return JournalReminderNotificationDecision(
            title =
                genericTitle,
            body =
                genericBody,
            visibility =
                JournalReminderVisibility
                    .Secret,
            publicTitle =
                null,
            publicBody =
                null,
        )
    }

    return JournalReminderNotificationDecision(
        title =
            currentTitle
                .ifBlank {
                    "Journal reminder"
                },
        body =
            currentPreview
                .ifBlank {
                    "You asked Impulsive to remind you."
                }
                .take(120),
        visibility =
            JournalReminderVisibility
                .Private,
        publicTitle =
            genericTitle,
        publicBody =
            genericBody,
    )
}
