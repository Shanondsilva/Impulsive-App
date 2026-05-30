package com.impulsive.app.backend.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.resetReadProgressDataStore by preferencesDataStore(name = "reset_read_progress")

class ResetReadProgressDataSource(private val context: Context) {
    val readArticleIds: Flow<Set<String>> = context.resetReadProgressDataStore.data.map { preferences ->
        preferences[ReadArticleIdsKey] ?: emptySet()
    }

    suspend fun markRead(articleId: String) {
        context.resetReadProgressDataStore.edit { preferences ->
            val current = preferences[ReadArticleIdsKey] ?: emptySet()
            preferences[ReadArticleIdsKey] = current + articleId
        }
    }

    private companion object {
        val ReadArticleIdsKey = stringSetPreferencesKey("read_article_ids")
    }
}
