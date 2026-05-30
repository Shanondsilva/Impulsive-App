package com.impulsive.app.backend.data.repository

import android.content.Context
import com.impulsive.app.backend.data.local.preferences.ResetReadProgressDataSource
import com.impulsive.app.backend.domain.model.tasks.ResetReadArticle
import com.impulsive.app.backend.domain.model.tasks.StarterResetReadArticles
import kotlinx.coroutines.flow.Flow

class ResetReadRepository(context: Context) {
    private val progressDataSource = ResetReadProgressDataSource(context)

    val articles: List<ResetReadArticle> = StarterResetReadArticles
    val readArticleIds: Flow<Set<String>> = progressDataSource.readArticleIds

    suspend fun markRead(articleId: String) {
        progressDataSource.markRead(articleId)
    }
}
