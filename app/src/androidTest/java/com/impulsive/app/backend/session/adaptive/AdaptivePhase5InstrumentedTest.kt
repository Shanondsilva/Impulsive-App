package com.impulsive.app.backend.session.adaptive

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.impulsive.app.MainActivity
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
import com.impulsive.app.backend.domain.model.protection.BlockLaunchTarget
import com.impulsive.app.backend.domain.model.protection.BlockRequest
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdaptivePhase5InstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var decisions: RoomAdaptiveDecisionRepository
    private lateinit var plans: RoomMomentPlanRepository
    private lateinit var preferences: RoomAdaptivePreferenceRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        decisions = RoomAdaptiveDecisionRepository(database.adaptiveDecisionDao())
        plans = RoomMomentPlanRepository(database.momentPlanDao())
        preferences = RoomAdaptivePreferenceRepository(database.adaptivePreferenceDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun duplicateIncidentDeliveryReturnsOneEncryptedRoomDecision() = runBlocking {
        preferences.insertDefaults(1L)
        val bridge = AdaptiveProtectionBridge(coordinator())
        val signal = AdaptiveIncidentSignal(
            AdaptiveProtectionSource.MonitoredApplication,
            1_000L,
            "test.protected.source",
        )

        val first = bridge.recognise(signal)
        val duplicate = bridge.recognise(signal)

        assertEquals(first.decisionId, duplicate.decisionId)
        assertTrue(duplicate.duplicate)
        assertNotNull(decisions.getById(requireNotNull(first.decisionId)))
    }

    @Test
    fun laterIncidentGetsDifferentDecision() = runBlocking {
        preferences.insertDefaults(1L)
        val bridge = AdaptiveProtectionBridge(coordinator())
        val first = bridge.recognise(signal(1_000L))
        val later = bridge.recognise(signal(1_301_000L))
        assertNotEquals(first.decisionId, later.decisionId)
    }

    @Test
    fun actualChoiceAndMomentContextPersist() = runBlocking {
        val stored = decision()
        assertTrue(decisions.insertOnce(stored))
        assertTrue(
            decisions.recordMomentContextOnce(
                stored.decisionId,
                MomentCue.Stress,
                7,
            ),
        )
        assertTrue(
            decisions.recordActualChoiceOnce(
                stored.decisionId,
                InterventionFamily.PivotReading,
                null,
                null,
                userOverrodeSuggestion = true,
            ),
        )
        val reloaded = decisions.getById(stored.decisionId)
        assertEquals(MomentCue.Stress, reloaded?.momentCue)
        assertEquals(7, reloaded?.baselineUrgeRating)
        assertEquals(InterventionFamily.PivotReading, reloaded?.assignment?.actualIntervention)
        assertTrue(reloaded?.assignment?.userOverrodeSuggestion == true)
    }

    @Test
    fun recreatedRepositoryReloadsSameDecisionById() = runBlocking {
        val stored = decision()
        decisions.insertOnce(stored)
        val recreated = RoomAdaptiveDecisionRepository(database.adaptiveDecisionDao())
        assertEquals(stored, recreated.getById(stored.decisionId))
    }

    @Test
    fun deletedPlanCannotBeReloadedForDelivery() = runBlocking {
        val plan = plan()
        plans.create(plan)
        assertNotNull(plans.getById(plan.planId))
        plans.delete(plan.planId)
        assertNull(plans.getById(plan.planId))
    }

    @Test
    fun disabledPlanCannotBeNewlyStarted() = runBlocking {
        val plan = plan().copy(enabled = false)
        plans.create(plan)
        val lifecycle = lifecycle()
        val stored = decision(
            eligible = setOf(InterventionFamily.MomentPlan),
            assigned = InterventionFamily.MomentPlan,
            planId = plan.planId,
        )
        decisions.insertOnce(stored)
        assertEquals(
            AdaptiveLifecycleResult.InvalidMomentPlan,
            lifecycle.recordActualChoice(
                stored.decisionId,
                InterventionFamily.MomentPlan,
                plan.planId,
            ),
        )
    }

    @Test
    fun adaptivePendingIntentPayloadContainsOnlyInternalDecisionData() {
        val id = UUID.randomUUID().toString()
        val intent = MainActivity.createAdaptiveMomentIntent(context, id)
        val keys = requireNotNull(intent.extras).keySet()

        assertEquals(
            setOf(
                BlockRequest.ExtraLaunchTarget,
                BlockRequest.ExtraAdaptiveDecisionId,
            ),
            keys,
        )
        assertEquals(id, intent.getStringExtra(BlockRequest.ExtraAdaptiveDecisionId))
        assertEquals(
            BlockLaunchTarget.AdaptiveMoment.name,
            intent.getStringExtra(BlockRequest.ExtraLaunchTarget),
        )
    }

    @Test
    fun selectedInstalledApplicationMustStillHaveLauncherActivity() {
        val ownLaunch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val missingLaunch = context.packageManager.getLaunchIntentForPackage(
            "com.impulsive.test.missing",
        )
        assertNotNull(ownLaunch)
        assertNull(missingLaunch)
    }

    @Test
    fun tokenHasNoRawProtectedIdentity() {
        val raw = "com.private.protected"
        val token = AdaptiveIncidentTokenFactory.create(
            AdaptiveIncidentSignal(
                AdaptiveProtectionSource.VpnWebsite,
                1_000L,
                raw,
            ),
        )
        assertFalse(token.contains(raw))
        assertFalse(token.contains("private"))
    }

    @Test
    fun phaseFiveLifecycleDoesNotCompleteOrAddFeedback() = runBlocking {
        val stored = decision()
        decisions.insertOnce(stored)
        val lifecycle = lifecycle()
        lifecycle.recordActualChoice(
            stored.decisionId,
            InterventionFamily.PivotGame,
        )
        lifecycle.markPresented(stored.decisionId, 2_000L)
        lifecycle.markStarted(stored.decisionId, 3_000L)

        val reloaded = decisions.getById(stored.decisionId)
        assertNull(reloaded?.completedAtMillis)
        assertNull(reloaded?.feedbackUpdatedAtMillis)
    }

    private fun signal(at: Long) = AdaptiveIncidentSignal(
        AdaptiveProtectionSource.MonitoredApplication,
        at,
        "test.protected.source",
    )

    private fun coordinator() = AdaptiveMomentCoordinator(
        decisions = decisions,
        preferences = preferences,
        momentPlans = plans,
        recommendationPolicy = AdaptiveRecommendationPolicy(
            object : RandomisationSource {
                override fun nextDouble(): Double = 0.9
                override fun nextInt(bound: Int): Int = 0
            },
        ),
        clock = AdaptiveClock { 2_000_000L },
        idSource = AdaptiveIdSource { UUID.randomUUID().toString() },
        logger = AdaptiveSafeLogger { _, _ -> },
    )

    private fun lifecycle() = AdaptiveDecisionLifecycle(
        decisions = decisions,
        momentPlans = plans,
        scheduler = object : AdaptiveObservationScheduler {
            override fun schedule(decisionId: String, deadlineAtMillis: Long) = true
            override fun cancelAll() = true
        },
        clock = AdaptiveClock { 10_000L },
        logger = AdaptiveSafeLogger { _, _ -> },
    )

    private fun decision(
        eligible: Set<InterventionFamily> = setOf(
            InterventionFamily.PivotGame,
            InterventionFamily.PivotReading,
        ),
        assigned: InterventionFamily? = InterventionFamily.PivotGame,
        planId: String? = null,
    ) = AdaptiveDecision(
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
            eligibleInterventions = eligible,
            assignedSuggestion = assigned,
            selectionProbability = null,
            reasonCode = AdaptiveReasonCode.RecentCompletionPattern,
            momentPlanId = planId,
        ),
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
