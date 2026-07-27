package com.impulsive.app.backend.data.local.onboarding

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.impulsive.app.backend.domain.model.onboarding.OnboardingAnswers
import com.impulsive.app.backend.data.account.isValidGoogleSubjectHash
import com.impulsive.app.backend.data.restore.cloud.requireValidCloudRecoveryOnboardingAnswers
import com.impulsive.app.backend.domain.model.release.minuteOfDayToLocalTime
import com.impulsive.app.backend.domain.model.release.plannedWindowsForDate
import com.impulsive.app.backend.domain.model.release.toMinuteOfDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.onboardingDataStore by preferencesDataStore(
    name = "onboarding_state",
)

class OnboardingPreferencesDataSource(
    context: Context,
) {
    private val dataStore = context.applicationContext.onboardingDataStore

    val answers: Flow<OnboardingAnswers> = dataStore.data.map { preferences ->
        OnboardingAnswers(
            name = preferences[NameKey].orEmpty(),
            avatarId = preferences[AvatarIdKey] ?: DefaultAvatarId,
            interrupting = preferences[InterruptingKey].toAnswerList(),
            timing = preferences[TimingKey].toAnswerList(),
            triggers = preferences[TriggersKey].toAnswerList(),
            weekOneGoal = preferences[WeekOneGoalKey],
            dailyRelapseUrgeCount = preferences[DailyRelapseUrgeCountKey]?.coerceIn(1, 10) ?: 3,
            activeDayStartMinute = preferences[ActiveDayStartMinuteKey]?.coerceIn(0, 24 * 60 - 1) ?: DefaultActiveDayStartMinute,
            activeDayEndMinute = preferences[ActiveDayEndMinuteKey]?.coerceIn(0, 24 * 60 - 1) ?: DefaultActiveDayEndMinute,
            plannedReleaseWindowMinutes = preferences[PlannedReleaseWindowMinutesKey].toMinuteList(),
        )
    }

    val isCompleted: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[OnboardingCompletedKey] ?: false
    }

    val completedAccountUid: Flow<String?> = dataStore.data.map { preferences ->
        preferences[OnboardingCompletedAccountUidKey]
    }

    val completedGoogleSubjectHash: Flow<String?> = dataStore.data.map { preferences ->
        preferences[OnboardingCompletedGoogleSubjectHashKey]
    }

    suspend fun setPersonalization(
        name: String,
        avatarId: String,
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return

        dataStore.edit { preferences ->
            preferences[NameKey] = trimmedName
            preferences[AvatarIdKey] = avatarId.ifBlank { DefaultAvatarId }
        }
    }

    suspend fun setInterrupting(selectedOptionIds: List<String>) {
        dataStore.edit { preferences ->
            preferences[InterruptingKey] = selectedOptionIds.toStoredValue()
        }
    }

    suspend fun setTiming(selectedOptionIds: List<String>) {
        dataStore.edit { preferences ->
            preferences[TimingKey] = selectedOptionIds.toStoredValue()
        }
    }

    suspend fun setTriggers(selectedOptionIds: List<String>) {
        dataStore.edit { preferences ->
            preferences[TriggersKey] = selectedOptionIds.toStoredValue()
        }
    }

    suspend fun setWeekOneGoal(selectedOptionId: String?) {
        dataStore.edit { preferences ->
            if (selectedOptionId == null) {
                preferences.remove(WeekOneGoalKey)
            } else {
                preferences[WeekOneGoalKey] = selectedOptionId
            }
        }
    }

    suspend fun setDailyRelapseUrgeCount(count: Int) {
        dataStore.edit { preferences ->
            val selectedCount = count.coerceIn(1, 10)
            val activeDayStartMinute = preferences[ActiveDayStartMinuteKey]?.coerceIn(0, 24 * 60 - 1)
                ?: DefaultActiveDayStartMinute
            val activeDayEndMinute = preferences[ActiveDayEndMinuteKey]?.coerceIn(0, 24 * 60 - 1)
                ?: DefaultActiveDayEndMinute

            preferences[DailyRelapseUrgeCountKey] = selectedCount
            preferences[PlannedReleaseWindowMinutesKey] = plannedWindowsForDate(
                date = LocalDate.now(),
                count = selectedCount,
                activeDayStart = minuteOfDayToLocalTime(activeDayStartMinute),
                activeDayEnd = minuteOfDayToLocalTime(activeDayEndMinute),
            )
                .map { it.toLocalTime().toMinuteOfDay() }
                .joinToString(StoredListSeparator)
        }
    }

    suspend fun setCompleted(isCompleted: Boolean) {
        setCompletedForAccount(
            isCompleted = isCompleted,
            accountUid = null,
        )
    }

    suspend fun setCompletedForAccount(
        isCompleted: Boolean,
        accountUid: String?,
        googleSubjectHash: String? = null,
    ) {
        val normalizedUid =
            accountUid
                ?.trim()
                ?.takeIf(String::isNotBlank)

        val normalizedGoogleSubjectHash =
            googleSubjectHash
                ?.takeIf(::isValidGoogleSubjectHash)

        dataStore.edit { preferences ->
            preferences[OnboardingCompletedKey] = isCompleted

            if (isCompleted && normalizedUid != null) {
                preferences[OnboardingCompletedAccountUidKey] =
                    normalizedUid

                if (normalizedGoogleSubjectHash != null) {
                    preferences[OnboardingCompletedGoogleSubjectHashKey] =
                        normalizedGoogleSubjectHash
                } else {
                    preferences.remove(
                        OnboardingCompletedGoogleSubjectHashKey,
                    )
                }
            } else {
                preferences.remove(
                    OnboardingCompletedAccountUidKey,
                )
                preferences.remove(
                    OnboardingCompletedGoogleSubjectHashKey,
                )
            }
        }
    }

    internal suspend fun restoreCompletedSnapshotForAccount(
        answers: OnboardingAnswers,
        accountUid: String,
        googleSubjectHash: String?,
    ) {
        requireValidCloudRecoveryOnboardingAnswers(answers)
        val normalizedUid = accountUid.trim()
        require(normalizedUid.isNotBlank() && normalizedUid.length <= 128) {
            "Verified Firebase UID is invalid for onboarding restore."
        }
        val normalizedGoogleSubjectHash =
            googleSubjectHash?.takeIf(::isValidGoogleSubjectHash)

        dataStore.edit { preferences ->
            preferences[NameKey] = answers.name
            preferences[AvatarIdKey] = answers.avatarId
            preferences.putOrRemove(InterruptingKey, answers.interrupting.toStoredValue())
            preferences.putOrRemove(TimingKey, answers.timing.toStoredValue())
            preferences.putOrRemove(TriggersKey, answers.triggers.toStoredValue())
            preferences.putOrRemove(WeekOneGoalKey, answers.weekOneGoal)
            preferences[DailyRelapseUrgeCountKey] = answers.dailyRelapseUrgeCount
            preferences[ActiveDayStartMinuteKey] = answers.activeDayStartMinute
            preferences[ActiveDayEndMinuteKey] = answers.activeDayEndMinute
            preferences.putOrRemove(
                PlannedReleaseWindowMinutesKey,
                answers.plannedReleaseWindowMinutes.joinToString(StoredListSeparator),
            )
            preferences[OnboardingCompletedKey] = true
            preferences[OnboardingCompletedAccountUidKey] = normalizedUid
            preferences.putOrRemove(
                OnboardingCompletedGoogleSubjectHashKey,
                normalizedGoogleSubjectHash,
            )
        }
    }

    suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    private fun String?.toAnswerList(): List<String> {
        if (isNullOrBlank()) return emptyList()
        return split(StoredListSeparator).filter(String::isNotBlank)
    }

    private fun List<String>.toStoredValue(): String = joinToString(StoredListSeparator)

    private fun androidx.datastore.preferences.core.MutablePreferences.putOrRemove(
        key: androidx.datastore.preferences.core.Preferences.Key<String>,
        value: String?,
    ) {
        if (value.isNullOrEmpty()) {
            remove(key)
        } else {
            this[key] = value
        }
    }

    private fun String?.toMinuteList(): List<Int> {
        if (isNullOrBlank()) return emptyList()
        return split(StoredListSeparator)
            .mapNotNull { it.toIntOrNull()?.coerceIn(0, 24 * 60 - 1) }
    }

    private companion object {
        const val StoredListSeparator = "\u001F"
        const val DefaultAvatarId = "wave"

        val NameKey = stringPreferencesKey("name")
        val AvatarIdKey = stringPreferencesKey("avatar_id")
        val InterruptingKey = stringPreferencesKey("interrupting")
        val TimingKey = stringPreferencesKey("timing")
        val TriggersKey = stringPreferencesKey("triggers")
        val WeekOneGoalKey = stringPreferencesKey("week_one_goal")
        val DailyRelapseUrgeCountKey = intPreferencesKey("daily_relapse_urge_count")
        val ActiveDayStartMinuteKey = intPreferencesKey("active_day_start_minute")
        val ActiveDayEndMinuteKey = intPreferencesKey("active_day_end_minute")
        val PlannedReleaseWindowMinutesKey = stringPreferencesKey("planned_release_window_minutes")
        val OnboardingCompletedKey = booleanPreferencesKey("onboarding_completed")
        val OnboardingCompletedAccountUidKey =
            stringPreferencesKey("onboarding_completed_account_uid")
        val OnboardingCompletedGoogleSubjectHashKey =
            stringPreferencesKey("onboarding_completed_google_subject_hash")
        const val DefaultActiveDayStartMinute = 7 * 60
        const val DefaultActiveDayEndMinute = 23 * 60
    }
}
