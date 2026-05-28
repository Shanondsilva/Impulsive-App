package com.impulsive.app.backend.session.onboarding

import com.impulsive.app.backend.domain.model.onboarding.OnboardingAnswers

data class OnboardingState(
    val answers: OnboardingAnswers = OnboardingAnswers(),
    val isCompleted: Boolean = false,
    val isLoading: Boolean = true,
)
