package com.impulsive.app.backend.service.protection

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.app.NotificationManagerCompat
import com.impulsive.app.backend.data.local.device.ForegroundAppReader
import com.impulsive.app.backend.data.local.device.PrivateDnsChecker
import com.impulsive.app.backend.data.local.device.UsageAccessPermissionChecker
import com.impulsive.app.backend.data.local.preferences.AppSettingsPreferencesDataSource
import com.impulsive.app.backend.data.local.preferences.OneMinuteAccessDataSource
import com.impulsive.app.backend.data.local.preferences.OneMinuteAccessState
import com.impulsive.app.backend.data.local.preferences.ProtectionWindowNotificationDataSource
import com.impulsive.app.backend.data.local.preferences.ProtectionWindowNotificationState
import com.impulsive.app.backend.data.local.preferences.WebsiteProtectionIncidentDataSource
import com.impulsive.app.backend.session.adaptive.AdaptiveIncidentSignal
import com.impulsive.app.backend.session.adaptive.AdaptivePhase4Dependencies
import com.impulsive.app.backend.session.adaptive.AdaptiveProtectionBridge
import com.impulsive.app.backend.session.adaptive.AdaptiveProtectionSource
import com.impulsive.app.backend.data.local.preferences.WebsiteProtectionIncidentRecord
import com.impulsive.app.backend.data.repository.OnboardingRepository
import com.impulsive.app.backend.data.repository.PremiumRepository
import com.impulsive.app.backend.data.repository.ProtectionSetupRepository
import com.impulsive.app.backend.data.repository.ScoreRepository
import com.impulsive.app.backend.data.repository.TaskRewardRepository
import com.impulsive.app.backend.data.repository.UrgeEventRepository
import com.impulsive.app.backend.data.repository.WindowOutcomeRepository
import com.impulsive.app.backend.data.repository.FocusSessionRepository
import com.impulsive.app.backend.data.repository.FocusSetupRepository
import com.impulsive.app.backend.domain.model.focus.FocusSessionPhase
import com.impulsive.app.backend.domain.model.focus.FocusSessionState
import com.impulsive.app.backend.domain.model.focus.isElapsed
import com.impulsive.app.backend.domain.model.premium.PremiumFeature
import com.impulsive.app.backend.domain.model.onboarding.OnboardingAnswers
import com.impulsive.app.backend.domain.model.protection.ProtectionSetupState
import com.impulsive.app.backend.domain.model.protection.ProtectionWindowEvaluator
import com.impulsive.app.backend.domain.model.protection.ProtectionWindowSnapshot
import com.impulsive.app.backend.domain.model.protection.toProtectionWindowKey
import com.impulsive.app.backend.domain.model.release.calculateReleasePlan
import com.impulsive.app.backend.domain.model.release.ReleasePlanDefaults
import com.impulsive.app.backend.domain.model.release.ReleasePlanState
import com.impulsive.app.backend.domain.model.release.minuteOfDayToLocalTime
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.impulsive.app.backend.domain.model.tasks.InitialLevel
import com.impulsive.app.backend.domain.model.tasks.InitialLevelPoints
import com.impulsive.app.backend.domain.model.tasks.PsychologyTaskType
import com.impulsive.app.backend.domain.model.tasks.TaskCompletionRecord
import com.impulsive.app.backend.domain.model.tasks.TaskRewardStoreState
import com.impulsive.app.backend.session.focus.FocusSessionCompletionCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime

