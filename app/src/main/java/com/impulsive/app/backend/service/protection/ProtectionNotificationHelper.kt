package com.impulsive.app.backend.service.protection

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.impulsive.app.MainActivity
import com.impulsive.app.R
import com.impulsive.app.backend.domain.model.focus.FocusSessionPhase
import com.impulsive.app.backend.domain.model.focus.FocusSessionState
import com.impulsive.app.backend.domain.model.focus.formattedRemaining
import com.impulsive.app.backend.domain.model.focus.remainingSeconds
import com.impulsive.app.backend.domain.model.protection.BlockLaunchTarget
import com.impulsive.app.backend.domain.model.protection.toImpulsiveCompactTime
import java.time.LocalDateTime
import java.time.ZoneId

sealed interface InterruptionNotificationStatus {
    data object Available : InterruptionNotificationStatus
    data object RuntimePermissionMissing : InterruptionNotificationStatus
    data object AppNotificationsDisabled : InterruptionNotificationStatus
    data object ChannelDisabled : InterruptionNotificationStatus
    data object ChannelNotHighPriority : InterruptionNotificationStatus
}

sealed interface InterruptionNotificationResult {
    data object Posted : InterruptionNotificationResult
    data object Queued : InterruptionNotificationResult
    data object Suppressed : InterruptionNotificationResult
    data class Unavailable(
        val status: InterruptionNotificationStatus,
    ) : InterruptionNotificationResult
    data class Failed(
        val throwable: Throwable,
    ) : InterruptionNotificationResult
}

internal const val InterruptionFallbackNotificationTitle =
    "Pause before you continue"
internal const val InterruptionFallbackNotificationBody =
    "Protected content was detected. Choose one quick reset before continuing."

