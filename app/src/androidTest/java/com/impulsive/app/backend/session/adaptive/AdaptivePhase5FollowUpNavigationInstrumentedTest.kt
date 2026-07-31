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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdaptivePhase5FollowUpNavigationInstrumentedTest {
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
    fun gameBackReadingBackMomentPlanUsesFreshFollowUpIds() = runBlocking {
        val plan = momentPlan()
        plans.create(plan)
        val original = unstartedDecision()
        decisions.insertOnce(original)
        val lifecycle = lifecycle()

        assertEquals(
            AdaptiveLifecycleResult.Applied,
            lifecycle.markPresented(original.decisionId, 2_000L),
        )
        assertEquals(
            AdaptiveLifecycleResult.Applied,
            lifecycle.recordActualChoice(
                original.decisionId,
                InterventionFamily.PivotGame,
            ),
        )
        assertEquals(
            AdaptiveLifecycleResult.Applied,
            lifecycle.markStarted(original.decisionId, 3_000L),
        )
        val startedOriginal = requireNotNull(decisions.getById(original.decisionId))

        val resumedChooser = requireNotNull(
            AdaptiveChooserRefresh(decisions, plans).load(original.decisionId),
        )
        assertEquals(3_000L, resumedChooser.decision.startedAtMillis)

        val reading = support(lifecycle).chooseAnother(
            AdaptiveFollowUpRequest(
                previousDecisionId = resumedChooser.decision.decisionId,
                intervention = InterventionFamily.PivotReading,
            ),
        ) as AdaptiveFollowUpResult.Ready
        assertNotEquals(original.decisionId, reading.decisionId)
        assertEquals(AdaptiveRouteKind.Reading, reading.routeRequest?.kind)
        assertEquals(reading.decisionId, reading.routeRequest?.decisionId)

        // Android Back returns to the existing chooser, whose next resume reloads
        // the same original decision from Room before this second deliberate tap.
        val secondResume = requireNotNull(
            AdaptiveChooserRefresh(decisions, plans).load(original.decisionId),
        )
        val momentPlan = support(lifecycle).chooseAnother(
            AdaptiveFollowUpRequest(
                previousDecisionId = secondResume.decision.decisionId,
                intervention = InterventionFamily.MomentPlan,
                momentPlanId = plan.planId,
            ),
        ) as AdaptiveFollowUpResult.Ready

        assertNotEquals(original.decisionId, momentPlan.decisionId)
        assertNotEquals(reading.decisionId, momentPlan.decisionId)
        assertEquals(AdaptiveRouteKind.MomentPlan, momentPlan.routeRequest?.kind)
        assertEquals(momentPlan.decisionId, momentPlan.routeRequest?.decisionId)
        assertEquals(startedOriginal, decisions.getById(original.decisionId))
        assertEquals(
            InterventionFamily.PivotReading,
            decisions.getById(reading.decisionId)?.assignment?.actualIntervention,
        )
        assertEquals(
            InterventionFamily.MomentPlan,
            decisions.getById(momentPlan.decisionId)?.assignment?.actualIntervention,
        )
        assertTrue(reading.decisionId.isNotBlank())
        assertTrue(momentPlan.decisionId.isNotBlank())
    }

    private fun support(
        lifecycle: AdaptiveDecisionLifecycle,
    ) = AdaptiveFollowUpSupport(
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
        lifecycle = lifecycle,
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

    private fun unstartedDecision() = AdaptiveDecision(
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
        ),
        observationDeadlineAtMillis = 1_201_000L,
    )

    private fun momentPlan() = MomentPlan(
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
