package com.impulsive.app.backend.data.repository

import android.content.Context
import com.impulsive.app.backend.data.local.database.AppDatabase
import com.impulsive.app.backend.data.local.entity.FeedbackResponseEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneId

class FeedbackResponseRepository(
    context: Context,
) {
    private val dao = AppDatabase
        .getInstance(context.applicationContext)
        .feedbackResponseDao()

    fun observePending(
        nowMillis: Long,
    ): Flow<List<FeedbackResponseEntity>> =
        dao.observePending(nowMillis)

    fun observeAnswered(
        nowMillis: Long,
    ): Flow<List<FeedbackResponseEntity>> =
        dao.observeAnswered(nowMillis)

    fun observePendingCount(
        nowMillis: Long,
    ): Flow<Int> =
        dao.observePendingCount(nowMillis)

    suspend fun getById(
        responseId: Long,
    ): FeedbackResponseEntity? =
        dao.getById(responseId)

    suspend fun createPending(
        promptDateEpochDay: Long,
        questionIndex: Int,
        questionText: String,
        positiveAnswerText: String,
        honestAnswerText: String,
        createdAtMillis: Long,
    ): Long {
        val insertedId = dao.insertPending(
            FeedbackResponseEntity(
                promptDateEpochDay = promptDateEpochDay,
                questionIndex = questionIndex,
                questionText = questionText,
                positiveAnswerText = positiveAnswerText,
                honestAnswerText = honestAnswerText,
                createdAtMillis = createdAtMillis,
                expiresAtMillis =
                    pendingExpiresAtMillis(
                        createdAtMillis = createdAtMillis,
                    ),
                updatedAtMillis = createdAtMillis,
            ),
        )

        if (insertedId != -1L) {
            return insertedId
        }

        return checkNotNull(
            dao.getByPromptDateEpochDay(
                promptDateEpochDay = promptDateEpochDay,
            ),
        ) {
            "Feedback response missing after same-day insert conflict."
        }.id
    }

    suspend fun markAnswered(
        responseId: Long,
        answerIndex: Int,
        answeredAtMillis: Long,
    ): Boolean {
        require(answerIndex == 0 || answerIndex == 1) {
            "Feedback answer index must be 0 or 1."
        }

        return dao.markAnswered(
            responseId = responseId,
            answerIndex = answerIndex,
            answeredAtMillis = answeredAtMillis,
            expiresAtMillis =
                answeredAtMillis + RetentionMillis,
        ) == 1
    }

    suspend fun deleteExpired(
        nowMillis: Long,
    ): Int {
        val zone =
            ZoneId.systemDefault()

        val currentDate =
            Instant
                .ofEpochMilli(nowMillis)
                .atZone(zone)
                .toLocalDate()

        val currentEpochDay =
            currentDate.toEpochDay()

        dao.updatePendingExpiryForDate(
            promptDateEpochDay =
                currentEpochDay,
            expiresAtMillis =
                pendingExpiresAtMillis(
                    createdAtMillis =
                        nowMillis,
                    zone = zone,
                ),
        )

        return dao.deleteExpired(
            nowMillis = nowMillis,
            currentEpochDay =
                currentEpochDay,
        )
    }

    companion object {
        const val RetentionMillis: Long =
            7L * 24L * 60L * 60L * 1000L

        fun pendingExpiresAtMillis(
            createdAtMillis: Long,
            zone: ZoneId =
                ZoneId.systemDefault(),
        ): Long {
            return Instant
                .ofEpochMilli(createdAtMillis)
                .atZone(zone)
                .toLocalDate()
                .plusDays(1L)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
        }
    }
}
