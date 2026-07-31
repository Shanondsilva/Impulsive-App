package com.impulsive.app.backend.session.adaptive

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveHistoryRetentionSourceTest {
    private val retention = source(
        "backend/session/adaptive/AdaptiveHistoryRetention.kt",
    )
    private val decisionDao = source(
        "backend/data/local/dao/AdaptiveDecisionDao.kt",
    )
    private val rehearsalDao = source(
        "backend/data/local/dao/MomentPlanRehearsalDao.kt",
    )
    private val settings = source(
        "frontend/screens/settings/SettingsScreen.kt",
    )
    private val viewModels = source(
        "backend/session/adaptive/MomentPlanViewModels.kt",
    )
    private val importer = source(
        "backend/data/restore/RestoreBundleImporter.kt",
    )

    @Test
    fun roomPruningIsBoundedAndTransactional() {
        assertTrue(retention.contains("database.withTransaction"))
        assertTrue(retention.contains("DefaultBatchLimit = 500"))
        assertTrue(retention.contains("MaximumBatchLimit = 1_000"))
        assertTrue(decisionDao.contains("LIMIT :limit"))
        assertTrue(rehearsalDao.contains("LIMIT :limit"))
    }

    @Test
    fun decisionQueryKeepsOpenAndUnfinalisedRecords() {
        assertTrue(decisionDao.contains("observationFinalisedAtMillis IS NOT NULL"))
        assertTrue(
            decisionDao.contains(
                "(completedAtMillis IS NOT NULL OR dismissedAtMillis IS NOT NULL)",
            ),
        )
        assertTrue(decisionDao.contains("createdAtMillis >= 0"))
    }

    @Test
    fun rehearsalQueryKeepsOpenEvents() {
        assertTrue(rehearsalDao.contains("completedAtMillis IS NOT NULL"))
        assertTrue(rehearsalDao.contains("dismissedAtMillis IS NOT NULL"))
        assertTrue(rehearsalDao.contains("COALESCE(completedAtMillis, dismissedAtMillis)"))
    }

    @Test
    fun weeklyWorkerHasOneUniqueLowFrequencySchedule() {
        assertTrue(retention.contains("adaptive-history-retention-weekly"))
        assertTrue(retention.contains("PeriodicWorkRequestBuilder"))
        assertTrue(retention.contains("7,"))
        assertTrue(retention.contains("TimeUnit.DAYS"))
        assertTrue(retention.contains("ExistingPeriodicWorkPolicy.KEEP"))
    }

    @Test
    fun policyChangeAndRestoreRequestBoundedCleanup() {
        assertTrue(
            viewModels.contains(
                "preferences.historyRetentionPolicy != previousRetentionPolicy",
            ),
        )
        assertTrue(viewModels.contains(".runBounded()"))
        assertTrue(importer.contains("AdaptiveHistoryRetentionScheduler"))
        assertTrue(importer.contains(".requestCleanup(appContext)"))
    }

    @Test
    fun cleanupDoesNotUploadPersonalRowsIndependently() {
        listOf(
            "Http",
            "Firebase",
            "Drive",
            "upload(",
            "packageName",
        ).forEach { forbidden ->
            assertFalse(retention.contains(forbidden, ignoreCase = true))
        }
        assertTrue(retention.contains("RestoreSnapshotRefreshScheduler.request"))
    }

    @Test
    fun compactSettingHasAllOptionsAndScopeCopy() {
        assertTrue(settings.contains("title = \"Personal support history\""))
        assertTrue(settings.contains("Older personal support records are removed automatically."))
        assertTrue(settings.contains("Moment Plans stay unless you delete them."))
        assertTrue(settings.contains("AdaptiveHistoryRetentionPolicy.entries"))
    }

    @Test
    fun pruningScopesOnlyAdaptiveHistoryTables() {
        assertTrue(retention.contains("adaptiveDecisionDao()"))
        assertTrue(retention.contains("momentPlanRehearsalDao()"))
        assertFalse(retention.contains("momentPlanDao().delete"))
        assertFalse(retention.contains("journalNoteDao"))
        assertFalse(retention.contains("recoverySessionDao"))
        assertFalse(retention.contains("adaptivePreferenceDao().clear"))
    }

    private fun source(path: String): String =
        File("src/main/java/com/impulsive/app/$path").readText()
}
