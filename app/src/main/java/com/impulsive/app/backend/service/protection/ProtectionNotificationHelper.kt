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

class ProtectionNotificationHelper(
    private val context: Context,
) {
    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val monitoringChannel = NotificationChannel(
            MonitoringChannelId,
            "Impulsive protection",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows that Impulsive is actively checking protected apps."
            setShowBadge(false)
        }
        val blockedAttemptChannel = NotificationChannel(
            BlockedAttemptChannelId,
            "Protected app attempts",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Opens Impulsive when a protected app is opened outside a planned window."
        }
        val releaseWindowChannel = NotificationChannel(
            ReleaseWindowChannelId,
            "Protection pause windows",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Tells you when protection pauses and turns back on around release windows."
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
            .setContentTitle("Impulsive protection is on")
            .setContentText("Protected apps are checked during your protected time.")
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
            .setContentTitle("Impulsive stepped in")
            .setContentText("$sourceLabel is protected right now. Tap to return to Impulsive.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(BlockedAttemptNotificationId, notification)
        }
    }

    fun showReleaseWindowPausedNotification(
        windowEnd: LocalDateTime,
    ) {
        val notification = NotificationCompat.Builder(context, ReleaseWindowChannelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Release window is open")
            .setContentText("Protection is paused until ${windowEnd.toImpulsiveCompactTime()}.")
            .setContentIntent(homePendingIntent(ReleaseWindowPausedNotificationId))
            .setAutoCancel(true)
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
            .setContentTitle("Protection is back on")
            .setContentText("Protected apps are being checked again.")
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
    }
}
