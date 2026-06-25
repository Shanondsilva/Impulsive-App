package com.impulsive.app.backend.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.impulsive.app.backend.data.local.entity.FeedbackResponseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedbackResponseDao {

    @Query(
        """
        SELECT * FROM feedback_responses
        WHERE answeredAtMillis IS NULL
          AND expiresAtMillis > :nowMillis
        ORDER BY createdAtMillis DESC
        """,
    )
    fun observePending(
        nowMillis: Long,
    ): Flow<List<FeedbackResponseEntity>>

    @Query(
        """
        SELECT * FROM feedback_responses
        WHERE answeredAtMillis IS NOT NULL
          AND expiresAtMillis > :nowMillis
        ORDER BY answeredAtMillis DESC
        """,
    )
    fun observeAnswered(
        nowMillis: Long,
    ): Flow<List<FeedbackResponseEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM feedback_responses
        WHERE answeredAtMillis IS NULL
          AND expiresAtMillis > :nowMillis
        """,
    )
    fun observePendingCount(
        nowMillis: Long,
    ): Flow<Int>

    @Query(
        """
        SELECT * FROM feedback_responses
        WHERE id = :responseId
        LIMIT 1
        """,
    )
    suspend fun getById(
        responseId: Long,
    ): FeedbackResponseEntity?

    @Query(
        """
        SELECT * FROM feedback_responses
        WHERE promptDateEpochDay = :promptDateEpochDay
        LIMIT 1
        """,
    )
    suspend fun getByPromptDateEpochDay(
        promptDateEpochDay: Long,
    ): FeedbackResponseEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPending(
        response: FeedbackResponseEntity,
    ): Long

    @Query(
        """
        UPDATE feedback_responses
        SET selectedAnswerIndex = :answerIndex,
            answeredAtMillis = :answeredAtMillis,
            expiresAtMillis = :expiresAtMillis,
            updatedAtMillis = :answeredAtMillis
        WHERE id = :responseId
          AND answeredAtMillis IS NULL
          AND expiresAtMillis > :answeredAtMillis
        """,
    )
    suspend fun markAnswered(
        responseId: Long,
        answerIndex: Int,
        answeredAtMillis: Long,
        expiresAtMillis: Long,
    ): Int

    @Query(
        """
        DELETE FROM feedback_responses
        WHERE expiresAtMillis <= :nowMillis
        """,
    )
    suspend fun deleteExpired(
        nowMillis: Long,
    ): Int
}
