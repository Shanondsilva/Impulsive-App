package com.impulsive.app.backend.data.restore

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveBackupRestoreSourceTest {
    @Test
    fun adaptiveChangesUseExistingSnapshotWorker_notDirectCloudUploads() {
        val source = source(
            "app/src/main/java/com/impulsive/app/backend/data/restore/" +
                "AdaptiveRestoreSnapshotObserver.kt",
        )

        assertTrue(source.contains("RestoreSnapshotRefreshScheduler.request(appContext)"))
        assertFalse(source.contains("CloudRecoveryUploadScheduler"))
        assertTrue(source.contains("\"adaptive_decisions\""))
        assertTrue(source.contains("\"moment_plan_rehearsals\""))
    }

    @Test
    fun fullDeletionCancelsAdaptiveWorkBeforeClearingRoom() {
        val source = source(
            "app/src/main/java/com/impulsive/app/backend/data/UserDataManager.kt",
        )

        val cancel = source.indexOf("WorkManagerAdaptiveObservationScheduler(context).cancelAll()")
        val clear = source.indexOf("database.clearAllTables()")
        assertTrue(cancel >= 0)
        assertTrue(clear > cancel)
    }

    @Test
    fun exportHasPersonalSupportSectionAndOmitsPrivateAdaptiveFields() {
        val source = source(
            "app/src/main/java/com/impulsive/app/backend/data/UserDataExporter.kt",
        )

        assertTrue(source.contains("Personal support data"))
        assertTrue(source.contains("\"personalSupportData\""))
        assertTrue(source.contains("\"actualChoice\""))
        assertTrue(source.contains("\"repeatObservation\""))
        assertTrue(source.contains("\"recommendationPolicyVersion\""))
        assertTrue(source.contains("\"assignedProtocolId\""))
        assertTrue(source.contains("\"actualProtocolId\""))
        assertTrue(source.contains("\"contentRevisionId\""))
        assertTrue(source.contains("\"assignedPlanContentRevisionId\""))
        assertTrue(source.contains("\"actualPlanContentRevisionId\""))
        assertTrue(source.contains("\"planContentRevisionId\""))
        assertTrue(source.contains("\"evidenceQualityTier\""))
        assertTrue(source.contains("\"historyRetentionPolicy\""))
        assertFalse(source.contains(".put(\"protectionIncidentToken\""))
        assertFalse(source.contains(".put(\"sourceKind\""))
        assertFalse(source.contains(".put(\"selectionProbability\""))
        assertFalse(source.contains(".put(\"utilityByIntervention\""))
        assertFalse(source.contains(".put(\"randomSourceState\""))
    }

    @Test
    fun adaptiveRecoveryKeepsExistingOwnershipAndCloudBoundaries() {
        val restoreSource = source(
            "app/src/main/java/com/impulsive/app/backend/data/restore/" +
                "RestoreBundleImporter.kt",
        )
        val adaptiveSource = source(
            "app/src/main/java/com/impulsive/app/backend/data/restore/" +
                "AdaptiveRestorePayloadCodec.kt",
        )

        assertTrue(restoreSource.contains("database.withTransaction"))
        assertTrue(restoreSource.contains("validateOwnerProof"))
        assertFalse(adaptiveSource.contains("Firebase"))
        assertFalse(adaptiveSource.contains("email", ignoreCase = true))
        assertFalse(adaptiveSource.contains("ownerUid"))
    }

    @Test
    fun resetAndPermanentDeletionClearPendingAdaptiveWorkBeforeData() {
        val resetSource = source(
            "app/src/main/java/com/impulsive/app/backend/session/adaptive/" +
                "AdaptiveResetCoordinator.kt",
        )
        val deletionSource = source(
            "app/src/main/java/com/impulsive/app/backend/data/UserDataManager.kt",
        )

        assertTrue(resetSource.indexOf("scheduler.cancelAll()") < resetSource.indexOf("clear()"))
        assertTrue(resetSource.contains("clearPendingRuntimeState()"))
        assertTrue(deletionSource.contains("AdaptiveHistoryRetentionScheduler.cancelAllAndAwait"))
        assertTrue(deletionSource.contains("AdaptiveRetentionRuntimeState.clearAllAdaptiveReferences"))
    }

    private fun source(path: String): String {
        val file = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .map { File(it, path) }
            .firstOrNull(File::isFile)
        return requireNotNull(file) { "Could not find $path" }.readText()
    }
}
