package com.impulsive.app.backend.session.adaptive

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.impulsive.app.backend.data.local.database.AppDatabase
import com.impulsive.app.backend.data.repository.adaptive.RoomAdaptiveDecisionRepository
import com.impulsive.app.backend.data.repository.adaptive.RoomMomentPlanRehearsalRepository
import com.impulsive.app.backend.domain.engine.adaptive.WhatWorksForMeBuilder
import com.impulsive.app.backend.domain.engine.adaptive.EvidenceQualityTier
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveAssignment
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveDecision
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveReasonCode
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSourceKind
import com.impulsive.app.backend.domain.model.adaptive.AssignmentMode
import com.impulsive.app.backend.domain.model.adaptive.FeedbackCode
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentIntensity
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanRehearsal
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanRehearsalMode
import com.impulsive.app.backend.domain.model.adaptive.RepeatObservation
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdaptiveDashboardInstrumentedTest {
    private lateinit var database: AppDatabase
    private lateinit var decisions: RoomAdaptiveDecisionRepository
    private lateinit var rehearsals: RoomMomentPlanRehearsalRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).build()
        decisions = RoomAdaptiveDecisionRepository(database.adaptiveDecisionDao())
        rehearsals = RoomMomentPlanRehearsalRepository(
            database.momentPlanRehearsalDao(),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun roomFlowSummarisesActualChoiceRatherThanAssignment() = runBlocking {
        decisions.insertOnce(
            decision(
                assigned = InterventionFamily.PivotGame,
                actual = InterventionFamily.PivotReading,
                completed = true,
            ),
        )

        val report = WhatWorksForMeBuilder.build(
            decisions = decisions.observeRecentDecisions(100).first(),
            rehearsals = emptyList(),
            plans = emptyList(),
            nowMillis = 10_000L,
        )

        assertEquals(InterventionFamily.PivotReading, report.interventions.single().intervention)
        assertEquals(1, report.interventions.single().completed)
    }

    @Test
    fun pendingAndFinalisedObservationsRemainSeparate() = runBlocking {
        decisions.insertOnce(
            decision(
                actual = InterventionFamily.PivotGame,
                repeat = RepeatObservation.NotFinalised,
            ),
        )
        decisions.insertOnce(
            decision(
                actual = InterventionFamily.PivotGame,
                repeat = RepeatObservation.NoRepeatDetected,
                finalised = true,
            ),
        )

        val report = WhatWorksForMeBuilder.build(
            decisions.observeRecentDecisions(100).first(),
            emptyList(),
            emptyList(),
            10_000L,
        )

        assertEquals(1, report.interventions.single().awaitingObservation)
        assertEquals(1, report.interventions.single().noLaterRepeatObserved)
    }

    @Test
    fun roomBackedDashboardAdvancesThroughEveryEvidenceTier() = runBlocking {
        suspend fun report() = WhatWorksForMeBuilder.build(
            decisions = decisions.observeRecentDecisions(100).first(),
            rehearsals = emptyList(),
            plans = emptyList(),
            nowMillis = 10_000L,
        )

        assertEquals(EvidenceQualityTier.CountOnly, report().evidenceQualityTier)
        repeat(3) {
            decisions.insertOnce(
                decision(
                    actual = InterventionFamily.PivotGame,
                    completed = true,
                    repeat = RepeatObservation.NoRepeatDetected,
                    finalised = true,
                ),
            )
        }
        assertEquals(EvidenceQualityTier.EarlyPattern, report().evidenceQualityTier)
        repeat(5) {
            decisions.insertOnce(
                decision(
                    actual = InterventionFamily.PivotGame,
                    completed = true,
                    feedback = FeedbackCode.Helped,
                    repeat = RepeatObservation.NoRepeatDetected,
                    finalised = true,
                ),
            )
        }
        repeat(8) {
            decisions.insertOnce(
                decision(
                    assigned = InterventionFamily.PivotReading,
                    actual = InterventionFamily.PivotReading,
                    completed = true,
                    feedback = FeedbackCode.DidNotHelp,
                    repeat = RepeatObservation.NoRepeatDetected,
                    finalised = true,
                ),
            )
        }
        assertEquals(
            EvidenceQualityTier.ComparisonSupported,
            report().evidenceQualityTier,
        )
    }

    @Test
    fun rehearsalAndSameRevisionLaterUseRoundTripThroughQueries() = runBlocking {
        val planId = UUID.randomUUID().toString()
        val contentRevisionId = UUID.randomUUID().toString()
        rehearsals.insertOnce(
            MomentPlanRehearsal(
                rehearsalId = UUID.randomUUID().toString(),
                planId = planId,
                planUpdatedAtMillisAtStart = 100L,
                mode = MomentPlanRehearsalMode.Guided,
                startedAtMillis = 1_000L,
                completedAtMillis = 1_100L,
                planContentRevisionId = contentRevisionId,
            ),
        )
        decisions.insertOnce(
            decision(
                actual = InterventionFamily.MomentPlan,
                planId = planId,
                planRevision = 100L,
                planContentRevisionId = contentRevisionId,
                startedAt = 2_000L,
            ),
        )

        val report = WhatWorksForMeBuilder.build(
            decisions.observeRecentDecisions(100).first(),
            rehearsals.observeRecentCompleted(100).first(),
            emptyList(),
            10_000L,
        )

        assertEquals(1, report.practice.completedRehearsals)
        assertEquals(1, report.practice.laterRealUsesWithinSevenDays)
        assertTrue(report.recentHistory.isEmpty())
    }

    private fun decision(
        assigned: InterventionFamily = InterventionFamily.PivotGame,
        actual: InterventionFamily,
        completed: Boolean = false,
        feedback: FeedbackCode = FeedbackCode.NotProvided,
        repeat: RepeatObservation = RepeatObservation.NotFinalised,
        finalised: Boolean = false,
        planId: String? = null,
        planRevision: Long? = null,
        planContentRevisionId: String? = null,
        startedAt: Long = 2_000L,
    ): AdaptiveDecision {
        val id = UUID.randomUUID().toString()
        return AdaptiveDecision(
            decisionId = id,
            protectionIncidentToken = "opaque-$id",
            sourceKind = AdaptiveSourceKind.App,
            createdAtMillis = 1_000L,
            momentWindowStartedAtMillis = 1_000L,
            momentCue = null,
            baselineUrgeRating = null,
            assignment = AdaptiveAssignment(
                momentIntensity = MomentIntensity.RepeatedAttempt,
                assignmentMode = AssignmentMode.AdaptiveSuggestion,
                eligibleInterventions = setOf(assigned, actual),
                assignedSuggestion = assigned,
                selectionProbability = null,
                reasonCode = AdaptiveReasonCode.StableFallback,
                momentPlanId = planId,
                momentPlanUpdatedAtMillis = planRevision,
                assignedPlanContentRevisionId =
                    planContentRevisionId.takeIf {
                        assigned == InterventionFamily.MomentPlan
                    },
                actualPlanContentRevisionId =
                    planContentRevisionId.takeIf {
                        actual == InterventionFamily.MomentPlan
                    },
                actualIntervention = actual,
                userOverrodeSuggestion = actual != assigned,
            ),
            presentedAtMillis = 1_500L,
            startedAtMillis = startedAt,
            completedAtMillis = (startedAt + 100L).takeIf { completed },
            dismissedAtMillis = null,
            feedbackCode = feedback,
            feedbackUpdatedAtMillis = 2_200L.takeIf {
                feedback != FeedbackCode.NotProvided
            },
            repeatObservation = repeat,
            observationDeadlineAtMillis = 3_000L,
            observationFinalisedAtMillis = 3_000L.takeIf { finalised },
        )
    }
}
