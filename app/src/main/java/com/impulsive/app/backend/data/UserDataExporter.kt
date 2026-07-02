package com.impulsive.app.backend.data

import android.content.Context
import android.net.Uri
import com.impulsive.app.backend.data.local.database.AppDatabase
import com.impulsive.app.backend.data.local.entity.JournalChecklistItemEntity
import com.impulsive.app.backend.data.local.entity.JournalNoteEntity
import com.impulsive.app.backend.data.local.entity.RecoverySessionEntity
import com.impulsive.app.backend.data.local.preferences.AppSettingsPreferencesDataSource
import com.impulsive.app.backend.data.local.preferences.LevelPreferencesDataSource
import com.impulsive.app.backend.data.local.preferences.ResetReadProgressDataSource
import com.impulsive.app.backend.data.local.preferences.ScoreDataSource
import com.impulsive.app.backend.data.local.preferences.TaskRewardDataSource
import com.impulsive.app.backend.data.local.preferences.UrgeEventDataSource
import com.impulsive.app.backend.domain.model.score.ScoreSessionRecord
import com.impulsive.app.backend.domain.model.score.UrgeEventRecord
import com.impulsive.app.backend.domain.model.tasks.ResetReadSessionRecord
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
import java.util.Locale

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
        val resetReadSessions = runCatching { ResetReadProgressDataSource(context).sessions.first() }.getOrDefault(emptyList())
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

            appendLine("== Reset Reading summary ==")
            appendResetReadingSummary(resetReadSessions)
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

    /**
     * Writes the export directly to a user-selected document URI.
     *
     * No sensitive restore export is written to app cache. A cleanup pass still
     * removes the old cache export folder left by previous versions.
     */
    suspend fun writeExportToUri(destinationUri: Uri): Boolean = withContext(Dispatchers.IO) {
        deleteLegacyTemporaryExportFiles()

        val json = buildRestorableExportJson()
        val written = runCatching {
            val outputStream = context.contentResolver.openOutputStream(destinationUri, "wt")
                ?: return@runCatching false

            outputStream.use { output ->
                output.write(json.toByteArray(Charsets.UTF_8))
            }

            true
        }.getOrDefault(false)

        deleteLegacyTemporaryExportFiles()

        written
    }

    fun deleteLegacyTemporaryExportFiles() {
        File(context.cacheDir, LegacyExportDirectory).deleteRecursively()
    }

    /** Builds a structured export for the future manual restore flow. */
    suspend fun buildRestorableExportJson(): String = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val notes = runCatching { db.journalNoteDao().observeNotes().first() }.getOrDefault(emptyList())
        val sessions = runCatching { db.recoverySessionDao().getAllSessions() }.getOrDefault(emptyList())
        val level = runCatching { LevelPreferencesDataSource(context).currentLevel.first() }.getOrDefault(1)
        val rewards = runCatching { TaskRewardDataSource(context).storeState.first() }.getOrNull()
        val scoreSessions = runCatching { ScoreDataSource(context).sessions.first() }.getOrDefault(emptyList())
        val resetReadSessions = runCatching { ResetReadProgressDataSource(context).sessions.first() }.getOrDefault(emptyList())
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
            .put("resetReadingSummary", resetReadSessions.toResetReadingSummaryJson())
            .put("resetReadSessions", resetReadSessions.toResetReadSessionsJson())
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

    private fun StringBuilder.appendResetReadingSummary(sessions: List<ResetReadSessionRecord>) {
        val completedSessions = sessions.filter { it.validCompletion }
        val abandonedCount = sessions.count { !it.validCompletion }
        val safeSeconds = completedSessions.sumOf { session ->
            session.secondsSpent.coerceAtLeast(0)
        }
        val safeReadingMinutes = if (safeSeconds == 0) {
            0
        } else {
            (safeSeconds + 59) / 60
        }
        val helpfulRatings = completedSessions.mapNotNull { it.helpfulnessRating }
        val highlyHelpfulCount = helpfulRatings.count { it >= 4 }
        val averageHelpfulness = helpfulRatings
            .takeIf { it.isNotEmpty() }
            ?.average()
        val lastCompletedAt = completedSessions
            .maxByOrNull { it.completedAt }
            ?.completedAt

        appendLine("Completed resets: ${completedSessions.size}")
        appendLine("Safe reading minutes: $safeReadingMinutes")
        appendLine("Helpful reads: $highlyHelpfulCount")
        appendLine(
            "Average helpfulness: ${
                averageHelpfulness?.let { average -> String.format(Locale.UK, "%.1f/5", average) } ?: "Not rated"
            }",
        )
        appendLine("Abandoned attempts: $abandonedCount")
        appendLine("Last completed reset: ${lastCompletedAt?.let { localDateTime(it) } ?: "None yet"}")
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

    private fun List<ResetReadSessionRecord>.toResetReadingSummaryJson(): JSONObject {
        val completedSessions = filter { it.validCompletion }
        val abandonedCount = count { !it.validCompletion }
        val safeSeconds = completedSessions.sumOf { session ->
            session.secondsSpent.coerceAtLeast(0)
        }
        val safeReadingMinutes = if (safeSeconds == 0) {
            0
        } else {
            (safeSeconds + 59) / 60
        }
        val helpfulRatings = completedSessions.mapNotNull { it.helpfulnessRating }
        val lastCompletedAt = completedSessions
            .maxByOrNull { it.completedAt }
            ?.completedAt

        return JSONObject()
            .put("completedCount", completedSessions.size)
            .put("abandonedCount", abandonedCount)
            .put("safeReadingMinutes", safeReadingMinutes)
            .put("helpfulRatingCount", helpfulRatings.size)
            .put("highlyHelpfulCount", helpfulRatings.count { it >= 4 })
            .putNullable(
                "averageHelpfulness",
                helpfulRatings
                    .takeIf { it.isNotEmpty() }
                    ?.average(),
            )
            .putNullable("lastCompletedAt", lastCompletedAt?.toString())
    }

    private fun List<ResetReadSessionRecord>.toResetReadSessionsJson(): JSONArray =
        JSONArray().also { array ->
            forEach { session ->
                array.put(
                    JSONObject()
                        .put("id", session.id)
                        .put("articleId", session.articleId)
                        .put("articleTitle", session.articleTitle)
                        .put("startedAt", session.startedAt.toString())
                        .put("completedAt", session.completedAt.toString())
                        .put("selectedDurationSeconds", session.selectedDurationSeconds)
                        .put("requiredDurationSeconds", session.requiredDurationSeconds)
                        .put("secondsSpent", session.secondsSpent)
                        .put("selectedOptionIndex", session.selectedOptionIndex)
                        .put("validCompletion", session.validCompletion)
                        .put("answerText", session.answerText)
                        .put("completionQuality", session.completionQuality)
                        .putNullable("failureReason", session.failureReason)
                        .putNullable("rewardApplied", session.rewardApplied)
                        .putNullable("waitCutMinutes", session.waitCutMinutes)
                        .putNullable("helpfulnessRating", session.helpfulnessRating),
                )
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

    companion object {
        const val SuggestedExportFileName = "impulsive-restore-export.json"
        private const val LegacyExportDirectory = "exports"
    }
}
