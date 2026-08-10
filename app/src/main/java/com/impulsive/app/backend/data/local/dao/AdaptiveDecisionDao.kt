package com.impulsive.app.backend.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.impulsive.app.backend.data.local.entity.AdaptiveDecisionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AdaptiveDecisionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOnce(decision: AdaptiveDecisionEntity): Long

    @Query("SELECT * FROM adaptive_decisions WHERE decisionId = :decisionId LIMIT 1")
    suspend fun getById(decisionId: String): AdaptiveDecisionEntity?

    @Query(
        "SELECT * FROM adaptive_decisions " +
            "WHERE protectionIncidentToken = :incidentToken LIMIT 1",
    )
    suspend fun getByIncidentToken(incidentToken: String): AdaptiveDecisionEntity?

    @Query("SELECT COUNT(*) FROM adaptive_decisions")
    suspend fun count(): Int

    @Query(
        """
        SELECT *
        FROM adaptive_decisions
        ORDER BY createdAtMillis ASC, decisionId ASC
        """,
    )
    suspend fun getAllForBackup(): List<AdaptiveDecisionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertForRestore(decision: AdaptiveDecisionEntity)

    @Query(
        """
        UPDATE adaptive_decisions
        SET
            actualIntervention = :actualIntervention,
            momentPlanId = COALESCE(:momentPlanId, momentPlanId),
            momentPlanUpdatedAtMillis =
                COALESCE(:momentPlanUpdatedAtMillis, momentPlanUpdatedAtMillis),
            actualPlanContentRevisionId =
                COALESCE(:actualPlanContentRevisionId, actualPlanContentRevisionId),
            actualProtocolId = :actualProtocolId,
            actualProtocolVersion = :actualProtocolVersion,
            userOverrodeSuggestion = :userOverrodeSuggestion
        WHERE decisionId = :decisionId
            AND actualIntervention IS NULL
            AND startedAtMillis IS NULL
            AND completedAtMillis IS NULL
            AND dismissedAtMillis IS NULL
        """,
    )
    suspend fun recordActualChoiceOnce(
        decisionId: String,
        actualIntervention: String,
        momentPlanId: String?,
        momentPlanUpdatedAtMillis: Long?,
        userOverrodeSuggestion: Boolean,
        actualPlanContentRevisionId: String?,
        actualProtocolId: String?,
        actualProtocolVersion: Int?,
    ): Int

    @Query(
        """
        UPDATE adaptive_decisions
        SET
            actualIntervention = :actualIntervention,
            momentPlanId = CASE
                WHEN assignedSuggestion = 'MomentPlan' THEN momentPlanId
                ELSE :momentPlanId
            END,
            momentPlanUpdatedAtMillis = CASE
                WHEN assignedSuggestion = 'MomentPlan' THEN momentPlanUpdatedAtMillis
                ELSE :momentPlanUpdatedAtMillis
            END,
            actualPlanContentRevisionId = CASE
                WHEN :actualIntervention = 'MomentPlan' THEN :actualPlanContentRevisionId
                ELSE NULL
            END,
            actualProtocolId = :actualProtocolId,
            actualProtocolVersion = :actualProtocolVersion,
            userOverrodeSuggestion = :userOverrodeSuggestion
        WHERE decisionId = :decisionId
            AND actualIntervention IS NOT NULL
            AND startedAtMillis IS NULL
            AND completedAtMillis IS NULL
            AND dismissedAtMillis IS NULL
            AND (eligibleInterventionsMask & :eligibilityBit) != 0
            AND (
                (
                    :actualIntervention = 'MomentPlan'
                    AND :momentPlanId IS NOT NULL
                    AND :momentPlanUpdatedAtMillis IS NOT NULL
                    AND :actualPlanContentRevisionId IS NOT NULL
                    AND EXISTS (
                        SELECT 1
                        FROM moment_plans
                        WHERE planId = :momentPlanId
                            AND enabled = 1
                    )
                )
                OR (:actualIntervention != 'MomentPlan' AND :momentPlanId IS NULL)
            )
        """,
    )
    suspend fun replacePendingActualChoice(
        decisionId: String,
        actualIntervention: String,
        momentPlanId: String?,
        momentPlanUpdatedAtMillis: Long?,
        userOverrodeSuggestion: Boolean,
        actualPlanContentRevisionId: String?,
        actualProtocolId: String?,
        actualProtocolVersion: Int?,
        eligibilityBit: Int,
    ): Int

    @Query(
        """
        UPDATE adaptive_decisions
        SET
            momentCue = COALESCE(momentCue, :momentCue),
            baselineUrgeRating = COALESCE(baselineUrgeRating, :urgeRating)
        WHERE decisionId = :decisionId
            AND actualIntervention IS NULL
            AND startedAtMillis IS NULL
            AND completedAtMillis IS NULL
            AND dismissedAtMillis IS NULL
            AND (:urgeRating IS NULL OR :urgeRating BETWEEN 0 AND 10)
        """,
    )
    suspend fun recordMomentContextOnce(
        decisionId: String,
        momentCue: String?,
        urgeRating: Int?,
    ): Int

    @Query(
        """
        UPDATE adaptive_decisions
        SET eligibleInterventionsMask =
            eligibleInterventionsMask | :additionalMask
        WHERE decisionId = :decisionId
            AND actualIntervention IS NULL
            AND startedAtMillis IS NULL
            AND completedAtMillis IS NULL
            AND dismissedAtMillis IS NULL
        """,
    )
    suspend fun addEligibleInterventions(
        decisionId: String,
        additionalMask: Int,
    ): Int

    @Query(
        """
        UPDATE adaptive_decisions
        SET presentedAtMillis = :presentedAtMillis
        WHERE decisionId = :decisionId
            AND presentedAtMillis IS NULL
            AND completedAtMillis IS NULL
            AND dismissedAtMillis IS NULL
        """,
    )
    suspend fun markPresentedOnce(
        decisionId: String,
        presentedAtMillis: Long,
    ): Int

    @Query(
        """
        UPDATE adaptive_decisions
        SET startedAtMillis = :startedAtMillis
        WHERE decisionId = :decisionId
            AND presentedAtMillis IS NOT NULL
            AND startedAtMillis IS NULL
            AND completedAtMillis IS NULL
            AND dismissedAtMillis IS NULL
        """,
    )
    suspend fun markStartedOnce(
        decisionId: String,
        startedAtMillis: Long,
    ): Int

    @Query(
        """
        UPDATE adaptive_decisions
        SET completedAtMillis = :completedAtMillis
        WHERE decisionId = :decisionId
            AND startedAtMillis IS NOT NULL
            AND completedAtMillis IS NULL
            AND dismissedAtMillis IS NULL
        """,
    )
    suspend fun markCompletedOnce(
        decisionId: String,
        completedAtMillis: Long,
    ): Int

    @Query(
        """
        UPDATE adaptive_decisions
        SET dismissedAtMillis = :dismissedAtMillis
        WHERE decisionId = :decisionId
            AND presentedAtMillis IS NOT NULL
            AND completedAtMillis IS NULL
            AND dismissedAtMillis IS NULL
        """,
    )
    suspend fun markDismissedOnce(
        decisionId: String,
        dismissedAtMillis: Long,
    ): Int

    @Query(
        """
        UPDATE adaptive_decisions
        SET
            feedbackCode = :feedbackCode,
            feedbackUpdatedAtMillis = :feedbackUpdatedAtMillis
        WHERE decisionId = :decisionId
        """,
    )
    suspend fun updateFeedback(
        decisionId: String,
        feedbackCode: String,
        feedbackUpdatedAtMillis: Long,
    ): Int

    @Query(
        """
        UPDATE adaptive_decisions
        SET
            repeatDetectedWithin20Minutes = 1,
            firstRepeatAtMillis = :firstRepeatAtMillis
        WHERE decisionId = :decisionId
            AND repeatDetectedWithin20Minutes IS NULL
            AND observationFinalisedAtMillis IS NULL
        """,
    )
    suspend fun markFirstRepeatOnce(
        decisionId: String,
        firstRepeatAtMillis: Long,
    ): Int

    @Query(
        """
        UPDATE adaptive_decisions
        SET
            repeatDetectedWithin20Minutes =
                COALESCE(repeatDetectedWithin20Minutes, 0),
            observationFinalisedAtMillis = :finalisedAtMillis
        WHERE decisionId = :decisionId
            AND observationFinalisedAtMillis IS NULL
        """,
    )
    suspend fun finaliseOnce(
        decisionId: String,
        finalisedAtMillis: Long,
    ): Int

    @Query(
        """
        SELECT *
        FROM adaptive_decisions
        WHERE createdAtMillis >= :windowStartedAtMillis
            AND createdAtMillis <= :nowMillis
        ORDER BY createdAtMillis DESC
        LIMIT 1
        """,
    )
    suspend fun getLatestInsideMomentWindow(
        windowStartedAtMillis: Long,
        nowMillis: Long,
    ): AdaptiveDecisionEntity?

    @Query(
        """
        SELECT *
        FROM adaptive_decisions
        WHERE createdAtMillis >= :windowStartedAtMillis
            AND createdAtMillis <= :nowMillis
            AND observationFinalisedAtMillis IS NULL
        ORDER BY createdAtMillis DESC
        LIMIT 1
        """,
    )
    suspend fun getLatestOpenInsideMomentWindow(
        windowStartedAtMillis: Long,
        nowMillis: Long,
    ): AdaptiveDecisionEntity?

    @Query(
        """
        SELECT *
        FROM adaptive_decisions
        WHERE observationFinalisedAtMillis IS NULL
            AND observationDeadlineAtMillis <= :nowMillis
        ORDER BY observationDeadlineAtMillis ASC
        LIMIT :limit
        """,
    )
    suspend fun getOpenObservationDeadlines(
        nowMillis: Long,
        limit: Int,
    ): List<AdaptiveDecisionEntity>

    @Query(
        """
        SELECT *
        FROM adaptive_decisions
        WHERE observationFinalisedAtMillis IS NULL
            AND observationDeadlineAtMillis > :nowMillis
        ORDER BY observationDeadlineAtMillis ASC
        LIMIT :limit
        """,
    )
    suspend fun getFutureOpenObservationDeadlines(
        nowMillis: Long,
        limit: Int,
    ): List<AdaptiveDecisionEntity>

    @Query(
        """
        SELECT *
        FROM adaptive_decisions
        WHERE startedAtMillis IS NOT NULL
            AND (completedAtMillis IS NOT NULL OR dismissedAtMillis IS NOT NULL)
            AND feedbackUpdatedAtMillis IS NULL
        ORDER BY COALESCE(completedAtMillis, dismissedAtMillis) DESC
        LIMIT 1
        """,
    )
    suspend fun getLatestPendingFeedback(): AdaptiveDecisionEntity?

    @Query(
        """
        SELECT *
        FROM adaptive_decisions
        WHERE createdAtMillis >= :startedAtMillis
            AND createdAtMillis < :endedAtMillis
        ORDER BY createdAtMillis ASC, decisionId ASC
        """,
    )
    suspend fun getBetween(
        startedAtMillis: Long,
        endedAtMillis: Long,
    ): List<AdaptiveDecisionEntity>


    @Query(
        """
        SELECT *
        FROM adaptive_decisions
        WHERE actualIntervention = 'MomentPlan'
            AND momentPlanId IS NOT NULL
            AND momentPlanUpdatedAtMillis IS NOT NULL
            AND actualPlanContentRevisionId IS NOT NULL
            AND startedAtMillis IS NOT NULL
            AND startedAtMillis >= :sinceMillis
        ORDER BY startedAtMillis DESC, decisionId ASC
        """,
    )
    suspend fun getMomentPlanUsesSince(sinceMillis: Long): List<AdaptiveDecisionEntity>

    @Query(
        """
        SELECT *
        FROM adaptive_decisions
        ORDER BY createdAtMillis DESC, decisionId ASC
        LIMIT :limit
        """,
    )
    fun observeRecent(limit: Int): Flow<List<AdaptiveDecisionEntity>>

    @Query(
        """
        SELECT *
        FROM adaptive_decisions
        WHERE observationFinalisedAtMillis IS NOT NULL
            AND actualIntervention IS NOT NULL
        ORDER BY createdAtMillis DESC
        LIMIT :limit
        """,
    )
    suspend fun getRecentFinalised(limit: Int): List<AdaptiveDecisionEntity>

    @Query(
        """
        SELECT *
        FROM adaptive_decisions
        WHERE observationFinalisedAtMillis IS NOT NULL
            AND actualIntervention = :actualIntervention
        ORDER BY createdAtMillis DESC
        LIMIT :limit
        """,
    )
    suspend fun getFinalisedByActualIntervention(
        actualIntervention: String,
        limit: Int,
    ): List<AdaptiveDecisionEntity>

    @Query(
        """
        SELECT *
        FROM adaptive_decisions
        WHERE observationFinalisedAtMillis IS NOT NULL
            AND momentCue = :momentCue
            AND actualIntervention IS NOT NULL
        ORDER BY createdAtMillis DESC
        LIMIT :limit
        """,
    )
    suspend fun getFinalisedByCue(
        momentCue: String,
        limit: Int,
    ): List<AdaptiveDecisionEntity>

    @Query(
        """
        SELECT decisionId
        FROM adaptive_decisions
        WHERE createdAtMillis >= 0
            AND createdAtMillis < :cutoffMillis
            AND observationFinalisedAtMillis IS NOT NULL
            AND (completedAtMillis IS NOT NULL OR dismissedAtMillis IS NOT NULL)
        ORDER BY createdAtMillis ASC, decisionId ASC
        LIMIT :limit
        """,
    )
    suspend fun getSafeRetentionCandidateIds(
        cutoffMillis: Long,
        limit: Int,
    ): List<String>

    @Query("DELETE FROM adaptive_decisions WHERE decisionId IN (:decisionIds)")
    suspend fun deleteByIds(decisionIds: List<String>): Int

    @Query("DELETE FROM adaptive_decisions")
    suspend fun clearLearningHistory(): Int
}
