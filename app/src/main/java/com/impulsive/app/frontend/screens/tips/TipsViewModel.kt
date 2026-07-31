package com.impulsive.app.frontend.screens.tips

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.impulsive.app.R
import com.impulsive.app.backend.data.local.preferences.TipsPreferencesDataSource
import com.impulsive.app.backend.data.local.preferences.TipsPreferencesState
import com.impulsive.app.backend.domain.model.onboarding.OnboardingAnswers
import com.impulsive.app.backend.domain.tips.ImpulsiveTipId
import com.impulsive.app.backend.domain.tips.TipFeature
import com.impulsive.app.backend.domain.tips.TipSelectionContext
import com.impulsive.app.backend.domain.tips.TipSelectionPolicy
import com.impulsive.app.backend.domain.tips.TipSelectionReason
import com.impulsive.app.backend.domain.tips.TipSelectionResult
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private data class TipsRuntimeContext(
    val onboardingAnswerIds: Set<String> = emptySet(),
    val opportunities: Set<TipFeature> = emptySet(),
    val availableFeatures: Set<TipFeature> = TipFeature.entries.toSet(),
    val shownThisSession: ImpulsiveTipId? = null,
)

class TipsViewModel(application: Application) : AndroidViewModel(application) {
    private val catalogue = ImpulsiveTipCatalogue(application).tips
    private val preferences = TipsPreferencesDataSource(application)
    private val policy = TipSelectionPolicy()
    private val runtime = MutableStateFlow(TipsRuntimeContext())

    val state: StateFlow<TipsHomeUiState> = combine(preferences.state, runtime) {
            stored,
            current,
        ->
        val selected = select(stored, current)
        TipsHomeUiState(
            loading = false,
            catalogue = catalogue.filterNot { it.id in stored.dismissedTipIds },
            currentTip = selected.tip,
            currentReason = selected.reason,
            whyYouAreSeeingThis = when (selected.reason) {
                TipSelectionReason.OnboardingMatch ->
                    getApplication<Application>().getString(R.string.tips_match_onboarding)
                TipSelectionReason.ConfigurationOpportunity ->
                    getApplication<Application>().getString(R.string.tips_match_configuration)
                else -> null
            },
            dismissedCount = stored.dismissedTipIds.size,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = TipsHomeUiState(),
    )

    fun updateContext(
        answers: OnboardingAnswers,
        configurationOpportunities: Set<TipFeature> = emptySet(),
        availableFeatures: Set<TipFeature> = TipFeature.entries.toSet(),
    ) {
        runtime.value = runtime.value.copy(
            onboardingAnswerIds = buildSet {
                addAll(answers.interrupting)
                addAll(answers.triggers)
                addAll(answers.timing)
                answers.weekOneGoal?.let(::add)
            },
            opportunities = configurationOpportunities,
            availableFeatures = availableFeatures,
        )
    }

    fun ensureHomeTip() {
        viewModelScope.launch {
            if (preferences.state.first().currentHomeTipId == null) {
                recordCurrentSelection()
            }
        }
    }

    fun rotate() {
        viewModelScope.launch {
            val current = state.value.currentTip?.id
            runtime.value = runtime.value.copy(shownThisSession = current)
            recordCurrentSelection()
            runtime.value = runtime.value.copy(shownThisSession = null)
        }
    }

    fun markViewed(tipId: ImpulsiveTipId) {
        viewModelScope.launch { preferences.markViewed(tipId) }
    }

    fun dismiss(tipId: ImpulsiveTipId) {
        viewModelScope.launch {
            preferences.dismiss(tipId)
            runtime.value = runtime.value.copy(shownThisSession = tipId)
            recordCurrentSelection()
            runtime.value = runtime.value.copy(shownThisSession = null)
        }
    }

    fun resetHiddenTips() {
        viewModelScope.launch { preferences.resetHiddenTips() }
    }

    fun findTip(tipId: ImpulsiveTipId) = catalogue.firstOrNull { it.id == tipId }

    private suspend fun recordCurrentSelection() {
        val stored = preferences.state.first()
        val selected = select(stored, runtime.value).tip ?: return
        preferences.recordShown(
            tipId = selected.id,
            epochDay = LocalDate.now().toEpochDay(),
            nowMillis = System.currentTimeMillis(),
        )
    }

    private fun select(
        stored: TipsPreferencesState,
        current: TipsRuntimeContext,
    ): TipSelectionResult {
        val currentTip = stored.currentHomeTipId
            ?.takeUnless { it == current.shownThisSession }
            ?.let { id -> catalogue.firstOrNull { it.id == id } }
            ?.takeUnless { it.id in stored.dismissedTipIds || it.obsolete || !it.available }
            ?.takeIf { it.requiredFeature == null || it.requiredFeature in current.availableFeatures }
        if (currentTip != null) {
            val matched = currentTip.audienceTags.any(
                policy.audienceTagsFor(current.onboardingAnswerIds)::contains,
            )
            return TipSelectionResult(
                tip = currentTip,
                reason = if (matched) {
                    TipSelectionReason.OnboardingMatch
                } else {
                    TipSelectionReason.General
                },
                whyYouAreSeeingThis = if (matched) {
                    "This idea matches a choice you made during your private on-device setup."
                } else {
                    null
                },
            )
        }
        return policy.select(
            catalogue = catalogue,
            context = TipSelectionContext(
            onboardingAnswerIds = current.onboardingAnswerIds,
            configurationOpportunities = current.opportunities,
            availableFeatures = current.availableFeatures,
            viewedTipIds = stored.viewedTipIds,
            dismissedTipIds = stored.dismissedTipIds,
            lastShownEpochDayByTip = stored.lastShownEpochDayByTip,
            tipShownThisSession = current.shownThisSession,
        ),
        )
    }
}
