package com.impulsive.app.backend.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.impulsive.app.backend.data.local.entity.ProtectionCoachSuggestionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProtectionCoachSuggestionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOnce(suggestion: ProtectionCoachSuggestionEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertForRestore(suggestion: ProtectionCoachSuggestionEntity)

    @Query("SELECT * FROM protection_coach_suggestions WHERE suggestionId = :suggestionId LIMIT 1")
    suspend fun getById(suggestionId: String): ProtectionCoachSuggestionEntity?

    @Query(
        """
        SELECT *
        FROM protection_coach_suggestions
        WHERE status IN ('Prepared', 'Presented')
            AND expiresAtMillis > :nowMillis
        ORDER BY createdAtMillis ASC, suggestionId ASC
        """,
    )
    fun observeActiveSuggestions(nowMillis: Long): Flow<List<ProtectionCoachSuggestionEntity>>

    @Query(
        """
        SELECT *
        FROM protection_coach_suggestions
        WHERE status IN ('Prepared', 'Presented')
            AND suggestionType = :suggestionType
            AND (
                (:broadWindowStartMinute IS NULL AND broadWindowStartMinute IS NULL)
                OR broadWindowStartMinute = :broadWindowStartMinute
            )
            AND (
                (:broadWindowEndMinute IS NULL AND broadWindowEndMinute IS NULL)
                OR broadWindowEndMinute = :broadWindowEndMinute
            )
        ORDER BY createdAtMillis DESC
        LIMIT 1
        """,
    )
    suspend fun findEquivalentActiveSuggestion(
        suggestionType: String,
        broadWindowStartMinute: Int?,
        broadWindowEndMinute: Int?,
    ): ProtectionCoachSuggestionEntity?

    @Query(
        """
        SELECT *
        FROM protection_coach_suggestions
        WHERE suggestionType = :suggestionType
            AND status = 'Dismissed'
            AND dismissedAtMillis >= :dismissedSinceMillis
            AND (
                (:broadWindowStartMinute IS NULL AND broadWindowStartMinute IS NULL)
                OR broadWindowStartMinute = :broadWindowStartMinute
            )
            AND (
                (:broadWindowEndMinute IS NULL AND broadWindowEndMinute IS NULL)
                OR broadWindowEndMinute = :broadWindowEndMinute
            )
        ORDER BY dismissedAtMillis DESC
        LIMIT 1
        """,
    )
    suspend fun findEquivalentRecentlyDismissedSuggestion(
        suggestionType: String,
        broadWindowStartMinute: Int?,
        broadWindowEndMinute: Int?,
        dismissedSinceMillis: Long,
    ): ProtectionCoachSuggestionEntity?

    @Query(
        """
        SELECT *
        FROM protection_coach_suggestions
        ORDER BY createdAtMillis ASC, suggestionId ASC
        """,
    )
    suspend fun getAllForBackup(): List<ProtectionCoachSuggestionEntity>

    @Query(
        """
        UPDATE protection_coach_suggestions
        SET
            status = 'Presented',
            presentedAtMillis = COALESCE(presentedAtMillis, :presentedAtMillis)
        WHERE suggestionId = :suggestionId
            AND status = 'Prepared'
        """,
    )
    suspend fun markPresentedOnce(suggestionId: String, presentedAtMillis: Long): Int

    @Query(
        """
        UPDATE protection_coach_suggestions
        SET
            status = 'Accepted',
            acceptedAtMillis = COALESCE(acceptedAtMillis, :acceptedAtMillis)
        WHERE suggestionId = :suggestionId
            AND status IN ('Prepared', 'Presented')
            AND dismissedAtMillis IS NULL
            AND suppressedAtMillis IS NULL
        """,
    )
    suspend fun acceptOnce(suggestionId: String, acceptedAtMillis: Long): Int

    @Query(
        """
        UPDATE protection_coach_suggestions
        SET
            status = 'AcceptedWithEdits',
            acceptedAtMillis = COALESCE(acceptedAtMillis, :acceptedAtMillis),
            acceptedStartMinute = :acceptedStartMinute,
            acceptedEndMinute = :acceptedEndMinute
        WHERE suggestionId = :suggestionId
            AND status IN ('Prepared', 'Presented')
            AND dismissedAtMillis IS NULL
            AND suppressedAtMillis IS NULL
            AND :acceptedStartMinute BETWEEN 0 AND 1439
            AND :acceptedEndMinute BETWEEN 0 AND 1439
        """,
    )
    suspend fun acceptWithEditsOnce(
        suggestionId: String,
        acceptedAtMillis: Long,
        acceptedStartMinute: Int,
        acceptedEndMinute: Int,
    ): Int

    @Query(
        """
        UPDATE protection_coach_suggestions
        SET
            status = 'Dismissed',
            dismissedAtMillis = COALESCE(dismissedAtMillis, :dismissedAtMillis)
        WHERE suggestionId = :suggestionId
            AND status IN ('Prepared', 'Presented')
            AND acceptedAtMillis IS NULL
            AND suppressedAtMillis IS NULL
        """,
    )
    suspend fun dismissOnce(suggestionId: String, dismissedAtMillis: Long): Int

    @Query(
        """
        UPDATE protection_coach_suggestions
        SET
            status = 'Suppressed',
            suppressedAtMillis = COALESCE(suppressedAtMillis, :suppressedAtMillis)
        WHERE suggestionId = :suggestionId
            AND status IN ('Prepared', 'Presented', 'Dismissed')
            AND acceptedAtMillis IS NULL
        """,
    )
    suspend fun suppressOnce(suggestionId: String, suppressedAtMillis: Long): Int

    @Query(
        """
        UPDATE protection_coach_suggestions
        SET status = 'Expired'
        WHERE status IN ('Prepared', 'Presented')
            AND expiresAtMillis <= :nowMillis
        """,
    )
    suspend fun expireDue(nowMillis: Long): Int

    @Query("DELETE FROM protection_coach_suggestions")
    suspend fun clearAllCoachData(): Int

    @Query("DELETE FROM protection_coach_suggestions WHERE status NOT IN ('Prepared', 'Presented')")
    suspend fun clearCoachHistory(): Int
}
