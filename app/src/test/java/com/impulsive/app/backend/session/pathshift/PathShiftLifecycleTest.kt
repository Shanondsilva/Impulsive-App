package com.impulsive.app.backend.session.pathshift

import com.impulsive.app.backend.domain.model.adaptive.AdaptiveAssignment
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveDecision
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveReasonCode
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSourceKind
import com.impulsive.app.backend.domain.model.adaptive.AssignmentMode
import com.impulsive.app.backend.domain.model.adaptive.FeedbackCode
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentIntensity
import com.impulsive.app.backend.domain.model.adaptive.MomentPlan
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanActionType
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanRehearsal
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanUseRecord
import com.impulsive.app.backend.domain.model.adaptive.RepeatObservation
import com.impulsive.app.backend.domain.pathshift.PathShiftCycle
import com.impulsive.app.backend.domain.pathshift.PathShiftCycleStatus
import com.impulsive.app.backend.domain.pathshift.PathShiftEvidenceStrength
import com.impulsive.app.backend.domain.pathshift.PathShiftForecastPolicy
import com.impulsive.app.backend.domain.pathshift.PathShiftReviewCounts
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveDecisionRepository
import com.impulsive.app.backend.domain.repository.adaptive.MomentPlanRepository
import com.impulsive.app.backend.domain.repository.adaptive.MomentPlanSaveResult
import com.impulsive.app.backend.domain.repository.pathshift.PathShiftCycleRepository
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PathShiftLifecycleTest {
    private val zone = ZoneId.of("Europe/London")
    private val now = ZonedDateTime.of(
        LocalDate.of(2026, 7, 29),
        LocalTime.NOON,
        zone,
    )

    @Test
    fun `insufficient history does not persist or schedule`() = runBlocking {
        val harness = harness(decisions = roots().take(6))
        assertTrue(harness.coordinator.createCycle() is PathShiftCreateResult.Unavailable)
        assertTrue(harness.cycles.values.isEmpty())
        assertEquals(0, harness.scheduler.scheduleCalls)
    }

    @Test
    fun `double create returns one fixed cycle and schedules once`() = runBlocking {
        val harness = harness()
        val first = harness.coordinator.createCycle() as PathShiftCreateResult.Created
        val second = harness.coordinator.createCycle() as PathShiftCreateResult.Existing
        assertEquals(first.cycle, second.cycle)
        assertEquals(1, harness.cycles.values.size)
        assertEquals(1, harness.scheduler.scheduleCalls)
    }

    @Test
    fun `selecting plan stores exact revision without rewriting estimate`() = runBlocking {
        val harness = harness()
        val original = (harness.coordinator.createCycle() as PathShiftCreateResult.Created).cycle
        assertEquals(
            PathShiftMutationResult.Applied,
            harness.coordinator.preparePlan(original.cycleId, harness.plan.planId),
        )
        val updated = harness.cycles.getById(original.cycleId)!!
        assertEquals(original.estimatedLowerCount, updated.estimatedLowerCount)
        assertEquals(original.estimatedUpperCount, updated.estimatedUpperCount)
        assertEquals(harness.plan.contentRevisionId, updated.preparedPlanContentRevisionId)
    }

    @Test
    fun `metadata edit keeps revision while meaningful edit requires deliberate update`() = runBlocking {
        val harness = harness()
        val cycle = (harness.coordinator.createCycle() as PathShiftCreateResult.Created).cycle
        harness.coordinator.preparePlan(cycle.cycleId, harness.plan.planId)
        harness.plans.current = harness.plan.copy(title = "Renamed")
        assertTrue(harness.coordinator.preparedPlanRevisionMatches(cycle.cycleId))

        val changed = revised(harness.plan.copy(actionText = "Take a different step"))
        harness.plans.current = changed
        assertFalse(harness.coordinator.preparedPlanRevisionMatches(cycle.cycleId))
        assertEquals(
            PathShiftMutationResult.Applied,
            harness.coordinator.useNewPlanRevision(cycle.cycleId),
        )
        assertTrue(harness.coordinator.preparedPlanRevisionMatches(cycle.cycleId))
    }

    @Test
    fun `early finalisation is rejected and due finalisation is exact once`() = runBlocking {
        val harness = harness()
        val cycle = (harness.coordinator.createCycle() as PathShiftCreateResult.Created).cycle
        assertEquals(
            PathShiftFinalisationResult.NotDue,
            harness.finaliser.finalise(cycle.cycleId),
        )
        harness.clock.value = cycle.forecastWindowEndsAtMillis
        assertEquals(
            PathShiftFinalisationResult.Finalised,
            harness.finaliser.finalise(cycle.cycleId),
        )
        assertEquals(
            PathShiftFinalisationResult.AlreadyFinalised,
            harness.finaliser.finalise(cycle.cycleId),
        )
    }

    @Test
    fun `review separates real outcomes and exact plan revision`() = runBlocking {
        val harness = harness()
        val cycle = (harness.coordinator.createCycle() as PathShiftCreateResult.Created).cycle
        harness.coordinator.preparePlan(cycle.cycleId, harness.plan.planId)
        val start = cycle.forecastWindowStartedAtMillis + 1_000L
        harness.decisions.items += listOf(
            decision(
                id = "exact-complete",
                at = start,
                plan = harness.plan,
                started = true,
                completed = true,
                feedback = FeedbackCode.Helped,
                repeat = RepeatObservation.RepeatDetected,
                finalised = true,
            ),
            decision(
                id = "old-revision",
                at = start + 1,
                plan = harness.plan.copy(contentRevisionId = "old"),
                started = true,
                dismissed = true,
            ),
            decision(
                id = "wrong-timing",
                at = start + 2,
                feedback = FeedbackCode.WrongTiming,
            ),
            decision(
                id = "pending-repeat",
                at = start + 3,
                repeat = RepeatObservation.NotFinalised,
                finalised = false,
            ),
            decision(
                id = "follow-up",
                at = start + 4,
                source = AdaptiveSourceKind.ExplicitUserSupport,
                feedback = FeedbackCode.WrongTiming,
            ),
        )
        harness.clock.value = cycle.forecastWindowEndsAtMillis
        assertEquals(
            PathShiftFinalisationResult.Finalised,
            harness.finaliser.finalise(cycle.cycleId),
        )
        val review = harness.cycles.getById(cycle.cycleId)!!
        assertEquals(4, review.observedProtectedMomentCount)
        assertEquals(1, review.preparedPlanSelectedCount)
        assertEquals(1, review.preparedPlanStartedCount)
        assertEquals(1, review.preparedPlanCompletedCount)
        assertEquals(0, review.preparedPlanDismissedCount)
        assertEquals(1, review.wrongTimingCount)
        assertEquals(1, review.repeatDetectedCount)
    }

    @Test
    fun `cancellation is idempotent and preserves decisions`() = runBlocking {
        val harness = harness()
        val cycle = (harness.coordinator.createCycle() as PathShiftCreateResult.Created).cycle
        val before = harness.decisions.items.toList()
        assertEquals(PathShiftMutationResult.Applied, harness.coordinator.cancel(cycle.cycleId))
        assertEquals(PathShiftMutationResult.Idempotent, harness.coordinator.cancel(cycle.cycleId))
        assertEquals(before, harness.decisions.items)
        assertEquals(1, harness.scheduler.cancelCalls)
        assertEquals(PathShiftCycleStatus.Cancelled, harness.cycles.getById(cycle.cycleId)?.status)
    }

    @Test
    fun `startup reschedules future cycle and finalises overdue cycle`() = runBlocking {
        val future = harness()
        val futureCycle =
            (future.coordinator.createCycle() as PathShiftCreateResult.Created).cycle
        val before = future.scheduler.scheduleCalls
        val recovered = future.recovery.recover()
        assertTrue(recovered.rescheduled)
        assertEquals(before + 1, future.scheduler.scheduleCalls)

        future.clock.value = futureCycle.forecastWindowEndsAtMillis
        val overdue = future.recovery.recover()
        assertTrue(overdue.finalised)
        assertEquals(
            PathShiftCycleStatus.Finalised,
            future.cycles.getById(futureCycle.cycleId)?.status,
        )
    }

    private fun harness(
        decisions: List<AdaptiveDecision> = roots(),
    ): Harness {
        val cycles = FakeCycleRepository()
        val decisionRepo = FakeDecisionRepository(decisions.toMutableList())
        val plan = revised(
            MomentPlan(
                planId = "plan-1",
                title = "My plan",
                momentCue = null,
                actionText = "Take a short pause",
                futureCueText = "If a suitable moment happens, pause first",
                actionType = MomentPlanActionType.TextOnly,
                actionTarget = null,
                enabled = true,
                preferredForCue = false,
                createdAtMillis = 1,
                updatedAtMillis = 2,
            ),
        )
        val plans = FakeMomentPlanRepository(plan)
        val scheduler = FakeScheduler()
        val clock = MutableClock(now.toInstant().toEpochMilli())
        val coordinator = PathShiftCoordinator(
            cycles = cycles,
            decisions = decisionRepo,
            plans = plans,
            forecastPolicy = PathShiftForecastPolicy(),
            scheduler = scheduler,
            clock = clock,
            zoneIdSource = PathShiftZoneIdSource { zone },
            idSource = PathShiftIdSource { "cycle-1" },
        )
        val finaliser = PathShiftReviewFinaliser(cycles, decisionRepo, clock)
        return Harness(
            cycles,
            decisionRepo,
            plans,
            plan,
            scheduler,
            clock,
            coordinator,
            finaliser,
            PathShiftRecoveryCoordinator(cycles, finaliser, scheduler, clock),
        )
    }

    private fun roots(): List<AdaptiveDecision> =
        listOf(27L, 24L, 20L, 16L, 12L, 8L, 2L).mapIndexed { index, days ->
            decision(
                id = "root-$index",
                at = now.minusDays(days).withHour(10).toInstant().toEpochMilli(),
            )
        }

    private fun decision(
        id: String,
        at: Long,
        source: AdaptiveSourceKind = AdaptiveSourceKind.App,
        plan: MomentPlan? = null,
        started: Boolean = false,
        completed: Boolean = false,
        dismissed: Boolean = false,
        feedback: FeedbackCode = FeedbackCode.NotProvided,
        repeat: RepeatObservation = RepeatObservation.NotFinalised,
        finalised: Boolean = repeat != RepeatObservation.NotFinalised,
    ): AdaptiveDecision = AdaptiveDecision(
        decisionId = id,
        protectionIncidentToken = "incident-$id",
        sourceKind = source,
        createdAtMillis = at,
        momentWindowStartedAtMillis = at,
        momentCue = null,
        baselineUrgeRating = null,
        assignment = AdaptiveAssignment(
            momentIntensity = MomentIntensity.FirstAttempt,
            assignmentMode = AssignmentMode.AdaptiveSuggestion,
            eligibleInterventions = setOf(InterventionFamily.ShortPause),
            assignedSuggestion = null,
            selectionProbability = null,
            reasonCode = AdaptiveReasonCode.StableFallback,
            momentPlanId = plan?.planId,
            actualPlanContentRevisionId = plan?.contentRevisionId,
            actualIntervention = plan?.let { InterventionFamily.MomentPlan },
        ),
        startedAtMillis = at.takeIf { started },
        completedAtMillis = at.plus(1).takeIf { completed },
        dismissedAtMillis = at.plus(1).takeIf { dismissed },
        feedbackCode = feedback,
        repeatObservation = repeat,
        observationDeadlineAtMillis = at + 20 * 60_000L,
        observationFinalisedAtMillis = (at + 20 * 60_000L).takeIf { finalised },
    )

    private fun revised(plan: MomentPlan): MomentPlan =
        plan.copy(
            contentRevisionId = UUID.nameUUIDFromBytes(
                listOf(
                    plan.momentCue,
                    plan.futureCueText,
                    plan.actionText,
                    plan.actionType,
                    plan.actionTarget,
                ).joinToString("|").toByteArray(),
            ).toString(),
        )

    private data class Harness(
        val cycles: FakeCycleRepository,
        val decisions: FakeDecisionRepository,
        val plans: FakeMomentPlanRepository,
        val plan: MomentPlan,
        val scheduler: FakeScheduler,
        val clock: MutableClock,
        val coordinator: PathShiftCoordinator,
        val finaliser: PathShiftReviewFinaliser,
        val recovery: PathShiftRecoveryCoordinator,
    )
}

