package com.impulsive.app.backend.session.adaptive

import com.impulsive.app.backend.domain.engine.adaptive.AdaptiveRecommendationPolicy
import com.impulsive.app.backend.domain.engine.adaptive.RandomisationSource
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveAssignment
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveDecision
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveOutcomeRecord
import com.impulsive.app.backend.domain.model.adaptive.AdaptivePreferences
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveReasonCode
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSourceKind
import com.impulsive.app.backend.domain.model.adaptive.AssignmentMode
import com.impulsive.app.backend.domain.model.adaptive.EngagementOutcome
import com.impulsive.app.backend.domain.model.adaptive.FeedbackCode
import com.impulsive.app.backend.domain.model.adaptive.FamiliarStepEvidenceRecord
import com.impulsive.app.backend.domain.model.adaptive.ImpulsiveDestination
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentCue
import com.impulsive.app.backend.domain.model.adaptive.MomentIntensity
import com.impulsive.app.backend.domain.model.adaptive.MomentPlan
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanActionType
import com.impulsive.app.backend.domain.model.adaptive.RepeatObservation
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveDataRepository
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveDecisionRepository
import com.impulsive.app.backend.domain.repository.adaptive.AdaptivePreferenceRepository
import com.impulsive.app.backend.domain.repository.adaptive.MomentPlanRepository
import com.impulsive.app.backend.domain.repository.adaptive.MomentPlanSaveResult
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal class FakeClock(var current: Long = 10_000_000L) : AdaptiveClock {
    override fun nowMillis(): Long = current
}

internal class FakeRandomisationSource(
    private val doubles: ArrayDeque<Double> = ArrayDeque(listOf(0.9)),
    private val ints: ArrayDeque<Int> = ArrayDeque(listOf(0)),
) : RandomisationSource {
    override fun nextDouble(): Double =
        if (doubles.isEmpty()) 0.9 else doubles.removeFirst()

    override fun nextInt(bound: Int): Int =
        (if (ints.isEmpty()) 0 else ints.removeFirst()).coerceIn(0, bound - 1)
}

internal open class FakeDecisionRepository : AdaptiveDecisionRepository {
    val stored = mutableListOf<AdaptiveDecision>()
    var outcomes: List<AdaptiveOutcomeRecord> = emptyList()
    var insertCalls = 0
    var clearCalls = 0
    var throwOnRead = false
    var familiarStepEvidence: List<FamiliarStepEvidenceRecord> = emptyList()
    var familiarStepEvidenceReads = 0

    override suspend fun insertOnce(decision: AdaptiveDecision): Boolean {
        insertCalls++
        if (stored.any {
                it.decisionId == decision.decisionId ||
                    it.protectionIncidentToken == decision.protectionIncidentToken
            }
        ) {
            return false
        }
        stored += decision
        return true
    }

    override suspend fun getById(decisionId: String): AdaptiveDecision? {
        if (throwOnRead) error("read unavailable")
        return stored.firstOrNull { it.decisionId == decisionId }
    }

    override suspend fun getByIncidentToken(incidentToken: String): AdaptiveDecision? {
        if (throwOnRead) error("read unavailable")
        return stored.firstOrNull { it.protectionIncidentToken == incidentToken }
    }

    override suspend fun recordActualChoiceOnce(
        decisionId: String,
        intervention: InterventionFamily,
        momentPlanId: String?,
        momentPlanUpdatedAtMillis: Long?,
        userOverrodeSuggestion: Boolean,
    ): Boolean = mutate(decisionId) { decision ->
        if (
            decision.assignment.actualIntervention != null ||
            decision.startedAtMillis != null ||
            decision.completedAtMillis != null ||
            decision.dismissedAtMillis != null
        ) {
            null
        } else {
            decision.copy(
                assignment = decision.assignment.copy(
                    actualIntervention = intervention,
                    momentPlanId = momentPlanId ?: decision.assignment.momentPlanId,
                    momentPlanUpdatedAtMillis =
                        momentPlanUpdatedAtMillis
                            ?: decision.assignment.momentPlanUpdatedAtMillis,
                    userOverrodeSuggestion = userOverrodeSuggestion,
                ),
            )
        }
    }

