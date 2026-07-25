package com.impulsive.app.backend.session.settings

import com.impulsive.app.backend.data.local.preferences.CloudRecoveryBackupMetadata
import com.impulsive.app.backend.data.local.preferences.CloudRecoveryStoredUploadOutcome
import com.impulsive.app.backend.data.restore.cloud.CloudRecoveryTransportKind
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

internal data class CloudRecoveryBackupUiModel(
    val value: String,
    val subtext: String,
    val showProgress: Boolean,
    val showBackupNow: Boolean,
)

internal fun cloudRecoveryBackupUiModel(
    transportKind: CloudRecoveryTransportKind,
    cloudRecoveryEnabled: Boolean,
    cloudRecoverySetupInProgress: Boolean,
    hasQueuedUpload: Boolean,
    hasRunningUpload: Boolean,
    metadata: CloudRecoveryBackupMetadata,
    nowEpochMillis: Long,
): CloudRecoveryBackupUiModel {
    if (cloudRecoverySetupInProgress) {
        return CloudRecoveryBackupUiModel(
            value = "Preparing and backing up...",
            subtext = "Keep Impulsive installed until this says Backed up.",
            showProgress = true,
            showBackupNow = false,
        )
    }

    if (hasRunningUpload) {
        return CloudRecoveryBackupUiModel(
            value = "Uploading...",
            subtext = "Your encrypted recovery copy is being updated. Keep Impulsive installed until this says Backed up.",
            showProgress = true,
            showBackupNow = false,
        )
    }

    if (hasQueuedUpload) {
        val lastBackup =
            metadata.lastSuccessfulBackupEpochMillis
                ?.let { " Last backup: ${formatCloudRecoveryLastBackup(it, nowEpochMillis)}." }
                .orEmpty()

        return CloudRecoveryBackupUiModel(
            value = "Waiting to back up",
            subtext = "Waiting for a connection or background work to update your encrypted recovery copy.$lastBackup",
            showProgress = true,
            showBackupNow = false,
        )
    }

    if (!cloudRecoveryEnabled) {
        return CloudRecoveryBackupUiModel(
            value = "Off",
            subtext = "Keep an encrypted recovery copy ${cloudRecoveryDestinationDescription(transportKind)}",
            showProgress = false,
            showBackupNow = false,
        )
    }

    if (metadata.latestOutcome != CloudRecoveryStoredUploadOutcome.Uploaded) {
        val attempt = metadata.lastAttemptEpochMillis
        val success = metadata.lastSuccessfulBackupEpochMillis
        if (attempt != null && (success == null || attempt > success)) {
            return CloudRecoveryBackupUiModel(
                value = "Backup needs attention",
                subtext = attentionSubtext(transportKind, metadata.latestOutcome),
                showProgress = false,
                showBackupNow = true,
            )
        }
    }

    val successEpochMillis = metadata.lastSuccessfulBackupEpochMillis
    if (
        metadata.latestOutcome == CloudRecoveryStoredUploadOutcome.Uploaded &&
        successEpochMillis != null
    ) {
        return CloudRecoveryBackupUiModel(
            value = "Backed up",
            subtext = "Encrypted recovery copy ${cloudRecoveryDestinationDescription(transportKind)} Last backup: ${formatCloudRecoveryLastBackup(successEpochMillis, nowEpochMillis)}.",
            showProgress = false,
            showBackupNow = true,
        )
    }

    return CloudRecoveryBackupUiModel(
        value = "Not backed up yet",
        subtext = "Keep Impulsive installed and online until this says Backed up.",
        showProgress = false,
        showBackupNow = true,
    )
}

private fun cloudRecoveryDestinationDescription(
    transportKind: CloudRecoveryTransportKind,
): String =
    when (transportKind) {
        CloudRecoveryTransportKind.DriveAppData ->
            "in your private Google Drive app data. Your recovery password is never stored by Impulsive."

        CloudRecoveryTransportKind.FirebaseStorage ->
            "in your Impulsive account, encrypted with your recovery password. Impulsive cannot read it."
    }

internal fun formatCloudRecoveryLastBackup(
    backupEpochMillis: Long,
    nowEpochMillis: Long,
    locale: Locale = Locale.getDefault(),
): String {
    val elapsedMillis = max(0L, nowEpochMillis - backupEpochMillis)
    val elapsedMinutes = elapsedMillis / 60_000L

    if (elapsedMinutes < 1L) {
        return "just now"
    }

    if (elapsedMinutes < 60L) {
        return if (elapsedMinutes == 1L) {
            "1 min ago"
        } else {
            "$elapsedMinutes min ago"
        }
    }

    val backup = Date(backupEpochMillis)
    val now = Date(nowEpochMillis)
    val dateFormat = DateFormat.getDateInstance(DateFormat.SHORT, locale)

    return if (dateFormat.format(backup) == dateFormat.format(now)) {
        DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(backup)
    } else {
        dateFormat.format(backup)
    }
}

private fun attentionSubtext(
    transportKind: CloudRecoveryTransportKind,
    outcome: CloudRecoveryStoredUploadOutcome,
): String =
    when (outcome) {
        CloudRecoveryStoredUploadOutcome.NoAuthenticatedAccount ->
            "Sign in again to continue cloud recovery backup."

        CloudRecoveryStoredUploadOutcome.AccountMismatch ->
            "The signed-in account does not match the account that owns this recovery data."

        CloudRecoveryStoredUploadOutcome.SetupRequired ->
            "Recovery setup needs to be completed again."

        CloudRecoveryStoredUploadOutcome.AuthorizationRequired ->
            if (transportKind == CloudRecoveryTransportKind.DriveAppData) {
                "Google Drive needs to be connected again. Turn recovery off and on to reconnect."
            } else {
                "Sign in again to continue cloud recovery backup."
            }

        CloudRecoveryStoredUploadOutcome.RetryableFailure ->
            "The latest backup did not finish. Check your connection and try again."

        CloudRecoveryStoredUploadOutcome.PermanentFailure ->
            "The latest backup did not finish. Try again from Settings."

        CloudRecoveryStoredUploadOutcome.GuestNotApplicable ->
            "Cloud recovery backup is not available for guest accounts."

        CloudRecoveryStoredUploadOutcome.NoOwnedCompletedData ->
            "Complete setup before cloud recovery backup can continue."

        CloudRecoveryStoredUploadOutcome.Cancelled ->
            "The latest backup was cancelled before it finished."

        CloudRecoveryStoredUploadOutcome.NeverAttempted,
        CloudRecoveryStoredUploadOutcome.Uploaded,
        -> "Keep Impulsive installed and online until this says Backed up."
    }