private class MutableClock(var value: Long) :
    com.impulsive.app.backend.session.adaptive.AdaptiveClock {
    override fun nowMillis(): Long = value
}

private class FakeScheduler : PathShiftWorkScheduler {
    var scheduleCalls = 0
    var cancelCalls = 0
    override fun schedule(cycleId: String, finaliseAtMillis: Long): Boolean {
        scheduleCalls++
        return true
    }

    override fun cancel(cycleId: String): Boolean {
        cancelCalls++
        return true
    }

    override fun cancelAll(): Boolean = true
}

private class FakeCycleRepository : PathShiftCycleRepository {
    val values = linkedMapOf<String, PathShiftCycle>()
    private val activeFlow = MutableStateFlow<PathShiftCycle?>(null)

    override suspend fun insertOnce(cycle: PathShiftCycle): Boolean {
        if (values.containsKey(cycle.cycleId) || values.values.any {
                it.status == PathShiftCycleStatus.Active
            }
        ) {
            return false
        }
        values[cycle.cycleId] = cycle
        activeFlow.value = cycle
        return true
    }

    override suspend fun getById(cycleId: String): PathShiftCycle? = values[cycleId]
    override fun observeActive(): Flow<PathShiftCycle?> = activeFlow
    override suspend fun getActive(): PathShiftCycle? =
        values.values.firstOrNull { it.status == PathShiftCycleStatus.Active }

