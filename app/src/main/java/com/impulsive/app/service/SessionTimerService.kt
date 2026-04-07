package com.impulsive.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.impulsive.app.data.db.UserProfile
import com.impulsive.app.data.repository.ImpulsiveRepository
import com.impulsive.app.ui.timer.SessionTimerActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class SessionTimerService : Service() {

    private val repository: ImpulsiveRepository by inject()
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    companion object {
        const val CHANNEL_ID          = "impulsive_session"
        const val NOTIFICATION_ID     = 2001
        const val SESSION_DURATION_MS = 120_000L
        const val PREFS_NAME          = "impulsive_session"
        const val PREF_SESSION_START  = "session_start"
        const val PREF_LOCKED_PACKAGE = "locked_package"

        const val ACTION_STOP   = "com.impulsive.app.SESSION_STOP"
        const val EXTRA_ELAPSED = "extra_elapsed_ms"
        const val EXTRA_PACKAGE = "extra_locked_package"

        // Visible to AppMonitorService and SessionTimerActivity
        @Volatile var isSessionActive:  Boolean = false
        @Volatile var lockedPackage:    String  = ""
        @Volatile var sessionStartTime: Long    = 0L

        fun start(context: Context, lockedPackage: String = "") {
            context.startForegroundService(
                Intent(context, SessionTimerService::class.java).apply {
                    putExtra(EXTRA_PACKAGE, lockedPackage)
                }
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, SessionTimerService::class.java).apply {
                    action = ACTION_STOP
                }
            )
        }
    }

    private var startTime = 0L

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification(SESSION_DURATION_MS))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            isSessionActive = false
            lockedPackage   = ""
            recordSessionEnd()
            stopSelf()
            return START_NOT_STICKY
        }
        // Store the locked package and mark session active
        lockedPackage    = intent?.getStringExtra(EXTRA_PACKAGE) ?: ""
        isSessionActive  = true
        startTime        = System.currentTimeMillis()
        sessionStartTime = startTime
        // Persist so MainActivity can recover a killed session
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putLong(PREF_SESSION_START, startTime)
            .putString(PREF_LOCKED_PACKAGE, lockedPackage)
            .apply()
        runTimer()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun runTimer() {
        scope.launch {
            while (true) {
                val elapsed   = System.currentTimeMillis() - startTime
                val remaining = (SESSION_DURATION_MS - elapsed).coerceAtLeast(0L)

                getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, buildNotification(remaining))

                if (remaining <= 0L) {
                    isSessionActive = false
                    lockedPackage   = ""
                    recordSessionEnd()
                    stopSelf()
                    break
                }
                delay(500)
            }
        }
    }

    private fun recordSessionEnd() {
        // Clear crash-recovery prefs
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .remove(PREF_SESSION_START)
            .remove(PREF_LOCKED_PACKAGE)
            .apply()
        scope.launch {
            val profile = repository.getProfile() ?: return@launch
            repository.saveProfile(
                profile.copy(lastSessionCompleteTimestamp = System.currentTimeMillis())
            )
            repository.logEval(phase = 3, name = "session_complete", value = "duration=120s")
        }
    }

    private fun buildNotification(remainingMs: Long): Notification {
        val seconds = remainingMs / 1000
        val formatted = "%02d:%02d".format(seconds / 60, seconds % 60)

        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, SessionTimerActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Session in progress")
            .setContentText("Time remaining: $formatted")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openIntent)
            .build()
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Session Timer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                description = "Counts down your active session"
            }
        )
    }
}
