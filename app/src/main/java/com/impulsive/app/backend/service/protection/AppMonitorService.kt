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
import android.provider.Settings
import androidx.core.app.ServiceCompat
import com.impulsive.app.MainActivity
import com.impulsive.app.backend.data.local.device.ForegroundAppReader
import com.impulsive.app.backend.data.local.device.UsageAccessPermissionChecker
import com.impulsive.app.backend.data.local.preferences.AppSettingsPreferencesDataSource
import com.impulsive.app.backend.data.local.preferences.ProtectionWindowNotificationDataSource
import com.impulsive.app.backend.data.local.preferences.ProtectionWindowNotificationState
import com.impulsive.app.backend.data.repository.OnboardingRepository
import com.impulsive.app.backend.data.repository.ProtectionSetupRepository
import com.impulsive.app.backend.data.repository.TaskRewardRepository
import com.impulsive.app.backend.domain.model.onboarding.OnboardingAnswers
import com.impulsive.app.backend.domain.model.protection.ProtectionSetupState
import com.impulsive.app.backend.domain.model.protection.ProtectionWindowEvaluator
import com.impulsive.app.backend.domain.model.protection.ProtectionWindowSnapshot
import com.impulsive.app.backend.domain.model.protection.toProtectionWindowKey
import com.impulsive.app.backend.domain.model.release.calculateReleasePlan
import com.impulsive.app.backend.domain.model.release.minuteOfDayToLocalTime
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.impulsive.app.backend.domain.model.tasks.InitialLevel
import com.impulsive.app.backend.domain.model.tasks.InitialLevelPoints
import com.impulsive.app.backend.domain.model.tasks.PsychologyTaskType
import com.impulsive.app.backend.domain.model.tasks.TaskCompletionRecord
import com.impulsive.app.backend.domain.model.tasks.TaskRewardStoreState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class AppMonitorService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val usageAccessChecker by lazy { UsageAccessPermissionChecker(applicationContext) }
    private val foregroundAppReader by lazy { ForegroundAppReader(applicationContext) }
    private val notificationHelper by lazy { ProtectionNotificationHelper(applicationContext) }
    private val protectionSetupRepository by lazy { ProtectionSetupRepository(applicationContext) }
    private val onboardingRepository by lazy { OnboardingRepository(applicationContext) }
    private val taskRewardRepository by lazy { TaskRewardRepository(applicationContext) }
    private val appSettingsDataSource by lazy { AppSettingsPreferencesDataSource(applicationContext) }
    private val windowNotificationDataSource by lazy { ProtectionWindowNotificationDataSource(applicationContext) }

    // Collected once into the service scope — no per-tick disk reads.
    private val setupState by lazy {
        protectionSetupRepository.state
            .stateIn(serviceScope, SharingStarted.Eagerly, ProtectionSetupState())
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

    private var monitorJob: kotlinx.coroutines.Job? = null
    private var lastHandledPackageName: String? = null
    private var lastHandledAtMillis: Long = 0L

    // Tracks screen-on state so we can slow the poll cadence when the screen is off.
    private var isScreenOn = true
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> wakeMonitorForScreenOn()
                Intent.ACTION_SCREEN_OFF -> isScreenOn = false
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationHelper.ensureChannels()
        hideSensitiveNotifications.value
        startAsForegroundService()
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
            ActionStop -> {
                stopSelfSafely()
                return START_NOT_STICKY
            }
            ActionStart, null -> startMonitoringIfNeeded()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        unregisterReceiver(screenReceiver)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startAsForegroundService() {
        val notification = notificationHelper.createMonitoringNotification()
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
    }

    private fun startMonitoringIfNeeded() {
        if (monitorJob?.isActive == true) return
        monitorJob = serviceScope.launch {
            while (isActive) {
                runCatching { evaluateForegroundApp() }
                    .onFailure { FirebaseCrashlytics.getInstance().recordException(it) }
                delay(if (isScreenOn) CheckIntervalMillis else ScreenOffIntervalMillis)
            }
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
        if (!usageAccessChecker.hasUsageAccess()) return
        val protectedPackages = setupState.value.selectedBlockedAppPackageNames
        if (protectedPackages.isEmpty()) return
        val windowSnapshot = currentProtectionWindowSnapshot()
        handleWindowNotifications(windowSnapshot)
        val foregroundPackage = foregroundAppReader.getCurrentForegroundPackage() ?: return
        if (foregroundPackage == applicationContext.packageName) return
        if (foregroundPackage !in protectedPackages) return
        if (windowSnapshot.isProtectionPaused) return
        val sourceLabel = foregroundAppReader.getApplicationLabel(foregroundPackage)
        handleBlockedAppOpen(
            sourcePackageName = foregroundPackage,
            sourceLabel = sourceLabel,
        )
    }

    private fun currentProtectionWindowSnapshot(): ProtectionWindowSnapshot {
        val now = LocalDateTime.now()
        val answers = onboardingAnswers.value
        val baseReleasePlan = calculateReleasePlan(
            selectedDailyUrgeCount = answers.dailyRelapseUrgeCount,
            now = now,
            activeDayStart = minuteOfDayToLocalTime(answers.activeDayStartMinute),
            activeDayEnd = minuteOfDayToLocalTime(answers.activeDayEndMinute),
        )
        return ProtectionWindowEvaluator.evaluate(
            now = now,
            releasePlan = baseReleasePlan,
            adjustedNextReleaseWindow = taskStoreState.value.adjustedNextReleaseWindow,
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
        val blockIntent = MainActivity.createBlockIntent(
            context = this,
            sourcePackageName = sourcePackageName,
            sourceLabel = sourceLabel,
        )
        if (Settings.canDrawOverlays(this)) {
            runCatching { startActivity(blockIntent) }
                .onFailure {
                    FirebaseCrashlytics.getInstance().recordException(it)
                    notificationHelper.showBlockFullScreen(
                        sourcePackageName = sourcePackageName,
                        sourceLabel = sourceLabel,
                        hideSensitive = hideSensitiveNotifications.value,
                    )
                }
        } else {
            notificationHelper.showBlockFullScreen(
                sourcePackageName = sourcePackageName,
                sourceLabel = sourceLabel,
                hideSensitive = hideSensitiveNotifications.value,
            )
        }
    }

    private fun emptyTaskRewardStoreState() = TaskRewardStoreState(
        records = PsychologyTaskType.entries.associateWith { TaskCompletionRecord(it, false, 0, null) },
        currentLevel = InitialLevel,
        currentLevelPoints = InitialLevelPoints,
        rewardedWindowKey = null,
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
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ActionStart = "com.impulsive.app.action.START_APP_MONITOR"
        const val ActionStop = "com.impulsive.app.action.STOP_APP_MONITOR"
        private const val CheckIntervalMillis = 1_200L
        private const val ScreenOffIntervalMillis = 30_000L
        private const val BlockHandlingCooldownMillis = 12_000L
    }
}
