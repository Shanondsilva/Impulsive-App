package com.impulsive.app.frontend.adaptive

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalSupportPrivacyControlsSourceTest {
    private val settings = source("frontend/screens/settings/SettingsScreen.kt")
    private val explanation = source(
        "frontend/screens/adaptive/HowSuggestionsWorkScreen.kt",
    )
    private val controls = source(
        "backend/session/adaptive/PersonalSupportControlsViewModel.kt",
    )
    private val dataRepository = source(
        "backend/data/repository/adaptive/RoomAdaptiveDataRepository.kt",
    )

    @Test
    fun explanationContainsRequiredPlainLanguageWithoutAiClaim() {
        listOf(
            "First moment",
            "Repeated moment",
            "Personal history",
            "Occasional variation",
            "Your choice",
            "Private learning",
        ).forEach { heading ->
            assertTrue(explanation.contains("\"$heading\""))
        }
        assertFalse(explanation.contains("artificial intelligence", ignoreCase = true))
        assertFalse(explanation.contains("AI model", ignoreCase = true))
    }

    @Test
    fun resetAndDeletionRowsLiveInDedicatedPrivacyAndDataScreen() {
        val mainGroup = settings.substring(
            settings.indexOf("private fun PersonalSupportSettingsGroup"),
            settings.indexOf("private fun MultiSelectEditDialog"),
        )
        val privacyScreen = settings.substring(
            settings.indexOf("fun PersonalSupportPrivacyAndDataScreen"),
            settings.indexOf("private fun PersonalSupportSubScreen"),
        )

        assertFalse(mainGroup.contains("\"Reset personal learning\""))
        assertFalse(mainGroup.contains("\"Delete all Moment data\""))
        assertTrue(privacyScreen.contains("\"Reset personal learning\""))
        assertTrue(privacyScreen.contains("\"Delete all Moment data\""))
        assertTrue(privacyScreen.contains("\"DATA CONTROL\""))
        assertTrue(settings.contains("\"Reset personal learning\""))
        assertTrue(settings.contains("\"Delete all Moment data\""))
        assertTrue(settings.contains("SettingsRow("))
        assertFalse(settings.contains("ResetPersonalLearningCard"))
    }

    @Test
    fun completeDeletionRequiresTwoDeliberateConfirmations() {
        assertTrue(settings.contains("confirmation = \"delete-first\""))
        assertTrue(settings.contains("confirmation = \"delete-final\""))
        assertTrue(settings.contains("\"Delete permanently\""))
    }

    @Test
    fun personalResetPreservesPlansAndPreferencesByUsingScopedTransaction() {
        val resetSection = dataRepository.substring(
            dataRepository.indexOf("override suspend fun clearPersonalLearning"),
            dataRepository.indexOf("override suspend fun clearAllAdaptiveData"),
        )
        assertTrue(resetSection.contains("adaptiveDecisionDao().clearLearningHistory()"))
        assertTrue(resetSection.contains("momentPlanRehearsalDao().clearAll()"))
        assertFalse(resetSection.contains("momentPlanDao().clearAll()"))
        assertFalse(resetSection.contains("adaptivePreferenceDao().clearAll()"))
    }

    @Test
    fun uiErrorsAreGenericAndNeverExposeExceptionMessages() {
        assertTrue(controls.contains("That change could not be completed. Please try again."))
        assertFalse(controls.contains("error.message"))
        assertFalse(controls.contains("error.localizedMessage"))
        assertFalse(settings.contains("exception.message"))
    }

    private fun source(path: String): String =
        File("src/main/java/com/impulsive/app/$path").readText()
}