    override suspend fun recordActualChoiceOnce(
        decisionId: String,
        intervention: InterventionFamily,
        momentPlanId: String?,
        momentPlanUpdatedAtMillis: Long?,
        userOverrodeSuggestion: Boolean,
        actualPlanContentRevisionId: String?,
    ): Boolean {
        val applied = recordActualChoiceOnce(
            decisionId,
            intervention,
            momentPlanId,
            momentPlanUpdatedAtMillis,
            userOverrodeSuggestion,
        )
        if (applied) {
            mutate(decisionId) { decision ->
                decision.copy(
                    assignment = decision.assignment.copy(
                        actualPlanContentRevisionId = actualPlanContentRevisionId,
                    ),
                )
            }
        }
        return applied
    }

    override suspend fun recordActualChoiceOnce(
        decisionId: String,
        intervention: InterventionFamily,
        momentPlanId: String?,
        momentPlanUpdatedAtMillis: Long?,
        userOverrodeSuggestion: Boolean,
        actualPlanContentRevisionId: String?,
        actualProtocolId: String,
        actualProtocolVersion: Int,
    ): Boolean {
        val applied = recordActualChoiceOnce(
            decisionId,
            intervention,
            momentPlanId,
            momentPlanUpdatedAtMillis,
            userOverrodeSuggestion,
            actualPlanContentRevisionId,
        )
        if (applied) {
            mutate(decisionId) { decision ->
                decision.copy(
                    actualProtocolId = actualProtocolId,
                    actualProtocolVersion = actualProtocolVersion,
                )
            }
        }
        return applied
    }

    override suspend fun replacePendingActualChoice(
        decisionId: String,
        intervention: InterventionFamily,
        momentPlanId: String?,
        momentPlanUpdatedAtMillis: Long?,
        userOverrodeSuggestion: Boolean,
    ): Boolean = mutate(decisionId) { decision ->
        if (
            decision.assignment.actualIntervention == null ||
            decision.startedAtMillis != null ||
            decision.completedAtMillis != null ||
            decision.dismissedAtMillis != null ||
            intervention !in decision.assignment.eligibleInterventions
        ) {
            null
        } else {
            val preservedOrSelectedPlanId =
                if (decision.assignment.assignedSuggestion == InterventionFamily.MomentPlan) {
                    decision.assignment.momentPlanId
                } else {
                    momentPlanId
                }
            val preservedOrSelectedPlanRevision =
                if (decision.assignment.assignedSuggestion == InterventionFamily.MomentPlan) {
                    decision.assignment.momentPlanUpdatedAtMillis
                } else {
                    momentPlanUpdatedAtMillis
                }
            decision.copy(
                assignment = decision.assignment.copy(
                    actualIntervention = intervention,
                    momentPlanId = preservedOrSelectedPlanId,
                    momentPlanUpdatedAtMillis = preservedOrSelectedPlanRevision,
                    userOverrodeSuggestion = userOverrodeSuggestion,
                ),
            )
        }
    }

    override suspend fun replacePendingActualChoice(
        decisionId: String,
        intervention: InterventionFamily,
        momentPlanId: String?,
        momentPlanUpdatedAtMillis: Long?,
        userOverrodeSuggestion: Boolean,
        actualPlanContentRevisionId: String?,
    ): Boolean {
        val applied = replacePendingActualChoice(
            decisionId,
            intervention,
            momentPlanId,
            momentPlanUpdatedAtMillis,
            userOverrodeSuggestion,
        )
        if (applied) {
            mutate(decisionId) { decision ->
                decision.copy(
                    assignment = decision.assignment.copy(
                        actualPlanContentRevisionId = actualPlanContentRevisionId,
                    ),
                )
            }
        }
        return applied
    }

