package com.impulsive.app.backend.session.adaptive

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.impulsive.app.backend.data.local.database.AppDatabase
import com.impulsive.app.backend.data.local.database.SqlCipherDatabaseMigrator
import com.impulsive.app.backend.data.local.preferences.ProtectionSetupPreferencesDataSource
import com.impulsive.app.backend.data.repository.adaptive.RoomAdaptiveDecisionRepository
import com.impulsive.app.backend.data.repository.adaptive.RoomMomentPlanRepository
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveAssignment
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveDecision
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveReasonCode
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSourceKind
import com.impulsive.app.backend.domain.model.adaptive.AssignmentMode
import com.impulsive.app.backend.domain.model.adaptive.FeedbackCode
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentIntensity
import com.impulsive.app.backend.domain.model.adaptive.RepeatObservation
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
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
class AdaptivePhase6InstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var decisions: RoomAdaptiveDecisionRepository
    private lateinit var plans: RoomMomentPlanRepository
    private lateinit var databaseName: String
    private val clock = MutableClock(100_000L)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "adaptive-phase6-${System.nanoTime()}.db"
        SqlCipherDatabaseMigrator.ensureSqlCipherLoaded()
        openEncryptedDatabase()
    }

    @After
    fun tearDown() {
        if (::database.isInitialized && database.isOpen) database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun shortPauseCompletionCancellationAndProcessReloadPersistExactlyOnce() = runBlocking {
        val completed = terminalReadyDecision(
            family = InterventionFamily.ShortPause,
            startedAt = 10_000L,
        )
        val cancelled = terminalReadyDecision(
            family = InterventionFamily.ShortPause,
            startedAt = 20_000L,
        )
        decisions.insertOnce(completed)
        decisions.insertOnce(cancelled)
        val outcome = outcome()

        assertFalse(
            AdaptiveCompletionGate.pauseFinished(
                completed.startedAtMillis!!,
                completed.startedAtMillis + 29_999L,
            ),
        )
        clock.current = completed.startedAtMillis + 30_000L
        assertEquals(
            AdaptiveOutcomeResult.Applied,
            outcome.complete(completed.decisionId),
        )
        assertEquals(
            AdaptiveOutcomeResult.Idempotent,
            outcome.complete(completed.decisionId),
        )

        clock.current = 60_000L
        assertEquals(
            AdaptiveOutcomeResult.Applied,
            outcome.dismiss(cancelled.decisionId),
        )
        assertNull(decisions.getById(cancelled.decisionId)?.completedAtMillis)

        database.close()
        openEncryptedDatabase()
        assertNotNull(decisions.getById(completed.decisionId)?.completedAtMillis)
        assertNull(decisions.getById(completed.decisionId)?.dismissedAtMillis)
        assertNotNull(decisions.getById(cancelled.decisionId)?.dismissedAtMillis)
    }

    @Test
    fun gameAndReadingUseGenuineCompletionGatesAndKeepDecisionIdsIsolated() = runBlocking {
        val game = terminalReadyDecision(InterventionFamily.PivotGame, 10_000L)
        val reading = terminalReadyDecision(InterventionFamily.PivotReading, 20_000L)
        decisions.insertOnce(game)
        decisions.insertOnce(reading)
        val outcome = outcome()

        assertFalse(AdaptiveCompletionGate.gameCompleted(false))
        assertTrue(AdaptiveCompletionGate.gameCompleted(true))
        assertFalse(AdaptiveCompletionGate.readingCompleted(89, true, true))
        assertFalse(AdaptiveCompletionGate.readingCompleted(90, false, true))
        assertTrue(AdaptiveCompletionGate.readingCompleted(90, true, true))

        clock.current = 70_000L
        outcome.complete(game.decisionId)
        clock.current = 80_000L
        outcome.complete(reading.decisionId)
        clock.current = 81_000L
        outcome.submitFeedback(
            reading.decisionId,
            FeedbackCode.HelpedALittle,
            81_000L,
        )

        assertNotEquals(game.decisionId, reading.decisionId)
        assertNull(decisions.getById(game.decisionId)?.feedbackUpdatedAtMillis)
        assertEquals(
            FeedbackCode.HelpedALittle,
            decisions.getById(reading.decisionId)?.feedbackCode,
        )
    }

    @Test
    fun momentPlanManualConfirmationFeedbackRevisionAndSkipUseSameRows() = runBlocking {
        val textPlan = terminalReadyDecision(InterventionFamily.MomentPlan, 10_000L)
        val appPlan = terminalReadyDecision(InterventionFamily.MomentPlan, 20_000L)
        val skipped = terminalReadyDecision(InterventionFamily.PivotGame, 30_000L)
        decisions.insertOnce(textPlan)
        decisions.insertOnce(appPlan)
        decisions.insertOnce(skipped)
        val outcome = outcome()

        assertNull(decisions.getById(appPlan.decisionId)?.completedAtMillis)
        clock.current = 70_000L
        outcome.complete(textPlan.decisionId)
        outcome.complete(appPlan.decisionId)
        outcome.dismiss(skipped.decisionId)

        clock.current = 71_000L
        outcome.submitFeedback(textPlan.decisionId, FeedbackCode.Helped, 71_000L)
        clock.current = 72_000L
        outcome.submitFeedback(
            textPlan.decisionId,
            FeedbackCode.WrongTiming,
            72_000L,
        )
        clock.current = 73_000L
        outcome.submitFeedback(
            skipped.decisionId,
            FeedbackCode.NotProvided,
            73_000L,
        )

        assertEquals(3, database.adaptiveDecisionDao().count())
        assertEquals(
            FeedbackCode.WrongTiming,
            decisions.getById(textPlan.decisionId)?.feedbackCode,
        )
        assertEquals(
            72_000L,
            decisions.getById(textPlan.decisionId)?.feedbackUpdatedAtMillis,
        )
        assertEquals(
            FeedbackCode.NotProvided,
            decisions.getById(skipped.decisionId)?.feedbackCode,
        )
        assertNotNull(decisions.getById(skipped.decisionId)?.feedbackUpdatedAtMillis)
    }

    @Test
    fun pendingRecoveryObservationWorkAndWebsiteProtectionRemainIndependent() = runBlocking {
        val protection = ProtectionSetupPreferencesDataSource(context)
        protection.setWebsiteProtectionEnabled(true)
        val pending = terminalReadyDecision(InterventionFamily.PivotGame, 10_000L)
            .copy(
                presentedAtMillis = null,
                startedAtMillis = null,
                completedAtMillis = null,
            )
        decisions.insertOnce(pending)
        val lifecycle = AdaptiveDecisionLifecycle(
            decisions = decisions,
            momentPlans = plans,
            scheduler = WorkManagerAdaptiveObservationScheduler(context, clock),
            clock = clock,
            logger = AdaptiveSafeLogger { _, _ -> },
        )
        clock.current = 20_000L
        assertEquals(
            AdaptiveLifecycleResult.Applied,
            lifecycle.markPresented(pending.decisionId, 20_000L),
        )
        clock.current = 21_000L
        assertEquals(
            AdaptiveLifecycleResult.Applied,
            lifecycle.markStarted(pending.decisionId, 21_000L),
        )
        clock.current = 60_000L
        val outcome = AdaptiveOutcomeCoordinator(decisions, lifecycle, clock)
        outcome.complete(pending.decisionId, 60_000L)
        clock.current = 61_000L
        outcome.submitFeedback(
            pending.decisionId,
            FeedbackCode.Helped,
            61_000L,
        )

        val workManager = WorkManager.getInstance(context)
        val work = workManager.getWorkInfosForUniqueWork(
            AdaptiveObservationWork.uniqueName(pending.decisionId),
        ).get(10, TimeUnit.SECONDS)
        assertTrue(
            work.any {
                it.state == WorkInfo.State.ENQUEUED ||
                    it.state == WorkInfo.State.RUNNING
            },
        )
        assertTrue(protection.state.first().websiteProtectionEnabled)
        assertEquals(
            RepeatObservation.NotFinalised,
            decisions.getById(pending.decisionId)?.repeatObservation,
        )
        assertNull(
            AdaptivePendingFeedbackCoordinator(decisions)
                .claimMostRecentEligible(
                    AdaptivePendingFeedbackSafety(false, false, false),
                ),
        )

        workManager.cancelUniqueWork(
            AdaptiveObservationWork.uniqueName(pending.decisionId),
        )
        protection.setWebsiteProtectionEnabled(false)
    }

    private fun openEncryptedDatabase() {
        database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .openHelperFactory(
                SupportOpenHelperFactory(
                    ByteArray(32) { index -> (index + 17).toByte() },
                ),
            )
            .build()
        decisions = RoomAdaptiveDecisionRepository(database.adaptiveDecisionDao())
        plans = RoomMomentPlanRepository(database.momentPlanDao())
    }

    private fun outcome(): AdaptiveOutcomeCoordinator {
        val lifecycle = AdaptiveDecisionLifecycle(
            decisions = decisions,
            momentPlans = plans,
            scheduler = object : AdaptiveObservationScheduler {
                override fun schedule(decisionId: String, deadlineAtMillis: Long) = true
                override fun cancelAll() = true
            },
            clock = clock,
            logger = AdaptiveSafeLogger { _, _ -> },
        )
        return AdaptiveOutcomeCoordinator(decisions, lifecycle, clock)
    }

    private fun terminalReadyDecision(
        family: InterventionFamily,
        startedAt: Long,
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
            assignmentMode = AssignmentMode.UserChosen,
            eligibleInterventions = setOf(family),
            assignedSuggestion = null,
            selectionProbability = null,
            reasonCode = AdaptiveReasonCode.UserOverride,
            momentPlanId = if (family == InterventionFamily.MomentPlan) {
                UUID.randomUUID().toString()
            } else {
                null
            },
            actualIntervention = family,
        ),
        presentedAtMillis = 2_000L,
        startedAtMillis = startedAt,
        observationDeadlineAtMillis = 1_201_000L,
    )

    private class MutableClock(var current: Long) : AdaptiveClock {
        override fun nowMillis(): Long = current
    }
}
