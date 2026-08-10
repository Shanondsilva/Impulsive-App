package com.impulsive.app.backend.data

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveSupportCycleTransferBoundaryTest {
    @Test
    fun activeCycleStoreLivesInNoBackupStorageAndIsNotAllowListed() {
        val dataSource = source(
            "app/src/main/java/com/impulsive/app/backend/data/local/preferences/" +
                "AdaptiveSupportCyclePreferencesDataSource.kt",
        )
        val legacyRules = source("app/src/main/res/xml/backup_rules.xml")
        val modernRules = source("app/src/main/res/xml/data_extraction_rules.xml")

        assertTrue(dataSource.contains("context.noBackupFilesDir"))
        assertTrue(dataSource.contains("adaptive_support_cycle.preferences_pb"))
        assertFalse(legacyRules.contains("adaptive_support_cycle"))
        assertFalse(modernRules.contains("adaptive_support_cycle"))
    }

    @Test
    fun manualBackupAndUserExportDoNotReadActiveCycleState() {
        val writer = source(
            "app/src/main/java/com/impulsive/app/backend/data/restore/RestoreBundleWriter.kt",
        )
        val exporter = source(
            "app/src/main/java/com/impulsive/app/backend/data/UserDataExporter.kt",
        )

        assertFalse(writer.contains("AdaptiveSupportCycle"))
        assertFalse(writer.contains("adaptive_support_cycle"))
        assertFalse(exporter.contains("AdaptiveSupportCycle"))
        assertFalse(exporter.contains("adaptive_support_cycle"))
    }

    @Test
    fun deletionAndRestoreExplicitlyClearTransientCycleState() {
        val deletion = source(
            "app/src/main/java/com/impulsive/app/backend/data/UserDataManager.kt",
        )
        val restore = source(
            "app/src/main/java/com/impulsive/app/backend/data/restore/RestoreBundleImporter.kt",
        )

        assertTrue(deletion.contains("AdaptiveSupportCyclePreferencesDataSource"))
        assertTrue(restore.contains("AdaptiveSupportCyclePreferencesDataSource"))
        assertTrue(deletion.contains(".clearAll()"))
        assertTrue(restore.contains(".clearAll()"))
    }

    private fun source(path: String): String {
        val direct = File(path)
        if (direct.exists()) return direct.readText()
        return File("..", path).readText()
    }
}
