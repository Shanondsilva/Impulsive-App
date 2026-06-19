package com.impulsive.app.backend.service.journal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.impulsive.app.backend.data.local.entity.JournalNoteEntity
import com.impulsive.app.backend.data.repository.JournalRepository
import com.impulsive.app.backend.data.repository.TaskRewardRepository
import com.impulsive.app.backend.domain.model.journal.FeedbackPrompt
import com.impulsive.app.backend.domain.model.journal.JournalNoteType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Handles a tapped answer from the end-of-day feedback notification. It records the
 * answer as today's feedback note, when none exists yet, awards 2 Level Points once
 * per day, and clears the notification, all without opening the app.
 */
class FeedbackAnswerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val questionIndex = intent.getIntExtra(ExtraQuestionIndex, 0)
        val answerIndex = intent.getIntExtra(ExtraAnswerIndex, 0)
        val notificationId = intent.getIntExtra(ExtraNotificationId, FeedbackNotificationId)
        val appContext = context.applicationContext

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val journalRepository = JournalRepository(appContext)
                val taskRewardRepository = TaskRewardRepository(appContext)
                val zone = ZoneId.systemDefault()
                val today = LocalDate.now(zone)

                val alreadyDone = journalRepository.observeNotes().first().any { note ->
                    note.noteType == JournalNoteType.Feedback.storageValue &&
                        Instant.ofEpochMilli(note.createdAtMillis).atZone(zone).toLocalDate() == today
                }

                if (!alreadyDone) {
                    val now = System.currentTimeMillis()
                    journalRepository.upsertNote(
                        JournalNoteEntity(
                            noteType = JournalNoteType.Feedback.storageValue,
                            title = "Today's feedback",
                            body = FeedbackPrompt.answerNoteBody(questionIndex, answerIndex),
                            source = "feedback_notification",
                            createdAtMillis = now,
                            updatedAtMillis = now,
                        ),
                    )
                    taskRewardRepository.awardFeedbackAnswerPointsIfNewDay(FeedbackAnswerPoints)
                }

                NotificationManagerCompat.from(appContext).cancel(notificationId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val Action = "com.impulsive.app.FEEDBACK_ANSWER"
        const val ExtraQuestionIndex = "feedback_question_index"
        const val ExtraAnswerIndex = "feedback_answer_index"
        const val ExtraNotificationId = "feedback_notification_id"
        const val FeedbackNotificationId = 4310
        const val FeedbackAnswerPoints = 2
    }
}
