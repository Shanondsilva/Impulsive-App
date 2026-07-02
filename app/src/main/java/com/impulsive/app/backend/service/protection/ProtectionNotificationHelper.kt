package com.impulsive.app.backend.service.protection

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.impulsive.app.MainActivity
import com.impulsive.app.R
import com.impulsive.app.backend.domain.model.focus.FocusSessionPhase
import com.impulsive.app.backend.domain.model.focus.FocusSessionState
import com.impulsive.app.backend.domain.model.focus.formattedRemaining
import com.impulsive.app.backend.domain.model.focus.remainingSeconds
import com.impulsive.app.backend.domain.model.protection.toImpulsiveCompactTime
import java.time.LocalDateTime
import java.time.ZoneId

class ProtectionNotificationHelper(
    private val context: Context,
) {
    /**
     * Android 14+ requires this runtime permission for a full-screen-intent
     * notification to actually take over the screen. Below 14 it is implicitly
     * granted. Without it, the block notification only buzzes.
     */
    fun canUseFullScreenIntent(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return manager.canUseFullScreenIntent()
    }

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

    fun createMonitoringNotification(
        session: FocusSessionState? = null,
        now: LocalDateTime = LocalDateTime.now(),
        hideSensitive: Boolean = false,
    ): Notification {
        val builder = NotificationCompat.Builder(context, MonitoringChannelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(homePendingIntent(MonitoringNotificationId))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        return when (session?.phase) {
            FocusSessionPhase.Running -> {
                val remainingSeconds = session.remainingSeconds(now).coerceAtLeast(0L)
                val focusEndMillis = now
                    .plusSeconds(remainingSeconds)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()

                builder
                    .setContentTitle(if (hideSensitive) "Impulsive" else "Focus is running")
                    .setContentText(
                        if (hideSensitive) {
                            "Session active. Time remaining."
                        } else {
                            "Guarding your focus. Time remaining."
                        },
                    )
                    .setWhen(focusEndMillis)
                    .setShowWhen(true)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .build()
            }

            FocusSessionPhase.Paused -> {
                val remaining = session.formattedRemaining(now)

                builder
                    .setContentTitle(if (hideSensitive) "Impulsive" else "Focus is paused")
                    .setContentText(
                        if (hideSensitive) {
                            "Session paused. $remaining remaining."
                        } else {
                            "$remaining remaining."
                        },
                    )
                    .setShowWhen(false)
                    .setUsesChronometer(false)
                    .build()
            }

            else -> {
                builder
                    .setContentTitle(context.getString(R.string.notif_monitoring_title))
                    .setContentText(context.getString(R.string.notif_monitoring_body))
                    .setShowWhen(false)
                    .setUsesChronometer(false)
                    .build()
            }
        }
    }

    fun createTemporaryProtectionNotification(): Notification =
        NotificationCompat.Builder(context, MonitoringChannelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_monitoring_title))
            .setContentText(context.getString(R.string.notif_monitoring_body))
            .setContentIntent(homePendingIntent(MonitoringNotificationId))
            .setDeleteIntent(protectionDismissedPendingIntent())
            .setOngoing(false)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setUsesChronometer(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    fun postMonitoringNotification(
        session: FocusSessionState?,
        now: LocalDateTime,
        hideSensitive: Boolean,
    ) {
        val notification = createMonitoringNotification(
            session = session,
            now = now,
            hideSensitive = hideSensitive,
        )
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(MonitoringNotificationId, notification)
        }
    }

    fun isMonitoringNotificationActive(): Boolean {
        val manager = NotificationManagerCompat.from(context)

        // Android settings are controlled by the user. Treat disabled notifications
        // as non-recoverable here so the monitoring loop does not retry forever.
        if (!manager.areNotificationsEnabled()) return true

        return runCatching {
            manager.getActiveNotifications()
                .any { notification -> notification.id == MonitoringNotificationId }
        }.getOrDefault(true)
    }

    fun showOneMinuteAccessCountdown(
        sourceLabel: String,
        remainingSeconds: Int,
        hideSensitive: Boolean = false,
    ) {
        val title = if (hideSensitive) "Impulsive" else "Quick access"
        val text = if (hideSensitive) {
            "Locks again in ${remainingSeconds}s"
        } else {
            "$sourceLabel locks again in ${remainingSeconds}s"
        }
        val notification = NotificationCompat.Builder(context, MonitoringChannelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(homePendingIntent(OneMinuteAccessNotificationId))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(OneMinuteAccessNotificationId, notification)
        }
    }

    fun cancelOneMinuteAccessCountdown() {
        runCatching {
            NotificationManagerCompat.from(context).cancel(OneMinuteAccessNotificationId)
        }
    }

    fun cancelBlockFullScreen() {
        runCatching {
            NotificationManagerCompat.from(context).cancel(BlockFullScreenNotificationId)
        }
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
            .setSmallIcon(R.drawable.ic_notification)
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
        isFocusSession: Boolean = false,
    ) {
        val pendingIntent = PendingIntent.getActivity(
            context,
            sourcePackageName.hashCode(),
            MainActivity.createBlockIntent(
                context = context,
                sourcePackageName = sourcePackageName,
                sourceLabel = sourceLabel,
                isFocusSession = isFocusSession,
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, BlockedAttemptChannelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (hideSensitive) "Impulsive" else context.getString(R.string.notif_block_fullscreen_title, sourceLabel))
            .setContentText(if (hideSensitive) "Open Impulsive to continue." else context.getString(R.string.notif_block_fullscreen_body))
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, canUseFullScreenIntent())
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
        val builder = NotificationCompat.Builder(context, ReleaseWindowChannelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_release_window_open_title))
            .setContentText(context.getString(R.string.notif_release_window_open_body, windowEnd.toImpulsiveCompactTime()))
            .setContentIntent(homePendingIntent(ReleaseWindowPausedNotificationId))
            .setAutoCancel(true)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        val millisUntilWindowEnd = windowEndMillis - System.currentTimeMillis()
        if (millisUntilWindowEnd > 0L) {
            builder.setTimeoutAfter(millisUntilWindowEnd)
        }
        runCatching {
            NotificationManagerCompat.from(context).notify(ReleaseWindowPausedNotificationId, builder.build())
        }
    }

    fun showProtectionResumedNotification() {
        cancelReleaseWindowPausedNotification()
        val notification = NotificationCompat.Builder(context, ReleaseWindowChannelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_protection_resumed_title))
            .setContentText(context.getString(R.string.notif_protection_resumed_body))
            .setContentIntent(homePendingIntent(ProtectionResumedNotificationId))
            .setAutoCancel(true)
            .setTimeoutAfter(ProtectionResumedTimeoutMillis)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(ProtectionResumedNotificationId, notification)
        }
    }

    fun cancelReleaseWindowPausedNotification() {
        runCatching {
            NotificationManagerCompat.from(context).cancel(ReleaseWindowPausedNotificationId)
        }
    }

    private fun homePendingIntent(requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            MainActivity.createHomeIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun protectionDismissedPendingIntent(): PendingIntent =
        PendingIntent.getService(
            context,
            ProtectionDismissedRequestCode,
            Intent(context, AppMonitorService::class.java).apply {
                action = AppMonitorService.ActionProtectionNotificationDismissed
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun createVpnNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            context,
            1002,
            MainActivity.createHomeIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, MonitoringChannelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_vpn_title))
            .setContentText(context.getString(R.string.notif_vpn_body))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val MonitoringChannelId = "impulsive_protection_monitoring"
        const val BlockedAttemptChannelId = "impulsive_blocked_attempts"
        const val ReleaseWindowChannelId = "impulsive_release_window_pause"
        const val MonitoringNotificationId = 4201
        const val BlockedAttemptNotificationId = 4202
        const val ReleaseWindowPausedNotificationId = 4203
        const val ProtectionResumedNotificationId = 4204
        const val BlockFullScreenNotificationId = 4205
        const val VpnNotificationId = 4206
        const val OneMinuteAccessNotificationId = 4208
        const val ProtectionResumedTimeoutMillis = 10L * 60L * 1000L
        private const val ProtectionDismissedRequestCode = 1002
    }
}
