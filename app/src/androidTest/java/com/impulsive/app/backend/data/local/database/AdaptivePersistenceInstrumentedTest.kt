package com.impulsive.app.backend.data.local.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.impulsive.app.backend.data.local.dao.MomentPlanMutationResult
import com.impulsive.app.backend.data.local.entity.AdaptiveDecisionEntity
import com.impulsive.app.backend.data.local.entity.AdaptivePreferenceEntity
import com.impulsive.app.backend.data.local.entity.MomentPlanEntity
import com.impulsive.app.backend.data.repository.adaptive.RoomAdaptiveDataRepository
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveReasonCode
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSourceKind
import com.impulsive.app.backend.domain.model.adaptive.AssignmentMode
import com.impulsive.app.backend.domain.model.adaptive.FeedbackCode
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentCue
import com.impulsive.app.backend.domain.model.adaptive.MomentIntensity
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanActionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
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
class AdaptivePersistenceInstrumentedTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun decisionInsertAndLifecycleTransitionsAreIdempotent() = runBlocking {
        val dao = database.adaptiveDecisionDao()
        val decision = decision(id = 1)

        assertTrue(dao.insertOnce(decision) != -1L)
        assertEquals(-1L, dao.insertOnce(decision))
        assertEquals(1, dao.count())

        assertEquals(1, dao.markPresentedOnce(decision.decisionId, 1_100L))
        assertEquals(0, dao.markPresentedOnce(decision.decisionId, 1_101L))
        assertEquals(1, dao.markStartedOnce(decision.decisionId, 1_200L))
        assertEquals(0, dao.markStartedOnce(decision.decisionId, 1_201L))
        assertEquals(1, dao.markCompletedOnce(decision.decisionId, 1_300L))
        assertEquals(0, dao.markCompletedOnce(decision.decisionId, 1_301L))
        assertEquals(0, dao.markDismissedOnce(decision.decisionId, 1_400L))