    override fun observeLatestFinalised(limit: Int): Flow<List<PathShiftCycle>> =
        flowOf(
            values.values.filter { it.status == PathShiftCycleStatus.Finalised }
                .takeLast(limit),
        )

    override suspend fun attachPreparedPlan(
        cycleId: String,
        planId: String,
        contentRevisionId: String,
        preparedAtMillis: Long,
    ): Boolean = update(cycleId) {
        it.copy(
            preparedPlanId = planId,
            preparedPlanContentRevisionId = contentRevisionId,
            preparedAtMillis = preparedAtMillis,
        )
    }

    override suspend fun clearPreparedPlan(cycleId: String): Boolean = update(cycleId) {
        it.copy(
            preparedPlanId = null,
            preparedPlanContentRevisionId = null,
            preparedAtMillis = null,
        )
    }

    override suspend fun finaliseOnce(
        cycleId: String,
        finalisedAtMillis: Long,
        counts: PathShiftReviewCounts,
    ): Boolean {
        val current = values[cycleId] ?: return false
        if (
            current.status != PathShiftCycleStatus.Active ||
            finalisedAtMillis < current.forecastWindowEndsAtMillis
        ) return false
        return update(cycleId) {
        it.copy(
            reviewFinalisedAtMillis = finalisedAtMillis,
            observedProtectedMomentCount = counts.observedProtectedMomentCount,
            preparedPlanSelectedCount = counts.preparedPlanSelectedCount,
            preparedPlanStartedCount = counts.preparedPlanStartedCount,
            preparedPlanCompletedCount = counts.preparedPlanCompletedCount,
            preparedPlanDismissedCount = counts.preparedPlanDismissedCount,
            wrongTimingCount = counts.wrongTimingCount,
            repeatDetectedCount = counts.repeatDetectedCount,
            status = PathShiftCycleStatus.Finalised,
        )
        }
    }

