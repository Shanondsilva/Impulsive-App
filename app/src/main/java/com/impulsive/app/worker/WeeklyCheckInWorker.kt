package com.impulsive.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.impulsive.app.MainActivity
import com.impulsive.app.R
import com.impulsive.app.data.db.WeeklyTargetDao
import com.impulsive.app.util.WeekUtil
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Fires every 7 days. Checks if the user has already done a check-in for this week.
 * If not, posts a notification prompting them to review their week.
 * Scheduled to fire on Sunday evenings (~8PM local time).
 */
class WeeklyCheckInWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params), KoinComponent {

    private val weeklyTargetDao: WeeklyTargetDao by inject()

    override suspend fun doWork(): Result {
        val mondayOfCurrentWeek = WeekUtil.currentWeekStart()
        val existing = weeklyTargetDao.getForWeek(mondayOfCurrentWeek)

        // Already have a target for this week — nothing to do
        if (existing != null) return Result.success()

        postCheckInNotification(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "weekly_check_in"
        const val CHANNEL_ID = "weekly_check_in_channel"

        fun schedule(context: Context) {
            val delayMs = WeekUtil.msUntilNextSunday(hour = 20)

            val request = PeriodicWorkRequestBuilder<WeeklyCheckInWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun createNotificationChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Weekly Check-In",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminder to review your weekly progress and confirm your next session target."
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        private fun postCheckInNotification(context: Context) {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("navigate_to", "weekly_check_in")
            }
            val pending = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Weekly reflection available.")
                .setContentText("Review your progress and set next week's target.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(context).notify(3001, notification)
        }
    }
}
