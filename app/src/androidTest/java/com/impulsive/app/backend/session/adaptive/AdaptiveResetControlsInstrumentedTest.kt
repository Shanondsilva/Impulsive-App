package com.impulsive.app.backend.session.adaptive

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.impulsive.app.backend.data.local.database.AppDatabase
import com.impulsive.app.backend.data.repository.adaptive.RoomAdaptiveDataRepository
import com.impulsive.app.backend.data.repository.adaptive.RoomAdaptiveDecisionRepository
import com.impulsive.app.backend.data.repository.adaptive.RoomMomentPlanRehearsalRepository
import com.impulsive.app.backend.data.repository.adaptive.RoomMomentPlanRepository
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveAssignment
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveDecision
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveReasonCode
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSourceKind
import com.impulsive.app.backend.domain.model.adaptive.AssignmentMode
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentIntensity
import com.impulsive.app.backend.domain.model.adaptive.MomentPlan
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanActionType
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanRehearsal
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanRehearsalMode
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdaptiveResetControlsInstrumentedTest {
    private lateinit var database: AppDatabase
    private lateinit var decisions: RoomAdaptiveDecisionRepository
    private lateinit var plans: RoomMomentPlanRepository
    private lateinit var rehearsals: RoomMomentPlanRehearsalRepository
    private lateinit var scheduler: RecordingScheduler

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).build()
        decisions = RoomAdaptiveDecisionRepository(database.adaptiveDecisionDao())
        plans = RoomMomentPlanRepository(database.momentPlanDao())
        rehearsals = RoomMomentPlanRehearsalRepository(
            database.momentPlanRehearsalDao(),
        )
        scheduler = RecordingScheduler()
        seed()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun resetLearningClearsHistoryButPreservesPlansAndPreferences() = runBlocking {
        val result = coordinator().resetPersonalLearning()

        assertEquals(AdaptiveLifecycleResult.Applied, result)
        assertEquals(0, database.adaptiveDecisionDao().count())
        assertNull(database.momentPlanRehearsalDao().getOpenRehearsal())
        assertEquals(0, database.momentPlanRehearsalDao().getRecentCompleted(10).size)
        assertNotNull(plans.getById(PlanId))
        assertNotNull(database.adaptivePreferenceDao().get())
        assertEquals(1, unrelatedRecoveryCount())
        assertEquals(1, scheduler.cancelCalls)
    }

    @Test
    fun completeClearRemovesAllMomentDataOnly() = runBlocking {
        val result = coordinator().clearAllAdaptiveData()

        assertEquals(AdaptiveLifecycleResult.Applied, result)
        assertEquals(0, database.adaptiveDecisionDao().count())
        assertEquals(0, database.momentPlanDao().count())
        assertEquals(0, database.momentPlanRehearsalDao().getRecentCompleted(10).size)
        assertNull(database.adaptivePreferenceDao().get())
        assertEquals(1, unrelatedRecoveryCount())
        assertEquals(1, scheduler.cancelCalls)
    }

    @Test
    fun completeLocalDeletionLeavesNoSchemaTenAdaptiveRecord() = runBlocking {
        database.clearAllTables()

        assertEquals(0, database.adaptiveDecisionDao().count())
        assertEquals(0, database.momentPlanDao().count())
        assertTrue(database.momentPlanRehearsalDao().getAllForBackup().isEmpty())
        assertNull(database.adaptivePreferenceDao().get())
    }

    private fun coordinator() = AdaptiveResetCoordinator(
        decisions = decisions,
        allAdaptiveData = RoomAdaptiveDataRepository(database),
        scheduler = scheduler,
        logger = AdaptiveSafeLogger { _, _ -> },
    )

    private suspend fun seed() {
        database.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO recovery_sessions (
                startedAt,
                completedAt,
                durationSeconds,
                urgeBefore,
                urgeAfter,
                helped,
                triggerSource,
                recoveryType
            ) VALUES (10, 20, 10, 5, 3, 1, 'unrelated', 'breathing')
            """.trimIndent(),
        )
        database.adaptivePreferenceDao().insertDefaults(1_000L)
        plans.create(
            MomentPlan(
                planId = PlanId,
                title = "Clear morning",
                momentCue = null,
                actionText = "Take a short walk",
                futureCueText = "Feel clear tomorrow",
                actionType = MomentPlanActionType.TextOnly,
                actionTarget = null,
                enabled = true,
                preferredForCue = false,
                createdAtMillis = 1_000L,
                updatedAtMillis = 1_000L,
            ),
        )
        rehearsals.insertOnce(
            MomentPlanRehearsal(
                rehearsalId = UUID.randomUUID().toString(),
                planId = PlanId,
                planUpdatedAtMillisAtStart = 1_000L,
                mode = MomentPlanRehearsalMode.Guided,
                startedAtMillis = 2_000L,
                completedAtMillis = 2_100L,
            ),
        )
        val id = UUID.randomUUID().toString()
        decisions.insertOnce(
            AdaptiveDecision(
                decisionId = id,
                protectionIncidentToken = "opaque-$id",
                sourceKind = AdaptiveSourceKind.App,
                createdAtMillis = 3_000L,
                momentWindowStartedAtMillis = 3_000L,
                momentCue = null,
                baselineUrgeRating = null,
                assignment = AdaptiveAssignment(
                    momentIntensity = MomentIntensity.RepeatedAttempt,
                    assignmentMode = AssignmentMode.AdaptiveSuggestion,
                    eligibleInterventions = setOf(InterventionFamily.PivotGame),
                    assignedSuggestion = InterventionFamily.PivotGame,
                    selectionProbability = null,
                    reasonCode = AdaptiveReasonCode.OnlyEligibleIntervention,
                    actualIntervention = InterventionFamily.PivotGame,
                ),
                observationDeadlineAtMillis = 4_000L,
            ),
        )
    }

    private fun unrelatedRecoveryCount(): Int =
        database.openHelper.writableDatabase
            .query("SELECT COUNT(*) FROM recovery_sessions")
            .use {
                it.moveToFirst()
                it.getInt(0)
            }

    private class RecordingScheduler : AdaptiveObservationScheduler {
        var cancelCalls = 0

        override fun schedule(
            decisionId: String,
            deadlineAtMillis: Long,
        ): Boolean = true

        override fun cancelAll(): Boolean {
            cancelCalls++
            return true
        }
    }

    private companion object {
        const val PlanId = "00000000-0000-0000-0000-000000000555"
    }
}
