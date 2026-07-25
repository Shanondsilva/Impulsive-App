package com.impulsive.app.backend.service.journal

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.impulsive.app.backend.service.protection.ProtectionNotificationGate
import java.util.concurrent.TimeUnit

class JournalReminderScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)

    fun schedule(
        noteId: Long,
        title: String,
        preview: String,
        reminderAtMillis: Long?,
    ) {
        if (noteId <= 0L || reminderAtMillis == null) {
            cancel(noteId)
            return
        }

        val delayMillis = reminderAtMillis - System.currentTimeMillis()
        if (delayMillis <= 0L) {
            cancel(noteId)
            return
        }

        val data = Data.Builder()
            .putLong(JournalReminderWorker.KeyNoteId, noteId)
            .putString(JournalReminderWorker.KeyTitle, title.ifBlank { "Journal reminder" })
            .putString(JournalReminderWorker.KeyPreview, preview.ifBlank { "You asked Impulsive to remind you." })
            .build()

        val request = OneTimeWorkRequestBuilder<JournalReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()

        workManager.enqueueUniqueWork(
            uniqueWorkName(noteId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancel(noteId: Long) {
        if (noteId <= 0L) return
        ProtectionNotificationGate.cancelQueued(
            JournalReminderWorker.notificationId(noteId),
        )
        workManager.cancelUniqueWork(uniqueWorkName(noteId))
    }

    private fun uniqueWorkName(noteId: Long): String = "journal_reminder_$noteId"
}