class AppMonitorService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val usageAccessChecker by lazy { UsageAccessPermissionChecker(applicationContext) }
    private val foregroundAppReader by lazy { ForegroundAppReader(applicationContext) }
    private val privateDnsChecker by lazy { PrivateDnsChecker(applicationContext) }
    private val notificationHelper by lazy { ProtectionNotificationHelper(applicationContext) }
    private val interruptionMessageSelector by lazy { InterruptionMessageSelector(applicationContext) }
    private val protectionSetupRepository by lazy { ProtectionSetupRepository(applicationContext) }
    private val premiumRepository by lazy { PremiumRepository(applicationContext) }
    private val onboardingRepository by lazy { OnboardingRepository(applicationContext) }
    private val taskRewardRepository by lazy { TaskRewardRepository(applicationContext) }
    private val scoreRepository by lazy { ScoreRepository(applicationContext) }
    private val urgeEventRepository by lazy { UrgeEventRepository(applicationContext) }
    private val windowOutcomeRepository by lazy { WindowOutcomeRepository(applicationContext) }
    private val focusSessionRepository by lazy { FocusSessionRepository(applicationContext) }
    private val focusSetupRepository by lazy { FocusSetupRepository(applicationContext) }
    private val focusSessionCompletionCoordinator by lazy {
        FocusSessionCompletionCoordinator(
            focusSessionRepository = focusSessionRepository,
            taskRewardRepository = taskRewardRepository,
            scoreRepository = scoreRepository,
        )
    }
    private val focusConfiguredBlockedPackages by lazy {
        focusSetupRepository.configuredBlockedPackages.stateIn(
            scope = serviceScope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )
    }
    private val focusSession by lazy {
        focusSessionRepository.session.stateIn(
            scope = serviceScope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )
    }
    private val appSettingsDataSource by lazy { AppSettingsPreferencesDataSource(applicationContext) }
    private val windowNotificationDataSource by lazy { ProtectionWindowNotificationDataSource(applicationContext) }
    private val oneMinuteAccessDataSource by lazy { OneMinuteAccessDataSource(applicationContext) }
    private val websiteProtectionIncidentDataSource by lazy { WebsiteProtectionIncidentDataSource(applicationContext) }
    private val adaptiveProtectionBridge by lazy {
        AdaptiveProtectionBridge(
            AdaptivePhase4Dependencies.coordinator(applicationContext),
            AdaptivePhase4Dependencies.decisions(applicationContext),
            AdaptivePhase4Dependencies.momentPlans(applicationContext),
        )
    }
    private val fallbackReminderCoordinator by lazy {
        InterruptionNotificationReminderCoordinator(
            nowMillis = System::currentTimeMillis,
            schedule = { delayMillis, action ->
                serviceScope.launch {
                    delay(delayMillis)
                    action()
                }
            },
            log = { message -> ProtectionLog.debug(message) },
        )
    }

    // Collected once into the service scope, with no per-tick disk reads.
    private val setupState by lazy {
        protectionSetupRepository.state
            .stateIn(serviceScope, SharingStarted.Eagerly, ProtectionSetupState())
    }
    private val websiteProtectionPlusUnlocked by lazy {
        premiumRepository.hasFeature(PremiumFeature.VpnWebsiteBlocker)
            .stateIn(serviceScope, SharingStarted.Eagerly, false)
    }
    private val onboardingAnswers by lazy {
        onboardingRepository.answers
            .stateIn(serviceScope, SharingStarted.Eagerly, OnboardingAnswers())
    }
    private val taskStoreState by lazy {
        taskRewardRepository.storeState
            .stateIn(serviceScope, SharingStarted.Eagerly, emptyTaskRewardStoreState())
    }
    private val hideSensitiveNotifications by lazy {
        appSettingsDataSource.hideSensitiveNotifications
            .stateIn(serviceScope, SharingStarted.Eagerly, false)
    }
    private val windowNotificationState by lazy {
        windowNotificationDataSource.state
            .stateIn(serviceScope, SharingStarted.Eagerly, ProtectionWindowNotificationState())
    }
    private val oneMinuteAccessState by lazy {
        oneMinuteAccessDataSource.state
            .stateIn(serviceScope, SharingStarted.Eagerly, OneMinuteAccessState())
    }

    private var monitorJob: Job? = null
    private var oneMinuteCountdownJob: Job? = null
    private var foregroundNotificationJob: Job? = null
    private var focusCompletionJob: Job? = null
    private var temporaryNotificationJob: Job? = null
    private var temporaryProtectionNotificationDismissed: Boolean = false

    // Set when the user swipes the persistent monitoring notification away.
    // Honored until protection is toggled off and on: stopping protection
    // destroys this service instance, so a fresh start resets the flag and
    // shows the notification again, which is the intended reset point.
    @Volatile
    private var monitoringNotificationDismissed: Boolean = false

    // The first startForeground after a fresh service start is mandatory and
    // must never be skipped, so dismissal is only honored after it happened.
    @Volatile
    private var hasPromotedToForeground: Boolean = false
    private var focusCompletionJobKey: String? = null
    private var focusMonitoringNotificationActive: Boolean = false
    private var oneMinuteCountdownPackage: String? = null
    private var usageAccessAlertPosted: Boolean = false
    private var vpnConsentAlertPosted: Boolean = false
    private var privateDnsBypassAlertPosted: Boolean = false
    private var lastPrivateDnsCheckAtMillis: Long = 0L
    private var lastHandledPackageName: String? = null
    private var lastHandledAtMillis: Long = 0L
    private var activeFallbackIncidentPackageName: String? = null
    private var activeFallbackIncidentStartedAtMillis: Long? = null
    private var activeFallbackIncidentIsWebsite: Boolean = false
    private var activeFallbackIncidentIsFocus: Boolean = false
    private var activeFallbackAdaptiveDecisionId: String? = null
    // In-memory guards so window outcome recording does not write to DataStore
    // on every poll tick. lastUsedWindowKey prevents repeated used-writes while
    // the user stays inside a protected app during one release window.
    // lastSkippedSweepMinute limits the skipped-window sweep to once per minute.
    private var lastUsedWindowKey: String? = null
    private var lastSkippedSweepMinute: Long = -1L

    // Tracks screen-on state so we can slow the poll cadence when the screen is off.
    private var isScreenOn = true
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> wakeMonitorForScreenOn()
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    websiteProtectionIncidentDataSource.reconcileForegroundPackage(
                        foregroundPackage = null,
                        nowEpochMillis = System.currentTimeMillis(),
                    )
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationHelper.ensureChannels()
        hideSensitiveNotifications.value
        startForegroundNotificationObserver()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        isScreenOn = powerManager.isInteractive
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ActionCancelProtectionNotification -> {
                val shouldStopAfterCancel = monitorJob?.isActive != true
                val keepForegroundMonitor = hasActiveProtectionReason()
                if (keepForegroundMonitor) {
                    promoteToForegroundMonitor()
                } else {
                    removeProtectionNotificationOnly()
                }
                temporaryProtectionNotificationDismissed = false
                monitoringNotificationDismissed = false
                if (shouldStopAfterCancel && !keepForegroundMonitor) stopSelf()
                return if (keepForegroundMonitor) START_STICKY else START_NOT_STICKY
            }
            ActionProtectionNotificationDismissed -> {
                val shouldStopAfterDismiss = monitorJob?.isActive != true
                temporaryProtectionNotificationDismissed = true
                monitoringNotificationDismissed = true
                val keepForegroundMonitor = hasActiveProtectionReason()
                // Deliberately no re-promotion here. Swiping the notification
                // does not demote the service from foreground state, and
                // re-posting it is exactly what the user just declined.
                if (!keepForegroundMonitor) {
                    removeProtectionNotificationOnly()
                }
                if (shouldStopAfterDismiss && !keepForegroundMonitor) stopSelf()
                return if (keepForegroundMonitor) START_STICKY else START_NOT_STICKY
            }
            ActionEndFallbackNotificationIncident -> {
                endFallbackNotificationIncident(
                    packageName =
                        intent.getStringExtra(ExtraFallbackIncidentPackageName),
                    reason = "terminating notification action selected",
                )
                return START_STICKY
            }
            ActionFallbackNotificationDismissed -> {
                recordFallbackNotificationDismissed(intent)
                return START_STICKY
            }
            ActionStop -> {
                stopSelfSafely()
                return START_NOT_STICKY
            }
            ActionStart, null -> {
                promoteToForegroundMonitor()
                startMonitoringIfNeeded()
                reportMonitorHealthyIfRunning()
                val showTemporaryNotification = intent?.getBooleanExtra(
                    ExtraShowTemporaryProtectionNotification,
                    false,
                ) == true
                if (showTemporaryNotification) {
                    temporaryProtectionNotificationDismissed = false
                    monitoringNotificationDismissed = false
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        ProtectionMonitorHealthRegistry.markStopped()
        ProtectionServiceOperationalStateStore.markStopped()
        ProtectionInterruptionOverlay.dismissOwned(ProtectionInterruptionOverlay.Owner.AppMonitor)
        endFallbackNotificationIncident()
        notificationHelper.cancelOneMinuteAccessCountdown()
        unregisterReceiver(screenReceiver)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun promoteToForegroundMonitor() {
        // Every ActionStart lands here: app opens, setup changes, boot, app
        // updates, and watchdog ticks. Re-posting on each of those is what made
        // a swiped notification keep coming back. Skip once dismissed, except
        // for the mandatory first promotion of a fresh service start.
        if (monitoringNotificationDismissed && hasPromotedToForeground) return
        val notification = notificationHelper.createMonitoringNotification(
            session = focusSession.value,
            now = LocalDateTime.now(),
            hideSensitive = hideSensitiveNotifications.value,
        )
        startForegroundWithMonitoringNotification(notification)
    }

    private fun shouldPublishMonitoringNotificationUpdate(): Boolean =
        MonitoringNotificationReconciliationPolicy.resolve(
            monitoringNotificationDismissed = monitoringNotificationDismissed,
            hasPromotedToForeground = hasPromotedToForeground,
        ) == MonitoringNotificationReconciliationAction.PostGenericNotification

    private fun replaceForegroundWithGenericMonitoringNotification() {
        temporaryNotificationJob?.cancel()
        temporaryNotificationJob = null

        if (!shouldPublishMonitoringNotificationUpdate()) {
            return
        }

        val notification = notificationHelper.createMonitoringNotification(
            session = null,
            now = LocalDateTime.now(),
            hideSensitive = hideSensitiveNotifications.value,
        )

        startForegroundWithMonitoringNotification(notification)
    }

    private fun startForegroundWithMonitoringNotification(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                ProtectionNotificationHelper.MonitoringNotificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            ServiceCompat.startForeground(
                this,
                ProtectionNotificationHelper.MonitoringNotificationId,
                notification,
                0,
            )
        }
        hasPromotedToForeground = true
    }

    private fun startForegroundNotificationObserver() {
        if (foregroundNotificationJob?.isActive == true) return

        foregroundNotificationJob = serviceScope.launch {
            combine(
                focusSession,
                hideSensitiveNotifications,
            ) { session, hideSensitive ->
                session to hideSensitive
            }.collectLatest { (session, hideSensitive) ->
                when (session?.phase) {
                    FocusSessionPhase.Running -> {
                        focusMonitoringNotificationActive = true
                        scheduleFocusCompletion(session)

                        if (shouldPublishMonitoringNotificationUpdate()) {
                            notificationHelper.postMonitoringNotification(
                                session = session,
                                now = LocalDateTime.now(),
                                hideSensitive = hideSensitive,
                            )
                        }
                    }

                    FocusSessionPhase.Paused -> {
                        cancelFocusCompletionTimer()
                        focusMonitoringNotificationActive = true

                        if (shouldPublishMonitoringNotificationUpdate()) {
                            notificationHelper.postMonitoringNotification(
                                session = session,
                                now = LocalDateTime.now(),
                                hideSensitive = hideSensitive,
                            )
                        }
                    }

                    FocusSessionPhase.Completed,
                    FocusSessionPhase.EndedEarly,
                    null -> {
                        cancelFocusCompletionTimer()
                        if (focusMonitoringNotificationActive) {
                            focusMonitoringNotificationActive = false
                            reconcileMonitoringAfterFocusEnded()
                        }
                    }
                }
            }
        }
    }

    private fun scheduleFocusCompletion(session: FocusSessionState) {
        val completionAt = session.focusCompletionAt()
        val scheduleKey = "${session.sessionId}|$completionAt"
        if (focusCompletionJob?.isActive == true && focusCompletionJobKey == scheduleKey) return

        cancelFocusCompletionTimer()
        focusCompletionJobKey = scheduleKey
        focusCompletionJob = serviceScope.launch {
            while (isActive) {
                val current = focusSession.value
                    ?.takeIf { candidate ->
                        candidate.sessionId == session.sessionId &&
                            candidate.phase == FocusSessionPhase.Running
                    }
                    ?: return@launch
                val now = LocalDateTime.now()
                val delayMillis = Duration.between(now, current.focusCompletionAt()).toMillis()
                if (delayMillis > 0L) {
                    delay(delayMillis)
                    continue
                }

                completeElapsedFocusSessionIfNeeded(now)
                return@launch
            }
        }
    }

    private fun cancelFocusCompletionTimer() {
        focusCompletionJob?.cancel()
        focusCompletionJob = null
        focusCompletionJobKey = null
    }

    private fun FocusSessionState.focusCompletionAt(): LocalDateTime =
        startedAt
            .plusMinutes(durationMinutes.toLong())
            .plusSeconds(totalPausedSeconds)

    private fun startMonitoringIfNeeded() {
        if (monitorJob?.isActive == true) return
        monitorJob = serviceScope.launch {
            while (isActive) {
                try {
                    evaluateForegroundApp()
                    reportMonitorHealthyIfRunning()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Throwable) {
                    if (
                        ProtectionLog.warnThrottled(
                            key = "monitor_iteration_failure",
                            message =
                                "Protection monitor iteration failed " +
                                    "(exception=${error.javaClass.simpleName})",
                        )
                    ) {
                        FirebaseCrashlytics.getInstance().recordException(error)
                    }
                }

                delay(if (isScreenOn) CheckIntervalMillis else ScreenOffIntervalMillis)
            }
        }
    }

    private fun reportMonitorHealthyIfRunning() {
        if (
            hasPromotedToForeground &&
            monitorJob?.isActive == true
        ) {
            val nowElapsedRealtimeMillis =
                SystemClock.elapsedRealtime()

            ProtectionMonitorHealthRegistry
                .markHealthy(
                    nowElapsedRealtimeMillis,
                )

            ProtectionServiceOperationalStateStore
                .markHealthy(
                    sdkInt = Build.VERSION.SDK_INT,
                    updatedAtElapsedRealtimeMillis =
                        nowElapsedRealtimeMillis,
                )
        }
    }

    /**
     * When the screen turns on, the poll loop may be parked in a long screen-off
     * delay. Restart it so it evaluates the foreground app immediately and resumes
     * the fast on-screen cadence, instead of waiting out the remaining sleep.
     */
    private fun wakeMonitorForScreenOn() {
        isScreenOn = true
        monitorJob?.cancel()
        monitorJob = null
        startMonitoringIfNeeded()
    }

    private suspend fun evaluateForegroundApp() {
        recoverInvalidatedOverlay()

        val now = LocalDateTime.now()
        val windowSnapshot = currentProtectionWindowSnapshot()

        if (completeElapsedFocusSessionIfNeeded(now)) {
            return
        }

        syncWebsiteProtectionTunnel(windowSnapshot)

        if (!hasActiveProtectionReason()) {
            Log.i(Tag, "Stopping AppMonitorService because no protection reason is active")
            endFallbackNotificationIncident()
            stopSelfSafely()
            return
        }

        if (!usageAccessChecker.hasUsageAccess()) {
            endFallbackNotificationIncident()
            ProtectionLog.warnThrottled(
                key = "usage_access_missing",
                message = "Protection monitor skipped: Usage Access is not granted",
            )
            // Usage access can be revoked behind our back by permission auto-reset
            // or OEM cleaners. Without this alert the monitor keeps polling but
            // protection silently does nothing, which the user has no way to see.
            // Latched so the notification posts once per outage, not per tick.
            if (!usageAccessAlertPosted) {
                usageAccessAlertPosted = true
                notificationHelper.showUsageAccessLostNotification()
            }
            return
        }
        if (usageAccessAlertPosted) {
            usageAccessAlertPosted = false
            notificationHelper.cancelUsageAccessLostNotification()
        }

        val currentSetup = setupState.value
        val protectedPackages = currentSetup.selectedBlockedAppPackageNames
        handleWindowNotifications(windowSnapshot)
        sweepSkippedWindows(windowSnapshot.now)
        val foregroundPackage = foregroundAppReader.getCurrentForegroundPackage()
        val websiteIncidentNow =
            System.currentTimeMillis()
        val currentWebsiteIncident =
            websiteProtectionIncidentDataSource.reconcileForegroundPackage(
                foregroundPackage =
                    foregroundPackage,
                nowEpochMillis =
                    websiteIncidentNow,
            )

        if (foregroundPackage == null) {
            endFallbackNotificationIncident()
            ProtectionLog.warnThrottled(
                key = "foreground_package_missing",
                message = "Protection monitor skipped: unable to determine foreground package",
            )
            return
        }
        if (foregroundPackage != activeFallbackIncidentPackageName) {
            endFallbackNotificationIncident(reason = "foreground browser changed")
        }
        if (foregroundPackage == applicationContext.packageName) return

        if (
            currentSetup.websiteProtectionEnabled &&
            foregroundPackage in currentSetup.websiteProtectedAppPackageNames
        ) {
            RecentForegroundWebsiteBrowserRegistry.observe(
                packageName = foregroundPackage,
                observedAtEpochMillis = websiteIncidentNow,
            )
        } else if (!currentSetup.websiteProtectionEnabled) {
            RecentForegroundWebsiteBrowserRegistry.clear()
        }

        currentFallbackIncidentId()
            ?.takeIf { incidentId -> incidentId.isWebsiteIncident }
            ?.let { incidentId ->
                val cancellationReason = when {
                    !currentSetup.websiteProtectionEnabled ->
                        "Website Protection disabled"
                    incidentId.packageName !in currentSetup.websiteProtectedAppPackageNames ->
                        "browser no longer managed by Website Protection"
                    windowSnapshot.isProtectionPaused &&
                        !currentSetup.websiteProtectionAlwaysOn ->
                        "Website Protection paused"
                    ProtectionInterruptionOverlay.isShowing(applicationContext) ->
                        "interruption overlay is showing"
                    else -> null
                }
                if (cancellationReason != null) {
                    endFallbackNotificationIncident(
                        packageName = incidentId.packageName,
                        reason = cancellationReason,
                    )
                }
            }

        if (
            currentSetup.websiteProtectionEnabled &&
            (!windowSnapshot.isProtectionPaused || currentSetup.websiteProtectionAlwaysOn) &&
            currentWebsiteIncident != null &&
            canStartWebsiteInterruption(currentWebsiteIncident.phase)
        ) {
            launchWebsiteProtectionIncidentSurface(
                currentWebsiteIncident,
            )
            return
        }

        // The user has moved off the app we last intercepted (home screen,
        // launcher, or any other app), so clear the handled latch. This lets the
        // very next open of a protected app re-trigger the block screen straight
        // away, instead of being swallowed by the cooldown for up to
        // BlockHandlingCooldownMillis after the first interception. While our own
        // block screen is in the foreground we have already returned above, so
        // the latch is preserved during that transition.
        if (foregroundPackage != lastHandledPackageName) {
            lastHandledPackageName?.let(InterruptionNotificationLimiter::endAppEncounter)
            lastHandledPackageName = null
        }
        // A running focus session blocks its own effective app list,
        // unconditionally and including during release windows. The focus list
        // is the configured one, or the urge-protection list when the user has
        // never configured focus apps. This check runs before the urge gate so
        // focus can block apps the urge system does not cover.
        val liveFocusSession = focusSession.value
        if (liveFocusSession != null && liveFocusSession.phase == FocusSessionPhase.Running) {
            val focusBlockedPackages = focusConfiguredBlockedPackages.value ?: protectedPackages
            if (foregroundPackage in focusBlockedPackages) {
                handleFocusInterruption(
                    sourcePackageName = foregroundPackage,
                    sourceLabel = foregroundAppReader.getApplicationLabel(foregroundPackage),
                )
                return
            }
        }
        if (
            shouldBypassGenericAppInterceptionForWebsiteProtection(
                foregroundPackage = foregroundPackage,
                websiteProtectionEnabled = currentSetup.websiteProtectionEnabled,
                websiteProtectedPackages =
                    currentSetup.websiteProtectedAppPackageNames,
            )
        ) {
            currentFallbackIncidentId()
                ?.takeIf { incidentId -> !incidentId.isWebsiteIncident }
                ?.let { incidentId ->
                    endFallbackNotificationIncident(
                        packageName = incidentId.packageName,
                        reason = "Website Protection owns managed browser",
                    )
                }
            ProtectionLog.debugThrottled(
                key = "website_protected_package:$foregroundPackage",
                message =
                    "Generic app interception skipped because Website Protection owns package: " +
                        foregroundPackage,
                intervalMillis = 10_000L,
            )
            return
        }
        if (!currentSetup.configurationDrivenAppProtectionConsented) {
            endFallbackNotificationIncident()
            ProtectionLog.debugThrottled(
                key = "app_protection_disabled",
                message = "Protection monitor skipped: App Protection transition is not confirmed",
            )
            return
        }
        if (protectedPackages.isEmpty()) {
            endFallbackNotificationIncident()
            ProtectionLog.debugThrottled(
                key = "protected_app_selection_empty",
                message = "Protection monitor skipped: protected-app selection is empty",
            )
            return
        }
        if (foregroundPackage !in protectedPackages) {
            endFallbackNotificationIncident()
            return
        }
        ProtectionLog.debugThrottled(
            key = "protected_package:$foregroundPackage",
            message = "Protected package detected: $foregroundPackage",
            intervalMillis = 10_000L,
        )
        if (windowSnapshot.isProtectionPaused) {
            endFallbackNotificationIncident()
            val pausedStart = windowSnapshot.pausedWindowStart
            if (pausedStart != null && pausedStart.toString() != lastUsedWindowKey) {
                lastUsedWindowKey = pausedStart.toString()
                windowOutcomeRepository.markWindowUsed(pausedStart)
            }
            return
        }
        val activeAllow = oneMinuteAccessState.value
        val nowEpochMillis = System.currentTimeMillis()
        if (
            oneMinuteAccessDataSource.isAllowActiveImmediately(
                key = foregroundPackage,
                nowEpochMillis = nowEpochMillis,
                persistedState = activeAllow,
            )
        ) {
            endFallbackNotificationIncident()
            ensureOneMinuteCountdown(
                packageName = foregroundPackage,
                sourceLabel = foregroundAppReader.getApplicationLabel(foregroundPackage),
                untilEpochMillis = oneMinuteAccessDataSource.activeAllowUntilEpochMillisImmediately(
                    key = foregroundPackage,
                    nowEpochMillis = nowEpochMillis,
                    persistedState = activeAllow,
                ),
            )
            return
        }
        val sourceLabel = foregroundAppReader.getApplicationLabel(foregroundPackage)
        handleBlockedAppOpen(
            sourcePackageName = foregroundPackage,
            sourceLabel = sourceLabel,
        )
    }

    private suspend fun completeElapsedFocusSessionIfNeeded(now: LocalDateTime): Boolean {
        val session = focusSession.value ?: return false
        if (session.phase != FocusSessionPhase.Running) return false
        if (!session.isElapsed(now)) return false
        val completed = focusSessionCompletionCoordinator.completeIfElapsed(now) ?: return false
        if (currentFallbackIncidentId()?.isFocusSession == true) {
            endFallbackNotificationIncident(reason = "focus session completed")
        }
        focusMonitoringNotificationActive = false
        reconcileMonitoringAfterFocusEnded()
        ProtectionLog.debug(
            "Focus session completed: sessionId=${completed.sessionId}",
        )
        return true
    }

    private fun reconcileMonitoringAfterFocusEnded() {
        if (hasActiveNonFocusProtectionReason()) {
            replaceForegroundWithGenericMonitoringNotification()
        } else {
            Log.i(Tag, "Stopping AppMonitorService because Focus completed with no remaining protection reason")
            stopSelfSafely()
        }
    }

    private fun launchWebsiteProtectionIncidentSurface(
        incident: WebsiteProtectionIncidentRecord,
    ) {
        if (
            ProtectionInterruptionOverlay.isShowing(
                applicationContext,
            )
        ) {
            endFallbackNotificationIncident()
            return
        }

        val nowMillis = System.currentTimeMillis()
        val incidentStartedAtMillis = beginFallbackNotificationIncident(
            packageName = incident.packageName,
            nowMillis = nowMillis,
            persistedStartedAtMillis = incident.incidentStartedAtEpochMillis,
            isWebsiteIncident = true,
        )
        val message =
            InterruptionNotificationLimiter.messageForApp(
                packageName = incident.packageName,
                nowMillis = nowMillis,
                incidentStartedAtMillis = incidentStartedAtMillis,
            ) {
                InterruptionFallbackNotificationBody
            }

        serviceScope.launch {
            val handoff = adaptiveProtectionBridge.recognise(
                AdaptiveIncidentSignal(
                    source = AdaptiveProtectionSource.VpnWebsite,
                    incidentStartedAtMillis = incidentStartedAtMillis,
                    ephemeralSourceIdentity = incident.packageName,
                ),
            )
            activeFallbackAdaptiveDecisionId = handoff.decisionId
            ProtectionInterruptionOverlay.show(
                context = applicationContext,
                owner = ProtectionInterruptionOverlay.Owner.Vpn,
                sourcePackageName = incident.packageName,
                sourceLabel = incident.sourceLabel,
                message = message,
                isFocusSession = false,
                resetAtEpochMillis = incident.cooldownUntilEpochMillis,
                incidentStartedAtMillis = incidentStartedAtMillis,
                adaptiveDecisionId = handoff.decisionId,
                onShown = {
                    endFallbackNotificationIncident(incident.packageName)
                },
                onFailure = {
                    if (
                        notificationHelper.interruptionNotificationStatus() !=
                        InterruptionNotificationStatus.Available
                    ) {
                        return@show
                    }

                    scheduleFallbackNotificationStages(
                        incidentId = InterruptionNotificationIncidentId(
                            packageName = incident.packageName,
                            startedAtMillis = incidentStartedAtMillis,
                            isWebsiteIncident = true,
                            isFocusSession = false,
                        ),
                        sourceLabel = incident.sourceLabel,
                        adaptiveDecisionId = handoff.decisionId,
                    )
                },
            )
        }
    }
    /**
     * A protected app reached the foreground during a running focus session.
     * Debounced identically to handleBlockedAppOpen. Records a session
     * interruption instead of an urge event: focus interruptions are a
     * different signal and must not feed the urge trend or taper inputs.
     * Interim: launches the existing block screen; a later prompt reroutes
     * this to the focus recovery screen.
     */
    private fun handleFocusInterruption(
        sourcePackageName: String,
        sourceLabel: String,
    ) {
        val nowMillis = System.currentTimeMillis()
        val sameRecentPackage = lastHandledPackageName == sourcePackageName &&
            nowMillis - lastHandledAtMillis < BlockHandlingCooldownMillis
        if (sameRecentPackage) return
        lastHandledPackageName = sourcePackageName
        lastHandledAtMillis = nowMillis
        serviceScope.launch {
            focusSessionRepository.recordInterruption()
        }
        launchBlockSurface(
            sourcePackageName = sourcePackageName,
            sourceLabel = sourceLabel,
            isFocusSession = true,
        )
    }

    private fun currentBaseReleasePlan(now: LocalDateTime): ReleasePlanState {
        val answers = onboardingAnswers.value
        return calculateReleasePlan(
            selectedDailyUrgeCount = answers.dailyRelapseUrgeCount,
            now = now,
            activeDayStart = minuteOfDayToLocalTime(answers.activeDayStartMinute),
            activeDayEnd = minuteOfDayToLocalTime(answers.activeDayEndMinute),
        )
    }

    private fun currentProtectionWindowSnapshot(): ProtectionWindowSnapshot {
        val now = LocalDateTime.now()
        return ProtectionWindowEvaluator.evaluate(
            now = now,
            releasePlan = currentBaseReleasePlan(now),
            adjustedNextReleaseWindow = taskStoreState.value.adjustedNextReleaseWindow,
        )
    }

    private fun syncWebsiteProtectionTunnel(windowSnapshot: ProtectionWindowSnapshot) {
        val setup = setupState.value

        val desiredByUser = setup.websiteProtectionEnabled
        val plusUnlocked = websiteProtectionPlusUnlocked.value
        val pauseForReleaseWindow =
            windowSnapshot.isProtectionPaused && !setup.websiteProtectionAlwaysOn

        val shouldRunVpn =
            desiredByUser &&
                plusUnlocked &&
                !pauseForReleaseWindow

        if (shouldRunVpn) {
            checkPrivateDnsBypassIfDue()

            if (ImpulsiveVpnService.isRunning) {
                clearVpnConsentAlertIfPosted()
                return
            }
            if (ImpulsiveVpnController.consentIntent(this) == null) {
                clearVpnConsentAlertIfPosted()
                ImpulsiveVpnController.start(this)
            } else if (!vpnConsentAlertPosted) {
                // Android allows one VPN consent at a time, so another VPN app
                // taking the slot revokes ours. Without this alert the restart
                // above skips silently on every tick and website protection is
                // dead with no signal to the user. Latched once per outage.
                vpnConsentAlertPosted = true
                notificationHelper.showVpnConsentLostNotification()
            }
            return
        }

        clearVpnConsentAlertIfPosted()
        clearPrivateDnsBypassAlertIfPosted()
        if (ImpulsiveVpnService.isRunning) {
            ImpulsiveVpnController.stop(this)
        }
    }

    private fun checkPrivateDnsBypassIfDue() {
        val nowMillis =
            System.currentTimeMillis()

        if (
            nowMillis - lastPrivateDnsCheckAtMillis <
            PrivateDnsRecheckIntervalMillis
        ) {
            return
        }

        lastPrivateDnsCheckAtMillis = nowMillis

        if (privateDnsChecker.bypassesLocalDnsFilter()) {
            ProtectionLog.warnThrottled(
                key = "private_dns_bypasses_website_protection",
                message = "Private DNS is active while website protection should run",
            )
            if (!privateDnsBypassAlertPosted) {
                privateDnsBypassAlertPosted = true
                notificationHelper.showPrivateDnsBypassNotification()
            }
            return
        }

        clearPrivateDnsBypassAlertIfPosted()
    }

    private fun clearPrivateDnsBypassAlertIfPosted() {
        if (!privateDnsBypassAlertPosted) return
        privateDnsBypassAlertPosted = false
        notificationHelper.cancelPrivateDnsBypassNotification()
    }
    private fun clearVpnConsentAlertIfPosted() {
        if (!vpnConsentAlertPosted) return
        vpnConsentAlertPosted = false
        notificationHelper.cancelVpnConsentLostNotification()
    }

    /**
     * Marks planned windows from today as skipped once their 25 minute span has
     * fully ended with no recorded usage. Runs at most once per minute. Only
     * today's planned windows are swept on purpose: windows that passed while
     * the service was not running stay unrecorded instead of being guessed as
     * skipped, so the future taper engine only acts on real observations.
     */
    private suspend fun sweepSkippedWindows(now: LocalDateTime) {
        val minuteKey = now.toLocalDate().toEpochDay() * 1_440L + now.hour * 60L + now.minute
        if (minuteKey == lastSkippedSweepMinute) return
        lastSkippedSweepMinute = minuteKey
        val plan = currentBaseReleasePlan(now)
        windowOutcomeRepository.markEndedWindowsSkipped(
            plannedWindowStarts = plan.plannedWindowsToday,
            windowMinutes = ReleasePlanDefaults.ReleaseWindowMinutes,
            now = now,
        )
    }

    private suspend fun handleWindowNotifications(windowSnapshot: ProtectionWindowSnapshot) {
        val notificationState = windowNotificationState.value
        val pausedWindowStart = windowSnapshot.pausedWindowStart
        val pausedWindowEnd = windowSnapshot.pausedWindowEnd
        if (windowSnapshot.isProtectionPaused && pausedWindowStart != null && pausedWindowEnd != null) {
            val windowKey = pausedWindowStart.toProtectionWindowKey()
            if (notificationState.lastPauseWindowKey != windowKey) {
                notificationHelper.showReleaseWindowPausedNotification(pausedWindowEnd)
                windowNotificationDataSource.markPauseNotified(windowKey)
            }
            return
        }

        val lastPausedWindowKey = notificationState.lastPauseWindowKey ?: return
        if (notificationState.lastResumeWindowKey == lastPausedWindowKey) return
        notificationHelper.showProtectionResumedNotification()
        windowNotificationDataSource.markResumeNotified(lastPausedWindowKey)
    }

    private fun handleBlockedAppOpen(
        sourcePackageName: String,
        sourceLabel: String,
    ) {
        val nowMillis = System.currentTimeMillis()
        val sameRecentPackage = lastHandledPackageName == sourcePackageName &&
            nowMillis - lastHandledAtMillis < BlockHandlingCooldownMillis
        if (sameRecentPackage) return
        lastHandledPackageName = sourcePackageName
        lastHandledAtMillis = nowMillis
        serviceScope.launch {
            urgeEventRepository.recordEvent(source = "app", packageName = sourcePackageName)
        }
        launchBlockSurface(
            sourcePackageName = sourcePackageName,
            sourceLabel = sourceLabel,
        )
    }

    private fun recoverInvalidatedOverlay() {
        val interruption =
            ProtectionInterruptionOverlay.consumeInvalidatedInterruption(
                applicationContext,
            ) ?: return

        if (
            notificationHelper.interruptionNotificationStatus() !=
            InterruptionNotificationStatus.Available
        ) {
            return
        }

        val nowMillis = System.currentTimeMillis()
        val isWebsiteIncident =
            interruption.owner == ProtectionInterruptionOverlay.Owner.Vpn
        val incidentStartedAtMillis = beginFallbackNotificationIncident(
            packageName = interruption.sourcePackageName,
            nowMillis = nowMillis,
            isWebsiteIncident = isWebsiteIncident,
            isFocusSession = interruption.isFocusSession,
        )
        InterruptionNotificationLimiter.messageForApp(
            packageName = interruption.sourcePackageName,
            nowMillis = nowMillis,
            incidentStartedAtMillis = incidentStartedAtMillis,
        ) {
            interruption.message
        }

        ProtectionLog.warn("Invalidated overlay fallback notification path activated")
        scheduleFallbackNotificationStages(
            incidentId = InterruptionNotificationIncidentId(
                packageName = interruption.sourcePackageName,
                startedAtMillis = incidentStartedAtMillis,
                isWebsiteIncident = isWebsiteIncident,
                isFocusSession = interruption.isFocusSession,
            ),
            sourceLabel = interruption.sourceLabel,
        )
    }

    private fun launchBlockSurface(
        sourcePackageName: String,
        sourceLabel: String,
        isFocusSession: Boolean = false,
    ) {
        if (
            ProtectionInterruptionOverlay.isShowing(
                applicationContext,
            )
        ) {
            return
        }

        val nowMillis = System.currentTimeMillis()

        val incidentStartedAtMillis = beginFallbackNotificationIncident(
            packageName = sourcePackageName,
            nowMillis = nowMillis,
            isWebsiteIncident = false,
            isFocusSession = isFocusSession,
        )

        val message =
            InterruptionNotificationLimiter.messageForApp(
                packageName = sourcePackageName,
                nowMillis = nowMillis,
                incidentStartedAtMillis = incidentStartedAtMillis,
                selectMessage =
                interruptionMessageSelector::select,
            )

        val normalResetAtEpochMillis =
            oneMinuteAccessState.value
                .cooldownUntilEpochMillis(
                    sourcePackageName,
                    OneMinuteAccessDataSource.OneMinuteAccessCooldownMillis,
                )
                ?.takeIf { resetAt ->
                    resetAt > System.currentTimeMillis()
                }

        serviceScope.launch {
            val adaptiveDecisionId = if (isFocusSession) {
                null
            } else {
                adaptiveProtectionBridge.recognise(
                    AdaptiveIncidentSignal(
                        source = AdaptiveProtectionSource.MonitoredApplication,
                        incidentStartedAtMillis = incidentStartedAtMillis,
                        ephemeralSourceIdentity = sourcePackageName,
                    ),
                ).decisionId
            }
            activeFallbackAdaptiveDecisionId = adaptiveDecisionId
            ProtectionInterruptionOverlay.show(
                context = applicationContext,
                owner =
                ProtectionInterruptionOverlay.Owner.AppMonitor,
                sourcePackageName = sourcePackageName,
                sourceLabel = sourceLabel,
                message = message,
                isFocusSession = isFocusSession,
                resetAtEpochMillis = normalResetAtEpochMillis,
                incidentStartedAtMillis = incidentStartedAtMillis,
                adaptiveDecisionId = adaptiveDecisionId,
                onShown = {
                    endFallbackNotificationIncident(sourcePackageName)
                },
                onFailure = {
                    if (
                        notificationHelper.interruptionNotificationStatus() !=
                        InterruptionNotificationStatus.Available
                    ) {
                        return@show
                    }

                    scheduleFallbackNotificationStages(
                        incidentId = InterruptionNotificationIncidentId(
                            packageName = sourcePackageName,
                            startedAtMillis = incidentStartedAtMillis,
                            isWebsiteIncident = false,
                            isFocusSession = isFocusSession,
                        ),
                        sourceLabel = sourceLabel,
                        adaptiveDecisionId = adaptiveDecisionId,
                    )
                },
            )
        }
        ProtectionLog.debug("Overlay display requested for protected package: $sourcePackageName")
    }

    private fun scheduleFallbackNotificationStages(
        incidentId: InterruptionNotificationIncidentId,
        sourceLabel: String,
        adaptiveDecisionId: String? = activeFallbackAdaptiveDecisionId,
    ) {
        fallbackReminderCoordinator.startOrContinue(incidentId) { stage ->
            if (!fallbackIncidentStillEligible(incidentId)) {
                endFallbackNotificationIncident(
                    packageName = incidentId.packageName,
                    reason = "scheduled stage no longer eligible",
                )
                return@startOrContinue
            }

            if (stage == InterruptionNotificationStage.Initial) {
                ProtectionLog.warn(
                    "Overlay unavailable; fallback notification path activated",
                )
            }

            notificationHelper.showInterruptionFallback(
                sourcePackageName = incidentId.packageName,
                sourceLabel = sourceLabel,
                hideSensitive = hideSensitiveNotifications.value,
                isFocusSession = incidentId.isFocusSession,
                incidentStartedAtMillis = incidentId.startedAtMillis,
                isWebsiteIncident = incidentId.isWebsiteIncident,
                stage = stage,
                adaptiveDecisionId = adaptiveDecisionId,
            )
            ProtectionLog.debug(
                "posted stage=$stage incident=$incidentId notificationId=" +
                    ProtectionNotificationHelper.BlockedAttemptNotificationId,
            )
        }
    }

    private fun fallbackIncidentStillEligible(
        incidentId: InterruptionNotificationIncidentId,
    ): Boolean {
        if (currentFallbackIncidentId() != incidentId) {
            return false
        }

        val foregroundPackage = foregroundAppReader.getCurrentForegroundPackage()
        if (foregroundPackage != incidentId.packageName) {
            return false
        }
        if (ProtectionInterruptionOverlay.isShowing(applicationContext)) {
            return false
        }

        val currentSetup = setupState.value
        val nowMillis = System.currentTimeMillis()
        val windowSnapshot = currentProtectionWindowSnapshot()

        if (incidentId.isWebsiteIncident) {
            return isWebsiteFallbackIncidentEligible(
                incidentMatches = currentFallbackIncidentId() == incidentId,
                websiteProtectionEnabled = currentSetup.websiteProtectionEnabled,
                sameBrowserForeground = foregroundPackage == incidentId.packageName,
                browserIsWebsiteProtected =
                    incidentId.packageName in currentSetup.websiteProtectedAppPackageNames,
                protectionPaused = windowSnapshot.isProtectionPaused,
                websiteProtectionAlwaysOn = currentSetup.websiteProtectionAlwaysOn,
                overlayShowing =
                    ProtectionInterruptionOverlay.isShowing(applicationContext),
                terminatingActionSelected = false,
            )
        }

        if (
            shouldBypassGenericAppInterceptionForWebsiteProtection(
                foregroundPackage = foregroundPackage,
                websiteProtectionEnabled = currentSetup.websiteProtectionEnabled,
                websiteProtectedPackages =
                    currentSetup.websiteProtectedAppPackageNames,
            )
        ) {
            return false
        }

        if (
            oneMinuteAccessDataSource.isAllowActiveImmediately(
                key = foregroundPackage,
                nowEpochMillis = nowMillis,
                persistedState = oneMinuteAccessState.value,
            )
        ) {
            return false
        }

        if (incidentId.isFocusSession) {
            val session = focusSession.value
            val focusPackages =
                focusConfiguredBlockedPackages.value
                    ?: currentSetup.selectedBlockedAppPackageNames

            return session?.phase == FocusSessionPhase.Running &&
                foregroundPackage in focusPackages
        }

        return currentSetup.configurationDrivenAppProtectionConsented &&
            foregroundPackage in currentSetup.selectedBlockedAppPackageNames &&
            !windowSnapshot.isProtectionPaused
    }

    private fun currentFallbackIncidentId(): InterruptionNotificationIncidentId? {
        val packageName = activeFallbackIncidentPackageName ?: return null
        val startedAtMillis = activeFallbackIncidentStartedAtMillis ?: return null

        return InterruptionNotificationIncidentId(
            packageName = packageName,
            startedAtMillis = startedAtMillis,
            isWebsiteIncident = activeFallbackIncidentIsWebsite,
            isFocusSession = activeFallbackIncidentIsFocus,
        )
    }

    private fun recordFallbackNotificationDismissed(intent: Intent) {
        val packageName =
            intent.getStringExtra(ExtraFallbackIncidentPackageName)
                ?: return
        val startedAtMillis =
            intent.getLongExtra(ExtraFallbackIncidentStartedAtMillis, Long.MIN_VALUE)
        val isWebsiteIncident =
            intent.getBooleanExtra(ExtraFallbackIncidentIsWebsite, false)
        val isFocusSession =
            intent.getBooleanExtra(ExtraFallbackIncidentIsFocus, false)
        val stage =
            intent.getStringExtra(ExtraFallbackNotificationStage)
                ?.let { stored ->
                    InterruptionNotificationStage.entries.firstOrNull { candidate ->
                        candidate.name == stored
                    }
                }
                ?: return
        val incidentId = InterruptionNotificationIncidentId(
            packageName = packageName,
            startedAtMillis = startedAtMillis,
            isWebsiteIncident = isWebsiteIncident,
            isFocusSession = isFocusSession,
        )

        fallbackReminderCoordinator.recordDismissed(incidentId, stage)
    }
    private fun beginFallbackNotificationIncident(
        packageName: String,
        nowMillis: Long,
        persistedStartedAtMillis: Long? = null,
        isWebsiteIncident: Boolean,
        isFocusSession: Boolean = false,
    ): Long {
        val continuingIncident =
            activeFallbackIncidentPackageName == packageName &&
                activeFallbackIncidentIsWebsite == isWebsiteIncident &&
                activeFallbackIncidentIsFocus == isFocusSession

        if (!continuingIncident) {
            endFallbackNotificationIncident()
            activeFallbackIncidentPackageName = packageName
            activeFallbackIncidentStartedAtMillis =
                persistedStartedAtMillis ?: nowMillis
            activeFallbackIncidentIsWebsite = isWebsiteIncident
            activeFallbackIncidentIsFocus = isFocusSession
            activeFallbackAdaptiveDecisionId = null
        }

        return requireNotNull(activeFallbackIncidentStartedAtMillis)
    }

    private fun endFallbackNotificationIncident(
        packageName: String? = activeFallbackIncidentPackageName,
        reason: String = "incident ended",
    ) {
        val endedPackageName = packageName ?: return
        InterruptionNotificationLimiter.endAppEncounter(endedPackageName)

        if (activeFallbackIncidentPackageName != endedPackageName) {
            return
        }

        currentFallbackIncidentId()?.let { incidentId ->
            fallbackReminderCoordinator.cancel(incidentId, reason)
        }
        activeFallbackIncidentPackageName = null
        activeFallbackIncidentStartedAtMillis = null
        activeFallbackIncidentIsWebsite = false
        activeFallbackIncidentIsFocus = false
        activeFallbackAdaptiveDecisionId = null
        notificationHelper.cancelBlockedAttemptNotification()
    }

    private fun emptyTaskRewardStoreState() = TaskRewardStoreState(
        records = PsychologyTaskType.entries.associateWith { TaskCompletionRecord(it, false, 0, null) },
        currentLevel = InitialLevel,
        currentLevelPoints = InitialLevelPoints,
        rewardedWindowKey = null,
        rewardedWaitCutDate = null,
        adjustedNextReleaseWindow = null,
        lastRecommendedTaskType = null,
        lastCompletedTaskType = null,
        recentRecommendedTaskTypes = emptyList(),
        currentUrgeIntensity = null,
        currentTriggerType = null,
        currentTriggerSource = null,
        userEnergyState = null,
    )

    private fun stopSelfSafely() {
        ProtectionMonitorHealthRegistry.markStopped()
        ProtectionServiceOperationalStateStore.markStopped()
        cancelFocusCompletionTimer()
        cancelOneMinuteAccessCountdown()
        endFallbackNotificationIncident()
        removeProtectionNotificationOnly()
        stopSelf()
    }

    private fun hasActiveProtectionReason(): Boolean {
        val setup = setupState.value
        if (!setup.isLoaded) return true
        val sessionPhase = focusSession.value?.phase
        val focusActive = sessionPhase == FocusSessionPhase.Running ||
            sessionPhase == FocusSessionPhase.Paused
        return (setup.configurationDrivenAppProtectionConsented &&
            setup.selectedBlockedAppPackageNames.isNotEmpty()) ||
            setup.websiteProtectionEnabled ||
            focusActive
    }

    private fun hasActiveNonFocusProtectionReason(): Boolean {
        val setup = setupState.value
        if (!setup.isLoaded) return true
        return (setup.configurationDrivenAppProtectionConsented &&
            setup.selectedBlockedAppPackageNames.isNotEmpty()) ||
            setup.websiteProtectionEnabled
    }

    private fun removeProtectionNotificationOnly() {
        temporaryNotificationJob?.cancel()
        temporaryNotificationJob = null

        runCatching {
            NotificationManagerCompat.from(this).cancel(ProtectionNotificationHelper.MonitoringNotificationId)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        hasPromotedToForeground = false
    }

    private fun ensureOneMinuteCountdown(
        packageName: String,
        sourceLabel: String,
        untilEpochMillis: Long,
    ) {
        if (oneMinuteCountdownJob?.isActive == true && oneMinuteCountdownPackage == packageName) return

        cancelOneMinuteAccessCountdown()
        oneMinuteCountdownPackage = packageName

        oneMinuteCountdownJob = serviceScope.launch {
            try {
                while (isActive) {
                    val remainingSeconds = ((untilEpochMillis - System.currentTimeMillis()) / 1000L)
                        .coerceAtLeast(0L)
                        .toInt()

                    if (remainingSeconds <= 0) break

                    notificationHelper.showOneMinuteAccessCountdown(
                        sourceLabel = sourceLabel,
                        remainingSeconds = remainingSeconds,
                        hideSensitive = hideSensitiveNotifications.value,
                    )

                    delay(1000L)
                }

                if (isActive) {
                    reblockAfterOneMinuteAccessExpiry(
                        packageName = packageName,
                        sourceLabel = sourceLabel,
                    )
                }
            } finally {
                oneMinuteCountdownPackage = null
                oneMinuteCountdownJob = null
                notificationHelper.cancelOneMinuteAccessCountdown()
            }
        }
    }

    private suspend fun reblockAfterOneMinuteAccessExpiry(
        packageName: String,
        sourceLabel: String,
    ) {
        oneMinuteAccessDataSource.clearActiveAllow()

        val foregroundPackage = foregroundAppReader.getCurrentForegroundPackage()
        if (foregroundPackage != packageName) return
        if (foregroundPackage == applicationContext.packageName) return
        if (foregroundPackage !in setupState.value.selectedBlockedAppPackageNames) return

        val windowSnapshot = currentProtectionWindowSnapshot()
        if (windowSnapshot.isProtectionPaused) {
            val pausedStart = windowSnapshot.pausedWindowStart
            if (pausedStart != null && pausedStart.toString() != lastUsedWindowKey) {
                lastUsedWindowKey = pausedStart.toString()
                windowOutcomeRepository.markWindowUsed(pausedStart)
            }
            return
        }

        lastHandledPackageName = null
        lastHandledAtMillis = 0L

        launchBlockSurface(
            sourcePackageName = packageName,
            sourceLabel = sourceLabel,
        )
    }

    private fun cancelOneMinuteAccessCountdown() {
        oneMinuteCountdownJob?.cancel()
        oneMinuteCountdownJob = null
        oneMinuteCountdownPackage = null
        notificationHelper.cancelOneMinuteAccessCountdown()
    }

    companion object {
        const val ActionStart = "com.impulsive.app.action.START_APP_MONITOR"
        const val ActionStop = "com.impulsive.app.action.STOP_APP_MONITOR"
        const val ActionCancelProtectionNotification =
            "com.impulsive.app.action.CANCEL_PROTECTION_NOTIFICATION"
        const val ActionProtectionNotificationDismissed =
            "com.impulsive.app.action.PROTECTION_NOTIFICATION_DISMISSED"
        const val ActionEndFallbackNotificationIncident =
            "com.impulsive.app.action.END_FALLBACK_NOTIFICATION_INCIDENT"
        const val ActionFallbackNotificationDismissed =
            "com.impulsive.app.action.FALLBACK_NOTIFICATION_DISMISSED"
        const val ExtraFallbackIncidentPackageName =
            "com.impulsive.app.extra.FALLBACK_INCIDENT_PACKAGE_NAME"
        const val ExtraFallbackIncidentStartedAtMillis =
            "com.impulsive.app.extra.FALLBACK_INCIDENT_STARTED_AT_MILLIS"
        const val ExtraFallbackIncidentIsWebsite =
            "com.impulsive.app.extra.FALLBACK_INCIDENT_IS_WEBSITE"
        const val ExtraFallbackIncidentIsFocus =
            "com.impulsive.app.extra.FALLBACK_INCIDENT_IS_FOCUS"
        const val ExtraFallbackNotificationStage =
            "com.impulsive.app.extra.FALLBACK_NOTIFICATION_STAGE"
        const val ExtraShowTemporaryProtectionNotification =
            "com.impulsive.app.extra.SHOW_TEMPORARY_PROTECTION_NOTIFICATION"
        private const val CheckIntervalMillis = 1_200L
        private const val ScreenOffIntervalMillis = 30_000L
        private const val PrivateDnsRecheckIntervalMillis = 60_000L
        private const val Tag = "AppMonitorService"
        // Short guard covering only the gap between launching the block screen
        // and it actually reaching the foreground, so one interception cannot
        // fire twice. Leaving the protected app now clears the latch directly in
        // evaluateForegroundApp, so this no longer needs to be long.
        private const val BlockHandlingCooldownMillis = 4_000L
    }
}
