package com.impulsive.app.backend.session.adaptive

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.impulsive.app.backend.data.local.database.AppDatabase
import com.impulsive.app.backend.data.local.entity.AdaptiveDecisionEntity
import com.impulsive.app.backend.data.local.entity.MomentPlanEntity
import com.impulsive.app.backend.data.local.entity.MomentPlanRehearsalEntity
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveReasonCode
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSourceKind
import com.impulsive.app.backend.domain.model.adaptive.AssignmentMode
import com.impulsive.app.backend.domain.model.adaptive.FeedbackCode
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentIntensity
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanActionType
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanRehearsalMode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdaptiveHistoryRetentionInstrumentedTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun transactionalPruneRemovesOnlyOldSafeAdaptiveHistory() = runBlocking {
        val decisions = database.adaptiveDecisionDao()
        val rehearsals = database.momentPlanRehearsalDao()
        val plan = plan()
        database.momentPlanDao().insertForRestore(plan)
        database.adaptivePreferenceDao().insertDefaults(1L)

        decisions.insertForRestore(decision(1, createdAt = 100L))
        decisions.insertForRestore(decision(2, createdAt = 1_100L))
        decisions.insertForRestore(
            decision(3, createdAt = 100L).copy(
                completedAtMillis = null,
                observationFinalisedAtMillis = null,
            ),
        )
        decisions.insertForRestore(
            decision(4, createdAt = 100L).copy(
                observationFinalisedAtMillis = null,
            ),
        )
        decisions.insertForRestore(decision(5, createdAt = 100L))

        rehearsals.insertForRestore(rehearsal(1, completedAt = 100L))
        rehearsals.insertForRestore(rehearsal(2, completedAt = 1_100L))
        rehearsals.insertForRestore(rehearsal(3, completedAt = null))

        val result = RoomAdaptiveRetentionStore(database).prune(
            cutoffMillis = 1_000L,
            protectedDecisionIds = setOf(uuid(5)),
            limit = 100,
        )

        assertEquals(listOf(uuid(1)), result.decisionIds)
        assertEquals(listOf(uuid(1)), result.rehearsalIds)
        assertNull(decisions.getById(uuid(1)))
        assertNotNull(decisions.getById(uuid(2)))
        assertNotNull(decisions.getById(uuid(3)))
        assertNotNull(decisions.getById(uuid(4)))
        assertNotNull(decisions.getById(uuid(5)))
        assertNull(rehearsals.getById(uuid(1)))
        assertNotNull(rehearsals.getById(uuid(2)))
        assertNotNull(rehearsals.getById(uuid(3)))
        assertNotNull(database.momentPlanDao().getById(plan.planId))
        assertNotNull(database.adaptivePreferenceDao().get())
    }

    @Test
    fun repeatedPruneIsIdempotent() = runBlocking {
        database.adaptiveDecisionDao().insertForRestore(decision(10, createdAt = 100L))
        val store = RoomAdaptiveRetentionStore(database)

        assertEquals(1, store.prune(1_000L, emptySet(), 100).decisionIds.size)
        assertEquals(0, store.prune(1_000L, emptySet(), 100).decisionIds.size)
    }

    private fun decision(id: Int, createdAt: Long) = AdaptiveDecisionEntity(
        decisionId = uuid(id),
        protectionIncidentToken = "retention-$id",
        sourceKind = AdaptiveSourceKind.App.name,
        createdAtMillis = createdAt,
        momentWindowStartedAtMillis = createdAt,
        momentIntensity = MomentIntensity.RepeatedAttempt.name,
        momentCue = null,
        baselineUrgeRating = null,
        assignmentMode = AssignmentMode.AdaptiveSuggestion.name,
        eligibleInterventionsMask = InterventionFamily.PivotGame.eligibilityBit,
        assignedSuggestion = InterventionFamily.PivotGame.name,
        actualIntervention = InterventionFamily.PivotGame.name,
        selectionProbability = null,
        reasonCode = AdaptiveReasonCode.StableFallback.name,
        momentPlanId = null,
        momentPlanUpdatedAtMillis = null,
        userOverrodeSuggestion = false,
        presentedAtMillis = createdAt + 1,
        startedAtMillis = createdAt + 2,
        completedAtMillis = createdAt + 3,
        dismissedAtMillis = null,
        feedbackCode = FeedbackCode.NotProvided.name,
        feedbackUpdatedAtMillis = null,
        repeatDetectedWithin20Minutes = false,
        firstRepeatAtMillis = null,
        observationDeadlineAtMillis = createdAt + 4,
        observationFinalisedAtMillis = createdAt + 5,
    )

    private fun rehearsal(id: Int, completedAt: Long?) =
        MomentPlanRehearsalEntity(
            rehearsalId = uuid(id),
            planId = PlanId,
            planUpdatedAtMillisAtStart = 100L,
            mode = MomentPlanRehearsalMode.Guided.name,
            startedAtMillis = 50L,
            completedAtMillis = completedAt,
            dismissedAtMillis = null,
            planContentRevisionId = RevisionId,
        )

    private fun plan() = MomentPlanEntity(
        planId = PlanId,
        title = "Plan",
        momentCue = null,
        actionText = "Pause",
        futureCueText = "Feel steady",
        actionType = MomentPlanActionType.TextOnly.name,
        actionTarget = null,
        enabled = true,
        preferredForCue = false,
        createdAtMillis = 1L,
        updatedAtMillis = 2L,
        rehearsedAtMillis = null,
        contentRevisionId = RevisionId,
    )

    private fun uuid(id: Int): String =
        "00000000-0000-0000-0000-${id.toString().padStart(12, '0')}"

    private companion object {
        const val PlanId = "00000000-0000-0000-0000-000000009601"
        const val RevisionId = "00000000-0000-0000-0000-000000009602"
    }
}
