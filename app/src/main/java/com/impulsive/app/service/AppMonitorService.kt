package com.impulsive.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.impulsive.app.data.repository.ImpulsiveRepository
import com.impulsive.app.ui.home.hasNotificationPermission
import com.impulsive.app.ui.home.hasUsageStatsPermission
import com.impulsive.app.ui.intercept.InterceptActivity
import com.impulsive.app.ui.relapse.RelapseActivity
import com.impulsive.app.ui.timer.SessionTimerActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class AppMonitorService : Service() {

    private val repository: ImpulsiveRepository by inject()
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private var monitoredPackages: Set<String> = emptySet()
    private var lastInterceptedPackage: String = ""
    private var interceptActive: Boolean = false
    private var bypassNotified: Boolean = false

    companion object {
        const val MONITOR_CHANNEL_ID   = "impulsive_monitor"
        const val INTERCEPT_CHANNEL_ID = "impulsive_intercept"
        const val MONITOR_NOTIF_ID     = 1001
        const val INTERCEPT_NOTIF_ID   = 1002
        const val POLL_INTERVAL_MS     = 500L

        const val ACTION_INTERCEPT_DISMISSED = "com.impulsive.app.INTERCEPT_DISMISSED"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, AppMonitorService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AppMonitorService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannels()
        startForeground(MONITOR_NOTIF_ID, buildMonitorNotification())
        observeProfile()
        startPolling()
        startBypassCheck()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_INTERCEPT_DISMISSED) {
            interceptActive = false
            cancelInterceptNotification()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun observeProfile() {
        scope.launch {
            repository.observeProfile().collect { profile ->
                monitoredPackages = profile?.monitoredApps
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() }
                    ?.toSet()
                    ?: emptySet()
            }
        }
    }

    private fun startPolling() {
        scope.launch {
            val usageStatsManager =
                getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

            while (true) {
                val foreground = getForegroundPackage(usageStatsManager)

                when {
                    // Session active: user tried to open the locked app — bring timer back
                    foreground != null
                            && SessionTimerService.isSessionActive
                            && foreground == SessionTimerService.lockedPackage -> {
                        bringTimerToFront()
                    }

                    // Monitored app detected — show intercept if not already shown
                    foreground != null
                            && foreground in monitoredPackages
                            && foreground != lastInterceptedPackage
                            && !SessionTimerService.isSessionActive -> {
                        lastInterceptedPackage = foreground
                        interceptActive = true
                        showInterceptNotification(foreground)
                    }

                    // User left a monitored app — reset so we intercept on next open
                    foreground != null
                            && foreground !in monitoredPackages
                            && foreground != packageName -> {
                        if (lastInterceptedPackage.isNotEmpty()) {
                            lastInterceptedPackage = ""
                        }
                    }
                }

                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun getForegroundPackage(usageStatsManager: UsageStatsManager): String? {
        val now = System.currentTimeMillis()
        return usageStatsManager
            .queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 5_000L, now)
            ?.filter { it.lastTimeUsed > 0 }
            ?.maxByOrNull { it.lastTimeUsed }
            ?.packageName
    }

    private fun showInterceptNotification(detectedPackage: String) {
        val fullScreenIntent = Intent(this, InterceptActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(InterceptActivity.EXTRA_PACKAGE, detectedPackage)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            INTERCEPT_NOTIF_ID,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, INTERCEPT_CHANNEL_ID)
            .setContentTitle("Pause before you open that.")
            .setContentText("Tap to engage the impulse check.")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setAutoCancel(false)
            .setOngoing(true)
            .build()

        getSystemService(NotificationManager::class.java)
            .notify(INTERCEPT_NOTIF_ID, notification)
    }

    private fun bringTimerToFront() {
        val intent = Intent(this, SessionTimerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            this, 999, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, INTERCEPT_CHANNEL_ID)
            .setContentTitle("Your session is still running.")
            .setContentText("Return to your focus window.")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pending, true)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java)
            .notify(INTERCEPT_NOTIF_ID, notification)
    }

    private fun startBypassCheck() {
        scope.launch {
            delay(10_000L) // Give the user time to grant permissions on first launch
            while (true) {
                val usageRevoked = !hasUsageStatsPermission(this@AppMonitorService)
                val notifRevoked = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !hasNotificationPermission(this@AppMonitorService)
                if (usageRevoked || notifRevoked) {
                    if (!bypassNotified) {
                        bypassNotified = true
                        repository.logBypass("monitoring_revoked")
                        RelapseActivity.start(this@AppMonitorService)
                    }
                } else {
                    bypassNotified = false
                }
                delay(5_000L)
            }
        }
    }

    private fun cancelInterceptNotification() {
        getSystemService(NotificationManager::class.java).cancel(INTERCEPT_NOTIF_ID)
    }

    private fun buildMonitorNotification(): Notification =
        NotificationCompat.Builder(this, MONITOR_CHANNEL_ID)
            .setContentTitle("Impulsive is active")
            .setContentText("Monitoring for impulse triggers")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(
            NotificationChannel(
                MONITOR_CHANNEL_ID,
                "Background Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent monitor notification"
                setShowBadge(false)
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                INTERCEPT_CHANNEL_ID,
                "Impulse Intercept",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Intercept alert when a monitored app is opened"
                setShowBadge(false)
            }
        )
    }
}