    override suspend fun cancelOnce(cycleId: String, cancelledAtMillis: Long): Boolean {
        val current = values[cycleId] ?: return false
        if (current.status != PathShiftCycleStatus.Active) return false
        return update(cycleId) {
            it.copy(
                status = PathShiftCycleStatus.Cancelled,
                cancelledAtMillis = cancelledAtMillis,
            )
        }
    }

    override suspend fun deleteExpiredFinalised(cutoffMillis: Long, limit: Int): Int {
        val ids = values.values.filter {
            it.status == PathShiftCycleStatus.Finalised &&
                (it.reviewFinalisedAtMillis ?: Long.MAX_VALUE) < cutoffMillis
        }.take(limit).map { it.cycleId }
        ids.forEach(values::remove)
        return ids.size
    }

    override suspend fun clearAll() {
        values.clear()
        activeFlow.value = null
    }

    private fun update(
        id: String,
        transform: (PathShiftCycle) -> PathShiftCycle,
    ): Boolean {
        val current = values[id] ?: return false
        val updated = transform(current)
        values[id] = updated
        activeFlow.value = updated.takeIf { it.status == PathShiftCycleStatus.Active }
        return true
    }
}

private class FakeMomentPlanRepository(
    var current: MomentPlan,
) : MomentPlanRepository {
    override suspend fun create(plan: MomentPlan): MomentPlanSaveResult = MomentPlanSaveResult.Applied
    override suspend fun update(plan: MomentPlan): MomentPlanSaveResult {
        current = plan
        return MomentPlanSaveResult.Applied
    }
    override suspend fun delete(planId: String): MomentPlanSaveResult =
        MomentPlanSaveResult.Applied
    override suspend fun getById(planId: String): MomentPlan? =
        current.takeIf { it.planId == planId }
    override fun observeAll(): Flow<List<MomentPlan>> = flowOf(listOf(current))
    override fun observeEnabled(): Flow<List<MomentPlan>> =
        flowOf(listOf(current).filter { it.enabled })
    override suspend fun getMatchingEnabledByCue(
        cue: com.impulsive.app.backend.domain.model.adaptive.MomentCue,
    ): List<MomentPlan> = listOf(current).filter { it.enabled && it.momentCue == cue }
    override suspend fun setPreferred(
        planId: String,
        updatedAtMillis: Long,
    ): MomentPlanSaveResult = MomentPlanSaveResult.Applied
}

