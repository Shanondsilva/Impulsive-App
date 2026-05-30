package com.impulsive.app.backend.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.mindLessonProgressDataStore by preferencesDataStore(name = "mind_lesson_progress")

class MindLessonProgressDataSource(private val context: Context) {
    val completedLessonIds: Flow<Set<String>> = context.mindLessonProgressDataStore.data.map { preferences ->
        preferences[CompletedLessonIdsKey] ?: emptySet()
    }

    suspend fun markCompleted(lessonId: String) {
        context.mindLessonProgressDataStore.edit { preferences ->
            val current = preferences[CompletedLessonIdsKey] ?: emptySet()
            preferences[CompletedLessonIdsKey] = current + lessonId
        }
    }

    private companion object {
        val CompletedLessonIdsKey = stringSetPreferencesKey("completed_lesson_ids")
    }
}
