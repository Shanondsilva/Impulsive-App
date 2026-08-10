package com.impulsive.app.backend.session.adaptive

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FamiliarStepIntegrationBoundaryTest {
    private val main = File("src/main/java/com/impulsive/app")

    @Test
    fun familiarStepsDerivesFromExistingLedgerWithoutNewPersistence() {
        val entityAndDatabaseSources = main.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter {
                it.invariantSeparatorsPath.contains("/local/entity/") ||
                    it.name == "AppDatabase.kt"
            }
            .joinToString("\n") { it.readText() }
        assertFalse(entityAndDatabaseSources.contains("FamiliarStep"))

        val activeCyclePersistence = listOf(
            "backend/data/local/preferences/AdaptiveSupportCyclePreferencesDataSource.kt",
            "backend/data/repository/adaptive/DataStoreAdaptiveSupportCycleRepository.kt",
        ).joinToString("\n") { File(main, it).readText() }
        assertFalse(activeCyclePersistence.contains("FamiliarStep"))

        val repository = File(
            main,
            "backend/data/repository/adaptive/RoomAdaptiveDecisionRepository.kt",
        ).readText()
        assertTrue(repository.contains("getRecentFamiliarStepEvidence"))
        assertTrue(repository.contains("getRecentFinalised"))
    }

    @Test
    fun matcherInputAndDerivedStateExcludeProtectedSourceData() {
        val sources = listOf(
            "backend/domain/model/adaptive/FamiliarStepModels.kt",
            "backend/domain/engine/adaptive/FamiliarStepMatcher.kt",
            "backend/session/adaptive/FamiliarStepCoordinator.kt",
            "backend/session/adaptive/FamiliarStepServices.kt",
        ).joinToString("\n") { File(main, it).readText().lowercase() }
        val forbiddenProperties = listOf(
            "val incidenttoken", "val sourcepackage", "val url", "val domain",
            "val pagetitle", "val searchterm", "val query", "val dnspayload",
            "val privatemode",
        )
        assertFalse(forbiddenProperties.any(sources::contains))
    }

    @Test
    fun onlyAdaptiveSessionInvokesMatcherAndTransferFormatsAreUnchanged() {
        val callers = main.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains("FamiliarStepMatcher.match(") }
            .toList()
        assertTrue(callers.size == 1)
        assertTrue(
            callers.single().invariantSeparatorsPath.contains(
                "/backend/session/adaptive/FamiliarStepCoordinator.kt",
            ),
        )

        val transferSources = main.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter {
                it.name.contains("Backup") || it.name.contains("Restore") ||
                    it.name.contains("Exporter")
            }
            .joinToString("\n") { it.readText() }
        assertFalse(transferSources.contains("FamiliarStep"))
    }

    @Test
    fun explanationHistoryAndControlsHaveProductionBackendConsumers() {
        val coordinator = File(
            main,
            "backend/session/adaptive/FamiliarStepCoordinator.kt",
        ).readText()
        val historyViewModel = File(
            main,
            "backend/session/adaptive/FamiliarStepHistoryViewModel.kt",
        ).readText()
        val momentViewModel = File(
            main,
            "backend/session/adaptive/AdaptiveMomentViewModel.kt",
        ).readText()

        assertTrue(coordinator.contains("FamiliarStepExplanationService.explain(candidate)"))
        assertTrue(historyViewModel.contains("historyService.snapshot()"))
        assertTrue(historyViewModel.contains("controls.clearAdaptiveHistory()"))
        assertTrue(historyViewModel.contains("controls.setPersonalSuggestionsEnabled(enabled)"))
        assertTrue(momentViewModel.contains("familiarStepControls.clearAdaptiveHistory()"))
        assertTrue(momentViewModel.contains("familiarSteps.state(decisionId)"))
    }
}
