package com.impulsive.app.backend.session.progress

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeExitProgressIntegrationSourceTest {
    @Test
    fun scoreViewModelUsesSharedSignalsAndLedgerChangesWithoutLifetimeRecords() {
        val source =
            source(
                "app/src/main/java/com/impulsive/app/" +
                    "backend/session/progress/" +
                    "ScoreViewModel.kt",
            )

        assertTrue(
            source.contains(
                "observeScoreAndSafeExitSignals",
            ),
        )
        assertTrue(
            source.contains(
                "observeLedgerChanges",
            ),
        )
        assertFalse(
            source.contains(
                "safeExitRepository.records",
            ),
        )
    }

    @Test
    fun safeExitRepositoryUsesBoundedProgressDaoQueries() {
        val source =
            source(
                "app/src/main/java/com/impulsive/app/" +
                    "backend/data/repository/" +
                    "SafeExitRepository.kt",
            )

        assertTrue(source.contains("observeSourceCountsInRange"))
        assertTrue(source.contains("observeExistingSourceKeysInRange"))
        assertTrue(source.contains("observeRecentNonPivotInRange"))
    }

    @Test
    fun safeExitDaoContainsAggregateBoundedRecentAndLedgerChangeQueries() {
        val source =
            source(
                "app/src/main/java/com/impulsive/app/" +
                    "backend/data/local/dao/" +
                    "SafeExitDao.kt",
            )

        assertTrue(source.contains("COUNT(*)"))
        assertTrue(source.contains("GROUP BY source"))
        assertTrue(source.contains("sourceKey IN (:sourceKeys)"))
        assertTrue(source.contains("LIMIT :limit"))
    }

    @Test
    fun progressDashboardScreenDoesNotReferencePreparedBackendField() {
        val source =
            source(
                "app/src/main/java/com/impulsive/app/" +
                    "frontend/screens/progress/" +
                    "ProgressDashboardScreen.kt",
            )

        listOf(
            "recentSafeExits",
            "SafeExitRepository",
            "SafeExitDao",
        ).forEach { forbidden ->
            assertFalse(
                source.contains(
                    forbidden,
                ),
            )
        }
    }

    @Test
    fun scoreDashboardStateAddsSafeExitProgressAndKeepsSessionStreak() {
        val source =
            source(
                "app/src/main/java/com/impulsive/app/" +
                    "backend/domain/model/score/" +
                    "ScoreModels.kt",
            )

        assertTrue(source.contains("safeExitProgress"))
        assertTrue(source.contains(".ledgerSafeExitCount"))
        assertTrue(source.contains(".persistedPivotSourceKeys"))
        assertTrue(source.contains(".additionalControlPoints"))
        assertTrue(source.contains("bestSafeExitStreak(filtered)"))
    }

    private fun source(
        path: String,
    ): String {
        val direct =
            File(path)

        return if (direct.isFile) {
            direct.readText()
        } else {
            File("..", path).readText()
        }
    }
}