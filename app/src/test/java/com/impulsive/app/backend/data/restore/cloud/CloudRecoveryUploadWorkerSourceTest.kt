package com.impulsive.app.backend.data.restore.cloud

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudRecoveryUploadWorkerSourceTest {
    @Test
    fun `worker retries only explicitly retryable failures`() {
        val source =
            source(
                "src/main/java/com/impulsive/app/backend/data/restore/cloud/CloudRecoveryUploadWorker.kt",
            )

        assertTrue(
            source.contains(
                "is CloudRecoveryUploadResult.RetryableFailure -> Result.retry()",
            ),
        )

        assertTrue(
            source.contains(
                "is CloudRecoveryUploadResult.PermanentFailure -> Result.success()",
            ),
        )

        assertTrue(
            source.contains(
                "CloudRecoveryUploadResult.AuthorizationRequired",
            ),
        )

        assertFalse(
            source.contains(
                "AuthorizationRequired -> Result.retry()",
            ),
        )
    }

    @Test
    fun `scheduler requires network and does not drop later requests`() {
        val source =
            source(
                "src/main/java/com/impulsive/app/backend/data/restore/cloud/CloudRecoveryUploadScheduler.kt",
            )

        assertTrue(
            source.contains(
                "NetworkType.CONNECTED",
            ),
        )

        assertTrue(
            source.contains(
                "ExistingWorkPolicy.APPEND_OR_REPLACE",
            ),
        )

        assertFalse(
            source.contains(
                "ExistingWorkPolicy.KEEP",
            ),
        )

        assertTrue(
            source.contains(
                "OneTimeWorkRequestBuilder<CloudRecoveryUploadWorker>()",
            ),
        )

        assertTrue(
            source.contains(
                "cancelUniqueWork(UniqueWorkName)",
            ),
        )

        assertTrue(
            source.contains(
                "suspend fun cancelAndAwait(context: Context)",
            ),
        )
    }

    private fun source(
        path: String,
    ): String =
        File(path).readText()
}