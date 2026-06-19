package com.impulsive.app.backend.data.repository

import android.content.Context
import com.impulsive.app.backend.data.local.preferences.ResetReadProgressDataSource
import com.impulsive.app.backend.domain.model.tasks.ResetReadArticle
import com.impulsive.app.backend.domain.model.tasks.ResetReadArticleExposureRecord
import com.impulsive.app.backend.domain.model.tasks.ResetReadSessionRecord
import com.impulsive.app.backend.domain.model.tasks.StarterResetReadArticles
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

class ResetReadRepository(context: Context) {
    private val progressDataSource = ResetReadProgressDataSource(context)

    val articles: List<ResetReadArticle> = StarterResetReadArticles
    val readArticleIds: Flow<Set<String>> = progressDataSource.readArticleIds
    val sessions: Flow<List<ResetReadSessionRecord>> = progressDataSource.sessions
    val articleExposures: Flow<List<ResetReadArticleExposureRecord>> = progressDataSource.articleExposures

    suspend fun markRead(articleId: String) {
        progressDataSource.markRead(articleId)
    }

    suspend fun recordSession(session: ResetReadSessionRecord) {
        progressDataSource.recordSession(session)
    }

    suspend fun recordArticleShown(articleId: String, shownAt: LocalDateTime) {
        progressDataSource.recordArticleShown(articleId = articleId, shownAt = shownAt)
    }
}
