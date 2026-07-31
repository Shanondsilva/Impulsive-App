package com.impulsive.app.backend.domain.repository.adaptive

import com.impulsive.app.backend.domain.model.adaptive.AdaptiveDecision
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveOutcomeRecord
import com.impulsive.app.backend.domain.model.adaptive.AdaptivePreferences
import com.impulsive.app.backend.domain.model.adaptive.FeedbackCode
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentCue
import com.impulsive.app.backend.domain.model.adaptive.MomentPlan
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanRehearsal
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanUseRecord
import kotlinx.coroutines.flow.Flow

interface AdaptiveDecisionRepository {
    suspend fun insertOnce(decision: AdaptiveDecision): Boolean

    suspend fun getById(decisionId: String): AdaptiveDecision?

    suspend fun getByIncidentToken(incidentToken: String): AdaptiveDecision?

    suspend fun recordActualChoiceOnce(
        decisionId: String,
        intervention: InterventionFamily,
        momentPlanId: String?,
        momentPlanUpdatedAtMillis: Long?,
        userOverrodeSuggestion: Boolean,
    ): Boolean

    suspend fun recordActualChoiceOnce(
        decisionId: String,
        intervention: InterventionFamily,
        momentPlanId: String?,
        momentPlanUpdatedAtMillis: Long?,
        userOverrodeSuggestion: Boolean,
        actualPlanContentRevisionId: String?,
    ): Boolean = recordActualChoiceOnce(
        decisionId = decisionId,
        intervention = intervention,
        momentPlanId = momentPlanId,
        momentPlanUpdatedAtMillis = momentPlanUpdatedAtMillis,
        userOverrodeSuggestion = userOverrodeSuggestion,
    )

    suspend fun recordActualChoiceOnce(
        decisionId: String,
        intervention: InterventionFamily,
        momentPlanId: String?,
        momentPlanUpdatedAtMillis: Long?,
        userOverrodeSuggestion: Boolean,
        actualPlanContentRevisionId: String?,
        actualProtocolId: String,
        actualProtocolVersion: Int,
    ): Boolean = recordActualChoiceOnce(
        decisionId = decisionId,
        intervention = intervention,
        momentPlanId = momentPlanId,
        momentPlanUpdatedAtMillis = momentPlanUpdatedAtMillis,
        userOverrodeSuggestion = userOverrodeSuggestion,
        actualPlanContentRevisionId = actualPlanContentRevisionId,
    )

    suspend fun replacePendingActualChoice(
        decisionId: String,
        intervention: InterventionFamily,
        momentPlanId: String?,
        momentPlanUpdatedAtMillis: Long?,
        userOverrodeSuggestion: Boolean,
    ): Boolean

    suspend fun replacePendingActualChoice(
        decisionId: String,
        intervention: InterventionFamily,
        momentPlanId: String?,
        momentPlanUpdatedAtMillis: Long?,
        userOverrodeSuggestion: Boolean,
        actualPlanContentRevisionId: String?,
    ): Boolean = replacePendingActualChoice(
        decisionId = decisionId,
        intervention = intervention,
        momentPlanId = momentPlanId,
        momentPlanUpdatedAtMillis = momentPlanUpdatedAtMillis,
        userOverrodeSuggestion = userOverrodeSuggestion,
    )

    suspend fun replacePendingActualChoice(
        decisionId: String,
        intervention: InterventionFamily,
        momentPlanId: String?,
        momentPlanUpdatedAtMillis: Long?,
        userOverrodeSuggestion: Boolean,
        actualPlanContentRevisionId: String?,
        actualProtocolId: String,
        actualProtocolVersion: Int,
    ): Boolean = replacePendingActualChoice(
        decisionId = decisionId,
        intervention = intervention,
        momentPlanId = momentPlanId,
        momentPlanUpdatedAtMillis = momentPlanUpdatedAtMillis,
        userOverrodeSuggestion = userOverrodeSuggestion,
        actualPlanContentRevisionId = actualPlanContentRevisionId,
    )

    suspend fun recordMomentContextOnce(
        decisionId: String,
        cue: MomentCue?,
        urgeRating: Int?,
    ): Boolean

    suspend fun addEligibleInterventions(
        decisionId: String,
        interventions: Set<InterventionFamily>,
    ): Boolean

    suspend fun markPresentedOnce(
        decisionId: String,
        presentedAtMillis: Long,
    ): Boolean

    suspend fun markStartedOnce(
        decisionId: String,
        startedAtMillis: Long,
    ): Boolean

    suspend fun markCompletedOnce(
        decisionId: String,
        completedAtMillis: Long,
    ): Boolean

    suspend fun markDismissedOnce(
        decisionId: String,
        dismissedAtMillis: Long,
    ): Boolean