private class FakeDecisionRepository(
    val items: MutableList<AdaptiveDecision>,
) : AdaptiveDecisionRepository {
    override suspend fun insertOnce(decision: AdaptiveDecision): Boolean = items.add(decision)
    override suspend fun getById(decisionId: String): AdaptiveDecision? =
        items.firstOrNull { it.decisionId == decisionId }
    override suspend fun getByIncidentToken(incidentToken: String): AdaptiveDecision? =
        items.firstOrNull { it.protectionIncidentToken == incidentToken }
    override suspend fun recordActualChoiceOnce(
        decisionId: String,
        intervention: InterventionFamily,
        momentPlanId: String?,
        momentPlanUpdatedAtMillis: Long?,
        userOverrodeSuggestion: Boolean,
    ): Boolean = false
    override suspend fun replacePendingActualChoice(
        decisionId: String,
        intervention: InterventionFamily,
        momentPlanId: String?,
        momentPlanUpdatedAtMillis: Long?,
        userOverrodeSuggestion: Boolean,
    ): Boolean = false
    override suspend fun recordMomentContextOnce(
        decisionId: String,
        cue: com.impulsive.app.backend.domain.model.adaptive.MomentCue?,
        urgeRating: Int?,
    ): Boolean = false
    override suspend fun addEligibleInterventions(
        decisionId: String,
        interventions: Set<InterventionFamily>,
    ): Boolean = false
    override suspend fun markPresentedOnce(decisionId: String, presentedAtMillis: Long) = false
    override suspend fun markStartedOnce(decisionId: String, startedAtMillis: Long) = false
    override suspend fun markCompletedOnce(decisionId: String, completedAtMillis: Long) = false
    override suspend fun markDismissedOnce(decisionId: String, dismissedAtMillis: Long) = false
    override suspend fun updateFeedback(
        decisionId: String,
        feedbackCode: FeedbackCode,
        feedbackUpdatedAtMillis: Long,
    ) = false
    override suspend fun markFirstRepeatOnce(decisionId: String, firstRepeatAtMillis: Long) = false
    override suspend fun finaliseOnce(decisionId: String, finalisedAtMillis: Long) = false
    override suspend fun getLatestInsideMomentWindow(
        windowStartedAtMillis: Long,
        nowMillis: Long,
    ): AdaptiveDecision? = null
    override suspend fun getLatestOpenInsideMomentWindow(
        windowStartedAtMillis: Long,
        nowMillis: Long,
    ): AdaptiveDecision? = null
    override suspend fun getOpenObservationDeadlines(
        nowMillis: Long,
        limit: Int,
    ): List<AdaptiveDecision> = emptyList()
    override suspend fun getFutureOpenObservationDeadlines(
        nowMillis: Long,
        limit: Int,
    ): List<AdaptiveDecision> = emptyList()
    override suspend fun getBetween(
        startedAtMillis: Long,
        endedAtMillis: Long,
    ): List<AdaptiveDecision> = items.filter {
        it.createdAtMillis >= startedAtMillis && it.createdAtMillis < endedAtMillis
    }
    override suspend fun getMomentPlanUsesSince(sinceMillis: Long): List<MomentPlanUseRecord> =
        emptyList()
    override fun observeRecentDecisions(limit: Int): Flow<List<AdaptiveDecision>> =
        flowOf(items.takeLast(limit))
    override suspend fun getRecentFinalised(
        limit: Int,
    ): List<com.impulsive.app.backend.domain.model.adaptive.AdaptiveOutcomeRecord> = emptyList()
    override suspend fun getFinalisedByActualIntervention(
        intervention: InterventionFamily,
        limit: Int,
    ): List<com.impulsive.app.backend.domain.model.adaptive.AdaptiveOutcomeRecord> = emptyList()
    override suspend fun getFinalisedByCue(
        cue: com.impulsive.app.backend.domain.model.adaptive.MomentCue,
        limit: Int,
    ): List<com.impulsive.app.backend.domain.model.adaptive.AdaptiveOutcomeRecord> = emptyList()
    override suspend fun clearLearningHistory() = items.clear()
}
