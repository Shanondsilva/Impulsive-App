package com.impulsive.app.backend.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

enum class FutureSelfMessageKind { Voice, Text }

data class FutureSelfMessage(
    val kind: FutureSelfMessageKind,
    val voiceFilePath: String?,
    val text: String?,
    val createdAtEpochMillis: Long,
)

private val Context.futureSelfMessageDataStore by preferencesDataStore(name = "future_self_message")

class FutureSelfMessageDataSource(private val context: Context) {

    val message: Flow<FutureSelfMessage?> = context.futureSelfMessageDataStore.data.map { prefs ->
        val kindRaw = prefs[KindKey] ?: return@map null
        val createdAt = prefs[CreatedAtKey] ?: 0L
        when (kindRaw) {
            FutureSelfMessageKind.Voice.name -> {
                val path = prefs[VoiceFilePathKey]?.takeIf { File(it).exists() }
                    ?: return@map null
                FutureSelfMessage(
                    kind = FutureSelfMessageKind.Voice,
                    voiceFilePath = path,
                    text = null,
                    createdAtEpochMillis = createdAt,
                )
            }
            FutureSelfMessageKind.Text.name -> {
                val text = prefs[TextKey]?.takeIf { it.isNotBlank() } ?: return@map null
                FutureSelfMessage(
                    kind = FutureSelfMessageKind.Text,
                    voiceFilePath = null,
                    text = text,
                    createdAtEpochMillis = createdAt,
                )
            }
            else -> null
        }
    }

    suspend fun saveVoice(filePath: String, createdAtEpochMillis: Long) {
        context.futureSelfMessageDataStore.edit { prefs ->
            val previousPath = prefs[VoiceFilePathKey]
            if (previousPath != null && previousPath != filePath) {
                runCatching { File(previousPath).delete() }
            }
            prefs[KindKey] = FutureSelfMessageKind.Voice.name
            prefs[VoiceFilePathKey] = filePath
            prefs.remove(TextKey)
            prefs[CreatedAtKey] = createdAtEpochMillis
        }
    }

    suspend fun saveText(text: String, createdAtEpochMillis: Long) {
        context.futureSelfMessageDataStore.edit { prefs ->
            val previousPath = prefs[VoiceFilePathKey]
            if (previousPath != null) {
                runCatching { File(previousPath).delete() }
            }
            prefs[KindKey] = FutureSelfMessageKind.Text.name
            prefs[TextKey] = text
            prefs.remove(VoiceFilePathKey)
            prefs[CreatedAtKey] = createdAtEpochMillis
        }
    }

    suspend fun delete() {
        context.futureSelfMessageDataStore.edit { prefs ->
            val previousPath = prefs[VoiceFilePathKey]
            if (previousPath != null) {
                runCatching { File(previousPath).delete() }
            }
            prefs.remove(KindKey)
            prefs.remove(VoiceFilePathKey)
            prefs.remove(TextKey)
            prefs.remove(CreatedAtKey)
        }
    }

    private companion object {
        val KindKey = stringPreferencesKey("kind")
        val VoiceFilePathKey = stringPreferencesKey("voice_file_path")
        val TextKey = stringPreferencesKey("text_message")
        val CreatedAtKey = longPreferencesKey("created_at_millis")
    }
}
