package com.impulsive.app.backend.service.journal

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Instant
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
        val nowMillis =
            System.currentTimeMillis()

        val delayMillis =
            nextScheduledAtMillis(
                nowMillis = nowMillis,
            ) - nowMillis

        val request =
            OneTimeWorkRequestBuilder<
                FeedbackPromptWorker
            >()
                .setInitialDelay(
                    delayMillis.coerceAtLeast(0L),
                    TimeUnit.MILLISECONDS,
                )
                .build()

        workManager.enqueueUniqueWork(
            WorkName,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    companion object {
        const val WorkName =
            "feedback_prompt_daily"

        const val NudgeHour = 21

        fun nextScheduledAtMillis(
            nowMillis: Long =
                System.currentTimeMillis(),
            zone: ZoneId =
                ZoneId.systemDefault(),
        ): Long {
            val now =
                Instant
                    .ofEpochMilli(nowMillis)
                    .atZone(zone)

            var next =
                LocalDateTime.of(
                    now.toLocalDate(),
                    LocalTime.of(
                        NudgeHour,
                        0,
                    ),
                ).atZone(zone)

            if (!next.isAfter(now)) {
                next = next.plusDays(1)
            }

            return next
                .toInstant()
                .toEpochMilli()
        }
    }
}