class ProtectionNotificationHelper(
    context: Context,
) {
    private val context = context.applicationContext

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

    fun interruptionNotificationStatus(): InterruptionNotificationStatus {
        ensureChannels()
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return InterruptionNotificationStatus.RuntimePermissionMissing
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return InterruptionNotificationStatus.AppNotificationsDisabled
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return InterruptionNotificationStatus.Available
        }
        val manager = context.getSystemService(NotificationManager::class.java)
            ?: return InterruptionNotificationStatus.ChannelDisabled
        val channel = manager.getNotificationChannel(BlockedAttemptChannelId)
            ?: return InterruptionNotificationStatus.ChannelDisabled
        return when {
            channel.importance == NotificationManager.IMPORTANCE_NONE ->
                InterruptionNotificationStatus.ChannelDisabled
            channel.importance < NotificationManager.IMPORTANCE_HIGH ->
                InterruptionNotificationStatus.ChannelNotHighPriority
            else -> InterruptionNotificationStatus.Available
        }
    }

    internal fun createMonitoringNotification(
        session: FocusSessionState? = null,
        now: LocalDateTime = LocalDateTime.now(),
        hideSensitive: Boolean = false,
        // Ignored while a Focus session owns the notification. Defaults to the
        // neutral mode so no caller can accidentally claim protection is on.
        monitoringMode: ProtectionMonitoringNotificationMode =
            ProtectionMonitoringNotificationMode.Checking,
    ): Notification {
        val builder = NotificationCompat.Builder(context, MonitoringChannelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(homePendingIntent(MonitoringNotificationId))
            // Android 14+ lets the user swipe foreground service notifications
            // away. Without this delete intent the service never learns about
            // the dismissal and keeps resurrecting the notification.
            .setDeleteIntent(protectionDismissedPendingIntent())
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
                    .setContentTitle(
                        if (hideSensitive) {
                            "Impulsive"
                        } else {
                            context.getString(R.string.notif_focus_active_title)
                        },
                    )
                    .setContentText(
                        if (hideSensitive) {
                            "Session active. Time remaining."
                        } else {
                            context.getString(R.string.notif_focus_active_body)
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
                    .setContentTitle(
                        if (hideSensitive) {
                            "Impulsive"
                        } else {
                            context.getString(R.string.notif_focus_paused_title)
                        },
                    )
                    .setContentText(
                        if (hideSensitive) {
                            "Session paused. $remaining remaining."
                        } else {
                            context.getString(R.string.notif_focus_paused_body, remaining)
                        },
                    )
                    .setShowWhen(false)
                    .setUsesChronometer(false)
                    .setChronometerCountDown(false)
                    .build()
            }

            else -> {
                if (hideSensitive) {
                    // Hide-sensitive mode: title only, no body text at all.
                    builder
                        .setContentTitle("Impulsive")
                        .setShowWhen(false)
                        .setUsesChronometer(false)
                        .setChronometerCountDown(false)
                        .build()
                } else {
                    /*
                     * APP-015: describe only what is operational. Claiming
                     * protection is on while Usage Access is revoked is the
                     * defect this replaces.
                     */
                    val titleRes = when (monitoringMode) {
                        ProtectionMonitoringNotificationMode.Checking ->
                            R.string.notif_monitoring_checking_title
                        ProtectionMonitoringNotificationMode.WebsiteProtection ->
                            R.string.notif_monitoring_website_title
                        ProtectionMonitoringNotificationMode.AppProtection,
                        ProtectionMonitoringNotificationMode.AppAndWebsiteProtection,
                        -> R.string.notif_monitoring_title
                    }
                    val bodyRes = when (monitoringMode) {
                        ProtectionMonitoringNotificationMode.Checking ->
                            R.string.notif_monitoring_checking_body
                        ProtectionMonitoringNotificationMode.WebsiteProtection ->
                            R.string.notif_monitoring_website_body
                        ProtectionMonitoringNotificationMode.AppProtection ->
                            R.string.notif_monitoring_body
                        ProtectionMonitoringNotificationMode.AppAndWebsiteProtection ->
                            R.string.notif_monitoring_app_and_website_body
                    }

                    builder
                        .setContentTitle(context.getString(titleRes))
                        .setContentText(context.getString(bodyRes))
                        .setShowWhen(false)
                        .setUsesChronometer(false)
                        .setChronometerCountDown(false)
                        .build()
                }
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
            .setChronometerCountDown(false)
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
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
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

    fun cancelLegacyTemporaryAccessNotification() {
        ProtectionNotificationGate.cancelQueued(LegacyTemporaryAccessNotificationId)
        runCatching {
            NotificationManagerCompat.from(context).cancel(LegacyTemporaryAccessNotificationId)
        }
    }

    fun cancelBlockedAttemptNotification() {
        ProtectionNotificationGate.cancelQueued(BlockedAttemptNotificationId)
        runCatching {
            NotificationManagerCompat.from(context).cancel(BlockedAttemptNotificationId)
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
            .let { builder ->
                val variant = if (hideSensitive) null else nextBlockedAttemptVariant()
                builder
                    .setContentTitle(
                        if (hideSensitive) "Impulsive" else context.getString(variant!!.first),
                    )
                    .setContentText(
                        if (hideSensitive) {
                            "Open Impulsive to continue."
                        } else {
                            context.getString(variant!!.second, sourceLabel)
                        },
                    )
            }
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        submitStandardNotification(BlockedAttemptNotificationId, notification)
    }

    private data class InterruptionNotificationAction(
        val label: String,
        val pendingIntent: PendingIntent,
    )

    private data class InterruptionNotificationDestinationConfig(
        val contentIntent: PendingIntent,
        val actions: List<InterruptionNotificationAction>,
    )

    fun showInterruptionFallback(
        sourcePackageName: String,
        sourceLabel: String,
        hideSensitive: Boolean = false,
        isFocusSession: Boolean = false,
        incidentStartedAtMillis: Long = System.currentTimeMillis(),
        isWebsiteIncident: Boolean = false,
        stage: InterruptionNotificationStage = InterruptionNotificationStage.Initial,
        adaptiveDecisionId: String? = null,
    ): InterruptionNotificationResult {
        ensureChannels()
        val status = interruptionNotificationStatus()
        if (status != InterruptionNotificationStatus.Available) {
            return InterruptionNotificationResult.Unavailable(status)
        }
        return try {
            // Three distinct interruption concepts share this notification: an
            // adaptive-decision interruption, an active Focus interruption, and
            // ordinary app/website protection. Each branch below constructs only
            // the PendingIntents it actually uses; the Focus branch must never
            // construct or expose the ordinary Game/Reading actions or
            // destinations, and the ordinary branch never touches FocusRecovery.
            val isFocusFallback = isFocusSession && adaptiveDecisionId == null

            val destination = when {
                adaptiveDecisionId != null -> InterruptionNotificationDestinationConfig(
                    contentIntent = PendingIntent.getActivity(
                        context,
                        InterruptionHomeRequestCode,
                        MainActivity.createAdaptiveMomentIntent(context, adaptiveDecisionId),
                        PendingIntent.FLAG_UPDATE_CURRENT or
                            PendingIntent.FLAG_IMMUTABLE,
                    ),
                    actions = emptyList(),
                )

                isFocusFallback -> {
                    val focusPendingIntent = PendingIntent.getActivity(
                        context,
                        InterruptionFocusOptionsRequestCode,
                        MainActivity.createBlockIntent(
                            context = context,
                            sourcePackageName = sourcePackageName,
                            sourceLabel = sourceLabel,
                            launchTarget = BlockLaunchTarget.FocusRecovery,
                        ).apply {
                            action = ActionOpenInterruptionFocusOptions
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or
                            PendingIntent.FLAG_IMMUTABLE,
                    )
                    InterruptionNotificationDestinationConfig(
                        contentIntent = focusPendingIntent,
                        actions = listOf(
                            InterruptionNotificationAction(
                                label = context.getString(R.string.notif_action_focus_options),
                                pendingIntent = focusPendingIntent,
                            ),
                        ),
                    )
                }

                else -> {
                    val homePendingIntent = PendingIntent.getActivity(
                        context,
                        InterruptionHomeRequestCode,
                        MainActivity
                            .createHomeIntent(context)
                            .apply {
                                action = ActionOpenInterruptionHome
                                putExtra(
                                    AppMonitorService.ExtraFallbackIncidentPackageName,
                                    sourcePackageName,
                                )
                            },
                        PendingIntent.FLAG_UPDATE_CURRENT or
                            PendingIntent.FLAG_IMMUTABLE,
                    )
                    val gamePendingIntent = PendingIntent.getActivity(
                        context,
                        InterruptionGameRequestCode,
                        MainActivity.createBlockIntent(
                            context = context,
                            sourcePackageName = sourcePackageName,
                            sourceLabel = sourceLabel,
                            launchTarget = BlockLaunchTarget.RandomRecoveryGame,
                        ).apply {
                            action = ActionOpenInterruptionGame
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or
                            PendingIntent.FLAG_IMMUTABLE,
                    )
                    val readingPendingIntent = PendingIntent.getActivity(
                        context,
                        InterruptionReadingRequestCode,
                        MainActivity.createBlockIntent(
                            context = context,
                            sourcePackageName = sourcePackageName,
                            sourceLabel = sourceLabel,
                            launchTarget = BlockLaunchTarget.ReadingReset,
                        ).apply {
                            action = ActionOpenInterruptionReading
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or
                            PendingIntent.FLAG_IMMUTABLE,
                    )
                    InterruptionNotificationDestinationConfig(
                        contentIntent = homePendingIntent,
                        actions = listOf(
                            InterruptionNotificationAction(
                                label = context.getString(R.string.notif_action_game),
                                pendingIntent = gamePendingIntent,
                            ),
                            InterruptionNotificationAction(
                                label = context.getString(R.string.notif_action_reading),
                                pendingIntent = readingPendingIntent,
                            ),
                        ),
                    )
                }
            }

            val deletePendingIntent = PendingIntent.getService(
                context,
                InterruptionDismissedRequestCode,
                Intent(context, AppMonitorService::class.java).apply {
                    action = AppMonitorService.ActionFallbackNotificationDismissed
                    putExtra(
                        AppMonitorService.ExtraFallbackIncidentPackageName,
                        sourcePackageName,
                    )
                    putExtra(
                        AppMonitorService.ExtraFallbackIncidentStartedAtMillis,
                        incidentStartedAtMillis,
                    )
                    putExtra(
                        AppMonitorService.ExtraFallbackIncidentIsWebsite,
                        isWebsiteIncident,
                    )
                    putExtra(
                        AppMonitorService.ExtraFallbackIncidentIsFocus,
                        isFocusSession,
                    )
                    putExtra(
                        AppMonitorService.ExtraFallbackNotificationStage,
                        stage.name,
                    )
                },
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE,
            )
            val displayedTitle = when {
                adaptiveDecisionId != null || hideSensitive -> "Impulsive"
                isFocusFallback -> context.getString(R.string.notif_focus_active_title)
                else -> InterruptionFallbackNotificationTitle
            }

            val displayedBody = when {
                adaptiveDecisionId != null -> "Choose a different direction"
                hideSensitive -> "Open Impulsive to continue."
                isFocusFallback -> context.getString(R.string.notif_focus_fallback_body, sourceLabel)
                else -> InterruptionFallbackNotificationBody
            }

            val builder =
                NotificationCompat.Builder(
                    context,
                    BlockedAttemptChannelId,
                )
                    .setSmallIcon(R.drawable.ic_notification)
                    .setColor(
                        ContextCompat.getColor(
                            context,
                            R.color.protection_notification_accent,
                        ),
                    )
                    .setContentTitle(displayedTitle)
                    .setContentText(displayedBody)
                    .setStyle(
                        NotificationCompat.BigTextStyle()
                            .setBigContentTitle(displayedTitle)
                            .bigText(displayedBody),
                    )
                    .setContentIntent(destination.contentIntent)
                    .setDeleteIntent(deletePendingIntent)
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .setVisibility(
                        if (hideSensitive) {
                            NotificationCompat.VISIBILITY_SECRET
                        } else {
                            NotificationCompat.VISIBILITY_PRIVATE
                        },
                    )
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
            destination.actions.forEach { action ->
                builder.addAction(
                    R.drawable.ic_notification,
                    action.label,
                    action.pendingIntent,
                )
            }
            val notification = builder.build()
            when (
                submitStandardNotification(
                    BlockedAttemptNotificationId,
                    notification,
                    eligibleDuringSkippedState = false,
                )
            ) {
                ProtectionNotificationSubmission.Posted ->
                    InterruptionNotificationResult.Posted

                ProtectionNotificationSubmission.Queued ->
                    InterruptionNotificationResult.Queued

                ProtectionNotificationSubmission.Suppressed ->
                    InterruptionNotificationResult.Suppressed
            }
        } catch (throwable: Throwable) {
            InterruptionNotificationResult.Failed(throwable)
        }
    }

    fun showProtectionRecoveryNotification(): Boolean {
        val notificationStatus = interruptionNotificationStatus()

        if (
            !canPostProtectionRecoveryNotification(
                notificationStatus,
            )
        ) {
            return false
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            ProtectionRecoveryNotificationRequestCode,
            MainActivity
                .createHomeIntent(context)
                .apply {
                    action = ActionOpenProtectionRecovery
                },
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE,
        )

        val bodyText = context.getString(
            R.string.notif_protection_recovery_body,
        )

        val notification = NotificationCompat.Builder(
            context,
            BlockedAttemptChannelId,
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                context.getString(
                    R.string.notif_protection_recovery_title,
                ),
            )
            .setContentText(bodyText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(bodyText),
            )
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        return submitStandardNotification(
            ProtectionRecoveryNotificationId,
            notification,
        ) != ProtectionNotificationSubmission.Suppressed
    }

    fun cancelProtectionRecoveryNotification() {
        ProtectionNotificationGate.cancelQueued(ProtectionRecoveryNotificationId)
        runCatching {
            NotificationManagerCompat.from(context).cancel(
                ProtectionRecoveryNotificationId,
            )
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
        submitStandardNotification(
            ReleaseWindowPausedNotificationId,
            builder.build(),
        )
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
        submitStandardNotification(ProtectionResumedNotificationId, notification)
    }

    fun cancelReleaseWindowPausedNotification() {
        ProtectionNotificationGate.cancelQueued(ReleaseWindowPausedNotificationId)
        runCatching {
            NotificationManagerCompat.from(context).cancel(ReleaseWindowPausedNotificationId)
        }
    }

    fun showUsageAccessLostNotification() {
        val settingsIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            UsageAccessLostNotificationId,
            settingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val bodyText = context.getString(R.string.notif_usage_access_lost_body)
        val notification = NotificationCompat.Builder(context, BlockedAttemptChannelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_usage_access_lost_title))
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        submitStandardNotification(UsageAccessLostNotificationId, notification)
    }

    fun cancelUsageAccessLostNotification() {
        ProtectionNotificationGate.cancelQueued(UsageAccessLostNotificationId)
        runCatching {
            NotificationManagerCompat.from(context).cancel(UsageAccessLostNotificationId)
        }
    }

    fun showVpnConsentLostNotification() {
        // VpnService.prepare returns the system consent dialog Intent while
        // consent is missing. Tapping the notification opens that dialog
        // directly; if consent came back in the meantime it returns null and
        // the tap falls back to opening Impulsive.
        val consentIntent = VpnService.prepare(context)
        val contentIntent = if (consentIntent != null) {
            PendingIntent.getActivity(
                context,
                VpnConsentLostNotificationId,
                consentIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        } else {
            homePendingIntent(VpnConsentLostNotificationId)
        }
        val bodyText = context.getString(R.string.notif_vpn_consent_lost_body)
        val notification = NotificationCompat.Builder(context, BlockedAttemptChannelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_vpn_consent_lost_title))
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        submitStandardNotification(VpnConsentLostNotificationId, notification)
    }

    fun cancelVpnConsentLostNotification() {
        ProtectionNotificationGate.cancelQueued(VpnConsentLostNotificationId)
        runCatching {
            NotificationManagerCompat.from(context).cancel(VpnConsentLostNotificationId)
        }
    }

    fun showLockdownIncompatibleNotification() {
        val bodyText = "Android's Block connections without VPN setting stops all internet " +
            "for protected apps because Impulsive only checks websites. Open Settings > " +
            "Network > VPN > Impulsive and turn that switch off."
        val notification = NotificationCompat.Builder(context, BlockedAttemptChannelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Turn off Block connections without VPN")
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setContentIntent(vpnSettingsPendingIntent(LockdownIncompatibleNotificationId))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        submitStandardNotification(LockdownIncompatibleNotificationId, notification)
    }

    fun showEncryptedDnsUnreachableNotification() {
        val bodyText = "Websites in protected apps may not load until this network can " +
            "reach encrypted website protection."
        val notification = NotificationCompat.Builder(context, BlockedAttemptChannelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Encrypted website protection is unreachable")
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setContentIntent(homePendingIntent(EncryptedDnsUnreachableNotificationId))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        submitStandardNotification(EncryptedDnsUnreachableNotificationId, notification)
    }

    fun showPrivateDnsBypassNotification() {
        val bodyText = "Private DNS is on. Website protection is bypassed until you turn it off."
        val notification = NotificationCompat.Builder(context, BlockedAttemptChannelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Private DNS is on")
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setContentIntent(privateDnsSettingsPendingIntent(PrivateDnsBypassNotificationId))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        submitStandardNotification(PrivateDnsBypassNotificationId, notification)
    }

    fun cancelPrivateDnsBypassNotification() {
        ProtectionNotificationGate.cancelQueued(PrivateDnsBypassNotificationId)
        runCatching {
            NotificationManagerCompat.from(context).cancel(PrivateDnsBypassNotificationId)
        }
    }
    /**
     * Returns a (titleRes, bodyRes) pair for the blocked-attempt notification,
     * chosen at random so repeated blocks feel human rather than robotic.
     * Every body string keeps the %1$s app-label placeholder, so callers must
     * still pass sourceLabel into getString(bodyRes, sourceLabel).
     */
    private fun nextBlockedAttemptVariant(): Pair<Int, Int> =
        BlockedAttemptVariants.random()

    private fun submitStandardNotification(
        notificationId: Int,
        notification: Notification,
        eligibleDuringSkippedState: Boolean = true,
    ): ProtectionNotificationSubmission {
        val notificationContext = context
        return ProtectionNotificationGate.submit(
            notificationId = notificationId,
            eligibleDuringSkippedState = eligibleDuringSkippedState,
        ) {
            val canPost = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    notificationContext,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED

            if (canPost) {
                NotificationManagerCompat.from(notificationContext).notify(
                    notificationId,
                    notification,
                )
            }
        }
    }

    private fun homePendingIntent(requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            MainActivity.createHomeIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun vpnSettingsPendingIntent(requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            createVpnSettingsIntent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun privateDnsSettingsPendingIntent(requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            createPrivateDnsSettingsIntent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun createVpnSettingsIntent(): Intent {
        val vpnIntent = Intent(Settings.ACTION_VPN_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (vpnIntent.resolveActivity(context.packageManager) != null) {
            return vpnIntent
        }
        return Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun createPrivateDnsSettingsIntent(): Intent {
        val networkIntent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (networkIntent.resolveActivity(context.packageManager) != null) {
            return networkIntent
        }
        return Intent(Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
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
        private val BlockedAttemptVariants: List<Pair<Int, Int>> = listOf(
            R.string.notif_blocked_attempt_title_1 to R.string.notif_blocked_attempt_body_1,
            R.string.notif_blocked_attempt_title_2 to R.string.notif_blocked_attempt_body_2,
            R.string.notif_blocked_attempt_title_3 to R.string.notif_blocked_attempt_body_3,
            R.string.notif_blocked_attempt_title_4 to R.string.notif_blocked_attempt_body_4,
            R.string.notif_blocked_attempt_title_5 to R.string.notif_blocked_attempt_body_5,
            R.string.notif_blocked_attempt_title_6 to R.string.notif_blocked_attempt_body_6,
            R.string.notif_blocked_attempt_title_7 to R.string.notif_blocked_attempt_body_7,
            R.string.notif_blocked_attempt_title_8 to R.string.notif_blocked_attempt_body_8,
        )

        const val MonitoringChannelId = "impulsive_protection_monitoring"
        const val BlockedAttemptChannelId = "impulsive_blocked_attempts"
        const val ReleaseWindowChannelId = "impulsive_release_window_pause"
        const val MonitoringNotificationId = 4201
        const val BlockedAttemptNotificationId = 4202
        const val ReleaseWindowPausedNotificationId = 4203
        const val ProtectionResumedNotificationId = 4204
        const val VpnNotificationId = 4206
        private const val LegacyTemporaryAccessNotificationId = 4208
        const val UsageAccessLostNotificationId = 4209
        const val VpnConsentLostNotificationId = 4210
        const val ProtectionRecoveryNotificationId = 4211
        const val LockdownIncompatibleNotificationId = 4212
        const val EncryptedDnsUnreachableNotificationId = 4213
        const val PrivateDnsBypassNotificationId = 4214
        const val ProtectionResumedTimeoutMillis = 10L * 60L * 1000L
        private const val ProtectionDismissedRequestCode = 1002
        private const val ProtectionRecoveryNotificationRequestCode = 4211
        private const val InterruptionHomeRequestCode = 4220
        private const val InterruptionGameRequestCode = 4221
        private const val InterruptionReadingRequestCode = 4222
        private const val InterruptionDismissedRequestCode = 4223
        private const val InterruptionFocusOptionsRequestCode = 4224

        internal const val ActionOpenInterruptionHome =
            "com.impulsive.app.action.OPEN_INTERRUPTION_HOME"

        private const val ActionOpenInterruptionGame =
            "com.impulsive.app.action.OPEN_INTERRUPTION_GAME"

        private const val ActionOpenInterruptionReading =
            "com.impulsive.app.action.OPEN_INTERRUPTION_READING"

        private const val ActionOpenInterruptionFocusOptions =
            "com.impulsive.app.action.OPEN_INTERRUPTION_FOCUS_OPTIONS"

        private const val ActionOpenProtectionRecovery =
            "com.impulsive.app.action.OPEN_PROTECTION_RECOVERY"
    }
}
