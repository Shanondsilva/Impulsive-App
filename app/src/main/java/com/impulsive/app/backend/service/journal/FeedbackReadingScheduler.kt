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

/** Schedules the just-after-midnight reading of the day's feedback note. Reschedules itself. */
class FeedbackReadingScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun scheduleDailyReading() {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        var next = LocalDateTime.of(LocalDate.now(zone), LocalTime.of(ReadingHour, ReadingMinute))
        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }
        val delayMillis = next.atZone(zone).toInstant().toEpochMilli() -
            now.atZone(zone).toInstant().toEpochMilli()

        val request = OneTimeWorkRequestBuilder<FeedbackReadingWorker>()
            .setInitialDelay(delayMillis.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .build()

        workManager.enqueueUniqueWork(WorkName, ExistingWorkPolicy.REPLACE, request)
    }

    companion object {
        const val WorkName = "feedback_reading_daily"
        const val ReadingHour = 0
        const val ReadingMinute = 5
    }
}
