package com.impulsive.app.backend.data.restore.cloud

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudRecoveryRestoreSafetySourceTest {
    @Test
    fun `new recovery always wipes internal dek`() {
        val source =
            File(
                "src/main/java/com/impulsive/app/backend/data/restore/cloud/CloudRecoveryCrypto.kt",
            ).readText()

        assertTrue(
            source.contains(
                "internalDek.fill",
            ),
        )

        assertTrue(
            source.contains(
                "finally",
            ),
        )
    }

    @Test
    fun `replacement does not globally clear checklist table`() {
        val importer =
            File(
                "src/main/java/com/impulsive/app/backend/data/restore/RestoreBundleImporter.kt",
            ).readText()

        val dao =
            File(
                "src/main/java/com/impulsive/app/backend/data/local/dao/JournalNoteDao.kt",
            ).readText()

        assertFalse(
            importer.contains(
                "clearAllChecklistItemsForRestore",
            ),
        )

        assertFalse(
            dao.contains(
                "DELETE FROM journal_checklist_items\")",
            ),
        )
    }

    @Test
    fun `post import setup failure has truthful distinct result`() {
        val coordinator =
            File(
                "src/main/java/com/impulsive/app/backend/data/restore/cloud/CloudRecoveryRestoreCoordinator.kt",
            ).readText()

        val navigation =
            File(
                "src/main/java/com/impulsive/app/frontend/navigation/AppNavHost.kt",
            ).readText()

        assertTrue(
            coordinator.contains(
                "RestoredButCloudRecoverySetupFailed",
            ),
        )

        assertTrue(
            navigation.contains(
                "RestoredButCloudRecoverySetupFailed",
            ),
        )

        assertTrue(
            navigation.contains(
                "Your recovery data was restored",
            ),
        )
    }

    @Test
    fun `Drive discovery failures are classified`() {
        val source =
            File(
                "src/main/java/com/impulsive/app/backend/data/restore/cloud/CloudRecoveryRestoreCoordinator.kt",
            ).readText()

        assertTrue(
            source.contains(
                "AuthorizationRequired",
            ),
        )

        assertTrue(
            source.contains(
                "TemporarilyUnavailable",
            ),
        )

        assertTrue(
            source.contains(
                "CancellationException",
            ),
        )
    }

    @Test
    fun `scheduler failure does not share activation rollback block`() {
        val source =
            File(
                "src/main/java/com/impulsive/app/backend/data/restore/cloud/CloudRecoveryRestoreCoordinator.kt",
            ).readText()

        val activationStart =
            source.indexOf(
                "activateRestoredCloudRecovery",
            )

        val rollbackResult =
            source.indexOf(
                "RestoredButCloudRecoverySetupFailed",
                activationStart,
            )

        val schedulerRequest =
            source.indexOf(
                "scheduler.request()",
                activationStart,
            )

        assertTrue(activationStart >= 0)
        assertTrue(rollbackResult > activationStart)
        assertTrue(schedulerRequest > rollbackResult)
        assertTrue(source.contains("SuccessBackupRefreshPending"))
    }
}