        val stored = dao.getById(decision.decisionId)
        assertEquals(1_100L, stored?.presentedAtMillis)
        assertEquals(1_200L, stored?.startedAtMillis)
        assertEquals(1_300L, stored?.completedAtMillis)
        assertNull(stored?.dismissedAtMillis)
    }

    @Test
    fun decisionEvidencePassportRoundTripsThroughRoom() = runBlocking {
        val expected = decision(id = 91).copy(
            recommendationPolicyVersion = 7,
            assignedProtocolId = "pivot_game",
            assignedProtocolVersion = 1,
            actualProtocolId = "reset_reading",
            actualProtocolVersion = 1,
            eligibleMomentPlanCount = 3,
        )

        assertTrue(database.adaptiveDecisionDao().insertOnce(expected) != -1L)
        val restored = database.adaptiveDecisionDao().getById(expected.decisionId)

        assertEquals(7, restored?.recommendationPolicyVersion)
        assertEquals("pivot_game", restored?.assignedProtocolId)
        assertEquals(1, restored?.assignedProtocolVersion)
        assertEquals("reset_reading", restored?.actualProtocolId)
        assertEquals(1, restored?.actualProtocolVersion)
        assertEquals(3, restored?.eligibleMomentPlanCount)
    }

    @Test
    fun dismissalWinsOnceAndPreventsCompletion() = runBlocking {
        val dao = database.adaptiveDecisionDao()
        val decision = decision(id = 2)
        dao.insertOnce(decision)
        dao.markPresentedOnce(decision.decisionId, 1_100L)

        assertEquals(1, dao.markDismissedOnce(decision.decisionId, 1_200L))
        assertEquals(0, dao.markDismissedOnce(decision.decisionId, 1_201L))
        assertEquals(0, dao.markStartedOnce(decision.decisionId, 1_250L))
        assertEquals(0, dao.markCompletedOnce(decision.decisionId, 1_300L))

        val stored = dao.getById(decision.decisionId)
        assertEquals(1_200L, stored?.dismissedAtMillis)
        assertNull(stored?.completedAtMillis)
    }

    @Test
    fun feedbackRepeatAndFinalisationUpdateOneDecisionIdempotently() = runBlocking {
        val dao = database.adaptiveDecisionDao()
        val decision = decision(id = 3)
        dao.insertOnce(decision)

        assertEquals(
            1,
            dao.updateFeedback(
                decisionId = decision.decisionId,
                feedbackCode = FeedbackCode.Helped.name,
                feedbackUpdatedAtMillis = 1_500L,
            ),
        )
        assertEquals(1, dao.count())
        assertEquals(1, dao.markFirstRepeatOnce(decision.decisionId, 1_600L))
        assertEquals(0, dao.markFirstRepeatOnce(decision.decisionId, 1_700L))
        assertEquals(1, dao.finaliseOnce(decision.decisionId, 2_300L))
        assertEquals(0, dao.finaliseOnce(decision.decisionId, 2_400L))

        val stored = dao.getById(decision.decisionId)
        assertEquals(FeedbackCode.Helped.name, stored?.feedbackCode)
        assertEquals(1_500L, stored?.feedbackUpdatedAtMillis)
        assertEquals(true, stored?.repeatDetectedWithin20Minutes)
        assertEquals(1_600L, stored?.firstRepeatAtMillis)
        assertEquals(2_300L, stored?.observationFinalisedAtMillis)
    }

    @Test
    fun momentWindowDeadlineAndFinalisedQueriesReturnExpectedRows() = runBlocking {
        val dao = database.adaptiveDecisionDao()
        dao.insertOnce(decision(id = 10, createdAt = 1_000L, deadline = 2_000L))
        dao.insertOnce(decision(id = 11, createdAt = 1_500L, deadline = 2_500L))
        dao.insertOnce(
            decision(
                id = 12,
                createdAt = 2_000L,
                deadline = 3_000L,
                finalisedAt = 3_100L,
                cue = MomentCue.Stress,
                actualIntervention = InterventionFamily.PivotReading,
            ),
        )

        assertEquals(
            11.asUuid(),
            dao.getLatestInsideMomentWindow(
                windowStartedAtMillis = 900L,
                nowMillis = 1_900L,
            )?.decisionId,
        )
        assertEquals(
            listOf(10.asUuid()),
            dao.getOpenObservationDeadlines(
                nowMillis = 2_100L,
                limit = 10,
            ).map { it.decisionId },
        )
        assertEquals(
            listOf(12.asUuid()),
            dao.getRecentFinalised(30).map { it.decisionId },
        )
        assertEquals(
            listOf(12.asUuid()),
            dao.getFinalisedByActualIntervention(
                actualIntervention = InterventionFamily.PivotReading.name,
                limit = 30,
            ).map { it.decisionId },
        )
        assertEquals(
            listOf(12.asUuid()),
            dao.getFinalisedByCue(
                momentCue = MomentCue.Stress.name,
                limit = 30,
            ).map { it.decisionId },
        )
    }

    @Test
    fun preferredPlanAndSixEnabledPlanRulesAreTransactional() = runBlocking {
        val dao = database.momentPlanDao()
        val first = plan(id = 1, preferred = true)
        val second = plan(id = 2, preferred = true)

        assertEquals(MomentPlanMutationResult.Applied, dao.create(first))
        assertEquals(MomentPlanMutationResult.Applied, dao.create(second))
        assertFalse(dao.getById(first.planId)?.preferredForCue ?: true)
        assertTrue(dao.getById(second.planId)?.preferredForCue ?: false)

        (3..6).forEach { id ->
            assertEquals(
                MomentPlanMutationResult.Applied,
                dao.create(plan(id = id, cue = MomentCue.entries[id % MomentCue.entries.size])),
            )
        }
        assertEquals(6, dao.countEnabled())
        assertEquals(
            MomentPlanMutationResult.EnabledPlanLimitReached,
            dao.create(plan(id = 7, cue = MomentCue.Tiredness)),
        )
        assertNull(dao.getById(7.asUuid()))

        val disabled = plan(id = 8, enabled = false, preferred = false)
        assertEquals(MomentPlanMutationResult.Applied, dao.create(disabled))
        assertEquals(
            MomentPlanMutationResult.EnabledPlanLimitReached,
            dao.update(disabled.copy(enabled = true)),
        )
    }

    @Test
    fun deletingPlanKeepsHistoricalDecisionAndLearningClearKeepsPlans() = runBlocking {
        val decisionDao = database.adaptiveDecisionDao()
        val planDao = database.momentPlanDao()
        val preferenceDao = database.adaptivePreferenceDao()
        val plan = plan(id = 20)
        planDao.create(plan)
        preferenceDao.insertDefaults(updatedAtMillis = 0L)
        decisionDao.insertOnce(
            decision(id = 20).copy(
                assignedSuggestion = InterventionFamily.MomentPlan.name,
                actualIntervention = InterventionFamily.MomentPlan.name,
                momentPlanId = plan.planId,
            ),
        )

        assertEquals(MomentPlanMutationResult.Applied, planDao.delete(plan.planId))
        assertNotNull(decisionDao.getById(20.asUuid()))

        planDao.create(plan(id = 21))
        decisionDao.clearLearningHistory()
        assertEquals(0, decisionDao.count())
        assertEquals(1, planDao.count())
        assertNotNull(preferenceDao.get())

        decisionDao.insertOnce(decision(id = 22))
        RoomAdaptiveDataRepository(database).clearAllAdaptiveData()
        assertEquals(0, decisionDao.count())
        assertEquals(0, planDao.count())
        assertNull(preferenceDao.get())
    }

    @Test
    fun adaptivePreferenceDefaultsObserveUpdateAndReset() = runBlocking {
        val dao = database.adaptivePreferenceDao()
        assertNull(dao.get())

        dao.insertDefaults(updatedAtMillis = 0L)
        val defaults = dao.observe().first()
        assertEquals(1, defaults?.id)
        assertTrue(defaults?.personalSuggestionsEnabled ?: false)
        assertTrue(defaults?.gameSuggestionsEnabled ?: false)
        assertTrue(defaults?.readingSuggestionsEnabled ?: false)
        assertTrue(defaults?.momentPlanSuggestionsEnabled ?: false)
        assertTrue(defaults?.randomisedExplorationEnabled ?: false)
        assertTrue(defaults?.privateScreenProtectionEnabled ?: false)

        dao.update(
            AdaptivePreferenceEntity(
                personalSuggestionsEnabled = false,
                gameSuggestionsEnabled = false,
                readingSuggestionsEnabled = false,
                momentPlanSuggestionsEnabled = false,
             randomisedExplorationEnabled = false,
             privateScreenProtectionEnabled = false,
             updatedAtMillis = 5_000L,
            ),
        )
        assertFalse(dao.get()?.personalSuggestionsEnabled ?: true)
        assertFalse(dao.get()?.privateScreenProtectionEnabled ?: true)

        dao.resetDefaults(updatedAtMillis = 6_000L)
        val reset = dao.get()
        assertTrue(reset?.personalSuggestionsEnabled ?: false)
        assertTrue(reset?.randomisedExplorationEnabled ?: false)
        assertTrue(reset?.privateScreenProtectionEnabled ?: false)
        assertEquals(6_000L, reset?.updatedAtMillis)
    }

    @Test
    fun sqlCipherBackedRoomDatabaseOpensAtSchemaEleven() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "adaptive-sqlcipher-${System.nanoTime()}.db"
        SqlCipherDatabaseMigrator.ensureSqlCipherLoaded()
        val encrypted = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            databaseName,
        )
            .openHelperFactory(
                SupportOpenHelperFactory(
                    ByteArray(32) { index -> (index + 1).toByte() },
                ),
            )
            .build()

        try {
            encrypted.adaptivePreferenceDao().insertDefaults(
                updatedAtMillis = 1_000L,
            )
            assertEquals(1, encrypted.adaptivePreferenceDao().get()?.id)
            encrypted.openHelper.readableDatabase.query("PRAGMA user_version").use { cursor ->
                cursor.moveToFirst()
            assertEquals(11, cursor.getInt(0))
            }
        } finally {
            encrypted.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun decision(
        id: Int,
        createdAt: Long = 1_000L,
        deadline: Long = 2_200L,
        finalisedAt: Long? = null,
        cue: MomentCue = MomentCue.Boredom,
        actualIntervention: InterventionFamily = InterventionFamily.PivotGame,
    ): AdaptiveDecisionEntity = AdaptiveDecisionEntity(
        decisionId = id.asUuid(),
        protectionIncidentToken = "incident-$id",
        sourceKind = AdaptiveSourceKind.App.name,
        createdAtMillis = createdAt,
        momentWindowStartedAtMillis = createdAt,
        momentIntensity = MomentIntensity.RepeatedAttempt.name,
        momentCue = cue.name,
        baselineUrgeRating = 5,
        assignmentMode = AssignmentMode.AdaptiveSuggestion.name,
        eligibleInterventionsMask =
            InterventionFamily.PivotGame.eligibilityBit or
                InterventionFamily.PivotReading.eligibilityBit,
        assignedSuggestion = InterventionFamily.PivotGame.name,
        actualIntervention = actualIntervention.name,
        selectionProbability = null,
        reasonCode = AdaptiveReasonCode.InsufficientEvidenceExploration.name,
        momentPlanId = null,
        momentPlanUpdatedAtMillis = null,
        userOverrodeSuggestion = false,
        presentedAtMillis = null,
        startedAtMillis = null,
        completedAtMillis = null,
        dismissedAtMillis = null,
        feedbackCode = FeedbackCode.NotProvided.name,
        feedbackUpdatedAtMillis = null,
        repeatDetectedWithin20Minutes = finalisedAt?.let { false },
        firstRepeatAtMillis = null,
        observationDeadlineAtMillis = deadline,
        observationFinalisedAtMillis = finalisedAt,
    )

    private fun plan(
        id: Int,
        cue: MomentCue = MomentCue.Boredom,
        enabled: Boolean = true,
        preferred: Boolean = false,
    ): MomentPlanEntity = MomentPlanEntity(
        planId = id.asUuid(),
        title = "Plan $id",
        momentCue = cue.name,
        actionText = "Open my project for two minutes.",
        futureCueText = "Tomorrow morning, I want to feel clear.",
        actionType = MomentPlanActionType.TextOnly.name,
        actionTarget = null,
        enabled = enabled,
        preferredForCue = preferred,
        createdAtMillis = 1_000L + id,
        updatedAtMillis = 2_000L + id,
        rehearsedAtMillis = null,
    )

    private fun Int.asUuid(): String =
        "00000000-0000-0000-0000-${toString().padStart(12, '0')}"
}
