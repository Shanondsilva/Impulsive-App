package com.impulsive.app.backend.session.adaptive

import androidx.lifecycle.SavedStateHandle
import com.impulsive.app.backend.domain.model.adaptive.MomentCue

/**
 * Presentation-only state. Values stay in SavedStateHandle and never enter a
 * route or an unencrypted preference store.
 */
class AdaptiveOptionalPromptStateStore(
    private val savedStateHandle: SavedStateHandle,
) {
    fun selectCue(cue: MomentCue) {
        savedStateHandle[CuePromptStateKey] = OptionalPromptUiState.Selected.name
        savedStateHandle[SelectedCueKey] = cue.name
    }

    fun skipCue() {
        savedStateHandle[CuePromptStateKey] = OptionalPromptUiState.Skipped.name
        savedStateHandle.remove<String>(SelectedCueKey)
    }

    fun reopenCue() {
        savedStateHandle[CuePromptStateKey] = OptionalPromptUiState.Unanswered.name
        savedStateHandle.remove<String>(SelectedCueKey)
    }

    fun selectUrge(rating: Int): Boolean {
        if (rating !in 0..10) return false
        savedStateHandle[UrgePromptStateKey] = OptionalPromptUiState.Selected.name
        savedStateHandle[UrgeRatingKey] = rating
        return true
    }

    fun skipUrge() {
        savedStateHandle[UrgePromptStateKey] = OptionalPromptUiState.Skipped.name
        savedStateHandle.remove<Int>(UrgeRatingKey)
    }

    fun reopenUrge() {
        savedStateHandle[UrgePromptStateKey] = OptionalPromptUiState.Unanswered.name
        savedStateHandle.remove<Int>(UrgeRatingKey)
    }

    fun cuePromptState(fallback: MomentCue?): OptionalPromptUiState =
        savedStateHandle.get<String>(CuePromptStateKey)?.let { stored ->
            OptionalPromptUiState.entries.firstOrNull { it.name == stored }
        } ?: if (fallback == null) {
            OptionalPromptUiState.Unanswered
        } else {
            OptionalPromptUiState.Selected
        }

    fun selectedCue(fallback: MomentCue?): MomentCue? =
        if (cuePromptState(fallback) == OptionalPromptUiState.Selected) {
            savedStateHandle.get<String>(SelectedCueKey)?.let { stored ->
                MomentCue.entries.firstOrNull { it.name == stored }
            } ?: fallback
        } else {
            null
        }

    fun urgePromptState(fallback: Int?): OptionalPromptUiState =
        savedStateHandle.get<String>(UrgePromptStateKey)?.let { stored ->
            OptionalPromptUiState.entries.firstOrNull { it.name == stored }
        } ?: if (fallback == null) {
            OptionalPromptUiState.Unanswered
        } else {
            OptionalPromptUiState.Selected
        }

    fun urgeRating(fallback: Int?): Int? =
        if (urgePromptState(fallback) == OptionalPromptUiState.Selected) {
            savedStateHandle.get<Int>(UrgeRatingKey)?.takeIf { it in 0..10 }
                ?: fallback?.takeIf { it in 0..10 }
        } else {
            null
        }

    companion object {
        const val CuePromptStateKey = "adaptiveCuePromptState"
        const val SelectedCueKey = "adaptiveSelectedCue"
        const val UrgePromptStateKey = "adaptiveUrgePromptState"
        const val UrgeRatingKey = "adaptiveUrgeRating"
    }
}
