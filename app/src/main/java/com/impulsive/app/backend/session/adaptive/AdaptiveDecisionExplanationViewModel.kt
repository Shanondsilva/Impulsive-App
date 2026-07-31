package com.impulsive.app.backend.session.adaptive

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.impulsive.app.backend.data.local.database.AppDatabase
import com.impulsive.app.backend.data.repository.adaptive.RoomAdaptiveDecisionRepository
import com.impulsive.app.backend.domain.engine.adaptive.AdaptiveDecisionExplanation
import com.impulsive.app.backend.domain.engine.adaptive.AdaptiveDecisionExplanationBuilder
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AdaptiveDecisionExplanationUiState(
    val loading: Boolean = true,
    val explanation: AdaptiveDecisionExplanation? = null,
    val missing: Boolean = false,
)

class AdaptiveDecisionExplanationLoader(
    private val loadDecision: suspend (String) -> com.impulsive.app.backend.domain.model.adaptive.AdaptiveDecision?,
) {
    suspend fun load(decisionId: String): AdaptiveDecisionExplanation? {
        if (!decisionId.isCanonicalUuid()) return null
        return loadDecision(decisionId)?.let(AdaptiveDecisionExplanationBuilder::build)
    }
}

class AdaptiveDecisionExplanationViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val decisionId = savedStateHandle.get<String>("decisionId").orEmpty()
    private val decisions = RoomAdaptiveDecisionRepository(
        AppDatabase.getInstance(application).adaptiveDecisionDao(),
    )
    private val loader = AdaptiveDecisionExplanationLoader(decisions::getById)
    private val _state = MutableStateFlow(AdaptiveDecisionExplanationUiState())
    val state: StateFlow<AdaptiveDecisionExplanationUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val explanation = loader.load(decisionId)
                _state.value = if (explanation == null) {
                    AdaptiveDecisionExplanationUiState(loading = false, missing = true)
                } else {
                    AdaptiveDecisionExplanationUiState(
                        loading = false,
                        explanation = explanation,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                _state.value = AdaptiveDecisionExplanationUiState(
                    loading = false,
                    missing = true,
                )
            }
        }
    }

}

private fun String.isCanonicalUuid(): Boolean =
    runCatching { UUID.fromString(this).toString() == lowercase() }.getOrDefault(false)
