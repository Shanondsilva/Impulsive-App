package com.impulsive.app.backend.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.impulsive.app.backend.data.local.database.AppDatabase
import com.impulsive.app.backend.data.local.entity.JournalChecklistItemEntity
import com.impulsive.app.backend.data.local.entity.JournalNoteEntity
import com.impulsive.app.backend.data.local.entity.RecoverySessionEntity
import com.impulsive.app.backend.data.local.preferences.AppSettingsPreferencesDataSource
import com.impulsive.app.backend.data.local.preferences.LevelPreferencesDataSource
import com.impulsive.app.backend.data.local.preferences.ScoreDataSource
import com.impulsive.app.backend.data.local.preferences.TaskRewardDataSource
import com.impulsive.app.backend.data.local.preferences.UrgeEventDataSource
import com.impulsive.app.backend.domain.model.score.ScoreSessionRecord
import com.impulsive.app.backend.domain.model.score.UrgeEventRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class UserDataExporter(private val context: Context) {

    private val dateFmt: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.systemDefault())
    private val localDateTimeFmt: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")

    private fun millis(ms: Long?): String =
        if (ms == null || ms <= 0) "-" else dateFmt.format(Instant.ofEpochMilli(ms))

    private fun localDateTime(value: LocalDateTime): String = localDateTimeFmt.format(value)

    /** Builds the human-readable export text. */
    suspend fun buildReadableExport(): String = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val notes = runCatching { db.journalNoteDao().observeNotes().first() }.getOrDefault(emptyList())
        val sessions = runCatching { db.recoverySessionDao().getAllSessions() }.getOrDefault(emptyList())
        val level = runCatching { LevelPreferencesDataSource(context).currentLevel.first() }.getOrDefault(1)
        val rewards = runCatching { TaskRewardDataSource(context).storeState.first() }.getOrNull()
        val scoreSessions = runCatching { ScoreDataSource(context).sessions.first() }.getOrDefault(emptyList())
        val urgeEvents = runCatching { UrgeEventDataSource(context).events.first() }.getOrDefault(emptyList())
        val settings = AppSettingsPreferencesDataSource(context)
        val haptics = runCatching { settings.hapticsEnabled.first() }.getOrDefault(true)
        val sound = runCatching { settings.soundEffectsEnabled.first() }.getOrDefault(false)
        val hideSensitive = runCatching { settings.hideSensitiveNotifications.first() }.getOrDefault(false)
        val checklistItemsByNoteId = notes.associate { note ->
            note.id to runCatching { db.journalNoteDao().getChecklistItems(note.id) }.getOrDefault(emptyList())
        }

        buildString {
            appendLine("IMPULSIVE - Your data export")
            appendLine("Exported: ${dateFmt.format(Instant.now())}")
            appendLine("This file stays private to you. It was created on your device.")
            appendLine()

            appendLine("== Progress ==")
            appendLine("Level: $level")
            if (rewards != null) {
                appendLine("Level points: ${rewards.currentLevelPoints}")
            }
            appendLine()

            appendLine("== Pivot sessions (${sessions.size}) ==")
            if (sessions.isEmpty()) appendLine("None yet.")
            sessions.forEach { appendRecoverySession(it) }
            appendLine()

            appendLine("== Score sessions (${scoreSessions.size}) ==")
            if (scoreSessions.isEmpty()) appendLine("None yet.")
            scoreSessions.forEach { appendScoreSession(it) }
            appendLine()

            appendLine("== Difficult moment log (${urgeEvents.size}) ==")
            if (urgeEvents.isEmpty()) appendLine("None yet.")
            urgeEvents.forEach { appendUrgeEvent(it) }
            appendLine()

            appendLine("== Notes (${notes.size}) ==")
            if (notes.isEmpty()) appendLine("None yet.")
            notes.forEach { note ->
                appendNote(note, checklistItemsByNoteId[note.id].orEmpty().map { it.text to it.isChecked })
            }
            appendLine()

            appendLine("== Settings ==")
            appendLine("Haptics: ${if (haptics) "on" else "off"}")
            appendLine("Sound effects: ${if (sound) "on" else "off"}")
            appendLine("Hide sensitive notifications: ${if (hideSensitive) "on" else "off"}")
        }
    }

    /** Writes the export to a shareable cache file and returns a content:// uri. */
    suspend fun writeExportFile(): Uri = withContext(Dispatchers.IO) {
        val json = buildRestorableExportJson()
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        // Stable filename so repeated exports overwrite rather than pile up.
        val file = File(dir, "impulsive-restore-export.json")
        file.writeText(json)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /** Builds a structured export for the future manual restore flow. */
    suspend fun buildRestorableExportJson(): String = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val notes = runCatching { db.journalNoteDao().observeNotes().first() }.getOrDefault(emptyList())
        val sessions = runCatching { db.recoverySessionDao().getAllSessions() }.getOrDefault(emptyList())
        val level = runCatching { LevelPreferencesDataSource(context).currentLevel.first() }.getOrDefault(1)
        val rewards = runCatching { TaskRewardDataSource(context).storeState.first() }.getOrNull()
        val scoreSessions = runCatching { ScoreDataSource(context).sessions.first() }.getOrDefault(emptyList())
        val urgeEvents = runCatching { UrgeEventDataSource(context).events.first() }.getOrDefault(emptyList())
        val settings = AppSettingsPreferencesDataSource(context)
        val haptics = runCatching { settings.hapticsEnabled.first() }.getOrDefault(true)
        val sound = runCatching { settings.soundEffectsEnabled.first() }.getOrDefault(false)
        val hideSensitive = runCatching { settings.hideSensitiveNotifications.first() }.getOrDefault(false)
        val checklistItemsByNoteId = notes.associate { note ->
            note.id to runCatching { db.journalNoteDao().getChecklistItems(note.id) }.getOrDefault(emptyList())
        }

        JSONObject()
            .put("schemaVersion", 1)
            .put("exportType", "impulsive_restore_export")
            .put("exportedAt", Instant.now().toString())
            .put("createdBy", "Impulsive Android")
            .put("privacyNote", "This export can restore private Impulsive data. Keep it somewhere safe.")
            .put(
                "progress",
                JSONObject()
                    .put("level", level)
                    .put("levelPoints", rewards?.currentLevelPoints ?: 0),
            )
            .put(
                "settings",
                JSONObject()
                    .put("hapticsEnabled", haptics)
                    .put("soundEffectsEnabled", sound)
                    .put("hideSensitiveNotifications", hideSensitive),
            )
            .put("recoverySessions", sessions.toRecoverySessionsJson())
            .put("scoreSessions", scoreSessions.toScoreSessionsJson())
            .put("urgeEvents", urgeEvents.toUrgeEventsJson())
            .put("notes", notes.toNotesJson(checklistItemsByNoteId))
            .toString(2)
    }

    private fun StringBuilder.appendRecoverySession(session: RecoverySessionEntity) {
        val intensity = "${session.urgeBefore ?: "-"} -> ${session.urgeAfter ?: "-"}"
        val helped = when (session.helped) {
            true -> "helped"
            false -> "did not help"
            null -> "no answer"
        }
        appendLine("- ${millis(session.completedAt)} | intensity $intensity | $helped | ${session.durationSeconds}s | ${session.recoveryType}")
    }

    private fun StringBuilder.appendScoreSession(session: ScoreSessionRecord) {
        val intensity = "${session.urgeBefore ?: "-"} -> ${session.urgeAfter ?: "-"}"
        val valid = if (session.validCompletion) "valid" else "partial"
        appendLine(
            "- ${localDateTime(session.completedAt)} | ${session.gameType.displayName} | score ${session.score} | " +
                "intensity $intensity | ${session.outcome.label} | ${session.durationSec}s | $valid",
        )
    }

    private fun StringBuilder.appendUrgeEvent(event: UrgeEventRecord) {
        appendLine("- ${event.date} | source: ${event.source}")
    }

    private fun StringBuilder.appendNote(
        note: JournalNoteEntity,
        checklistItems: List<Pair<String, Boolean>>,
    ) {
        val pin = if (note.isPinned) "[pinned] " else ""
        val cat = if (note.category.isBlank()) "" else " (${note.category})"
        appendLine("- $pin${note.title.ifBlank { "Untitled" }}$cat - ${millis(note.updatedAtMillis)}")
        if (note.body.isNotBlank()) {
            note.body.lineSequence().forEach { appendLine("    $it") }
        }
        if (checklistItems.isNotEmpty()) {
            checklistItems.forEach { (text, isChecked) ->
                appendLine("    [${if (isChecked) "x" else " "}] $text")
            }
        } else if (note.checklist.isNotBlank()) {
            note.checklist.lineSequence().forEach { appendLine("    $it") }
        }
    }

    private fun List<RecoverySessionEntity>.toRecoverySessionsJson(): JSONArray =
        JSONArray().also { array ->
            forEach { session ->
                array.put(
                    JSONObject()
                        .put("id", session.id)
                        .put("startedAt", session.startedAt)
                        .put("completedAt", session.completedAt)
                        .put("durationSeconds", session.durationSeconds)
                        .putNullable("urgeBefore", session.urgeBefore)
                        .putNullable("urgeAfter", session.urgeAfter)
                        .putNullable("helped", session.helped)
                        .put("triggerSource", session.triggerSource)
                        .put("recoveryType", session.recoveryType),
                )
            }
        }

    private fun List<ScoreSessionRecord>.toScoreSessionsJson(): JSONArray =
        JSONArray().also { array ->
            forEach { session ->
                array.put(
                    JSONObject()
                        .put("id", session.id)
                        .put("gameType", session.gameType.id)
                        .put("score", session.score)
                        .put("startedAt", session.startedAt.toString())
                        .put("completedAt", session.completedAt.toString())
                        .put("durationSec", session.durationSec)
                        .putNullable("urgeBefore", session.urgeBefore)
                        .putNullable("urgeAfter", session.urgeAfter)
                        .put("outcome", session.outcome.id)
                        .put("validCompletion", session.validCompletion),
                )
            }
        }

    private fun List<UrgeEventRecord>.toUrgeEventsJson(): JSONArray =
        JSONArray().also { array ->
            forEach { event ->
                array.put(
                    JSONObject()
                        .put("date", event.date.toString())
                        .put("source", event.source)
                        .putNullable("packageName", event.packageName)
                        .putNullable("at", event.at?.toString()),
                )
            }
        }

    private fun List<JournalNoteEntity>.toNotesJson(
        checklistItemsByNoteId: Map<Long, List<JournalChecklistItemEntity>>,
    ): JSONArray =
        JSONArray().also { array ->
            forEach { note ->
                array.put(
                    JSONObject()
                        .put("id", note.id)
                        .put("noteType", note.noteType)
                        .put("title", note.title)
                        .put("body", note.body)
                        .put("checklist", note.checklist)
                        .put("sketch", note.sketch)
                        .putNullable("reminderAtMillis", note.reminderAtMillis)
                        .put("source", note.source)
                        .put("createdAtMillis", note.createdAtMillis)
                        .put("updatedAtMillis", note.updatedAtMillis)
                        .put("isPinned", note.isPinned)
                        .put("category", note.category)
                        .putNullable("highlightColor", note.highlightColor)
                        .putNullable("sortOrder", note.sortOrder)
                        .put("checklistItems", checklistItemsByNoteId[note.id].orEmpty().toChecklistItemsJson()),
                )
            }
        }

    private fun List<JournalChecklistItemEntity>.toChecklistItemsJson(): JSONArray =
        JSONArray().also { array ->
            forEach { item ->
                array.put(
                    JSONObject()
                        .put("id", item.id)
                        .put("text", item.text)
                        .put("isChecked", item.isChecked)
                        .put("sortOrder", item.sortOrder)
                        .put("createdAtMillis", item.createdAtMillis)
                        .put("updatedAtMillis", item.updatedAtMillis),
                )
            }
        }

    private fun JSONObject.putNullable(name: String, value: Any?): JSONObject =
        put(name, value ?: JSONObject.NULL)
}
