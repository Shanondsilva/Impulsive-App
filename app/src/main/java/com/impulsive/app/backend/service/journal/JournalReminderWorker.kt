package com.impulsive.app.backend.service.journal

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.impulsive.app.MainActivity
import com.impulsive.app.R

class JournalReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        createNotificationChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return Result.success()
        }

        val noteId = inputData.getLong(KeyNoteId, 0L)
        val notificationId = (noteId and Int.MAX_VALUE.toLong()).toInt().coerceAtLeast(1)
        val rawTitle = inputData.getString(KeyTitle).orEmpty()
        val rawPreview = inputData.getString(KeyPreview).orEmpty()
        val title = rawTitle.ifBlank { "Journal reminder" }
        val preview = rawPreview.ifBlank { "You asked Impulsive to remind you." }.take(120)

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ExtraOpenJournalNoteId, noteId)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(applicationContext, ChannelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
        return Result.success()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            ChannelId,
            "Journal reminders",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Private reminders for your Impulsive journal notes."
        }
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val ChannelId = "journal_reminders"
        const val KeyNoteId = "note_id"
        const val KeyTitle = "title"
        const val KeyPreview = "preview"
        const val ExtraOpenJournalNoteId = "open_journal_note_id"
    }
}
