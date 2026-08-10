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
import com.impulsive.app.backend.data.local.preferences.AppSettingsPreferencesDataSource
import com.impulsive.app.backend.data.repository.JournalRepository
import com.impulsive.app.backend.service.protection.ProtectionNotificationGate
import kotlinx.coroutines.flow.first

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

        val noteId =
            inputData.getLong(
                KeyNoteId,
                0L,
            )

        if (noteId <= 0L) {
            return Result.success()
        }

        val currentContent =
            JournalRepository(
                applicationContext,
            ).getReminderContent(
                noteId,
            )
                ?: return Result.success()

        val hideSensitive =
            AppSettingsPreferencesDataSource(
                applicationContext,
            )
                .hideSensitiveNotifications
                .first()

        val decision =
            resolveJournalReminderNotification(
                hideSensitiveNotifications =
                    hideSensitive,
                currentTitle =
                    currentContent.title,
                currentPreview =
                    currentContent.preview,
            )

        val notificationId = notificationId(noteId)

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

        val visibility =
            when (
                decision.visibility
            ) {
                JournalReminderVisibility
                    .Private ->
                    NotificationCompat
                        .VISIBILITY_PRIVATE

                JournalReminderVisibility
                    .Secret ->
                    NotificationCompat
                        .VISIBILITY_SECRET
            }

        val publicVersion =
            if (
                decision.publicTitle !=
                null &&
                decision.publicBody !=
                null
            ) {
                NotificationCompat
                    .Builder(
                        applicationContext,
                        ChannelId,
                    )
                    .setSmallIcon(
                        R.drawable
                            .ic_notification,
                    )
                    .setContentTitle(
                        decision
                            .publicTitle,
                    )
                    .setContentText(
                        decision
                            .publicBody,
                    )
                    .setVisibility(
                        NotificationCompat
                            .VISIBILITY_PUBLIC,
                    )
                    .build()
            } else {
                null
            }

        val builder =
            NotificationCompat
                .Builder(
                    applicationContext,
                    ChannelId,
                )
                .setSmallIcon(
                    R.drawable
                        .ic_notification,
                )
                .setContentTitle(
                    decision.title,
                )
                .setContentText(
                    decision.body,
                )
                .setStyle(
                    NotificationCompat
                        .BigTextStyle()
                        .bigText(
                            decision.body,
                        ),
                )
                .setContentIntent(
                    pendingIntent,
                )
                .setAutoCancel(
                    true,
                )
                .setVisibility(
                    visibility,
                )
                .setPriority(
                    NotificationCompat
                        .PRIORITY_DEFAULT,
                )

        if (publicVersion != null) {
            builder.setPublicVersion(
                publicVersion,
            )
        }

        val notification =
            builder.build()

        val notificationContext = applicationContext
        ProtectionNotificationGate.submit(notificationId) {
            NotificationManagerCompat.from(notificationContext).notify(
                notificationId,
                notification,
            )
        }
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
        internal fun notificationId(noteId: Long): Int =
            (noteId and Int.MAX_VALUE.toLong()).toInt().coerceAtLeast(1)

        const val ChannelId = "journal_reminders"
        const val KeyNoteId = "note_id"
        const val ExtraOpenJournalNoteId = "open_journal_note_id"
    }
}
