package com.impulsive.app.backend.data

import com.impulsive.app.backend.data.restore.RestoreBundleWriter
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserDataManagerRestoreDeletionPolicyTest {
    @Test
    fun automaticRestoreDirectoryNameStillTargetsPendingRestoreBundle() {
        assertEquals(
            "restore",
            RestoreBundleWriter.DirectoryName,
        )
        assertEquals(
            "impulsive_restore_bundle_v1.json",
            RestoreBundleWriter.FileName,
        )
        assertEquals(
            "impulsive_restore_bundle_v1.json.tmp",
            RestoreBundleWriter.TempFileName,
        )
    }

    @Test
    fun permanentDeletionChecksEveryCriticalLocalDeletion() {
        val source =
            File(
                "src/main/java/com/impulsive/app/backend/data/UserDataManager.kt",
            ).readText()

        assertTrue(
            source.contains(
                "database.clearAllTables()",
            ),
        )
        assertTrue(
            source.contains(
                "AndroidPendingCloudRestoreAuthorizationStore(context).clear()",
            ),
        )
        assertTrue(
            source.contains(
                "database.cloudRestoreReceiptDao().clearAll()",
            ),
        )

        assertFalse(
            Regex(
                """runCatching\s*\{\s*AppDatabase""",
            ).containsMatchIn(source),
        )

        assertTrue(
            source.contains(
                "val dataStoreDir",
            ),
        )
        assertTrue(
            source.contains(
                "context.filesDir",
            ),
        )
        assertTrue(
            source.contains(
                "DataStoreDirectoryName",
            ),
        )
        assertTrue(
            source.contains(
                "val deleted = dataStoreDir.deleteRecursively()",
            ),
        )
        assertTrue(
            source.contains(
                "!deleted || dataStoreDir.exists()",
            ),
        )

        assertTrue(
            source.contains(
                "val restoreDir",
            ),
        )
        assertTrue(
            source.contains(
                "RestoreBundleWriter.DirectoryName",
            ),
        )
        assertTrue(
            source.contains(
                "val deleted = restoreDir.deleteRecursively()",
            ),
        )
        assertTrue(
            source.contains(
                "!deleted || restoreDir.exists()",
            ),
        )
    }

    @Test
    fun permanentDeletionClearsExplicitRuntimePreferencesOnly() {
        val source =
            File(
                "src/main/java/com/impulsive/app/backend/data/UserDataManager.kt",
            ).readText()

        assertTrue(
            source.contains(
                "\"website_protection_incidents_v4\"",
            ),
        )
        assertTrue(
            source.contains(
                "\"website_protection_incidents_v3\"",
            ),
        )
        assertTrue(
            source.indexOf(
                "\"website_protection_incidents_v4\"",
            ) <
                source.indexOf(
                    "\"website_protection_incidents_v3\"",
                ),
        )
        assertTrue(
            source.contains(
                "\"vpn_diagnostics\"",
            ),
        )
        assertTrue(
            source.contains(
                "\"interruption_message_selector\"",
            ),
        )
        assertTrue(
            source.contains(
                "clearSharedPreferencesOrThrow",
            ),
        )
        assertTrue(
            source.contains(
                ".commit()",
            ),
        )

        assertFalse(
            source.contains(
                "sharedPreferencesDir.deleteRecursively",
            ),
        )
        assertFalse(
            source.contains(
                "\"shared_prefs\"",
            ),
        )
    }

    @Test
    fun backupChangeIsRequestedOnlyAfterEveryLocalRemovalOperation() {
        val source =
            File(
                "src/main/java/com/impulsive/app/backend/data/UserDataManager.kt",
            ).readText()

        val refreshCancellation =
            source.indexOf(
                "RestoreSnapshotRefreshScheduler.cancelAndAwait(context)",
            )
        val roomClear =
            source.indexOf(
                "database.clearAllTables()",
            )
        val authorizationClear =
            source.indexOf(
                "AndroidPendingCloudRestoreAuthorizationStore(context).clear()",
            )
        val receiptClear =
            source.indexOf(
                "database.cloudRestoreReceiptDao().clearAll()",
            )
        val dataStoreDelete =
            source.indexOf(
                "dataStoreDir.deleteRecursively()",
            )
        val restoreDelete =
            source.indexOf(
                "restoreDir.deleteRecursively()",
            )
        val preferencesClear =
            source.indexOf(
                "LocalStateSharedPreferences.forEach",
            )
        val backupChanged =
            source.indexOf(
                "BackupManager(context.applicationContext).dataChanged()",
            )

        assertTrue(refreshCancellation >= 0)
        assertTrue(authorizationClear > refreshCancellation)
        assertTrue(receiptClear > authorizationClear)
        assertTrue(roomClear > receiptClear)
        assertTrue(roomClear > refreshCancellation)
        assertTrue(dataStoreDelete > roomClear)
        assertTrue(restoreDelete > dataStoreDelete)
        assertTrue(preferencesClear > restoreDelete)
        assertTrue(backupChanged > preferencesClear)
        assertTrue(backupChanged > authorizationClear)
        assertTrue(backupChanged > receiptClear)
    }

    @Test
    fun restartFailsExplicitlyWhenLaunchIntentIsUnavailable() {
        val source =
            File(
                "src/main/java/com/impulsive/app/backend/data/UserDataManager.kt",
            ).readText()

        assertTrue(
            source.contains(
                "Could not create the Impulsive restart intent.",
            ),
        )
        assertTrue(
            source.contains(
                "Runtime.getRuntime().exit(0)",
            ),
        )
    }
}
