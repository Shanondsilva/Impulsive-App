package com.impulsive.app.backend.session.game

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards that the after-rating has exactly one source of truth.
 *
 * `SnakeGameViewModel` is an AndroidViewModel and the project deliberately
 * avoids Robolectric, so this wiring is checked at source level.
 */
class SnakeUrgeAfterStateSourceTest {

    private val viewModelSource = source(
        "src/main/java/com/impulsive/app/backend/session/game/SnakeGameViewModel.kt",
    )
    private val modelsSource = source(
        "src/main/java/com/impulsive/app/backend/domain/game/SnakeGameSessionModels.kt",
    )

    @Test
    fun `the ui state carries the urge rating`() {
        assertTrue(modelsSource.contains("val urgeAfterRating: Int? = null"))
        assertTrue(modelsSource.contains("urgeAfterRating in 0..10"))
    }

    @Test
    fun `setting the urge rating updates the visible state`() {
        val setter = viewModelSource
            .substringAfter("fun setUrgeAfter(rating: Int) {")
            .substringBefore("fun taskRewardCompletionToken")
            .replace(Regex("\\s+"), " ")

        assertTrue(setter.contains("_uiState.update { it.copy(urgeAfterRating = coerced) }"))

        // The visible update must precede the persistence early-return.
        val updateIndex = setter.indexOf("_uiState.update")
        val returnIndex = setter.indexOf("lastRecordedSession ?: return")

        assertTrue(updateIndex >= 0 && returnIndex >= 0)
        assertTrue("state must update before the persistence guard", updateIndex < returnIndex)
    }

    @Test
    fun `a restored result restores the saved urge rating`() {
        assertTrue(
            viewModelSource
                .replace(Regex("\\s+"), " ")
                .contains("urgeAfterRating = snapshot.urgeAfterRating,"),
        )
    }

    @Test
    fun `a new round and a new result start unrated`() {
        val collapsed = viewModelSource.replace(Regex("\\s+"), " ")

        // Ready transition, round reset, and the terminal result copy.
        assertEquals(
            3,
            Regex("urgeAfterRating = null,").findAll(collapsed).count(),
        )
        assertTrue(collapsed.contains("urgeAfterRating = null "))
    }

    @Test
    fun `the result snapshot still persists the rating`() {
        assertTrue(
            viewModelSource
                .replace(Regex("\\s+"), " ")
                .contains("urgeAfterRating = urgeAfterRating,"),
        )
    }

    @Test
    fun `no board state is written to saved state`() {
        val collapsed = viewModelSource.replace(Regex("\\s+"), " ")

        assertFalse(collapsed.contains("savedStateHandle[\"snake"))
        assertFalse(collapsed.contains("List<SnakeCell>"))
        assertFalse(collapsed.contains("gameState = runtime.state,\" +"))
        // The transient board is never part of a persisted payload.
        assertFalse(collapsed.contains("payload = RecoveryGameResultPayload.Snake( snake"))
    }

    private fun source(relativePath: String): String = File(relativePath).readText()
}
