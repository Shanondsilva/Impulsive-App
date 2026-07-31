package com.impulsive.app.backend.data.repository.adaptive

import com.impulsive.app.backend.data.local.dao.AdaptivePreferenceDao
import com.impulsive.app.backend.data.local.entity.AdaptivePreferenceEntity
import com.impulsive.app.backend.domain.model.adaptive.AdaptivePreferences
import com.impulsive.app.backend.domain.repository.adaptive.AdaptivePreferenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomAdaptivePreferenceRepository(
    private val dao: AdaptivePreferenceDao,
) : AdaptivePreferenceRepository {
    override fun observe(): Flow<AdaptivePreferences> =
        dao.observe().map { entity ->
            entity?.toDomain() ?: AdaptivePreferences()
        }

    override suspend fun get(): AdaptivePreferences =
        dao.get()?.toDomain() ?: AdaptivePreferences()

    override suspend fun insertDefaults(updatedAtMillis: Long) {
        require(updatedAtMillis >= 0L) { "Update time must not be negative." }
        dao.insertDefaults(updatedAtMillis)
    }

    override suspend fun update(
        preferences: AdaptivePreferences,
        updatedAtMillis: Long,
    ) {
        require(updatedAtMillis >= 0L) { "Update time must not be negative." }
        dao.update(preferences.toEntity(updatedAtMillis))
    }

    override suspend fun resetDefaults(updatedAtMillis: Long) {
        require(updatedAtMillis >= 0L) { "Update time must not be negative." }
        dao.resetDefaults(updatedAtMillis)
    }
}
