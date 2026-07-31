package com.impulsive.app.frontend.adaptive

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveDecisionExplanationSourceTest {
    private val navigation = source(
        "frontend/navigation/AppNavHost.kt",
    )
    private val viewModel = source(
        "backend/session/adaptive/AdaptiveDecisionExplanationViewModel.kt",
    )
    private val screen = source(
        "frontend/screens/adaptive/AdaptiveDecisionExplanationScreen.kt",
    )

    @Test
    fun routeCarriesOnlyOpaqueDecisionId() {
        assertTrue(
            navigation.contains(
                "const val AdaptiveExplanation = \"adaptive_explanation/{decisionId}\"",
            ),
        )
        assertTrue(
            navigation.contains(
                "\"adaptive_explanation/${'$'}{Uri.encode(decisionId)}\"",
            ),
        )
        assertFalse(navigation.contains("adaptive_explanation/{sourcePackageName}"))
        assertFalse(navigation.contains("adaptive_explanation/{url}"))
        assertFalse(navigation.contains("adaptive_explanation/{domain}"))
    }

    @Test
    fun destinationReloadsDecisionFromEncryptedRoomRepository() {
        assertTrue(viewModel.contains("AppDatabase.getInstance(application)"))
        assertTrue(viewModel.contains("RoomAdaptiveDecisionRepository"))
        assertTrue(viewModel.contains("decisions::getById"))
    }

    @Test
    fun missingDecisionHasSafeConsumerState() {
        assertTrue(viewModel.contains("missing = true"))
        assertTrue(screen.contains("This explanation is no longer available."))
    }

    @Test
    fun screenShowsRequiredSectionsAndPolicyVersion() {
        assertTrue(screen.contains("\"Why it was suggested\""))
        assertTrue(screen.contains("\"What was used\""))
        assertTrue(screen.contains("\"What was not used\""))
        assertTrue(screen.contains("\"Suggestion policy version"))
    }

    @Test
    fun screenDoesNotShowProtocolInternalsOrForbiddenScores() {
        listOf(
            "historicalProtocolDisplay",
            "assignedProtocolId",
            "actualProtocolId",
            "selectionProbability",
            "utility",
            "confidence",
        ).forEach { forbidden ->
            assertFalse(screen.contains(forbidden, ignoreCase = true))
        }
    }

    private fun source(path: String): String =
        File("src/main/java/com/impulsive/app/$path").readText()
}
