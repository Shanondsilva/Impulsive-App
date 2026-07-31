package com.impulsive.app.backend.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.impulsive.app.backend.data.local.entity.AdaptivePreferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AdaptivePreferenceDao {
    @Query(
        """
        SELECT *
        FROM adaptive_preferences
        WHERE id = 1
        LIMIT 1
        """,
    )
    abstract fun observe(): Flow<AdaptivePreferenceEntity?>

    @Query(
        """
        SELECT *
        FROM adaptive_preferences
        WHERE id = 1
        LIMIT 1
        """,
    )
    abstract suspend fun get(): AdaptivePreferenceEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertRaw(
        preferences: AdaptivePreferenceEntity,
    ): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun replaceRaw(
        preferences: AdaptivePreferenceEntity,
    )

    suspend fun insertDefaults(updatedAtMillis: Long): Long =
        insertRaw(AdaptivePreferenceEntity(updatedAtMillis = updatedAtMillis))

    suspend fun update(preferences: AdaptivePreferenceEntity) {
        require(preferences.id == AdaptivePreferenceEntity.SingleRowId) {
            "Adaptive preferences must use the single settings row."
        }
        replaceRaw(preferences)
    }

    suspend fun resetDefaults(updatedAtMillis: Long) =
        replaceRaw(AdaptivePreferenceEntity(updatedAtMillis = updatedAtMillis))

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertForRestore(
        preferences: AdaptivePreferenceEntity,
    )

    @Query("DELETE FROM adaptive_preferences")
    abstract suspend fun clearAll(): Int
}
