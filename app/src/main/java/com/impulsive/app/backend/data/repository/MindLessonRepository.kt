package com.impulsive.app.backend.data.repository

import android.content.Context
import com.impulsive.app.backend.data.local.preferences.MindLessonProgressDataSource
import com.impulsive.app.backend.domain.model.tasks.MindLesson
import com.impulsive.app.backend.domain.model.tasks.StarterMindLessons
import kotlinx.coroutines.flow.Flow

class MindLessonRepository(context: Context) {
    private val progressDataSource = MindLessonProgressDataSource(context)

    val lessons: List<MindLesson> = StarterMindLessons
    val completedLessonIds: Flow<Set<String>> = progressDataSource.completedLessonIds

    suspend fun markCompleted(lessonId: String) {
        progressDataSource.markCompleted(lessonId)
    }
}
