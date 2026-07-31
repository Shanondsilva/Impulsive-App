package com.impulsive.app.backend.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.protectionCoachDataStore by preferencesDataStore(
    name = "protection_coach_state",
)

data class ProtectionCoachPreferencesState(
    val suggestedSetupReviewed: Boolean = false,
    val onboardingColdStartPriorUsed: Boolean = false,
    val timingSuggestionsSuppressed: Boolean = false,
    val lastTimingSuggestionDismissedAtMillis: Long? = null,
    val plusPromotionLastShownAtMillis: Long? = null,
    val plusPromotionShownThisSession: Boolean = false,
)

class ProtectionCoachPreferencesDataSource(context: Context) {
    private val dataStore = context.applicationContext.protectionCoachDataStore

    val state: Flow<ProtectionCoachPreferencesState> = dataStore.data.map { preferences ->
        ProtectionCoachPreferencesState(
            suggestedSetupReviewed = preferences[SuggestedSetupReviewedKey] ?: false,
            onboardingColdStartPriorUsed = preferences[OnboardingColdStartPriorUsedKey] ?: false,
            timingSuggestionsSuppressed = preferences[TimingSuggestionsSuppressedKey] ?: false,
            lastTimingSuggestionDismissedAtMillis = preferences[LastTimingSuggestionDismissedKey],
            plusPromotionLastShownAtMillis = preferences[PlusPromotionLastShownKey],
            plusPromotionShownThisSession = preferences[PlusPromotionShownThisSessionKey] ?: false,
        )
    }

    suspend fun markSuggestedSetupReviewed() {
        dataStore.edit { it[SuggestedSetupReviewedKey] = true }
    }

    suspend fun markOnboardingColdStartPriorUsed() {
        dataStore.edit { it[OnboardingColdStartPriorUsedKey] = true }
    }

    suspend fun dismissTimingSuggestion(atMillis: Long) {
        dataStore.edit { it[LastTimingSuggestionDismissedKey] = atMillis }
    }

    suspend fun suppressTimingSuggestions() {
        dataStore.edit { it[TimingSuggestionsSuppressedKey] = true }
    }

    suspend fun markPlusPromotionShown(atMillis: Long) {
        dataStore.edit {
            it[PlusPromotionLastShownKey] = atMillis
            it[PlusPromotionShownThisSessionKey] = true
        }
    }

    suspend fun resetSessionPromotionCap() {
        dataStore.edit { it[PlusPromotionShownThisSessionKey] = false }
    }

    suspend fun clearLearningState() {
        dataStore.edit {
            it.remove(OnboardingColdStartPriorUsedKey)
            it.remove(TimingSuggestionsSuppressedKey)
            it.remove(LastTimingSuggestionDismissedKey)
            it.remove(PlusPromotionLastShownKey)
            it.remove(PlusPromotionShownThisSessionKey)
        }
    }

    suspend fun clearAllCoachPreferences() {
        dataStore.edit { it.clear() }
    }

    private companion object {
        val SuggestedSetupReviewedKey = booleanPreferencesKey("suggested_setup_reviewed")
        val OnboardingColdStartPriorUsedKey =
            booleanPreferencesKey("onboarding_cold_start_prior_used")
        val TimingSuggestionsSuppressedKey =
            booleanPreferencesKey("timing_suggestions_suppressed")
        val LastTimingSuggestionDismissedKey =
            longPreferencesKey("last_timing_suggestion_dismissed_at_millis")
        val PlusPromotionLastShownKey =
            longPreferencesKey("plus_promotion_last_shown_at_millis")
        val PlusPromotionShownThisSessionKey =
            booleanPreferencesKey("plus_promotion_shown_this_session")
    }
}
