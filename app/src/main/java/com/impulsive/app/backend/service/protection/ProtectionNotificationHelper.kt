package com.impulsive.app.backend.service.protection

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.impulsive.app.MainActivity
import com.impulsive.app.R
import com.impulsive.app.backend.domain.model.protection.toImpulsiveCompactTime
import java.time.LocalDateTime
import java.time.ZoneId

class ProtectionNotificationHelper(
    private val context: Context,
) {
    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val monitoringChannel = NotificationChannel(
            MonitoringChannelId,
            context.getString(R.string.notif_channel_monitoring_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notif_channel_monitoring_description)
            setShowBadge(false)
        }
        val blockedAttemptChannel = NotificationChannel(
            BlockedAttemptChannelId,
            context.getString(R.string.notif_channel_blocked_attempts_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notif_channel_blocked_attempts_description)
        }
        val releaseWindowChannel = NotificationChannel(
            ReleaseWindowChannelId,
            context.getString(R.string.notif_channel_release_window_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notif_channel_release_window_description)
        }
        manager.createNotificationChannel(monitoringChannel)
        manager.createNotificationChannel(blockedAttemptChannel)
        manager.createNotificationChannel(releaseWindowChannel)
    }

    fun createMonitoringNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            context,
            1001,
            MainActivity.createHomeIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, MonitoringChannelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.notif_monitoring_title))
            .setContentText(context.getString(R.string.notif_monitoring_body))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun showBlockedAttemptNotification(
        sourcePackageName: String,
        sourceLabel: String,
        hideSensitive: Boolean = false,
    ) {
        val pendingIntent = PendingIntent.getActivity(
            context,
            sourcePackageName.hashCode(),
            MainActivity.createBlockIntent(
                context = context,
                sourcePackageName = sourcePackageName,
                sourceLabel = sourceLabel,
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, BlockedAttemptChannelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(if (hideSensitive) "Impulsive" else context.getString(R.string.notif_blocked_attempt_title))
            .setContentText(if (hideSensitive) "Open Impulsive to continue." else context.getString(R.string.notif_blocked_attempt_body, sourceLabel))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(BlockedAttemptNotificationId, notification)
        }
    }

    fun showBlockFullScreen(
        sourcePackageName: String,
        sourceLabel: String,
        hideSensitive: Boolean = false,
    ) {
        val pendingIntent = PendingIntent.getActivity(
            context,
            sourcePackageName.hashCode(),
            MainActivity.createBlockIntent(
                context = context,
                sourcePackageName = sourcePackageName,
                sourceLabel = sourceLabel,
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, BlockedAttemptChannelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(if (hideSensitive) "Impulsive" else context.getString(R.string.notif_block_fullscreen_title, sourceLabel))
            .setContentText(if (hideSensitive) "Open Impulsive to continue." else context.getString(R.string.notif_block_fullscreen_body))
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(BlockFullScreenNotificationId, notification)
        }
    }

    fun showReleaseWindowPausedNotification(
        windowEnd: LocalDateTime,
    ) {
        val windowEndMillis = windowEnd
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val notification = NotificationCompat.Builder(context, ReleaseWindowChannelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.notif_release_window_open_title))
            .setContentText(context.getString(R.string.notif_release_window_open_body, windowEnd.toImpulsiveCompactTime()))
            .setContentIntent(homePendingIntent(ReleaseWindowPausedNotificationId))
            .setAutoCancel(true)
            .setWhen(windowEndMillis)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(ReleaseWindowPausedNotificationId, notification)
        }
    }

    fun showProtectionResumedNotification() {
        val notification = NotificationCompat.Builder(context, ReleaseWindowChannelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.notif_protection_resumed_title))
            .setContentText(context.getString(R.string.notif_protection_resumed_body))
            .setContentIntent(homePendingIntent(ProtectionResumedNotificationId))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(ProtectionResumedNotificationId, notification)
        }
    }

    private fun homePendingIntent(requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            MainActivity.createHomeIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        const val MonitoringChannelId = "impulsive_protection_monitoring"
        const val BlockedAttemptChannelId = "impulsive_blocked_attempts"
        const val ReleaseWindowChannelId = "impulsive_release_window_pause"
        const val MonitoringNotificationId = 4201
        const val BlockedAttemptNotificationId = 4202
        const val ReleaseWindowPausedNotificationId = 4203
        const val ProtectionResumedNotificationId = 4204
        const val BlockFullScreenNotificationId = 4205
    }
}
