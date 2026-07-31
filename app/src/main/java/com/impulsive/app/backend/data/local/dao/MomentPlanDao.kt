package com.impulsive.app.backend.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.impulsive.app.backend.data.local.entity.MomentPlanEntity
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveMomentLimits
import kotlinx.coroutines.flow.Flow

enum class MomentPlanMutationResult {
    Applied,
    AlreadyExists,
    NotFound,
    EnabledPlanLimitReached,
    PreferredPlanMustBeEnabled,
}

@Dao
abstract class MomentPlanDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertRaw(plan: MomentPlanEntity): Long

    @Update
    protected abstract suspend fun updateRaw(plan: MomentPlanEntity): Int

    @Query("SELECT * FROM moment_plans WHERE planId = :planId LIMIT 1")
    abstract suspend fun getById(planId: String): MomentPlanEntity?

    @Query("SELECT COUNT(*) FROM moment_plans WHERE enabled = 1")
    abstract suspend fun countEnabled(): Int

    @Query("SELECT COUNT(*) FROM moment_plans")
    abstract suspend fun count(): Int

    @Query(
        """
        SELECT *
        FROM moment_plans
        ORDER BY createdAtMillis ASC, planId ASC
        """,
    )
    abstract suspend fun getAllForBackup(): List<MomentPlanEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertForRestore(plan: MomentPlanEntity)

    @Query(
        """
        SELECT *
        FROM moment_plans
        ORDER BY enabled DESC, preferredForCue DESC, updatedAtMillis DESC, planId ASC
        """,
    )
    abstract fun observeAll(): Flow<List<MomentPlanEntity>>

    @Query(
        """
        SELECT *
        FROM moment_plans
        WHERE enabled = 1
        ORDER BY preferredForCue DESC, updatedAtMillis DESC, planId ASC
        """,
    )
    abstract fun observeEnabled(): Flow<List<MomentPlanEntity>>

    @Query(
        """
        SELECT *
        FROM moment_plans
        WHERE enabled = 1
            AND momentCue = :momentCue
        ORDER BY preferredForCue DESC, updatedAtMillis DESC, planId ASC
        """,
    )
    abstract suspend fun getMatchingEnabledByCue(
        momentCue: String,
    ): List<MomentPlanEntity>

    @Query(
        """
        UPDATE moment_plans
        SET preferredForCue = 0
        WHERE momentCue IS :momentCue
            AND preferredForCue = 1
            AND planId != :exceptPlanId
        """,
    )
    protected abstract suspend fun clearOtherPreferredForCue(
        momentCue: String?,
        exceptPlanId: String,
    ): Int

    @Query(
        """
        UPDATE moment_plans
        SET
            preferredForCue = 1,
            updatedAtMillis = :updatedAtMillis
        WHERE planId = :planId
            AND enabled = 1
        """,
    )
    protected abstract suspend fun setPreferredRaw(
        planId: String,
        updatedAtMillis: Long,
    ): Int

    @Query("DELETE FROM moment_plans WHERE planId = :planId")
    protected abstract suspend fun deleteRaw(planId: String): Int

    @Query("DELETE FROM moment_plans")
    abstract suspend fun clearAll(): Int

    @Query(
        """
        UPDATE moment_plans
        SET rehearsedAtMillis = :rehearsedAtMillis
        WHERE planId = :planId
            AND contentRevisionId = :expectedContentRevisionId
            AND (
                rehearsedAtMillis IS NULL
                OR rehearsedAtMillis < :rehearsedAtMillis
            )
        """,
    )
    abstract suspend fun markRehearsedIfContentRevisionMatches(
        planId: String,
        expectedContentRevisionId: String,
        rehearsedAtMillis: Long,
    ): Int

    @Query(
        """
        UPDATE moment_plans
        SET rehearsedAtMillis = :rehearsedAtMillis
        WHERE planId = :planId
            AND updatedAtMillis = :expectedUpdatedAtMillis
            AND (
                rehearsedAtMillis IS NULL
                OR rehearsedAtMillis < :rehearsedAtMillis
            )
        """,
    )
    abstract suspend fun markRehearsedIfRevisionMatches(
        planId: String,
        expectedUpdatedAtMillis: Long,
        rehearsedAtMillis: Long,
    ): Int

    @Transaction
    open suspend fun create(plan: MomentPlanEntity): MomentPlanMutationResult {
        if (getById(plan.planId) != null) {
            return MomentPlanMutationResult.AlreadyExists
        }
        if (plan.preferredForCue && !plan.enabled) {
            return MomentPlanMutationResult.PreferredPlanMustBeEnabled
        }
        if (
            plan.enabled &&
            countEnabled() >= AdaptiveMomentLimits.MaximumEnabledPlans
        ) {
            return MomentPlanMutationResult.EnabledPlanLimitReached
        }
        if (plan.preferredForCue) {
            clearOtherPreferredForCue(
                momentCue = plan.momentCue,
                exceptPlanId = plan.planId,
            )
        }
        return if (insertRaw(plan) != -1L) {
            MomentPlanMutationResult.Applied
        } else {
            MomentPlanMutationResult.AlreadyExists
        }
    }

    @Transaction
    open suspend fun update(plan: MomentPlanEntity): MomentPlanMutationResult {
        val existing = getById(plan.planId)
            ?: return MomentPlanMutationResult.NotFound
        if (plan.preferredForCue && !plan.enabled) {
            return MomentPlanMutationResult.PreferredPlanMustBeEnabled
        }
        if (
            plan.enabled &&
            !existing.enabled &&
            countEnabled() >= AdaptiveMomentLimits.MaximumEnabledPlans
        ) {
            return MomentPlanMutationResult.EnabledPlanLimitReached
        }
        if (plan.preferredForCue) {
            clearOtherPreferredForCue(
                momentCue = plan.momentCue,
                exceptPlanId = plan.planId,
            )
        }
        return if (updateRaw(plan) == 1) {
            MomentPlanMutationResult.Applied
        } else {
            MomentPlanMutationResult.NotFound
        }
    }

    @Transaction
    open suspend fun setPreferred(
        planId: String,
        updatedAtMillis: Long,
    ): MomentPlanMutationResult {
        val plan = getById(planId)
            ?: return MomentPlanMutationResult.NotFound
        if (!plan.enabled) {
            return MomentPlanMutationResult.PreferredPlanMustBeEnabled
        }
        clearOtherPreferredForCue(
            momentCue = plan.momentCue,
            exceptPlanId = plan.planId,
        )
        return if (setPreferredRaw(planId, updatedAtMillis) == 1) {
            MomentPlanMutationResult.Applied
        } else {
            MomentPlanMutationResult.NotFound
        }
    }

    @Transaction
    open suspend fun delete(planId: String): MomentPlanMutationResult =
        if (deleteRaw(planId) == 1) {
            MomentPlanMutationResult.Applied
        } else {
            MomentPlanMutationResult.NotFound
        }
}