    override suspend fun replacePendingActualChoice(
        decisionId: String,
        intervention: InterventionFamily,
        momentPlanId: String?,
        momentPlanUpdatedAtMillis: Long?,
        userOverrodeSuggestion: Boolean,
        actualPlanContentRevisionId: String?,
        actualProtocolId: String,
        actualProtocolVersion: Int,
    ): Boolean {
        val applied = replacePendingActualChoice(
            decisionId,
            intervention,
            momentPlanId,
            momentPlanUpdatedAtMillis,
            userOverrodeSuggestion,
            actualPlanContentRevisionId,
        )
        if (applied) {
            mutate(decisionId) { decision ->
                decision.copy(
                    actualProtocolId = actualProtocolId,
                    actualProtocolVersion = actualProtocolVersion,
                )
            }
        }
        return applied
    }

    override suspend fun recordMomentContextOnce(
        decisionId: String,
        cue: MomentCue?,
        urgeRating: Int?,
    ): Boolean {
        if (urgeRating != null && urgeRating !in 0..10) return false
        return mutate(decisionId) { decision ->
            if (
                decision.assignment.actualIntervention != null ||
                decision.startedAtMillis != null ||
                decision.completedAtMillis != null ||
                decision.dismissedAtMillis != null
            ) {
                null
            } else {
                decision.copy(
                    momentCue = decision.momentCue ?: cue,
                    baselineUrgeRating = decision.baselineUrgeRating ?: urgeRating,
                )
            }
        }
    }

    override suspend fun addEligibleInterventions(
        decisionId: String,
        interventions: Set<InterventionFamily>,
    ): Boolean = mutate(decisionId) { decision ->
        if (
            decision.assignment.actualIntervention != null ||
            decision.startedAtMillis != null ||
            decision.completedAtMillis != null ||
            decision.dismissedAtMillis != null
        ) {
            null
        } else {
            decision.copy(
                assignment = decision.assignment.copy(
                    eligibleInterventions =
                        decision.assignment.eligibleInterventions + interventions,
                ),
            )
        }
    }

    override suspend fun markPresentedOnce(
        decisionId: String,
        presentedAtMillis: Long,
    ): Boolean = mutate(decisionId) {
        if (
            it.presentedAtMillis != null ||
            it.completedAtMillis != null ||
            it.dismissedAtMillis != null
        ) {
            null
        } else {
            it.copy(presentedAtMillis = presentedAtMillis)
        }
    }

    override suspend fun markStartedOnce(
        decisionId: String,
        startedAtMillis: Long,
    ): Boolean = mutate(decisionId) {
        if (
            it.presentedAtMillis == null ||
            it.startedAtMillis != null ||
            it.completedAtMillis != null ||
            it.dismissedAtMillis != null
        ) {
            null
        } else {
            it.copy(startedAtMillis = startedAtMillis)
        }
    }

    override suspend fun markCompletedOnce(
        decisionId: String,
        completedAtMillis: Long,
    ): Boolean = mutate(decisionId) {
        if (
            it.startedAtMillis == null ||
            it.completedAtMillis != null ||
            it.dismissedAtMillis != null
        ) {
            null
        } else {
            it.copy(completedAtMillis = completedAtMillis)
        }
    }

    override suspend fun markDismissedOnce(
        decisionId: String,
        dismissedAtMillis: Long,
    ): Boolean = mutate(decisionId) {
        if (
            it.presentedAtMillis == null ||
            it.completedAtMillis != null ||
            it.dismissedAtMillis != null
        ) {
            null
        } else {
            it.copy(dismissedAtMillis = dismissedAtMillis)
        }
    }

    override suspend fun updateFeedback(
        decisionId: String,
        feedbackCode: FeedbackCode,
        feedbackUpdatedAtMillis: Long,
    ): Boolean = mutate(decisionId) {
        it.copy(
            feedbackCode = feedbackCode,
            feedbackUpdatedAtMillis = feedbackUpdatedAtMillis,
        )
    }

    override suspend fun markFirstRepeatOnce(
        decisionId: String,
        firstRepeatAtMillis: Long,
    ): Boolean = mutate(decisionId) {
        if (
            it.repeatObservation != RepeatObservation.NotFinalised ||
            it.observationFinalisedAtMillis != null
        ) {
            null
        } else {
            it.copy(
                repeatObservation = RepeatObservation.RepeatDetected,
                firstRepeatAtMillis = firstRepeatAtMillis,
            )
        }
    }

