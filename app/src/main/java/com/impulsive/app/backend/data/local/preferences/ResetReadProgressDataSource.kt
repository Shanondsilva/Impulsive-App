package com.impulsive.app.backend.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.impulsive.app.backend.domain.model.tasks.ResetReadArticleExposureRecord
import com.impulsive.app.backend.domain.model.tasks.ResetReadSessionRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime

private const val MaxStoredResetReadSessions = 100
private const val MaxStoredResetReadArticleExposures = 100
private const val RecordSeparator = "\u001E"
private const val FieldSeparator = "\u001F"

private val Context.resetReadProgressDataStore by preferencesDataStore(name = "reset_read_progress")

class ResetReadProgressDataSource(private val context: Context) {
    val readArticleIds: Flow<Set<String>> = context.resetReadProgressDataStore.data.map { preferences ->
        preferences[ReadArticleIdsKey] ?: emptySet()
    }

    val sessions: Flow<List<ResetReadSessionRecord>> = context.resetReadProgressDataStore.data.map { preferences ->
        preferences[SessionsKey]
            .orEmpty()
            .split(RecordSeparator)
            .mapNotNull { encoded -> encoded.decodeResetReadSessionOrNull() }
            .sortedByDescending { it.completedAt }
    }

    val articleExposures: Flow<List<ResetReadArticleExposureRecord>> = context.resetReadProgressDataStore.data.map { preferences ->
        preferences[ArticleExposuresKey]
            .orEmpty()
            .split(RecordSeparator)
            .mapNotNull { encoded -> encoded.decodeResetReadArticleExposureOrNull() }
            .sortedByDescending { it.shownAt }
    }

    suspend fun markRead(articleId: String) {
        context.resetReadProgressDataStore.edit { preferences ->
            val current = preferences[ReadArticleIdsKey] ?: emptySet()
            preferences[ReadArticleIdsKey] = current + articleId
        }
    }

    suspend fun recordSession(session: ResetReadSessionRecord) {
        context.resetReadProgressDataStore.edit { preferences ->
            val current = preferences[SessionsKey]
                .orEmpty()
                .split(RecordSeparator)
                .mapNotNull { it.decodeResetReadSessionOrNull() }
                .toMutableList()

            val existingIndex = current.indexOfFirst { it.id == session.id }
            if (existingIndex >= 0) {
                current[existingIndex] = session
            } else {
                current += session
            }

            preferences[SessionsKey] = current
                .sortedByDescending { it.completedAt }
                .take(MaxStoredResetReadSessions)
                .joinToString(RecordSeparator) { it.encode() }
        }
    }

    suspend fun recordArticleShown(articleId: String, shownAt: LocalDateTime) {
        context.resetReadProgressDataStore.edit { preferences ->
            val current = preferences[ArticleExposuresKey]
                .orEmpty()
                .split(RecordSeparator)
                .mapNotNull { it.decodeResetReadArticleExposureOrNull() }
                .toMutableList()

            current += ResetReadArticleExposureRecord(
                id = System.currentTimeMillis(),
                articleId = articleId,
                shownAt = shownAt,
            )

            preferences[ArticleExposuresKey] = current
                .sortedByDescending { it.shownAt }
                .take(MaxStoredResetReadArticleExposures)
                .joinToString(RecordSeparator) { it.encodeExposure() }
        }
    }

    private fun ResetReadSessionRecord.encode(): String = listOf(
        id.toString(),
        articleId,
        articleTitle,
        startedAt.toString(),
        completedAt.toString(),
        selectedDurationSeconds.toString(),
        requiredDurationSeconds.toString(),
        secondsSpent.toString(),
        selectedOptionIndex.toString(),
        if (validCompletion) "1" else "0",
        answerText,
        completionQuality,
        failureReason.orEmpty(),
        rewardApplied?.let { if (it) "1" else "0" }.orEmpty(),
        waitCutMinutes?.toString().orEmpty(),
        helpfulnessRating?.toString().orEmpty(),
    ).joinToString(FieldSeparator) { value ->
        value
            .replace(RecordSeparator, " ")
            .replace(FieldSeparator, " ")
    }

    private fun String.decodeResetReadSessionOrNull(): ResetReadSessionRecord? {
        if (isBlank()) return null
        val parts = split(FieldSeparator)
        if (parts.size < 10) return null

        return runCatching {
            val hasOldPersonalStateFields = parts.size >= 20
            val answerTextIndex = if (hasOldPersonalStateFields) 15 else 10
            val completionQualityIndex = if (hasOldPersonalStateFields) 16 else 11
            val failureReasonIndex = if (hasOldPersonalStateFields) 17 else 12
            val rewardAppliedIndex = if (hasOldPersonalStateFields) 18 else 13
            val waitCutMinutesIndex = if (hasOldPersonalStateFields) 19 else 14
            val helpfulnessRatingIndex = if (hasOldPersonalStateFields) 20 else 15

            ResetReadSessionRecord(
                id = parts[0].toLong(),
                articleId = parts[1],
                articleTitle = parts[2],
                startedAt = LocalDateTime.parse(parts[3]),
                completedAt = LocalDateTime.parse(parts[4]),
                selectedDurationSeconds = parts[5].toInt(),
                requiredDurationSeconds = parts[6].toInt(),
                secondsSpent = parts[7].toInt(),
                selectedOptionIndex = parts[8].toInt(),
                validCompletion = parts[9] == "1",
                answerText = parts.getOrNull(answerTextIndex).orEmpty(),
                completionQuality = parts.getOrNull(completionQualityIndex).takeUnless { it.isNullOrBlank() } ?: "valid",
                failureReason = parts.getOrNull(failureReasonIndex).toNullableText(),
                rewardApplied = parts.getOrNull(rewardAppliedIndex).toNullableBoolean(),
                waitCutMinutes = parts.getOrNull(waitCutMinutesIndex).toNullableInt(),
                helpfulnessRating = parts.getOrNull(helpfulnessRatingIndex).toNullableInt(),
            )
        }.getOrNull()
    }

    private fun ResetReadArticleExposureRecord.encodeExposure(): String = listOf(
        id.toString(),
        articleId,
        shownAt.toString(),
    ).joinToString(FieldSeparator) { value ->
        value
            .replace(RecordSeparator, " ")
            .replace(FieldSeparator, " ")
    }

    private fun String.decodeResetReadArticleExposureOrNull(): ResetReadArticleExposureRecord? {
        if (isBlank()) return null
        val parts = split(FieldSeparator)
        if (parts.size < 3) return null

        return runCatching {
            ResetReadArticleExposureRecord(
                id = parts[0].toLong(),
                articleId = parts[1],
                shownAt = LocalDateTime.parse(parts[2]),
            )
        }.getOrNull()
    }

    private fun String?.toNullableText(): String? {
        return this?.takeUnless { it.isBlank() }
    }

    private fun String?.toNullableInt(): Int? {
        return this?.takeUnless { it.isBlank() }?.toIntOrNull()
    }

    private fun String?.toNullableBoolean(): Boolean? {
        return when (this) {
            "1" -> true
            "0" -> false
            else -> null
        }
    }

    private companion object {
        val ReadArticleIdsKey = stringSetPreferencesKey("read_article_ids")
        val SessionsKey = stringPreferencesKey("reset_read_sessions")
        val ArticleExposuresKey = stringPreferencesKey("reset_read_article_exposures")
    }
}
