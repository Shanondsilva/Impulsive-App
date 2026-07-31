package com.impulsive.app.backend.data.restore

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.impulsive.app.backend.data.local.database.AppDatabase
import com.impulsive.app.backend.data.local.entity.AdaptiveDecisionEntity
import com.impulsive.app.backend.data.local.entity.AdaptivePreferenceEntity
import com.impulsive.app.backend.data.local.entity.MomentPlanEntity
import com.impulsive.app.backend.data.local.entity.MomentPlanRehearsalEntity
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdaptiveBackupRestoreInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun completePayload_restoresAllAdaptiveSectionsAndRunsPostCommitRecovery() = runBlocking {
        var recoveryCalls = 0
        val outcome = RestoreBundleImporter(
            context = context,
            database = database,
            recoverAdaptiveObservations = { recoveryCalls++ },
        ).importPayload(payloadWithAdaptive(openRehearsal = true))

        assertEquals(RestoreBundleImporter.ImportOutcome.Success, outcome)
        assertEquals(1, database.momentPlanDao().count())
        assertEquals(1, database.adaptiveDecisionDao().count())
        assertNotNull(database.adaptivePreferenceDao().get())
        val rehearsal = database.momentPlanRehearsalDao().getById(RehearsalId)
        assertNotNull(rehearsal?.dismissedAtMillis)
        assertEquals(1, recoveryCalls)
    }

    @Test
    fun oldPayloadWithoutAdaptiveSections_stillRestoresWithoutAdaptiveRecovery() = runBlocking {
        var recoveryCalls = 0
        val outcome = RestoreBundleImporter(
            context = context,
            database = database,
            recoverAdaptiveObservations = { recoveryCalls++ },
        ).importPayload(emptyPayload())

        assertEquals(RestoreBundleImporter.ImportOutcome.Success, outcome)
        assertEquals(0, database.momentPlanDao().count())
        assertEquals(0, database.adaptiveDecisionDao().count())
        assertEquals(0, recoveryCalls)
    }

    @Test
    fun adaptiveSchemaOneRestoresIntoSchemaTenWithDeterministicDefaults() = runBlocking {
        val payload = payloadWithAdaptive(openRehearsal = false)
        val adaptive = payload.getJSONObject(AdaptiveRestorePayloadCodec.JsonKey)
        adaptive.put("formatVersion", 1)
        adaptive.getJSONArray("momentPlans").getJSONObject(0).remove("contentRevisionId")
        adaptive.getJSONObject("preferences").apply {
            remove("privateScreenProtectionEnabled")
            remove("historyRetentionPolicy")
        }
        adaptive.getJSONArray("decisions").getJSONObject(0).apply {
            remove("recommendationPolicyVersion")
            remove("assignedProtocolId")
            remove("assignedProtocolVersion")
            remove("actualProtocolId")
            remove("actualProtocolVersion")
            remove("eligibleMomentPlanCount")
        }
        adaptive.getJSONArray("rehearsals").getJSONObject(0)
            .remove("planContentRevisionId")

        val outcome = RestoreBundleImporter(
            context = context,
            database = database,
            recoverAdaptiveObservations = {},
        ).importPayload(payload)

        assertEquals(RestoreBundleImporter.ImportOutcome.Success, outcome)
        val restoredDecision = database.adaptiveDecisionDao().getById(DecisionId)
        assertEquals("short_pause", restoredDecision?.assignedProtocolId)
        assertEquals(1, restoredDecision?.assignedProtocolVersion)
        assertTrue(database.momentPlanDao().getAllForBackup().single().contentRevisionId.isNotBlank())
    }

    @Test
    fun partialAdaptivePayload_failsBeforeAnyAdaptiveWrite() = runBlocking {
        val partial = JSONObject()
            .put("formatVersion", 1)
            .put("momentPlans", JSONArray())
            .put("preferences", JSONObject())
            .put("decisions", JSONArray())
        val payload = emptyPayload().put(AdaptiveRestorePayloadCodec.JsonKey, partial)

        val error = runCatching {
            RestoreBundleImporter(
                context = context,
                database = database,
                recoverAdaptiveObservations = {},
            ).importPayload(payload)
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals(0, database.momentPlanDao().count())
        assertEquals(0, database.adaptiveDecisionDao().count())
        assertTrue(database.momentPlanRehearsalDao().getAllForBackup().isEmpty())
    }

    @Test
    fun replacementImport_replacesRatherThanDuplicatesAdaptiveRecords() = runBlocking {
        val importer = RestoreBundleImporter(
            context = context,
            database = database,
            recoverAdaptiveObservations = {},
        )
        val payload = payloadWithAdaptive(openRehearsal = false)
        importer.importPayload(payload)

        val result = importer.importPayload(
            parsed = payload,
            mode = RestoreBundleImporter.ImportMode.ReplaceRestoreBundleData,
        )

        assertEquals(RestoreBundleImporter.ImportOutcome.Success, result)
        assertEquals(1, database.momentPlanDao().count())
        assertEquals(1, database.adaptiveDecisionDao().count())
        assertEquals(1, database.momentPlanRehearsalDao().getAllForBackup().size)
    }

    private fun payloadWithAdaptive(openRehearsal: Boolean): JSONObject =
        emptyPayload().put(
            AdaptiveRestorePayloadCodec.JsonKey,
            AdaptiveRestorePayloadCodec.encode(
                plans = listOf(plan()),
                preferences = AdaptivePreferenceEntity(updatedAtMillis = 50L),
                decisions = listOf(decision()),
                rehearsals = listOf(
                    rehearsal(
                        completedAtMillis = if (openRehearsal) null else 800L,
                    ),
                ),
            ),
        )

    private fun emptyPayload(): JSONObject = JSONObject()
        .put("journalNotes", JSONArray())
        .put("checklistItems", JSONArray())
        .put("recoverySessions", JSONArray())
        .put("blockedDomains", JSONArray())

    private fun plan() = MomentPlanEntity(
        planId = PlanId,
        title = "Pause and breathe",
        momentCue = "Stress",
        actionText = "Take three slow breaths",
        futureCueText = "When stress rises, pause first",
        actionType = "TextOnly",
        actionTarget = null,
        enabled = true,
        preferredForCue = true,
        createdAtMillis = 100L,
        updatedAtMillis = 500L,
        rehearsedAtMillis = null,
    )

    private fun rehearsal(completedAtMillis: Long?) = MomentPlanRehearsalEntity(
        rehearsalId = RehearsalId,
        planId = PlanId,
        planUpdatedAtMillisAtStart = 500L,
        mode = "Guided",
        startedAtMillis = 700L,
        completedAtMillis = completedAtMillis,
        dismissedAtMillis = null,
    )

    private fun decision() = AdaptiveDecisionEntity(
        decisionId = DecisionId,
        protectionIncidentToken = "not-exported",
        sourceKind = "App",
        createdAtMillis = 600L,
        momentWindowStartedAtMillis = 590L,
        momentIntensity = "FirstAttempt",
        momentCue = "Stress",
        baselineUrgeRating = 5,
        assignmentMode = "AdaptiveSuggestion",
        eligibleInterventionsMask = 1,
        assignedSuggestion = "ShortPause",
        actualIntervention = "ShortPause",
        selectionProbability = 1.0,
        reasonCode = "RecentHelpfulFeedback",
        momentPlanId = null,
        momentPlanUpdatedAtMillis = null,
        userOverrodeSuggestion = false,
        presentedAtMillis = 610L,
        startedAtMillis = 620L,
        completedAtMillis = 800L,
        dismissedAtMillis = null,
        feedbackCode = "Helped",
        feedbackUpdatedAtMillis = 820L,
        repeatDetectedWithin20Minutes = false,
        firstRepeatAtMillis = null,
        observationDeadlineAtMillis = 1_800L,
        observationFinalisedAtMillis = 1_800L,
    )

    private companion object {
        const val PlanId = "11111111-1111-4111-8111-111111111111"
        const val DecisionId = "22222222-2222-4222-8222-222222222222"
        const val RehearsalId = "33333333-3333-4333-8333-333333333333"
    }
}
