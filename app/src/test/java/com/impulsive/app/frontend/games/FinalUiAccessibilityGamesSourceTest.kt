package com.impulsive.app.frontend.games

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinalUiAccessibilityGamesSourceTest {
    private val games = File(
        "src/main/java/com/impulsive/app/frontend/screens/games/RecoveryGamesScreen.kt",
    ).readText()

    @Test
    fun pivotGameSelectionReflowsMetadataAtLargeFont() {
        assertTrue(games.contains("FlowRow("))
        assertTrue(games.contains("@OptIn(ExperimentalLayoutApi::class)"))
        assertFalse(games.contains("KeyboardArrowRight"))
    }

    @Test
    fun lockedGameActionsStackInsteadOfCompetingForNarrowWidth() {
        val locked = games.section("private fun LockedGameStoreCard", "private fun SoftChip")
        assertTrue(locked.contains("Column(verticalArrangement = Arrangement.spacedBy(10.dp))"))
        assertTrue(Regex("""modifier = Modifier\.fillMaxWidth\(\)""").findAll(locked).count() >= 2)
    }

    private fun String.section(from: String, to: String): String =
        substring(indexOf(from), indexOf(to, indexOf(from) + from.length))
}