    override suspend fun finaliseOnce(
        decisionId: String,
        finalisedAtMillis: Long,
    ): Boolean = mutate(decisionId) {
        if (it.observationFinalisedAtMillis != null) {
            null
        } else {
            it.copy(
                repeatObservation = if (
                    it.repeatObservation == RepeatObservation.NotFinalised
                ) {
                    RepeatObservation.NoRepeatDetected
                } else {
                    it.repeatObservation
                },
                observationFinalisedAtMillis = finalisedAtMillis,
            )
        }
    }

    override suspend fun getLatestInsideMomentWindow(
        windowStartedAtMillis: Long,
        nowMillis: Long,
    ): AdaptiveDecision? = stored
        .filter { it.createdAtMillis in windowStartedAtMillis..nowMillis }
        .maxByOrNull { it.createdAtMillis }

    override suspend fun getLatestOpenInsideMomentWindow(
        windowStartedAtMillis: Long,
        nowMillis: Long,
    ): AdaptiveDecision? = stored
        .filter {
            it.createdAtMillis in windowStartedAtMillis..nowMillis &&
                it.observationFinalisedAtMillis == null
        }
        .maxByOrNull { it.createdAtMillis }

    override suspend fun getOpenObservationDeadlines(
        nowMillis: Long,
        limit: Int,
    ): List<AdaptiveDecision> = stored
        .filter {
            it.observationFinalisedAtMillis == null &&
                it.observationDeadlineAtMillis <= nowMillis
        }
        .sortedBy { it.observationDeadlineAtMillis }
        .take(limit)

    override suspend fun getFutureOpenObservationDeadlines(
        nowMillis: Long,
        limit: Int,
    ): List<AdaptiveDecision> = stored
        .filter {
            it.observationFinalisedAtMillis == null &&
                it.observationDeadlineAtMillis > nowMillis
        }
        .sortedBy { it.observationDeadlineAtMillis }
        .take(limit)

    override suspend fun getLatestPendingFeedback(): AdaptiveDecision? = stored
        .filter {
            it.startedAtMillis != null &&
                (it.completedAtMillis != null || it.dismissedAtMillis != null) &&
                it.feedbackUpdatedAtMillis == null
        }
        .maxByOrNull { it.completedAtMillis ?: it.dismissedAtMillis ?: Long.MIN_VALUE }

    override suspend fun getRecentFinalised(limit: Int): List<AdaptiveOutcomeRecord> =
        outcomes.take(limit)

    override suspend fun getRecentFamiliarStepEvidence(
        limit: Int,
    ): List<FamiliarStepEvidenceRecord> {
        familiarStepEvidenceReads++
        return familiarStepEvidence.take(limit)
    }

    override suspend fun getFinalisedByActualIntervention(
        intervention: InterventionFamily,
        limit: Int,
    ): List<AdaptiveOutcomeRecord> =
        outcomes.filter { it.actualIntervention == intervention }.take(limit)

    override suspend fun getFinalisedByCue(
        cue: MomentCue,
        limit: Int,
    ): List<AdaptiveOutcomeRecord> =
        outcomes.filter { it.selectedCue == cue }.take(limit)

    override suspend fun clearLearningHistory() {
        clearCalls++
        stored.clear()
        outcomes = emptyList()
    }

    private fun mutate(
        decisionId: String,
        transform: (AdaptiveDecision) -> AdaptiveDecision?,
    ): Boolean {
        val index = stored.indexOfFirst { it.decisionId == decisionId }
        if (index < 0) return false
        val replacement = transform(stored[index]) ?: return false
        stored[index] = replacement
        return true
    }
}

