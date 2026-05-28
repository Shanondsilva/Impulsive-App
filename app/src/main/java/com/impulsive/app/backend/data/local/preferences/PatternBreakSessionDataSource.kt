package com.impulsive.app.backend.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.impulsive.app.backend.domain.model.tasks.PatternBreakSession

private val Context.patternBreakDataStore by preferencesDataStore(name = "pattern_break_sessions")

class PatternBreakSessionDataSource(
    context: Context,
) {
    private val dataStore = context.applicationContext.patternBreakDataStore

    suspend fun saveSession(session: PatternBreakSession) {
        dataStore.edit { preferences ->
            preferences[SessionCountKey] = (preferences[SessionCountKey] ?: 0) + 1
            preferences[TaskTypeKey] = "PATTERN_BREAK"
            preferences[StartedAtKey] = session.startedAt.toString()
            preferences[EndedAtKey] = session.endedAt.toString()
            preferences[DurationSecKey] = session.durationSec
            preferences[ScoreKey] = session.score
            preferences[AccuracyKey] = session.accuracy
            preferences[BestStreakKey] = session.bestStreak
            preferences[AttemptsKey] = session.attempts
            preferences[CorrectAnswersKey] = session.correctAnswers
            preferences[ValidCompletionKey] = if (session.validCompletion) 1 else 0
            preferences[RewardWaitReductionMinutesKey] = session.rewardWaitReductionMinutes
            preferences[RewardLevelPointsKey] = session.rewardLevelPoints
            preferences[WasFirstTimeRewardKey] = if (session.wasFirstTimeReward) 1 else 0
            preferences[WasSameDayRepeatKey] = if (session.wasSameDayRepeat) 1 else 0
            preferences[AppliedWaitReductionKey] = if (session.appliedWaitReduction) 1 else 0
        }
    }

    private companion object {
        val SessionCountKey = intPreferencesKey("session_count")
        val TaskTypeKey = stringPreferencesKey("task_type")
        val StartedAtKey = stringPreferencesKey("started_at")
        val EndedAtKey = stringPreferencesKey("ended_at")
        val DurationSecKey = intPreferencesKey("duration_sec")
        val ScoreKey = intPreferencesKey("score")
        val AccuracyKey = intPreferencesKey("accuracy")
        val BestStreakKey = intPreferencesKey("best_streak")
        val AttemptsKey = intPreferencesKey("attempts")
        val CorrectAnswersKey = intPreferencesKey("correct_answers")
        val ValidCompletionKey = intPreferencesKey("valid_completion")
        val RewardWaitReductionMinutesKey = intPreferencesKey("reward_wait_reduction_minutes")
        val RewardLevelPointsKey = intPreferencesKey("reward_level_points")
        val WasFirstTimeRewardKey = intPreferencesKey("was_first_time_reward")
        val WasSameDayRepeatKey = intPreferencesKey("was_same_day_repeat")
        val AppliedWaitReductionKey = intPreferencesKey("applied_wait_reduction")
    }
}
