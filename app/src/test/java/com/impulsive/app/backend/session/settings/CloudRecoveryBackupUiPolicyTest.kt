package com.impulsive.app.backend.session.settings

import com.impulsive.app.backend.data.local.preferences.CloudRecoveryBackupMetadata
import com.impulsive.app.backend.data.local.preferences.CloudRecoveryStoredUploadOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudRecoveryBackupUiPolicyTest {
    @Test
    fun off() {
        val model = model(enabled = false)

        assertEquals("Off", model.value)
        assertFalse(model.showProgress)
        assertFalse(model.showBackupNow)
    }

    @Test
    fun preparingAndBackingUp() {
        val model = model(setup = true)

        assertEquals("Preparing and backing up...", model.value)
        assertTrue(model.showProgress)
    }

    @Test
    fun waitingToBackUp() {
        val model = model(queued = true)

        assertEquals("Waiting to back up", model.value)
        assertTrue(model.subtext.contains("connection or background work"))
        assertTrue(model.showProgress)
    }

    @Test
    fun uploading() {
        val model = model(running = true)

        assertEquals("Uploading...", model.value)
        assertTrue(model.showProgress)
    }

    @Test
    fun enabledWithoutSuccessIsNotBackedUpYet() {
        val model = model()

        assertEquals("Not backed up yet", model.value)
        assertTrue(model.showBackupNow)
    }

    @Test
    fun backedUpJustNow() {
        val model = model(
            metadata = metadata(
                success = 10_000L,
                outcome = CloudRecoveryStoredUploadOutcome.Uploaded,
            ),
            now = 10_030L,
        )

        assertEquals("Backed up", model.value)
        assertTrue(model.subtext.contains("Last backup: just now."))
    }

    @Test
    fun backedUpMinutesAgo() {
        val model = model(
            metadata = metadata(
                success = 10_000L,
                outcome = CloudRecoveryStoredUploadOutcome.Uploaded,
            ),
            now = 10_000L + 12 * 60_000L,
        )

        assertTrue(model.subtext.contains("Last backup: 12 min ago."))
    }

    @Test
    fun backupNeedsAttention() {
        val model = model(
            metadata = metadata(
                attempt = 20_000L,
                success = null,
                outcome = CloudRecoveryStoredUploadOutcome.AuthorizationRequired,
            ),
        )

        assertEquals("Backup needs attention", model.value)
        assertTrue(model.subtext.contains("Google Drive needs to be connected again"))
    }

    @Test
    fun previousSuccessFollowedByNewerFailureNeedsAttention() {
        val model = model(
            metadata = metadata(
                attempt = 30_000L,
                success = 10_000L,
                outcome = CloudRecoveryStoredUploadOutcome.RetryableFailure,
            ),
        )

        assertEquals("Backup needs attention", model.value)
    }

    @Test
    fun queuedUploadTakesPriorityOverOlderSuccess() {
        val model = model(
            queued = true,
            metadata = metadata(
                attempt = 10_000L,
                success = 10_000L,
                outcome = CloudRecoveryStoredUploadOutcome.Uploaded,
            ),
            now = 10_030L,
        )

        assertEquals("Waiting to back up", model.value)
        assertTrue(model.subtext.contains("Last backup: just now."))
    }

    @Test
    fun finishedWorkStateIsNotAnInputProvingSuccess() {
        val model = model(
            metadata = metadata(),
        )

        assertEquals("Not backed up yet", model.value)
    }

    private fun model(
        enabled: Boolean = true,
        setup: Boolean = false,
        queued: Boolean = false,
        running: Boolean = false,
        metadata: CloudRecoveryBackupMetadata = metadata(),
        now: Long = 60_000L,
    ): CloudRecoveryBackupUiModel =
        cloudRecoveryBackupUiModel(
            cloudRecoveryEnabled = enabled,
            cloudRecoverySetupInProgress = setup,
            hasQueuedUpload = queued,
            hasRunningUpload = running,
            metadata = metadata,
            nowEpochMillis = now,
        )

    private fun metadata(
        attempt: Long? = null,
        success: Long? = null,
        outcome: CloudRecoveryStoredUploadOutcome =
            CloudRecoveryStoredUploadOutcome.NeverAttempted,
    ): CloudRecoveryBackupMetadata =
        CloudRecoveryBackupMetadata(
            lastAttemptEpochMillis = attempt,
            lastSuccessfulBackupEpochMillis = success,
            latestOutcome = outcome,
        )
}