internal class FakeMomentPlanRepository(
    initial: List<MomentPlan> = emptyList(),
) : MomentPlanRepository {
    val plans = MutableStateFlow(initial)

    override suspend fun create(plan: MomentPlan): MomentPlanSaveResult {
        plans.value += plan
        return MomentPlanSaveResult.Applied
    }

    override suspend fun update(plan: MomentPlan): MomentPlanSaveResult {
        plans.value = plans.value.map { if (it.planId == plan.planId) plan else it }
        return MomentPlanSaveResult.Applied
    }

    override suspend fun delete(planId: String): MomentPlanSaveResult {
        plans.value = plans.value.filterNot { it.planId == planId }
        return MomentPlanSaveResult.Applied
    }

    override suspend fun getById(planId: String): MomentPlan? =
        plans.value.firstOrNull { it.planId == planId }

    override fun observeAll(): Flow<List<MomentPlan>> = plans

    override fun observeEnabled(): Flow<List<MomentPlan>> = plans

    override suspend fun getMatchingEnabledByCue(cue: MomentCue): List<MomentPlan> =
        plans.value.filter { it.enabled && it.momentCue == cue }

    override suspend fun setPreferred(
        planId: String,
        updatedAtMillis: Long,
    ): MomentPlanSaveResult = MomentPlanSaveResult.Applied

    override suspend fun markRehearsedIfRevisionMatches(
        planId: String,
        expectedUpdatedAtMillis: Long,
        rehearsedAtMillis: Long,
    ): Boolean {
        val existing = plans.value.firstOrNull { it.planId == planId }
            ?: return false
        if (existing.updatedAtMillis != expectedUpdatedAtMillis) return false
        plans.value = plans.value.map {
            if (it.planId == planId) {
                it.copy(rehearsedAtMillis = maxOf(it.rehearsedAtMillis ?: 0L, rehearsedAtMillis))
            } else {
                it
            }
        }
        return true
    }

    override suspend fun markRehearsedIfContentRevisionMatches(
        planId: String,
        expectedContentRevisionId: String,
        rehearsedAtMillis: Long,
    ): Boolean {
        val existing = plans.value.firstOrNull { it.planId == planId }
            ?: return false
        if (existing.contentRevisionId != expectedContentRevisionId) return false
        plans.value = plans.value.map {
            if (it.planId == planId) {
                it.copy(rehearsedAtMillis = maxOf(it.rehearsedAtMillis ?: 0L, rehearsedAtMillis))
            } else {
                it
            }
        }
        return true
    }
}

internal class FakePreferenceRepository(
    var current: AdaptivePreferences = AdaptivePreferences(),
) : AdaptivePreferenceRepository {
    private val flow = MutableStateFlow(current)

    override fun observe(): Flow<AdaptivePreferences> = flow

    override suspend fun get(): AdaptivePreferences = current

    override suspend fun insertDefaults(updatedAtMillis: Long) = Unit

    override suspend fun update(
        preferences: AdaptivePreferences,
        updatedAtMillis: Long,
    ) {
        current = preferences
        flow.value = preferences
    }

    override suspend fun resetDefaults(updatedAtMillis: Long) {
        current = AdaptivePreferences()
        flow.value = current
    }
}

internal class FakeScheduler : AdaptiveObservationScheduler {
    val scheduled = linkedMapOf<String, Long>()
    var cancelCalls = 0
    var fail = false

    override fun schedule(decisionId: String, deadlineAtMillis: Long): Boolean {
        if (fail) return false
        scheduled.putIfAbsent(decisionId, deadlineAtMillis)
        return true
    }

    override fun cancelAll(): Boolean {
        cancelCalls++
        scheduled.clear()
        return !fail
    }
}

internal class FakeAdaptiveDataRepository : AdaptiveDataRepository {
    var clearCalls = 0
    var clearLearningCalls = 0

    override suspend fun clearPersonalLearning() {
        clearLearningCalls++
    }

    override suspend fun clearAllAdaptiveData() {
        clearCalls++
    }
}

internal fun coordinatorHarness(
    decisions: FakeDecisionRepository = FakeDecisionRepository(),
    preferences: FakePreferenceRepository = FakePreferenceRepository(),
    plans: FakeMomentPlanRepository = FakeMomentPlanRepository(),
    clock: FakeClock = FakeClock(),
    random: FakeRandomisationSource = FakeRandomisationSource(),
): AdaptiveMomentCoordinator = AdaptiveMomentCoordinator(
    decisions = decisions,
    preferences = preferences,
    momentPlans = plans,
    recommendationPolicy = AdaptiveRecommendationPolicy(random),
    clock = clock,
    idSource = object : AdaptiveIdSource {
        private var next = 0
        override fun newId(): String =
            UUID.nameUUIDFromBytes("decision-${next++}".toByteArray()).toString()
    },
    logger = AdaptiveSafeLogger { _, _ -> },
)

