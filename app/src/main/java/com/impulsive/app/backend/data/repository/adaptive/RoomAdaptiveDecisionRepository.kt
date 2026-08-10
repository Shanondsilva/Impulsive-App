package com.impulsive.app.backend.data.repository.adaptive

import com.impulsive.app.backend.data.local.dao.AdaptiveDecisionDao
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveDecision
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveOutcomeRecord
import com.impulsive.app.backend.domain.model.adaptive.FeedbackCode
import com.impulsive.app.backend.domain.model.adaptive.FamiliarStepEvidenceRecord
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentCue
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanUseRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveDecisionRepository

class RoomAdaptiveDecisionRepository(
    private val dao: AdaptiveDecisionDao,
) : AdaptiveDecisionRepository {
    override suspend fun insertOnce(decision: AdaptiveDecision): Boolean =
        dao.insertOnce(decision.toEntity()) != -1L

    override suspend fun getById(decisionId: String): AdaptiveDecision? =
        dao.getById(decisionId)?.toDomain()

    override suspend fun getByIncidentToken(
        incidentToken: String,
    ): AdaptiveDecision? = dao.getByIncidentToken(incidentToken)?.toDomain()

    override suspend fun recordActualChoiceOnce(
        decisionId: String,
        intervention: InterventionFamily,
        momentPlanId: String?,
        momentPlanUpdatedAtMillis: Long?,
        userOverrodeSuggestion: Boolean,
    ): Boolean = recordActualChoiceInternal(
        decisionId = decisionId,
        intervention = intervention,
        momentPlanId = momentPlanId,
        momentPlanUpdatedAtMillis = momentPlanUpdatedAtMillis,
        userOverrodeSuggestion = userOverrodeSuggestion,
        actualPlanContentRevisionId = null,
        actualProtocolId = null,
        actualProtocolVersion = null,
    )

    override suspend fun recordActualChoiceOnce(
        decisionId: String,
        intervention: InterventionFamily,
        momentPlanId: String?,
        momentPlanUpdatedAtMillis: Long?,
        userOverrodeSuggestion: Boolean,
        actualPlanContentRevisionId: String?,
    ): Boolean = recordActualChoiceInternal(
        decisionId = decisionId,
        intervention = intervention,
        momentPlanId = momentPlanId,
        momentPlanUpdatedAtMillis = momentPlanUpdatedAtMillis,
        userOverrodeSuggestion = userOverrodeSuggestion,
        actualPlanContentRevisionId = actualPlanContentRevisionId,
        actualProtocolId = null,
        actualProtocolVersion = null,
    )

    override suspend fun recordActualChoiceOnce(
        decisionId: String,
        intervention: InterventionFamily,
        momentPlanId: String?,
        momentPlanUpdatedAtMillis: Long?,
        userOverrodeSuggestion: Boolean,
        actualPlanContentRevisionId: String?,
        actualProtocolId: String,
        actualProtocolVersion: Int,
    ): Boolean = recordActualChoiceInternal(
        decisionId = decisionId,
        intervention = intervention,
        momentPlanId = momentPlanId,
        momentPlanUpdatedAtMillis = momentPlanUpdatedAtMillis,
        userOverrodeSuggestion = userOverrodeSuggestion,
        actualPlanContentRevisionId = actualPlanContentRevisionId,
        actualProtocolId = actualProtocolId,
        actualProtocolVersion = actualProtocolVersion,
    )

    private suspend fun recordActualChoiceInternal(
        decisionId: String,
        intervention: InterventionFamily,
        momentPlanId: String?,
        momentPlanUpdatedAtMillis: Long?,
        userOverrodeSuggestion: Boolean,
        actualPlanContentRevisionId: String?,
        actualProtocolId: String?,
        actualProtocolVersion: Int?,
    ): Boolean = dao.recordActualChoiceOnce(
        decisionId = decisionId,
            actualIntervention = intervention.name,
            momentPlanId = momentPlanId,
            momentPlanUpdatedAtMillis = momentPlanUpdatedAtMillis,
            userOverrodeSuggestion = userOverrodeSuggestion,
            actualPlanContentRevisionId = actualPlanContentRevisionId,
            actualProtocolId = actualProtocolId,
            actualProtocolVersion = actualProtocolVersion,
    ) == 1

    override suspend fun replacePendingActualChoice(
        decisionId: String,
        intervention: InterventionFamily,
        momentPlanId: String?,
        momentPlanUpdatedAtMillis: Long?,
        userOverrodeSuggestion: Boolean,
    ): Boolean = replacePendingActualChoiceInternal(
        decisionId = decisionId,
        intervention = intervention,
        momentPlanId = momentPlanId,
        momentPlanUpdatedAtMillis = momentPlanUpdatedAtMillis,
        userOverrodeSuggestion = userOverrodeSuggestion,
        actualPlanContentRevisionId = null,
        actualProtocolId = null,
        actualProtocolVersion = null,
    )

    override suspend fun replacePendingActualChoice(
        decisionId: String,
        intervention: InterventionFamily,
        momentPlanId: String?,
        momentPlanUpdatedAtMillis: Long?,
        userOverrodeSuggestion: Boolean,
        actualPlanContentRevisionId: String?,
    ): Boolean = replacePendingActualChoiceInternal(
        decisionId = decisionId,
        intervention = intervention,
        momentPlanId = momentPlanId,
        momentPlanUpdatedAtMillis = momentPlanUpdatedAtMillis,
        userOverrodeSuggestion = userOverrodeSuggestion,
        actualPlanContentRevisionId = actualPlanContentRevisionId,
        actualProtocolId = null,
        actualProtocolVersion = null,
    )

    override suspend fun replacePendingActualChoice(
        decisionId: String,
        intervention: InterventionFamily,
        momentPlanId: String?,
        momentPlanUpdatedAtMillis: Long?,
        userOverrodeSuggestion: Boolean,
        actualPlanContentRevisionId: String?,
        actualProtocolId: String,
        actualProtocolVersion: Int,
    ): Boolean = replacePendingActualChoiceInternal(
        decisionId = decisionId,
        intervention = intervention,
        momentPlanId = momentPlanId,
        momentPlanUpdatedAtMillis = momentPlanUpdatedAtMillis,
        userOverrodeSuggestion = userOverrodeSuggestion,
        actualPlanContentRevisionId = actualPlanContentRevisionId,
        actualProtocolId = actualProtocolId,
        actualProtocolVersion = actualProtocolVersion,
    )

    private suspend fun replacePendingActualChoiceInternal(
        decisionId: String,
        intervention: InterventionFamily,
        momentPlanId: String?,
        momentPlanUpdatedAtMillis: Long?,
        userOverrodeSuggestion: Boolean,
        actualPlanContentRevisionId: String?,
        actualProtocolId: String?,
        actualProtocolVersion: Int?,
    ): Boolean = dao.replacePendingActualChoice(
        decisionId = decisionId,
            actualIntervention = intervention.name,
            momentPlanId = momentPlanId,
            momentPlanUpdatedAtMillis = momentPlanUpdatedAtMillis,
            userOverrodeSuggestion = userOverrodeSuggestion,
            actualPlanContentRevisionId = actualPlanContentRevisionId,
            actualProtocolId = actualProtocolId,
            actualProtocolVersion = actualProtocolVersion,
        eligibilityBit = intervention.eligibilityBit,
    ) == 1

    override suspend fun recordMomentContextOnce(
        decisionId: String,
        cue: MomentCue?,
        urgeRating: Int?,
    ): Boolean = dao.recordMomentContextOnce(
        decisionId = decisionId,
        momentCue = cue?.name,
        urgeRating = urgeRating,
    ) == 1

    override suspend fun addEligibleInterventions(
        decisionId: String,
        interventions: Set<InterventionFamily>,
    ): Boolean = dao.addEligibleInterventions(
        decisionId = decisionId,
        additionalMask = interventions.fold(0) { mask, intervention ->
            mask or intervention.eligibilityBit
        },
    ) == 1

    override suspend fun markPresentedOnce(
        decisionId: String,
        presentedAtMillis: Long,
    ): Boolean = dao.markPresentedOnce(decisionId, presentedAtMillis) == 1

    override suspend fun markStartedOnce(
        decisionId: String,
        startedAtMillis: Long,
    ): Boolean = dao.markStartedOnce(decisionId, startedAtMillis) == 1

    override suspend fun markCompletedOnce(
        decisionId: String,
        completedAtMillis: Long,
    ): Boolean = dao.markCompletedOnce(decisionId, completedAtMillis) == 1

    override suspend fun markDismissedOnce(
        decisionId: String,
        dismissedAtMillis: Long,
    ): Boolean = dao.markDismissedOnce(decisionId, dismissedAtMillis) == 1

    override suspend fun updateFeedback(
        decisionId: String,
        feedbackCode: FeedbackCode,
        feedbackUpdatedAtMillis: Long,
    ): Boolean = dao.updateFeedback(
        decisionId = decisionId,
        feedbackCode = feedbackCode.name,
        feedbackUpdatedAtMillis = feedbackUpdatedAtMillis,
    ) == 1

    override suspend fun markFirstRepeatOnce(
        decisionId: String,
        firstRepeatAtMillis: Long,
    ): Boolean = dao.markFirstRepeatOnce(decisionId, firstRepeatAtMillis) == 1

    override suspend fun finaliseOnce(
        decisionId: String,
        finalisedAtMillis: Long,
    ): Boolean = dao.finaliseOnce(decisionId, finalisedAtMillis) == 1

    override suspend fun getLatestInsideMomentWindow(
        windowStartedAtMillis: Long,
        nowMillis: Long,
    ): AdaptiveDecision? = dao.getLatestInsideMomentWindow(
        windowStartedAtMillis = windowStartedAtMillis,
        nowMillis = nowMillis,
    )?.toDomain()

    override suspend fun getLatestOpenInsideMomentWindow(
        windowStartedAtMillis: Long,
        nowMillis: Long,
    ): AdaptiveDecision? = dao.getLatestOpenInsideMomentWindow(
        windowStartedAtMillis = windowStartedAtMillis,
        nowMillis = nowMillis,
    )?.toDomain()

    override suspend fun getOpenObservationDeadlines(
        nowMillis: Long,
        limit: Int,
    ): List<AdaptiveDecision> = dao.getOpenObservationDeadlines(
        nowMillis = nowMillis,
        limit = limit.requirePositiveLimit(),
    ).map { it.toDomain() }

    override suspend fun getFutureOpenObservationDeadlines(
        nowMillis: Long,
        limit: Int,
    ): List<AdaptiveDecision> = dao.getFutureOpenObservationDeadlines(
        nowMillis = nowMillis,
        limit = limit.requirePositiveLimit(),
    ).map { it.toDomain() }

    override suspend fun getLatestPendingFeedback(): AdaptiveDecision? =
        dao.getLatestPendingFeedback()?.toDomain()

    override suspend fun getBetween(
        startedAtMillis: Long,
        endedAtMillis: Long,
    ): List<AdaptiveDecision> {
        require(startedAtMillis >= 0L)
        require(endedAtMillis > startedAtMillis)
        return dao.getBetween(startedAtMillis, endedAtMillis).map { it.toDomain() }
    }

    override suspend fun getMomentPlanUsesSince(
        sinceMillis: Long,
    ): List<MomentPlanUseRecord> = dao.getMomentPlanUsesSince(sinceMillis).map {
        MomentPlanUseRecord(
            decisionId = it.decisionId,
            planId = checkNotNull(it.momentPlanId),
            planUpdatedAtMillis = checkNotNull(it.momentPlanUpdatedAtMillis),
            startedAtMillis = checkNotNull(it.startedAtMillis),
            planContentRevisionId = checkNotNull(it.actualPlanContentRevisionId),
        )
    }

    override fun observeRecentDecisions(limit: Int): Flow<List<AdaptiveDecision>> =
        dao.observeRecent(limit.coerceAtLeast(0)).map { decisions ->
            decisions.map { it.toDomain() }
        }

    override suspend fun getRecentFinalised(
        limit: Int,
    ): List<AdaptiveOutcomeRecord> =
        dao.getRecentFinalised(limit.requirePositiveLimit()).map {
            it.toOutcomeRecord()
        }

    override suspend fun getRecentFamiliarStepEvidence(
        limit: Int,
    ): List<FamiliarStepEvidenceRecord> =
        dao.getRecentFinalised(limit.requirePositiveLimit()).mapNotNull {
            it.toFamiliarStepEvidenceRecord()
        }

    override suspend fun getFinalisedByActualIntervention(
        intervention: InterventionFamily,
        limit: Int,
    ): List<AdaptiveOutcomeRecord> =
        dao.getFinalisedByActualIntervention(
            actualIntervention = intervention.name,
            limit = limit.requirePositiveLimit(),
        ).map { it.toOutcomeRecord() }

    override suspend fun getFinalisedByCue(
        cue: MomentCue,
        limit: Int,
    ): List<AdaptiveOutcomeRecord> =
        dao.getFinalisedByCue(
            momentCue = cue.name,
            limit = limit.requirePositiveLimit(),
        ).map { it.toOutcomeRecord() }

    override suspend fun clearLearningHistory() {
        dao.clearLearningHistory()
    }

    private fun Int.requirePositiveLimit(): Int {
        require(this > 0) { "Query limit must be positive." }
        return this
    }
}
