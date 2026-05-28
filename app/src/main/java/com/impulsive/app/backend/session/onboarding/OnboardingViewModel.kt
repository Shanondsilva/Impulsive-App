package com.impulsive.app.backend.session.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.impulsive.app.backend.data.repository.OnboardingRepository
import com.impulsive.app.backend.domain.model.onboarding.OnboardingQuestionId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OnboardingViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = OnboardingRepository(application)

    val state: StateFlow<OnboardingState> = combine(
        repository.answers,
        repository.isCompleted,
    ) { answers, isCompleted ->
        OnboardingState(
            answers = answers,
            isCompleted = isCompleted,
            isLoading = false,
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = OnboardingState(),
        )

    fun savePersonalization(
        name: String,
        avatarId: String,
        onSaved: () -> Unit,
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return

        viewModelScope.launch {
            repository.setPersonalization(
                name = trimmedName,
                avatarId = avatarId,
            )
            onSaved()
        }
    }

    fun setMultiSelectAnswer(
        questionId: OnboardingQuestionId,
        selectedOptionIds: List<String>,
    ) {
        viewModelScope.launch {
            when (questionId) {
                OnboardingQuestionId.Interrupting -> repository.setInterrupting(selectedOptionIds)
                OnboardingQuestionId.Timing -> repository.setTiming(selectedOptionIds)
                OnboardingQuestionId.Triggers -> repository.setTriggers(selectedOptionIds)
                OnboardingQuestionId.WeekOneGoal -> Unit
            }
        }
    }

    fun setSingleSelectAnswer(
        questionId: OnboardingQuestionId,
        selectedOptionId: String?,
    ) {
        viewModelScope.launch {
            when (questionId) {
                OnboardingQuestionId.WeekOneGoal -> repository.setWeekOneGoal(selectedOptionId)
                OnboardingQuestionId.Interrupting,
                OnboardingQuestionId.Timing,
                OnboardingQuestionId.Triggers,
                -> Unit
            }
        }
    }

    fun setDailyRelapseUrgeCount(count: Int) {
        viewModelScope.launch {
            repository.setDailyRelapseUrgeCount(count)
        }
    }

    fun completeOnboarding(onCompleted: () -> Unit) {
        viewModelScope.launch {
            if (state.value.answers.name.isBlank()) return@launch
            repository.setCompleted(true)
            onCompleted()
        }
    }

    fun clearAnswers() {
        viewModelScope.launch {
            repository.clear()
        }
    }
}
