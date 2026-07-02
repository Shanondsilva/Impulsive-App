package com.impulsive.app.backend.service.journal

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.impulsive.app.MainActivity
import com.impulsive.app.R
import com.impulsive.app.backend.data.repository.FeedbackResponseRepository
import com.impulsive.app.backend.domain.model.journal.FeedbackPrompt
import java.time.LocalDate
import java.time.ZoneId

/**
 * Posts the daily feedback question with two tap answers and then schedules
 * the next daily feedback notification.
 *
 * Manual feedback journal rows no longer exist and therefore never suppress this
 * notification.
 */
class FeedbackPromptWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        try {
            createChannel()

            val nowMillis = System.currentTimeMillis()
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val responseRepository =
                FeedbackResponseRepository(applicationContext)

            responseRepository.deleteExpired(nowMillis)

            val questionIndex =
                FeedbackPrompt.indexForDate(
                    today,
                )

            val question =
                FeedbackPrompt.questionAt(
                    questionIndex,
                )

            val responseId =
                responseRepository.createPending(
                    promptDateEpochDay =
                        today.toEpochDay(),
                    questionIndex =
                        questionIndex,
                    questionText =
                        question.question,
                    positiveAnswerText =
                        question.positiveAnswer,
                    honestAnswerText =
                        question.honestAnswer,
                    createdAtMillis =
                        nowMillis,
                )

            val pendingResponse =
                responseRepository.getById(
                    responseId = responseId,
                )

            if (
                pendingResponse != null &&
                pendingResponse.answeredAtMillis == null &&
                pendingResponse.expiresAtMillis >
                    nowMillis &&
                notificationsAllowed()
            ) {
                postNudge(
                    responseId = responseId,
                    questionIndex = questionIndex,
                    question = question,
                    expiresAtMillis =
                        pendingResponse.expiresAtMillis,
                )
            }
        } finally {
            FeedbackPromptScheduler(
                applicationContext,
            ).scheduleDailyNudge()
        }

        return Result.success()
    }

    private fun notificationsAllowed(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun postNudge(
        responseId: Long,
        questionIndex: Int,
        question: FeedbackPrompt.DailyQuestion,
        expiresAtMillis: Long,
    ) {
        val notificationId =
            FeedbackAnswerReceiver.FeedbackNotificationId

        val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            applicationContext,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(applicationContext, ChannelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(question.question)
            .setContentText("Tap an answer below.")
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(openPending)
            .setAutoCancel(true)
            .setTimeoutAfter(
                (
                    expiresAtMillis -
                        System.currentTimeMillis()
                ).coerceAtLeast(0L),
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(
                0,
                question.positiveAnswer,
                answerPendingIntent(
                    responseId = responseId,
                    questionIndex = questionIndex,
                    answerIndex = 0,
                    notificationId = notificationId,
                ),
            )
            .addAction(
                0,
                question.honestAnswer,
                answerPendingIntent(
                    responseId = responseId,
                    questionIndex = questionIndex,
                    answerIndex = 1,
                    notificationId = notificationId,
                ),
            )
            .build()

        NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
    }

    private fun answerPendingIntent(
        responseId: Long,
        questionIndex: Int,
        answerIndex: Int,
        notificationId: Int,
    ): PendingIntent {
        val intent = Intent(applicationContext, FeedbackAnswerReceiver::class.java).apply {
            action = FeedbackAnswerReceiver.Action
            putExtra(FeedbackAnswerReceiver.ExtraResponseId, responseId)
            putExtra(FeedbackAnswerReceiver.ExtraQuestionIndex, questionIndex)
            putExtra(FeedbackAnswerReceiver.ExtraAnswerIndex, answerIndex)
            putExtra(FeedbackAnswerReceiver.ExtraNotificationId, notificationId)
        }
        return PendingIntent.getBroadcast(
            applicationContext,
            notificationId * 10 + answerIndex,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            ChannelId,
            "Daily feedback",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "A gentle end-of-day check in from Impulsive."
        }
        applicationContext.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    companion object {
        const val ChannelId = "feedback_prompt"
    }
}
