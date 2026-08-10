package com.impulsive.app.frontend.screens.games

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the *active* user-facing surfaces only.
 *
 * Reflex strings still legitimately exist for legacy exhaustive branches and
 * historical data, so this deliberately inspects active blocks rather than
 * whole files.
 */
class SnakeActiveCutoverSourceTest {

    @Test
    fun `the recovery hub offers Snake and no Reflex card`() {
        val screen = source("frontend/screens/games/RecoveryGamesScreen.kt")

        assertTrue(screen.contains("title = \"Snake\""))
        assertTrue(screen.contains("gameTypeId = \"SNAKE\""))
        assertFalse(screen.contains("title = \"Reflex Override\""))
        assertFalse(screen.contains("gameTypeId = \"REFLEX_OVERRIDE\""))
    }

    @Test
    fun `the visible task list offers Snake and no Reflex`() {
        val visible = source("frontend/screens/tasks/TaskToCompleteScreen.kt")
            .substringAfter("private val VisiblePsychologyTasks")
            .substringBefore("@Composable")

        assertTrue(visible.contains("PsychologyTaskType.Snake"))
        assertTrue(visible.contains("title = \"Snake\""))
        assertFalse(visible.contains("PsychologyTaskType.ReflexOverride"))
    }

    @Test
    fun `the active Home pivot card advertises Snake`() {
        val dashboardCards = homeDashboardCards()

        assertTrue(dashboardCards.contains("Snake, block, stack and rhythm"))
        assertTrue(dashboardCards.contains("\"Snake\""))
    }

    @Test
    fun `the active Home pivot card no longer animates Reflex Override`() {
        val dashboardCards = homeDashboardCards()

        assertFalse(dashboardCards.contains("Reflex Override"))
        assertFalse(dashboardCards.contains("Reflex, block and stack games"))
    }

    @Test
    fun `the legacy Home preview branch is deliberately retained`() {
        val home = source("frontend/screens/dashboard/HomeScreen.kt")
        val legacyPreview = home.substringAfter("private fun PsychologyTaskType.homePreview()")

        // Historical task data must still render truthfully.
        assertTrue(legacyPreview.contains("PsychologyTaskType.ReflexOverride"))
        assertTrue(legacyPreview.contains("PsychologyTaskType.Snake"))
    }

    /** Only the active dashboard card block, excluding legacy enum rendering. */
    private fun homeDashboardCards(): String =
        source("frontend/screens/dashboard/HomeScreen.kt")
            .substringAfter("private fun DashboardCards(")
            .substringBefore("private fun PsychologyTaskType.homePreview()")

    private fun source(relative: String): String = File(
        "src/main/java/com/impulsive/app/$relative",
    ).readText()
}
