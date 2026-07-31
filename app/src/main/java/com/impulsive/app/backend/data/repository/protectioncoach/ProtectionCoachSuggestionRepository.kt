package com.impulsive.app.backend.data.repository.protectioncoach

import com.impulsive.app.backend.data.local.dao.ProtectionCoachSuggestionDao
import com.impulsive.app.backend.domain.protectioncoach.ProtectionCoachSuggestion
import com.impulsive.app.backend.domain.protectioncoach.ProtectionCoachSuggestionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProtectionCoachSuggestionRepository(
    private val dao: ProtectionCoachSuggestionDao,
) {
    suspend fun insertOnce(suggestion: ProtectionCoachSuggestion): Boolean =
        dao.insertOnce(suggestion.toEntity()) != -1L

    suspend fun getById(suggestionId: String): ProtectionCoachSuggestion? =
        dao.getById(suggestionId)?.toDomain()

    fun observeActive(nowMillis: Long): Flow<List<ProtectionCoachSuggestion>> =
        dao.observeActiveSuggestions(nowMillis).map { rows -> rows.map { it.toDomain() } }

    suspend fun markPresented(suggestionId: String, atMillis: Long): Boolean =
        dao.markPresentedOnce(suggestionId, atMillis) == 1

    suspend fun accept(suggestionId: String, atMillis: Long): Boolean =
        dao.acceptOnce(suggestionId, atMillis) == 1

    suspend fun acceptWithEdits(
        suggestionId: String,
        atMillis: Long,
        acceptedStartMinute: Int,
        acceptedEndMinute: Int,
    ): Boolean =
        dao.acceptWithEditsOnce(
            suggestionId,
            atMillis,
            acceptedStartMinute,
            acceptedEndMinute,
        ) == 1

    suspend fun dismiss(suggestionId: String, atMillis: Long): Boolean =
        dao.dismissOnce(suggestionId, atMillis) == 1

    suspend fun suppress(suggestionId: String, atMillis: Long): Boolean =
        dao.suppressOnce(suggestionId, atMillis) == 1

    suspend fun expireDue(nowMillis: Long): Int = dao.expireDue(nowMillis)

    suspend fun findEquivalentActive(
        type: ProtectionCoachSuggestionType,
        broadWindowStartMinute: Int?,
        broadWindowEndMinute: Int?,
    ): ProtectionCoachSuggestion? =
        dao.findEquivalentActiveSuggestion(
            type.name,
            broadWindowStartMinute,
            broadWindowEndMinute,
        )?.toDomain()

    suspend fun clearCoachHistory(): Int = dao.clearCoachHistory()

    suspend fun clearAllCoachData(): Int = dao.clearAllCoachData()
}
