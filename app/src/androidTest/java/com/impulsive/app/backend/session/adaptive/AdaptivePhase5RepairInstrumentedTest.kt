package com.impulsive.app.backend.session.adaptive

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.impulsive.app.backend.data.local.database.AppDatabase
import com.impulsive.app.backend.data.repository.adaptive.RoomAdaptiveDecisionRepository
import com.impulsive.app.backend.data.repository.adaptive.RoomAdaptivePreferenceRepository
import com.impulsive.app.backend.data.repository.adaptive.RoomMomentPlanRepository
import com.impulsive.app.backend.domain.engine.adaptive.AdaptiveRecommendationPolicy
import com.impulsive.app.backend.domain.engine.adaptive.RandomisationSource
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveAssignment
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveDecision
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveReasonCode
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSourceKind
import com.impulsive.app.backend.domain.model.adaptive.AssignmentMode
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentCue
import com.impulsive.app.backend.domain.model.adaptive.MomentIntensity
import com.impulsive.app.backend.domain.model.adaptive.MomentPlan
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanActionType
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdaptivePhase5RepairInstrumentedTest {
    private lateinit var database: AppDatabase
    private lateinit var decisions: RoomAdaptiveDecisionRepository
    private lateinit var plans: RoomMomentPlanRepository
    private lateinit var preferences: RoomAdaptivePreferenceRepository
    private val clock = AdaptiveClock { 10_000L }

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        decisions = RoomAdaptiveDecisionRepository(database.adaptiveDecisionDao())
        plans = RoomMomentPlanRepository(database.momentPlanDao())
        preferences = RoomAdaptivePreferenceRepository(database.adaptivePreferenceDao())
        preferences.insertDefaults(1L)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun pendingChoiceReplacementIsAtomicIdempotentAndPreservesAssignment() = runBlocking {
        val original = decision(started = false)
        assertTrue(decisions.insertOnce(original))
        val lifecycle = lifecycle()

        assertEquals(
            AdaptiveLifecycleResult.Applied,
            lifecycle.replacePendingActualChoice(
                original.decisionId,
                InterventionFamily.PivotReading,
            ),
        )
        assertEquals(
            AdaptiveLifecycleResult.Idempotent,
            lifecycle.replacePendingActualChoice(
                original.decisionId,
                InterventionFamily.PivotReading,
            ),
        )

        val replaced = requireNotNull(decisions.getById(original.decisionId))
        assertEquals(original.assignment.assignedSuggestion, replaced.assignment.assignedSuggestion)
        assertEquals(original.assignment.reasonCode, replaced.assignment.reasonCode)
        assertEquals(InterventionFamily.PivotReading, replaced.assignment.actualIntervention)
        assertTrue(replaced.assignment.userOverrodeSuggestion)
    }

    @Test
    fun startedChoiceCannotBeOverwrittenInRoom() = runBlocking {
        val original = decision(started = true)
        decisions.insertOnce(original)

        assertEquals(
            AdaptiveLifecycleResult.InvalidTransition,
            lifecycle().replacePendingActualChoice(
                original.decisionId,
                InterventionFamily.PivotReading,
            ),
        )
        assertEquals(original, decisions.getById(original.decisionId))
    }

    @Test
    fun pendingMomentPlanReplacementRequiresEnabledPlan() = runBlocking {
        val disabled = plan().copy(enabled = false)
        plans.create(disabled)
        val original = decision(started = false)
        decisions.insertOnce(original)

        assertEquals(
            AdaptiveLifecycleResult.InvalidMomentPlan,
            lifecycle().replacePendingActualChoice(
                original.decisionId,
                InterventionFamily.MomentPlan,
                disabled.planId,
            ),
        )
        assertEquals(
            InterventionFamily.PivotGame,
            decisions.getById(original.decisionId)?.assignment?.actualIntervention,
        )
    }

    @Test
    fun readingAfterStartedGameUsesNewDecisionAndKeepsOriginal() = runBlocking {
        val original = decision(started = true)
        decisions.insertOnce(original)

        val result = support().chooseAnother(
            AdaptiveFollowUpRequest(
                previousDecisionId = original.decisionId,
                intervention = InterventionFamily.PivotReading,
            ),
        ) as AdaptiveFollowUpResult.Ready

        val followUp = requireNotNull(decisions.getById(result.decisionId))
        assertEquals(original, decisions.getById(original.decisionId))
        assertNotEquals(original.decisionId, followUp.decisionId)
        assertNotEquals(original.protectionIncidentToken, followUp.protectionIncidentToken)
        assertEquals(AdaptiveSourceKind.ExplicitUserSupport, followUp.sourceKind)
        assertEquals(InterventionFamily.PivotReading, followUp.assignment.actualIntervention)
        assertEquals(AdaptiveRouteKind.Reading, result.routeRequest?.kind)
        assertNull(followUp.startedAtMillis)
    }

    @Test
    fun momentPlanAfterStartedGameUsesNewDecisionAndRoutes() = runBlocking {
        val plan = plan()
        plans.create(plan)
        val original = decision(started = true)
        decisions.insertOnce(original)

        val result = support().chooseAnother(
            AdaptiveFollowUpRequest(
                previousDecisionId = original.decisionId,
                intervention = InterventionFamily.MomentPlan,
                momentPlanId = plan.planId,
            ),
        ) as AdaptiveFollowUpResult.Ready

        val followUp = requireNotNull(decisions.getById(result.decisionId))
        assertEquals(original, decisions.getById(original.decisionId))
        assertEquals(InterventionFamily.MomentPlan, followUp.assignment.actualIntervention)
        assertEquals(plan.planId, followUp.assignment.momentPlanId)
        assertEquals(AdaptiveRouteKind.MomentPlan, result.routeRequest?.kind)
    }

    @Test
    fun followUpTokenContainsNoSourceIdentity() {
        val token = AdaptiveFollowUpIncidentTokenFactory.create(
            previousDecisionId = UUID.randomUUID().toString(),
            attemptIdentity = UUID.randomUUID().toString(),
        )
        listOf(
            "com.private.browser",
            "https://private.example/path",
            "private.example",
        ).forEach { privateValue ->
            assertFalse(token.contains(privateValue))
        }
        assertTrue(token.startsWith("afu1_"))
    }

    private fun support() = AdaptiveFollowUpSupport(
        coordinator = AdaptiveMomentCoordinator(
            decisions = decisions,
            preferences = preferences,
            momentPlans = plans,
            recommendationPolicy = AdaptiveRecommendationPolicy(
                object : RandomisationSource {
                    override fun nextDouble(): Double = 0.9
                    override fun nextInt(bound: Int): Int = 0
                },
            ),
            clock = clock,
            idSource = AdaptiveIdSource { UUID.randomUUID().toString() },
            logger = AdaptiveSafeLogger { _, _ -> },
        ),
        decisions = decisions,
        momentPlans = plans,
        lifecycle = lifecycle(),
        clock = clock,
        attemptIdSource = AdaptiveIdSource { UUID.randomUUID().toString() },
    )

    private fun lifecycle() = AdaptiveDecisionLifecycle(
        decisions = decisions,
        momentPlans = plans,
        scheduler = object : AdaptiveObservationScheduler {
            override fun schedule(decisionId: String, deadlineAtMillis: Long) = true
            override fun cancelAll() = true
        },
        clock = clock,
        logger = AdaptiveSafeLogger { _, _ -> },
    )

    private fun decision(started: Boolean) = AdaptiveDecision(
        decisionId = UUID.randomUUID().toString(),
        protectionIncidentToken = "ai1_${UUID.randomUUID()}",
        sourceKind = AdaptiveSourceKind.App,
        createdAtMillis = 1_000L,
        momentWindowStartedAtMillis = 1_000L,
        momentCue = null,
        baselineUrgeRating = null,
        assignment = AdaptiveAssignment(
            momentIntensity = MomentIntensity.RepeatedAttempt,
            assignmentMode = AssignmentMode.AdaptiveSuggestion,
            eligibleInterventions = setOf(
                InterventionFamily.PivotGame,
                InterventionFamily.PivotReading,
                InterventionFamily.MomentPlan,
            ),
            assignedSuggestion = InterventionFamily.PivotGame,
            selectionProbability = null,
            reasonCode = AdaptiveReasonCode.RecentCompletionPattern,
            actualIntervention = InterventionFamily.PivotGame,
        ),
        presentedAtMillis = if (started) 2_000L else null,
        startedAtMillis = if (started) 3_000L else null,
        observationDeadlineAtMillis = 1_201_000L,
    )

    private fun plan() = MomentPlan(
        planId = UUID.randomUUID().toString(),
        title = "Safe plan",
        momentCue = MomentCue.Stress,
        actionText = "Put the phone down and stretch",
        futureCueText = "I want to feel settled tomorrow",
        actionType = MomentPlanActionType.TextOnly,
        actionTarget = null,
        enabled = true,
        preferredForCue = false,
        createdAtMillis = 1L,
        updatedAtMillis = 1L,
    )
}
