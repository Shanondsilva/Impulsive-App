package com.impulsive.app.backend.service.journal

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.impulsive.app.backend.data.local.preferences.FeedbackInsightStore
import com.impulsive.app.backend.data.repository.JournalRepository
import com.impulsive.app.backend.domain.model.journal.FeedbackAnalyzer
import com.impulsive.app.backend.domain.model.journal.JournalNoteType
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Just after midnight, reads the previous day's single feedback note, analyses it
 * conservatively, and stores the insight once. Insight only, no timing changes.
 */
class FeedbackReadingWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        try {
            val zone = ZoneId.systemDefault()
            val day = LocalDate.now(zone).minusDays(1)
            val store = FeedbackInsightStore(applicationContext)
            if (!store.hasDate(day.toString())) {
                val note = JournalRepository(applicationContext).observeNotes().first()
                    .firstOrNull { n ->
                        n.noteType == JournalNoteType.Feedback.storageValue &&
                            Instant.ofEpochMilli(n.createdAtMillis).atZone(zone).toLocalDate() == day
                    }
                if (note != null) {
                    store.recordIfNewDay(FeedbackAnalyzer.analyze(day, note.body))
                }
            }
        } finally {
            FeedbackReadingScheduler(applicationContext).scheduleDailyReading()
        }
        return Result.success()
    }
}
