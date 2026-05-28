package com.impulsive.app.backend.data.repository

import android.content.Context
import com.impulsive.app.backend.data.local.onboarding.OnboardingPreferencesDataSource
import com.impulsive.app.backend.domain.model.onboarding.OnboardingAnswers
import kotlinx.coroutines.flow.Flow

class OnboardingRepository(
    context: Context,
) {
    private val dataSource = OnboardingPreferencesDataSource(context)

    val answers: Flow<OnboardingAnswers> = dataSource.answers
    val isCompleted: Flow<Boolean> = dataSource.isCompleted

    suspend fun setPersonalization(
        name: String,
        avatarId: String,
    ) {
        dataSource.setPersonalization(
            name = name,
            avatarId = avatarId,
        )
    }

    suspend fun setInterrupting(selectedOptionIds: List<String>) {
        dataSource.setInterrupting(selectedOptionIds)
    }

    suspend fun setTiming(selectedOptionIds: List<String>) {
        dataSource.setTiming(selectedOptionIds)
    }

    suspend fun setTriggers(selectedOptionIds: List<String>) {
        dataSource.setTriggers(selectedOptionIds)
    }

    suspend fun setWeekOneGoal(selectedOptionId: String?) {
        dataSource.setWeekOneGoal(selectedOptionId)
    }

    suspend fun setDailyRelapseUrgeCount(count: Int) {
        dataSource.setDailyRelapseUrgeCount(count)
    }

    suspend fun setCompleted(isCompleted: Boolean) {
        dataSource.setCompleted(isCompleted)
    }

    suspend fun clear() {
        dataSource.clear()
    }
}
