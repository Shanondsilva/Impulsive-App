package com.impulsive.app.backend.session.progress

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.impulsive.app.backend.data.repository.OnboardingRepository
import com.impulsive.app.backend.data.repository.TaperRepository
import com.impulsive.app.backend.data.repository.UrgeEventRepository
import com.impulsive.app.backend.data.repository.WindowOutcomeRepository
import com.impulsive.app.backend.domain.model.release.TaperEvaluator
import com.impulsive.app.backend.domain.model.release.TaperProposal
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class TaperViewModel(application: Application) : AndroidViewModel(application) {
    private val onboardingRepository = OnboardingRepository(application)
    private val taperRepository = TaperRepository(application)
    private val windowOutcomeRepository = WindowOutcomeRepository(application)
    private val urgeEventRepository = UrgeEventRepository(application)

    val proposal: StateFlow<TaperProposal?> = combine(
        onboardingRepository.answers,
        taperRepository.state,
        windowOutcomeRepository.outcomes,
        urgeEventRepository.events,
    ) { answers, taperState, windowOutcomes, urgeEvents ->
        TaperEvaluator.evaluate(
            now = LocalDateTime.now(),
            currentDailyUrgeCount = answers.dailyRelapseUrgeCount,
            windowOutcomes = windowOutcomes,
            urgeEvents = urgeEvents,
            lastAcceptedAt = taperState.lastAcceptedAt,
            lastDeclinedAt = taperState.lastDeclinedAt,
            proposalsDisabled = taperState.proposalsDisabled,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    val taperSuggestionsEnabled: StateFlow<Boolean> = taperRepository.state
        .map { !it.proposalsDisabled }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = true,
        )

    fun acceptProposal(proposal: TaperProposal) {
        viewModelScope.launch {
            onboardingRepository.setDailyRelapseUrgeCount(proposal.toCount)
            taperRepository.recordAccepted(proposal)
        }
    }

    fun declineProposal() {
        viewModelScope.launch {
            taperRepository.recordDeclined()
        }
    }

    fun disableProposals() {
        viewModelScope.launch {
            taperRepository.setProposalsDisabled(true)
        }
    }

    fun setTaperSuggestionsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            taperRepository.setProposalsDisabled(!enabled)
        }
    }
}