    suspend fun updateFeedback(
        decisionId: String,
        feedbackCode: FeedbackCode,
        feedbackUpdatedAtMillis: Long,
    ): Boolean

    suspend fun markFirstRepeatOnce(
        decisionId: String,
        firstRepeatAtMillis: Long,
    ): Boolean

    suspend fun finaliseOnce(
        decisionId: String,
        finalisedAtMillis: Long,
    ): Boolean

    suspend fun getLatestInsideMomentWindow(
        windowStartedAtMillis: Long,
        nowMillis: Long,
    ): AdaptiveDecision?

    suspend fun getLatestOpenInsideMomentWindow(
        windowStartedAtMillis: Long,
        nowMillis: Long,
    ): AdaptiveDecision?

    suspend fun getOpenObservationDeadlines(
        nowMillis: Long,
        limit: Int,
    ): List<AdaptiveDecision>

    suspend fun getFutureOpenObservationDeadlines(
        nowMillis: Long,
        limit: Int,
    ): List<AdaptiveDecision>

    suspend fun getLatestPendingFeedback(): AdaptiveDecision? = null

    suspend fun getBetween(
        startedAtMillis: Long,
        endedAtMillis: Long,
    ): List<AdaptiveDecision> = emptyList()

    suspend fun getMomentPlanUsesSince(sinceMillis: Long): List<MomentPlanUseRecord> =
        emptyList()

    fun observeRecentDecisions(limit: Int): Flow<List<AdaptiveDecision>> =
        kotlinx.coroutines.flow.flowOf(emptyList())

    suspend fun getRecentFinalised(limit: Int): List<AdaptiveOutcomeRecord>

    suspend fun getFinalisedByActualIntervention(
        intervention: InterventionFamily,
        limit: Int,
    ): List<AdaptiveOutcomeRecord>

    suspend fun getFinalisedByCue(
        cue: MomentCue,
        limit: Int,
    ): List<AdaptiveOutcomeRecord>

    suspend fun clearLearningHistory()
}

enum class MomentPlanSaveResult {
    Applied,
    AlreadyExists,
    NotFound,
    EnabledPlanLimitReached,
    PreferredPlanMustBeEnabled,
}

interface MomentPlanRepository {
    suspend fun create(plan: MomentPlan): MomentPlanSaveResult

    suspend fun update(plan: MomentPlan): MomentPlanSaveResult

    suspend fun delete(planId: String): MomentPlanSaveResult

    suspend fun getById(planId: String): MomentPlan?

    fun observeAll(): Flow<List<MomentPlan>>

    fun observeEnabled(): Flow<List<MomentPlan>>

    suspend fun getMatchingEnabledByCue(cue: MomentCue): List<MomentPlan>

    suspend fun setPreferred(
        planId: String,
        updatedAtMillis: Long,
    ): MomentPlanSaveResult

    suspend fun markRehearsedIfRevisionMatches(
        planId: String,
        expectedUpdatedAtMillis: Long,
        rehearsedAtMillis: Long,
    ): Boolean = false

    suspend fun markRehearsedIfContentRevisionMatches(
        planId: String,
        expectedContentRevisionId: String,
        rehearsedAtMillis: Long,
    ): Boolean = false
}

interface MomentPlanRehearsalRepository {
    suspend fun insertOnce(rehearsal: MomentPlanRehearsal): Boolean

    suspend fun getById(rehearsalId: String): MomentPlanRehearsal?

    suspend fun markCompletedOnce(
        rehearsalId: String,
        completedAtMillis: Long,
    ): Boolean

    suspend fun markDismissedOnce(
        rehearsalId: String,
        dismissedAtMillis: Long,
    ): Boolean

    suspend fun getOpenRehearsal(): MomentPlanRehearsal?

    suspend fun getRecentCompleted(limit: Int): List<MomentPlanRehearsal>

    fun observeRecentCompleted(limit: Int): Flow<List<MomentPlanRehearsal>> =
        kotlinx.coroutines.flow.flowOf(emptyList())

    suspend fun getCompletedByPlan(planId: String): List<MomentPlanRehearsal>

    suspend fun clearHistory()

    suspend fun clearAll()
}

interface AdaptivePreferenceRepository {
    fun observe(): Flow<AdaptivePreferences>

    suspend fun get(): AdaptivePreferences

    suspend fun insertDefaults(updatedAtMillis: Long)

    suspend fun update(
        preferences: AdaptivePreferences,
        updatedAtMillis: Long,
    )

    suspend fun resetDefaults(updatedAtMillis: Long)
}

interface AdaptiveDataRepository {
    suspend fun clearPersonalLearning()

    suspend fun clearAllAdaptiveData()
}