internal fun incident(
    token: String = "opaque-incident",
    at: Long = 1_000_000L,
    cue: MomentCue? = null,
    allowed: Set<InterventionFamily> = setOf(
        InterventionFamily.PivotGame,
        InterventionFamily.PivotReading,
        InterventionFamily.MomentPlan,
    ),
    game: Boolean = true,
    reading: Boolean = true,
    plans: Boolean = true,
): AdaptiveProtectionIncidentRequest = AdaptiveProtectionIncidentRequest(
    incidentToken = token,
    sourceKind = AdaptiveSourceKind.App,
    detectedAtMillis = at,
    currentlyAllowedInterventions = allowed,
    confirmedCue = cue,
    gameProductEligible = game,
    readingProductEligible = reading,
    momentPlansProductEligible = plans,
)

internal fun momentPlan(
    id: String = UUID.nameUUIDFromBytes("plan".toByteArray()).toString(),
    cue: MomentCue? = MomentCue.Boredom,
    enabled: Boolean = true,
    preferred: Boolean = false,
    actionType: MomentPlanActionType = MomentPlanActionType.TextOnly,
    target: String? = null,
): MomentPlan = MomentPlan(
    planId = id,
    title = "Clear morning",
    momentCue = cue,
    actionText = "Open my project for two minutes",
    futureCueText = "Tomorrow I want to feel clear.",
    actionType = actionType,
    actionTarget = when (actionType) {
        MomentPlanActionType.TextOnly -> null
        MomentPlanActionType.OpenImpulsiveDestination ->
            target ?: ImpulsiveDestination.Focus.storageValue
        MomentPlanActionType.LaunchSelectedApp -> target ?: "com.example.safe"
    },
    enabled = enabled,
    preferredForCue = preferred,
    createdAtMillis = 10L,
    updatedAtMillis = 20L,
    contentRevisionId = UUID.nameUUIDFromBytes("plan-revision-$id".toByteArray()).toString(),
)

internal fun decision(
    id: String = UUID.nameUUIDFromBytes("decision".toByteArray()).toString(),
    token: String = "opaque",
    created: Long = 1_000L,
    eligible: Set<InterventionFamily> = setOf(InterventionFamily.PivotGame),
    assigned: InterventionFamily? = InterventionFamily.PivotGame,
    actual: InterventionFamily? = null,
    planId: String? = null,
    presented: Long? = null,
    started: Long? = null,
    completed: Long? = null,
    dismissed: Long? = null,
    feedback: FeedbackCode = FeedbackCode.NotProvided,
    feedbackAt: Long? = null,
    repeat: RepeatObservation = RepeatObservation.NotFinalised,
    firstRepeat: Long? = null,
    deadline: Long = created + 1_200_000L,
    finalised: Long? = null,
): AdaptiveDecision = AdaptiveDecision(
    decisionId = id,
    protectionIncidentToken = token,
    sourceKind = AdaptiveSourceKind.App,
    createdAtMillis = created,
    momentWindowStartedAtMillis = created,
    momentCue = null,
    baselineUrgeRating = null,
    assignment = AdaptiveAssignment(
        momentIntensity = MomentIntensity.RepeatedAttempt,
        assignmentMode = AssignmentMode.AdaptiveSuggestion,
        eligibleInterventions = eligible,
        assignedSuggestion = assigned,
        selectionProbability = null,
        reasonCode = AdaptiveReasonCode.InsufficientEvidenceExploration,
        momentPlanId = planId,
        actualIntervention = actual,
        userOverrodeSuggestion = actual != null && actual != assigned,
    ),
    presentedAtMillis = presented,
    startedAtMillis = started,
    completedAtMillis = completed,
    dismissedAtMillis = dismissed,
    feedbackCode = feedback,
    feedbackUpdatedAtMillis = feedbackAt,
    repeatObservation = repeat,
    firstRepeatAtMillis = firstRepeat,
    observationDeadlineAtMillis = deadline,
    observationFinalisedAtMillis = finalised,
)
