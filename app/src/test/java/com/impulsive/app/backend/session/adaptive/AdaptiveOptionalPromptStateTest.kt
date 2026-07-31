package com.impulsive.app.backend.session.adaptive

import androidx.lifecycle.SavedStateHandle
import com.impulsive.app.backend.domain.model.adaptive.MomentCue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveOptionalPromptStateTest {
    @Test
    fun cueSkipIsExplicitReopenableAndSurvivesRecreation() {
        val handle = SavedStateHandle()
        val firstInstance = AdaptiveOptionalPromptStateStore(handle)
        assertEquals(OptionalPromptUiState.Unanswered, firstInstance.cuePromptState(null))

        firstInstance.skipCue()
        val recreated = AdaptiveOptionalPromptStateStore(handle)
        assertEquals(OptionalPromptUiState.Skipped, recreated.cuePromptState(null))
        assertNull(recreated.selectedCue(MomentCue.Stress))

        recreated.reopenCue()
        assertEquals(OptionalPromptUiState.Unanswered, recreated.cuePromptState(null))
        recreated.selectCue(MomentCue.Boredom)
        assertEquals(OptionalPromptUiState.Selected, recreated.cuePromptState(null))
        assertEquals(MomentCue.Boredom, recreated.selectedCue(null))
    }

    @Test
    fun urgeSkipIsExplicitReopenableAndSurvivesRecreation() {
        val handle = SavedStateHandle()
        val firstInstance = AdaptiveOptionalPromptStateStore(handle)
        firstInstance.skipUrge()

        val recreated = AdaptiveOptionalPromptStateStore(handle)
        assertEquals(OptionalPromptUiState.Skipped, recreated.urgePromptState(null))
        assertNull(recreated.urgeRating(8))

        recreated.reopenUrge()
        assertEquals(OptionalPromptUiState.Unanswered, recreated.urgePromptState(null))
        assertTrue(recreated.selectUrge(10))
        assertEquals(OptionalPromptUiState.Selected, recreated.urgePromptState(null))
        assertEquals(10, recreated.urgeRating(null))
    }

    @Test
    fun invalidUrgeRatingIsNotStored() {
        val handle = SavedStateHandle()
        val store = AdaptiveOptionalPromptStateStore(handle)

        assertFalse(store.selectUrge(-1))
        assertFalse(store.selectUrge(11))
        assertEquals(OptionalPromptUiState.Unanswered, store.urgePromptState(null))
        assertNull(store.urgeRating(null))
    }
}
