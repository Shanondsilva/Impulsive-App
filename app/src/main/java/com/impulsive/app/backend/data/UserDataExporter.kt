package com.impulsive.app.backend.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.impulsive.app.backend.data.local.database.AppDatabase
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
        val text = buildReadableExport()
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        // Stable filename so repeated exports overwrite rather than pile up.
        val file = File(dir, "impulsive-data-export.txt")
        file.writeText(text)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
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
}
