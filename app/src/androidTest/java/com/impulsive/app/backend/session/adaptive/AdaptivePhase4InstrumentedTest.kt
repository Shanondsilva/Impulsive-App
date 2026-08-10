package com.impulsive.app.backend.session.adaptive

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.impulsive.app.backend.data.local.database.AppDatabase
import com.impulsive.app.backend.data.repository.adaptive.RoomAdaptiveDataRepository
import com.impulsive.app.backend.data.repository.adaptive.RoomAdaptiveDecisionRepository
import com.impulsive.app.backend.data.repository.adaptive.RoomAdaptivePreferenceRepository
import com.impulsive.app.backend.data.repository.adaptive.RoomMomentPlanRepository
import com.impulsive.app.backend.domain.engine.adaptive.AdaptiveRecommendationPolicy
import com.impulsive.app.backend.domain.engine.adaptive.InterventionProtocolRegistry
import com.impulsive.app.backend.domain.engine.adaptive.InterventionProtocolValidator
import com.impulsive.app.backend.domain.engine.adaptive.RandomisationSource
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveAssignment
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveDecision
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveReasonCode
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSourceKind
import com.impulsive.app.backend.domain.model.adaptive.AssignmentMode
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentIntensity
import com.impulsive.app.backend.domain.model.adaptive.MomentPlan
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanActionType
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdaptivePhase4InstrumentedTest {
    private lateinit var database: AppDatabase
    private lateinit var decisions: RoomAdaptiveDecisionRepository
    private lateinit var plans: RoomMomentPlanRepository
    private lateinit var preferences: RoomAdaptivePreferenceRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
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
    fun realRoomDecisionCreationAndDuplicateIncidentLookup() = runBlocking {
        val first = decision(token = "incident-one")
        val duplicate = decision(
            id = UUID.nameUUIDFromBytes("duplicate".toByteArray()).toString(),
            token = "incident-one",
        )
        assertTrue(decisions.insertOnce(first))
        assertFalse(decisions.insertOnce(duplicate))
        assertEquals(first.decisionId, decisions.getByIncidentToken("incident-one")?.decisionId)
    }

    @Test
    fun interventionProtocolRegistryIsCompleteAndValidOnDevice() {
        assertEquals(9, InterventionProtocolRegistry.contracts.size)
        assertTrue(
            InterventionProtocolValidator.validate(
                InterventionProtocolRegistry.contracts,
            ).isEmpty(),
        )
        assertTrue(InterventionProtocolRegistry.contracts.all { it.version.value == 1 })
    }

    @Test
    fun realRoomLifecycleTransitionsAndChoiceAreIdempotent() = runBlocking {
        val stored = decision()
        decisions.insertOnce(stored)
        val scheduler = TestScheduler()
        val lifecycle = AdaptiveDecisionLifecycle(
            decisions,
            plans,
            scheduler,
            AdaptiveClock { 10_000L },
            AdaptiveSafeLogger { _, _ -> },
        )
        assertEquals(
            AdaptiveLifecycleResult.Applied,
            lifecycle.recordActualChoice(stored.decisionId, InterventionFamily.PivotGame),
        )
        assertEquals(
            AdaptiveLifecycleResult.Idempotent,
            lifecycle.recordActualChoice(stored.decisionId, InterventionFamily.PivotGame),
        )
        assertEquals(AdaptiveLifecycleResult.Applied, lifecycle.markPresented(stored.decisionId, 2_000L))
        assertEquals(AdaptiveLifecycleResult.Applied, lifecycle.markStarted(stored.decisionId, 3_000L))
        assertEquals(AdaptiveLifecycleResult.Applied, lifecycle.markCompleted(stored.decisionId, 4_000L))
        assertEquals(
            AdaptiveLifecycleResult.Idempotent,
            lifecycle.markCompleted(stored.decisionId, 4_000L),
        )
        assertEquals(1, scheduler.scheduled.size)
    }

    @Test
    fun realRoomFirstRepeatTimestampIsPreserved() = runBlocking {
        val stored = decision()
        decisions.insertOnce(stored)
        assertTrue(decisions.markFirstRepeatOnce(stored.decisionId, 2_000L))
        assertFalse(decisions.markFirstRepeatOnce(stored.decisionId, 3_000L))
        val reloaded = decisions.getById(stored.decisionId)
        assertEquals(2_000L, reloaded?.firstRepeatAtMillis)
    }

    @Test
    fun workManagerWorkerFinalisesRealEncryptedDecision() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appDatabase = AppDatabase.getInstance(context)
        val realRepository = RoomAdaptiveDecisionRepository(appDatabase.adaptiveDecisionDao())
        realRepository.clearLearningHistory()
        val now = System.currentTimeMillis()
        val stored = decision(
            id = UUID.nameUUIDFromBytes("worker".toByteArray()).toString(),
            token = "worker-incident",
            created = now - 1_300_000L,
            deadline = now - 100_000L,
        )
        realRepository.insertOnce(stored)
        try {
            val input = Data.Builder()
                .putString(AdaptiveObservationFinalizerWorker.InputDecisionId, stored.decisionId)
                .build()
            val worker = TestListenableWorkerBuilder<AdaptiveObservationFinalizerWorker>(context)
                .setInputData(input)
                .build()
            assertEquals(ListenableWorker.Result.success(), worker.doWork())
            assertNotNull(realRepository.getById(stored.decisionId)?.observationFinalisedAtMillis)
        } finally {
            realRepository.clearLearningHistory()
        }
    }

    @Test
    fun resetLearningPreservesPlansAndPreferences() = runBlocking {
        val plan = plan()
        plans.create(plan)
        preferences.insertDefaults(1L)
        decisions.insertOnce(decision())
        val scheduler = TestScheduler()
        val reset = AdaptiveResetCoordinator(
            decisions,
            RoomAdaptiveDataRepository(database),
            scheduler,
            AdaptiveSafeLogger { _, _ -> },
        )
        assertEquals(AdaptiveLifecycleResult.Applied, reset.resetPersonalLearning())
        assertNull(decisions.getById(decision().decisionId))
        assertNotNull(plans.getById(plan.planId))
        assertTrue(preferences.get().personalSuggestionsEnabled)
        assertEquals(1, scheduler.cancelCalls)
    }

    @Test
    fun completeClearRemovesAllAdaptiveData() = runBlocking {
        val plan = plan()
        plans.create(plan)
        preferences.insertDefaults(1L)
        decisions.insertOnce(decision())
        val reset = AdaptiveResetCoordinator(
            decisions,
            RoomAdaptiveDataRepository(database),
            TestScheduler(),
            AdaptiveSafeLogger { _, _ -> },
        )
        assertEquals(AdaptiveLifecycleResult.Applied, reset.clearAllAdaptiveData())
        assertNull(decisions.getById(decision().decisionId))
        assertNull(plans.getById(plan.planId))
        assertEquals(
            com.impulsive.app.backend.domain.model.adaptive.AdaptivePreferences(),
            preferences.get(),
        )
    }

    @Test
    fun recreatedCoordinatorReturnsExistingIncidentDecision() = runBlocking {
        preferences.insertDefaults(1L)
        val request = AdaptiveProtectionIncidentRequest(
            incidentToken = "recreated-incident",
            sourceKind = AdaptiveSourceKind.App,
            detectedAtMillis = 1_000L,
            currentlyAllowedInterventions = emptySet(),
        )
        val first = coordinator().coordinate(request)
        val second = coordinator().coordinate(request)
        assertTrue(first.persisted)
        assertTrue(second.duplicateIncident)
        assertEquals(first.presentation.decisionId, second.presentation.decisionId)
    }

    @Test
    fun currentSchemaSqlCipherDatabaseOpensThroughProductionFactory() =
        runBlocking {
            val context =
                ApplicationProvider
                    .getApplicationContext<Context>()
            val production =
                AppDatabase.getInstance(
                    context,
                )

            production
                .openHelper
                .readableDatabase
                .query(
                    "PRAGMA user_version",
                )
                .use { cursor ->
                    assertTrue(
                        cursor.moveToFirst(),
                    )
                    assertEquals(
                        14,
                        cursor.getInt(0),
                    )
                }

            production
                .adaptivePreferenceDao()
                .insertDefaults(
                    1L,
                )

            assertNotNull(
                production
                    .adaptivePreferenceDao()
                    .get(),
            )
        }

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
        clock = AdaptiveClock { 10_000L },
        idSource = AdaptiveIdSource { UUID.randomUUID().toString() },
        logger = AdaptiveSafeLogger { _, _ -> },
    )

    private fun decision(
        id: String = UUID.nameUUIDFromBytes("decision".toByteArray()).toString(),
        token: String = "incident",
        created: Long = 1_000L,
        deadline: Long = 1_201_000L,
    ) = AdaptiveDecision(
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
            eligibleInterventions = setOf(InterventionFamily.PivotGame),
            assignedSuggestion = InterventionFamily.PivotGame,
            selectionProbability = null,
            reasonCode = AdaptiveReasonCode.InsufficientEvidenceExploration,
        ),
        observationDeadlineAtMillis = deadline,
    )

    private fun plan() = MomentPlan(
        planId = UUID.nameUUIDFromBytes("plan".toByteArray()).toString(),
        title = "Clear morning",
        momentCue = null,
        actionText = "Open my project.",
        futureCueText = "Tomorrow I want to feel clear.",
        actionType = MomentPlanActionType.TextOnly,
        actionTarget = null,
        enabled = true,
        preferredForCue = false,
        createdAtMillis = 1L,
        updatedAtMillis = 2L,
    )

    private class TestScheduler : AdaptiveObservationScheduler {
        val scheduled = linkedMapOf<String, Long>()
        var cancelCalls = 0

        override fun schedule(decisionId: String, deadlineAtMillis: Long): Boolean {
            scheduled.putIfAbsent(decisionId, deadlineAtMillis)
            return true
        }

        override fun cancelAll(): Boolean {
            cancelCalls++
            scheduled.clear()
            return true
        }
    }
}
