package com.impulsive.app.backend.service.journal

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Schedules the end-of-day feedback nudge for the next evening. The worker reschedules
 * itself after each run, so this only needs calling on app start and after boot.
 */
class FeedbackPromptScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun scheduleDailyNudge() {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        var next = LocalDateTime.of(LocalDate.now(zone), LocalTime.of(NudgeHour, 0))
        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }
        val delayMillis = next.atZone(zone).toInstant().toEpochMilli() -
            now.atZone(zone).toInstant().toEpochMilli()

        val request = OneTimeWorkRequestBuilder<FeedbackPromptWorker>()
            .setInitialDelay(delayMillis.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .build()

        workManager.enqueueUniqueWork(
            WorkName,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    companion object {
        const val WorkName = "feedback_prompt_daily"
        const val NudgeHour = 21
    }
}
