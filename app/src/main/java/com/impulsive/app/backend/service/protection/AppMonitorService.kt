package com.impulsive.app.backend.service.protection

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.impulsive.app.MainActivity
import com.impulsive.app.backend.data.local.device.ForegroundAppReader
import com.impulsive.app.backend.data.local.device.UsageAccessPermissionChecker
import com.impulsive.app.backend.data.local.preferences.ProtectionWindowNotificationDataSource
import com.impulsive.app.backend.data.repository.OnboardingRepository
import com.impulsive.app.backend.data.repository.ProtectionSetupRepository
import com.impulsive.app.backend.data.repository.TaskRewardRepository
import com.impulsive.app.backend.domain.model.protection.ProtectionWindowEvaluator
import com.impulsive.app.backend.domain.model.protection.ProtectionWindowSnapshot
import com.impulsive.app.backend.domain.model.protection.toProtectionWindowKey
import com.impulsive.app.backend.domain.model.release.calculateReleasePlan
import com.impulsive.app.backend.domain.model.release.minuteOfDayToLocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
    private val windowNotificationDataSource by lazy { ProtectionWindowNotificationDataSource(applicationContext) }
    private var monitorStarted = false
    private var lastHandledPackageName: String? = null
    private var lastHandledAtMillis: Long = 0L

    override fun onCreate() {
        super.onCreate()
        notificationHelper.ensureChannels()
        startAsForegroundService()
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
        if (monitorStarted) return
        monitorStarted = true
        serviceScope.launch {
            while (isActive) {
                runCatching { evaluateForegroundApp() }
                delay(CheckIntervalMillis)
            }
        }
    }

    private suspend fun evaluateForegroundApp() {
        if (!usageAccessChecker.hasUsageAccess()) return
        val setupState = protectionSetupRepository.state.first()
        val protectedPackages = setupState.selectedBlockedAppPackageNames
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

    private suspend fun currentProtectionWindowSnapshot(): ProtectionWindowSnapshot {
        val now = LocalDateTime.now()
        val answers = onboardingRepository.answers.first()
        val taskStoreState = taskRewardRepository.storeState.first()
        val baseReleasePlan = calculateReleasePlan(
            selectedDailyUrgeCount = answers.dailyRelapseUrgeCount,
            now = now,
            activeDayStart = minuteOfDayToLocalTime(answers.activeDayStartMinute),
            activeDayEnd = minuteOfDayToLocalTime(answers.activeDayEndMinute),
        )
        return ProtectionWindowEvaluator.evaluate(
            now = now,
            releasePlan = baseReleasePlan,
            adjustedNextReleaseWindow = taskStoreState.adjustedNextReleaseWindow,
        )
    }

    private suspend fun handleWindowNotifications(windowSnapshot: ProtectionWindowSnapshot) {
        val notificationState = windowNotificationDataSource.state.first()
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
        runCatching { startActivity(blockIntent) }
        notificationHelper.showBlockedAttemptNotification(
            sourcePackageName = sourcePackageName,
            sourceLabel = sourceLabel,
        )
    }

    private fun stopSelfSafely() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ActionStart = "com.impulsive.app.action.START_APP_MONITOR"
        const val ActionStop = "com.impulsive.app.action.STOP_APP_MONITOR"
        private const val CheckIntervalMillis = 1_200L
        private const val BlockHandlingCooldownMillis = 12_000L
    }
}
