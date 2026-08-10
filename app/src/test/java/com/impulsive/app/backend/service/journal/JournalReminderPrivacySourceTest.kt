package com.impulsive.app.backend.service.journal

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Structural provenance guard for the Journal reminder privacy chain: WorkManager Data must
 * carry only noteId (never title/preview), and the Worker must resolve current note content
 * and the current privacy preference at execution time rather than from a scheduling-time
 * snapshot.
 */
class JournalReminderPrivacySourceTest {

    private val schedulerSource = File(
        "src/main/java/com/impulsive/app/backend/service/journal/JournalReminderScheduler.kt",
    ).readText()

    private val workerSource = File(
        "src/main/java/com/impulsive/app/backend/service/journal/JournalReminderWorker.kt",
    ).readText()

    private val repositorySource = File(
        "src/main/java/com/impulsive/app/backend/data/repository/JournalRepository.kt",
    ).readText()

    private fun blockBetween(
        source: String,
        startMarker: String,
        endMarker: String,
    ): String {
        val start = source.indexOf(startMarker)
        assertTrue("Missing start marker: $startMarker", start >= 0)
        val end = source.indexOf(endMarker, start + startMarker.length)
        assertTrue("Missing end marker: $endMarker", end > start)
        return source.substring(start, end)
    }

    @Test
    fun schedulerScheduleSignatureContainsOnlyNoteIdAndReminderAtMillis() {
        val signature = blockBetween(
            source = schedulerSource,
            startMarker = "fun schedule(",
            endMarker = "fun cancel(",
        )
        assertTrue(signature.contains("noteId: Long"))
        assertTrue(signature.contains("reminderAtMillis: Long?"))
        assertFalse(signature.contains("title: String"))
        assertFalse(signature.contains("preview: String"))
    }

    @Test
    fun schedulerWorkManagerDataContainsKeyNoteId() {
        assertTrue(schedulerSource.contains("JournalReminderWorker"))
        assertTrue(schedulerSource.contains("KeyNoteId"))
        assertTrue(schedulerSource.contains(".putLong("))
    }

    @Test
    fun schedulerSourceContainsNoKeyTitleOrKeyPreview() {
        assertFalse(schedulerSource.contains("KeyTitle"))
        assertFalse(schedulerSource.contains("KeyPreview"))
    }

    @Test
    fun workerSourceContainsNoInputDataGetStringOrTitlePreviewKeys() {
        assertFalse(workerSource.contains("inputData.getString"))
        assertFalse(workerSource.contains("KeyTitle"))
        assertFalse(workerSource.contains("KeyPreview"))
    }

    @Test
    fun workerConstructsJournalRepository() {
        assertTrue(workerSource.contains("JournalRepository("))
    }

    @Test
    fun workerCallsGetReminderContent() {
        assertTrue(workerSource.contains(".getReminderContent("))
    }

    @Test
    fun workerReadsHideSensitiveNotificationsAndFirst() {
        assertTrue(workerSource.contains("hideSensitiveNotifications"))
        assertTrue(workerSource.contains(".first()"))
    }

    @Test
    fun workerReturnsResultSuccessWhenRepositoryContentIsNull() {
        assertTrue(workerSource.contains("?: return Result.success()"))
    }

    @Test
    fun workerUsesVisibilitySecret() {
        assertTrue(workerSource.contains("VISIBILITY_SECRET"))
    }

    @Test
    fun workerUsesVisibilityPrivate() {
        assertTrue(workerSource.contains("VISIBILITY_PRIVATE"))
    }

    @Test
    fun workerUsesSetPublicVersion() {
        assertTrue(workerSource.contains(".setPublicVersion("))
    }

    @Test
    fun repositoryGetReminderContentUsesGetNoteAndGetChecklistItemsForChecklist() {
        val block = blockBetween(
            source = repositorySource,
            startMarker = "suspend fun getReminderContent(",
            endMarker = "suspend fun upsertNote(",
        )
        assertTrue(block.contains("dao.getNote("))
        assertTrue(block.contains("dao.getChecklistItems("))
        assertTrue(block.contains("\"CHECKLIST\""))
    }

    @Test
    fun upsertNoteSchedulesNoteIdAndReminderOnly() {
        val block = blockBetween(
            source = repositorySource,
            startMarker = "suspend fun upsertNote(",
            endMarker = "suspend fun updateNote(",
        )
        assertTrue(block.contains("reminderScheduler.schedule("))
        assertTrue(block.contains("noteId ="))
        assertTrue(block.contains("reminderAtMillis ="))
        assertFalse(block.contains("title = note.title"))
        assertFalse(block.contains("preview ="))
    }

    @Test
    fun updateNoteSchedulesNoteIdAndReminderOnly() {
        val block = blockBetween(
            source = repositorySource,
            startMarker = "suspend fun updateNote(",
            endMarker = "suspend fun deleteNote(",
        )
        assertTrue(block.contains("reminderScheduler.schedule("))
        assertTrue(block.contains("noteId ="))
        assertTrue(block.contains("reminderAtMillis ="))
        assertFalse(block.contains("title = note.title"))
        assertFalse(block.contains("preview ="))
    }

    @Test
    fun existingWorkPolicyReplaceRemains() {
        assertTrue(schedulerSource.contains("ExistingWorkPolicy.REPLACE"))
    }

    @Test
    fun uniqueWorkNameNoteIdRemains() {
        assertTrue(schedulerSource.contains("uniqueWorkName(noteId)"))
        assertTrue(schedulerSource.contains("\"journal_reminder_\$noteId\""))
    }

    @Test
    fun pendingIntentFlagImmutableRemains() {
        assertTrue(workerSource.contains("PendingIntent.FLAG_IMMUTABLE"))
    }

    @Test
    fun extraOpenJournalNoteIdRemains() {
        assertTrue(workerSource.contains("ExtraOpenJournalNoteId"))
        assertTrue(workerSource.contains("\"open_journal_note_id\""))
    }

    @Test
    fun protectionNotificationGateSubmitRemains() {
        assertTrue(workerSource.contains("ProtectionNotificationGate.submit("))
    }

    @Test
    fun noJournalTitleBodyOrChecklistIsPutIntoWorkManagerData() {
        assertFalse(schedulerSource.contains(".putString("))
    }
}
