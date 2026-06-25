package com.impulsive.app.backend.service.journal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.impulsive.app.backend.data.repository.FeedbackResponseRepository
import com.impulsive.app.backend.data.repository.TaskRewardRepository
import com.impulsive.app.backend.domain.model.journal.FeedbackPrompt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Handles an answer selected from the daily feedback notification.
 *
 * The answer is stored only in feedback_responses, receives the existing
 * seven-day expiry, awards Level Points once, and clears the Android
 * notification without opening the app.
 */
class FeedbackAnswerReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val hasResponseId =
            intent.hasExtra(ExtraResponseId)

        val responseId = intent.getLongExtra(
            ExtraResponseId,
            -1L,
        )

        val questionIndex = intent.getIntExtra(
            ExtraQuestionIndex,
            0,
        )

        val answerIndex = intent.getIntExtra(
            ExtraAnswerIndex,
            0,
        )

        val notificationId = intent.getIntExtra(
            ExtraNotificationId,
            FeedbackNotificationId,
        )

        val appContext = context.applicationContext
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val feedbackResponseRepository =
                    FeedbackResponseRepository(appContext)

                val taskRewardRepository =
                    TaskRewardRepository(appContext)

                if (hasResponseId) {
                    handleQueuedAnswer(
                        responseId = responseId,
                        answerIndex = answerIndex,
                        feedbackResponseRepository =
                            feedbackResponseRepository,
                        taskRewardRepository =
                            taskRewardRepository,
                    )
                } else {
                    handleLegacyAnswer(
                        questionIndex = questionIndex,
                        answerIndex = answerIndex,
                        feedbackResponseRepository =
                            feedbackResponseRepository,
                        taskRewardRepository =
                            taskRewardRepository,
                    )
                }

                NotificationManagerCompat
                    .from(appContext)
                    .cancel(notificationId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleQueuedAnswer(
        responseId: Long,
        answerIndex: Int,
        feedbackResponseRepository:
            FeedbackResponseRepository,
        taskRewardRepository: TaskRewardRepository,
    ) {
        if (responseId <= 0L) return
        if (answerIndex !in 0..1) return

        val answeredAtMillis =
            System.currentTimeMillis()

        val markedAnswered =
            feedbackResponseRepository.markAnswered(
                responseId = responseId,
                answerIndex = answerIndex,
                answeredAtMillis = answeredAtMillis,
            )

        if (!markedAnswered) {
            return
        }

        taskRewardRepository
            .awardFeedbackAnswerPointsIfNewDay(
                FeedbackAnswerPoints,
            )
    }

    private suspend fun handleLegacyAnswer(
        questionIndex: Int,
        answerIndex: Int,
        feedbackResponseRepository:
            FeedbackResponseRepository,
        taskRewardRepository: TaskRewardRepository,
    ) {
        if (answerIndex !in 0..1) return

        val nowMillis = System.currentTimeMillis()
        val today = LocalDate.now()

        feedbackResponseRepository.deleteExpired(
            nowMillis,
        )

        val normalizedQuestionIndex =
            questionIndex.coerceIn(
                0,
                FeedbackPrompt.count - 1,
            )

        val question =
            FeedbackPrompt.questionAt(
                normalizedQuestionIndex,
            )

        val responseId =
            feedbackResponseRepository.createPending(
                promptDateEpochDay =
                    today.toEpochDay(),
                questionIndex =
                    normalizedQuestionIndex,
                questionText =
                    question.question,
                positiveAnswerText =
                    question.positiveAnswer,
                honestAnswerText =
                    question.honestAnswer,
                createdAtMillis =
                    nowMillis,
            )

        val markedAnswered =
            feedbackResponseRepository.markAnswered(
                responseId = responseId,
                answerIndex = answerIndex,
                answeredAtMillis = nowMillis,
            )

        if (!markedAnswered) {
            return
        }

        taskRewardRepository
            .awardFeedbackAnswerPointsIfNewDay(
                FeedbackAnswerPoints,
            )
    }

    companion object {
        const val Action = "com.impulsive.app.FEEDBACK_ANSWER"
        const val ExtraResponseId = "feedback_response_id"
        const val ExtraQuestionIndex = "feedback_question_index"
        const val ExtraAnswerIndex = "feedback_answer_index"
        const val ExtraNotificationId = "feedback_notification_id"
        const val FeedbackNotificationId = 4310
        const val FeedbackAnswerPoints = 2
    }
}
