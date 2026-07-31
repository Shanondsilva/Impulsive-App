package com.impulsive.app.backend.data.repository.adaptive

import androidx.room.withTransaction
import com.impulsive.app.backend.data.local.database.AppDatabase
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveDataRepository

class RoomAdaptiveDataRepository(
    private val database: AppDatabase,
) : AdaptiveDataRepository {
    override suspend fun clearPersonalLearning() {
        database.withTransaction {
            database.adaptiveDecisionDao().clearLearningHistory()
            database.momentPlanRehearsalDao().clearAll()
            database.pathShiftCycleDao().clearAll()
            database.protectionCoachSuggestionDao().clearCoachHistory()
        }
    }

    override suspend fun clearAllAdaptiveData() {
        database.withTransaction {
            database.adaptiveDecisionDao().clearLearningHistory()
            database.momentPlanRehearsalDao().clearAll()
            database.pathShiftCycleDao().clearAll()
            database.protectionCoachSuggestionDao().clearAllCoachData()
            database.momentPlanDao().clearAll()
            database.adaptivePreferenceDao().clearAll()
        }
    }
}
