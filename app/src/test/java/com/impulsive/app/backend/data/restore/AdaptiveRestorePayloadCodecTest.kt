package com.impulsive.app.backend.data.restore

import com.impulsive.app.backend.data.local.entity.AdaptiveDecisionEntity
import com.impulsive.app.backend.data.local.entity.AdaptivePreferenceEntity
import com.impulsive.app.backend.data.local.entity.MomentPlanEntity
import com.impulsive.app.backend.data.local.entity.MomentPlanRehearsalEntity
import com.impulsive.app.backend.data.local.entity.PathShiftCycleEntity
import com.impulsive.app.backend.data.local.entity.ProtectionCoachSuggestionEntity
import com.impulsive.app.backend.domain.engine.adaptive.LegacyMomentPlanContentRevisionFactory
import com.impulsive.app.backend.domain.engine.adaptive.AdaptiveRecommendationPolicyVersion
import com.impulsive.app.backend.domain.engine.adaptive.AdaptiveHistoryRetentionPolicy
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveRestorePayloadCodecTest {
    @Test
    fun completeAdaptivePayload_roundTripsAllSectionsWithoutIncidentToken() {
        val encoded = AdaptiveRestorePayloadCodec.encode(
            plans = listOf(plan()),
            preferences = AdaptivePreferenceEntity(updatedAtMillis = 900L),
            decisions = listOf(decision()),
            rehearsals = listOf(rehearsal(completedAtMillis = 800L)),
        )
        val envelope = JSONObject().put(AdaptiveRestorePayloadCodec.JsonKey, encoded)

        val restored = AdaptiveRestorePayloadCodec.decodeIfPresent(envelope, 2_000L)

        assertNotNull(restored)
        assertEquals(PlanId, restored!!.plans.single().planId)
        assertEquals(DecisionId, restored.decisions.single().decisionId)
        assertEquals("restored:$DecisionId", restored.decisions.single().protectionIncidentToken)
        assertEquals(RehearsalId, restored.rehearsals.single().rehearsalId)
        assertEquals(4, encoded.getInt("formatVersion"))
        assertFalse(encoded.toString().contains("private-incident-token"))
    }

    @Test
    fun retentionAndScreenPrivacyPreferencesRoundTrip() {
        val encoded = AdaptiveRestorePayloadCodec.encode(
            plans = emptyList(),
            preferences = AdaptivePreferenceEntity(
                privateScreenProtectionEnabled = false,
                historyRetentionPolicy = AdaptiveHistoryRetentionPolicy.OneYear.name,
            ),
            decisions = emptyList(),
            rehearsals = emptyList(),
        )

        val restored = AdaptiveRestorePayloadCodec.decodeIfPresent(
            JSONObject().put(AdaptiveRestorePayloadCodec.JsonKey, encoded),
            2_000L,
        )!!.preferences

        assertFalse(restored.privateScreenProtectionEnabled)
        assertEquals(AdaptiveHistoryRetentionPolicy.OneYear.name, restored.historyRetentionPolicy)
    }

    @Test
    fun pathShiftSchemaThreeRoundTripsWithoutSourceIdentity() {
        val cycle = pathShiftCycle()
        val encoded = AdaptiveRestorePayloadCodec.encode(
            plans = listOf(plan()),
            preferences = AdaptivePreferenceEntity(pathShiftEnabled = true),
            decisions = emptyList(),
            rehearsals = emptyList(),
            pathShiftCycles = listOf(cycle),
        )

        val restored = AdaptiveRestorePayloadCodec.decodeIfPresent(
            JSONObject().put(AdaptiveRestorePayloadCodec.JsonKey, encoded),
            2_000L,
        )!!

        assertTrue(restored.preferences.pathShiftEnabled)
        assertEquals(cycle, restored.pathShiftCycles.single())
        val text = encoded.getJSONArray("pathShiftCycles").toString()
        listOf("url", "domain", "package", "email", "journal", "sourceIdentity").forEach {
            assertFalse(text.contains(it, ignoreCase = true))
        }
    }

    @Test
    fun protectionCoachPayloadFourRoundTripsAndExpiresStaleActiveSuggestions() {
        val current = coachSuggestion(
            id = "66666666-6666-4666-8666-666666666666",
            expiresAtMillis = 3_000L,
        )
        val stale = coachSuggestion(
            id = "77777777-7777-4777-8777-777777777777",
            expiresAtMillis = 1_500L,
        )
        val edited = coachSuggestion(
            id = "88888888-8888-4888-8888-888888888888",
            status = "AcceptedWithEdits",
            acceptedAtMillis = 1_400L,
            acceptedStartMinute = 1_260,
            acceptedEndMinute = 1_410,
        )
        val encoded = AdaptiveRestorePayloadCodec.encode(
            plans = emptyList(),
            preferences = AdaptivePreferenceEntity(pathShiftEnabled = true),
            decisions = emptyList(),
            rehearsals = emptyList(),
            protectionCoachSuggestions = listOf(current, stale, edited),
            protectionMonitorTransitionCompleted = true,
            suggestedSetupReviewed = true,
            onboardingColdStartPriorUsed = true,
        )

        val restored = AdaptiveRestorePayloadCodec.decodeIfPresent(
            JSONObject().put(AdaptiveRestorePayloadCodec.JsonKey, encoded),
            2_000L,
        )!!

        assertEquals(4, encoded.getInt("formatVersion"))
        assertTrue(restored.preferences.pathShiftEnabled)
        assertTrue(restored.protectionMonitorTransitionCompleted)
        assertTrue(restored.suggestedSetupReviewed)
        assertTrue(restored.onboardingColdStartPriorUsed)
        assertEquals("Prepared", restored.protectionCoachSuggestions[0].status)
        assertEquals("AcceptedWithEdits", restored.protectionCoachSuggestions[1].status)
        assertEquals("Expired", restored.protectionCoachSuggestions[2].status)
        assertEquals(1_260, restored.protectionCoachSuggestions[1].acceptedStartMinute)
        encoded.toString().let { text ->
            listOf("package", "url", "domain", "email", "uid", "journal").forEach {
                assertFalse(text.contains(it, ignoreCase = true))
            }
        }
    }

    @Test
    fun legacyPayloadVersionsOneTwoAndThreeRestoreWithoutCoachData() {
        listOf(1, 2, 3).forEach { version ->
            val adaptive = AdaptiveRestorePayloadCodec.encode(
                plans = if (version == 3) listOf(plan()) else emptyList(),
                preferences = AdaptivePreferenceEntity(pathShiftEnabled = version == 3),
                decisions = emptyList(),
                rehearsals = emptyList(),
                pathShiftCycles = if (version == 3) listOf(pathShiftCycle()) else emptyList(),
            ).apply {
                put("formatVersion", version)
                if (version < 3) remove("pathShiftCycles")
                remove("protectionCoachSuggestions")
                remove("protectionMonitorTransitionCompleted")
                remove("suggestedSetupReviewed")
                remove("onboardingColdStartPriorUsed")
                if (version == 1) {
                    getJSONObject("preferences").remove("historyRetentionPolicy")
                    getJSONObject("preferences").remove("privateScreenProtectionEnabled")
                    getJSONObject("preferences").remove("pathShiftEnabled")
                }
            }

            val restored = AdaptiveRestorePayloadCodec.decodeIfPresent(
                JSONObject().put(AdaptiveRestorePayloadCodec.JsonKey, adaptive),
                2_000L,
            )!!

            assertTrue(restored.protectionCoachSuggestions.isEmpty())
            assertFalse(restored.protectionMonitorTransitionCompleted)
            assertEquals(version == 3, restored.preferences.pathShiftEnabled)
        }
    }

    @Test
    fun invalidProtectionCoachStatusAndMinutesAreRejectedBeforeRoomWrite() {
        val invalidStatus = AdaptiveRestorePayloadCodec.encode(
            plans = emptyList(),
            preferences = AdaptivePreferenceEntity(),
            decisions = emptyList(),
            rehearsals = emptyList(),
            protectionCoachSuggestions = listOf(coachSuggestion()),
        )
        invalidStatus.getJSONArray("protectionCoachSuggestions")
            .getJSONObject(0)
            .put("status", "AutoApplied")

        val invalidMinute = AdaptiveRestorePayloadCodec.encode(
            plans = emptyList(),
            preferences = AdaptivePreferenceEntity(),
            decisions = emptyList(),
            rehearsals = emptyList(),
            protectionCoachSuggestions = listOf(coachSuggestion()),
        )
        invalidMinute.getJSONArray("protectionCoachSuggestions")
            .getJSONObject(0)
            .put("acceptedStartMinute", 1_500)

        assertTrue(decodeFailure(invalidStatus) is IllegalArgumentException)
        assertTrue(decodeFailure(invalidMinute) is IllegalArgumentException)
    }

    @Test
    fun corruptPathShiftCycleIsRejectedTransactionally() {
        val encoded = AdaptiveRestorePayloadCodec.encode(
            plans = listOf(plan()),
            preferences = AdaptivePreferenceEntity(pathShiftEnabled = true),
            decisions = emptyList(),
            rehearsals = emptyList(),
            pathShiftCycles = listOf(pathShiftCycle()),
        )
        encoded.getJSONArray("pathShiftCycles").getJSONObject(0)
            .put("estimatedLowerCount", 9)
            .put("estimatedUpperCount", 2)

        assertTrue(decodeFailure(encoded) is IllegalArgumentException)
    }

    @Test
    fun invalidRetentionPreferenceIsRejectedBeforeRoomWrite() {
        val encoded = validEncoded()
        encoded.getJSONObject("preferences")
            .put("historyRetentionPolicy", "ForeverMaybe")

        assertTrue(decodeFailure(encoded) is IllegalArgumentException)
    }

    @Test
    fun legacyPreferencePayloadDefaultsToSixMonthsAndPrivacyOn() {
        val encoded = validEncoded()
        encoded.put("formatVersion", 1)
        encoded.getJSONObject("preferences").apply {
            remove("historyRetentionPolicy")
            remove("privateScreenProtectionEnabled")
        }

        val restored = AdaptiveRestorePayloadCodec.decodeIfPresent(
            JSONObject().put(AdaptiveRestorePayloadCodec.JsonKey, encoded),
            2_000L,
        )!!.preferences

        assertTrue(restored.privateScreenProtectionEnabled)
        assertEquals(AdaptiveHistoryRetentionPolicy.SixMonths.name, restored.historyRetentionPolicy)
    }

    @Test
    fun historicalPayloadWithoutAdaptiveData_remainsValidAndReturnsNoAdaptiveImport() {
        assertNull(
            AdaptiveRestorePayloadCodec.decodeIfPresent(
                JSONObject()
                    .put("journalNotes", JSONArray())
                    .put("checklistItems", JSONArray()),
                2_000L,
            ),
        )
    }

    @Test
    fun partialAdaptivePayload_isRejectedBeforeRestore() {
        val adaptive = JSONObject()
            .put("formatVersion", 1)
            .put("momentPlans", JSONArray())
            .put("preferences", preferencesJson())
            .put("decisions", JSONArray())

        val error = runCatching {
            AdaptiveRestorePayloadCodec.decodeIfPresent(
                JSONObject().put(AdaptiveRestorePayloadCodec.JsonKey, adaptive),
                2_000L,
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun invalidEnum_isRejected() {
        val adaptive = validEncoded()
        adaptive.getJSONArray("decisions")
            .getJSONObject(0)
            .put("actualIntervention", "UntrustedChoice")

        assertTrue(decodeFailure(adaptive) is IllegalArgumentException)
    }

    @Test
    fun duplicateIds_areRejected() {
        val adaptive = validEncoded()
        adaptive.getJSONArray("decisions")
            .put(JSONObject(adaptive.getJSONArray("decisions").getJSONObject(0).toString()))

        assertTrue(decodeFailure(adaptive) is IllegalArgumentException)
    }

    @Test
    fun invalidTerminalState_isRejected() {
        val adaptive = validEncoded()
        adaptive.getJSONArray("decisions")
            .getJSONObject(0)
            .put("dismissedAtMillis", 850L)

        assertTrue(decodeFailure(adaptive) is IllegalArgumentException)
    }

    @Test
    fun openRehearsal_isRestoredAsDismissedAtRestoreTime() {
        val adaptive = AdaptiveRestorePayloadCodec.encode(
            plans = listOf(plan()),
            preferences = AdaptivePreferenceEntity(),
            decisions = emptyList(),
            rehearsals = listOf(rehearsal()),
        )

        val restored = AdaptiveRestorePayloadCodec.decodeIfPresent(
            JSONObject().put(AdaptiveRestorePayloadCodec.JsonKey, adaptive),
            2_000L,
        )!!

        assertNull(restored.rehearsals.single().completedAtMillis)
        assertEquals(2_000L, restored.rehearsals.single().dismissedAtMillis)
    }

    @Test
    fun historicalDecisionMayReferenceDeletedPlan_withoutRecreatingIt() {
        val adaptive = AdaptiveRestorePayloadCodec.encode(
            plans = emptyList(),
            preferences = AdaptivePreferenceEntity(),
            decisions = listOf(
                decision().copy(
                    assignedSuggestion = "MomentPlan",
                    actualIntervention = "MomentPlan",
                    momentPlanId = PlanId,
                    momentPlanUpdatedAtMillis = 500L,
                    eligibleInterventionsMask = 8,
                ),
            ),
            rehearsals = emptyList(),
        )

        val restored = AdaptiveRestorePayloadCodec.decodeIfPresent(
            JSONObject().put(AdaptiveRestorePayloadCodec.JsonKey, adaptive),
            2_000L,
        )!!

        assertTrue(restored.plans.isEmpty())
        assertEquals(PlanId, restored.decisions.single().momentPlanId)
    }

    @Test
    fun schemaNineBackupWithoutRevisionFieldsGetsCompatibleLegacyRevisions() {
        val adaptive = AdaptiveRestorePayloadCodec.encode(
            plans = listOf(plan()),
            preferences = AdaptivePreferenceEntity(),
            decisions = listOf(
                decision().copy(
                    assignedSuggestion = "MomentPlan",
                    actualIntervention = "MomentPlan",
                    momentPlanId = PlanId,
                    momentPlanUpdatedAtMillis = 500L,
                    eligibleInterventionsMask = 8,
                ),
            ),
            rehearsals = listOf(rehearsal(completedAtMillis = 800L)),
        )
        adaptive.put("formatVersion", 1)
        adaptive.getJSONArray("momentPlans").getJSONObject(0).remove("contentRevisionId")
        adaptive.getJSONArray("decisions").getJSONObject(0)
            .remove("assignedPlanContentRevisionId")
        adaptive.getJSONArray("decisions").getJSONObject(0)
            .remove("actualPlanContentRevisionId")
        adaptive.getJSONArray("rehearsals").getJSONObject(0)
            .remove("planContentRevisionId")

        val restored = AdaptiveRestorePayloadCodec.decodeIfPresent(
            JSONObject().put(AdaptiveRestorePayloadCodec.JsonKey, adaptive),
            2_000L,
        )!!
        val expected = LegacyMomentPlanContentRevisionFactory.create(PlanId, 500L)

        assertEquals(expected, restored.plans.single().contentRevisionId)
        assertEquals(expected, restored.rehearsals.single().planContentRevisionId)
        assertEquals(expected, restored.decisions.single().assignedPlanContentRevisionId)
        assertEquals(expected, restored.decisions.single().actualPlanContentRevisionId)
    }

    @Test
    fun decisionPassportRoundTripsWithoutInternalUtilityState() {
        val adaptive = AdaptiveRestorePayloadCodec.encode(
            plans = emptyList(),
            preferences = AdaptivePreferenceEntity(),
            decisions = listOf(
                decision().copy(
                    recommendationPolicyVersion = 3,
                    assignedProtocolId = "short_pause",
                    assignedProtocolVersion = 1,
                    actualProtocolId = "short_pause",
                    actualProtocolVersion = 1,
                    eligibleMomentPlanCount = 2,
                ),
            ),
            rehearsals = emptyList(),
        )

        val restored = AdaptiveRestorePayloadCodec.decodeIfPresent(
            JSONObject().put(AdaptiveRestorePayloadCodec.JsonKey, adaptive),
            2_000L,
        )!!.decisions.single()

        assertEquals(3, restored.recommendationPolicyVersion)
        assertEquals("short_pause", restored.assignedProtocolId)
        assertEquals(1, restored.assignedProtocolVersion)
        assertEquals("short_pause", restored.actualProtocolId)
        assertEquals(1, restored.actualProtocolVersion)
        assertEquals(2, restored.eligibleMomentPlanCount)
        assertFalse(adaptive.toString().contains("utility", ignoreCase = true))
        assertFalse(adaptive.toString().contains("randomSource", ignoreCase = true))
    }

    @Test
    fun restoreRejectsMalformedProtocolVersion() {
        val adaptive = validEncoded()
        adaptive.getJSONArray("decisions").getJSONObject(0)
            .put("assignedProtocolId", "short_pause")
            .put("assignedProtocolVersion", 0)

        assertTrue(decodeFailure(adaptive) is IllegalArgumentException)
    }

    @Test
    fun knownProtocolWithFutureVersionRemainsReadableButNonExecutable() {
        val adaptive = validEncoded()
        adaptive.getJSONArray("decisions").getJSONObject(0)
            .put("assignedProtocolId", "short_pause")
            .put("assignedProtocolVersion", 99)

        val restored = AdaptiveRestorePayloadCodec.decodeIfPresent(
            JSONObject().put(AdaptiveRestorePayloadCodec.JsonKey, adaptive),
            2_000L,
        )!!.decisions.single()

        assertEquals("short_pause", restored.assignedProtocolId)
        assertEquals(99, restored.assignedProtocolVersion)
    }

    @Test
    fun legacyPassportFieldsReceiveDocumentedDefaults() {
        val adaptive = validEncoded()
        adaptive.put("formatVersion", 1)
        adaptive.getJSONArray("decisions").getJSONObject(0).apply {
            remove("recommendationPolicyVersion")
            remove("assignedProtocolId")
            remove("assignedProtocolVersion")
            remove("actualProtocolId")
            remove("actualProtocolVersion")
            remove("eligibleMomentPlanCount")
        }

        val restored = AdaptiveRestorePayloadCodec.decodeIfPresent(
            JSONObject().put(AdaptiveRestorePayloadCodec.JsonKey, adaptive),
            2_000L,
        )!!.decisions.single()

        assertEquals(
            AdaptiveRecommendationPolicyVersion.Current,
            restored.recommendationPolicyVersion,
        )
        assertEquals("short_pause", restored.assignedProtocolId)
        assertEquals(1, restored.assignedProtocolVersion)
        assertEquals("short_pause", restored.actualProtocolId)
        assertEquals(1, restored.actualProtocolVersion)
        assertEquals(0, restored.eligibleMomentPlanCount)
    }

    @Test
    fun schemaTwoRejectsProtocolFromDifferentFamily() {
        val adaptive = validEncoded()
        adaptive.getJSONArray("decisions").getJSONObject(0)
            .put("assignedProtocolId", "pivot_game")
            .put("assignedProtocolVersion", 1)

        assertTrue(decodeFailure(adaptive) is IllegalArgumentException)
    }

    @Test
    fun schemaTwoRequiresRevisionAndPreferenceFields() {
        val missingRevision = validEncoded().apply {
            getJSONArray("momentPlans").getJSONObject(0).remove("contentRevisionId")
        }
        val missingPrivacy = validEncoded().apply {
            getJSONObject("preferences").remove("privateScreenProtectionEnabled")
        }

        assertNotNull(decodeFailure(missingRevision))
        assertNotNull(decodeFailure(missingPrivacy))
    }

    private fun validEncoded(): JSONObject = AdaptiveRestorePayloadCodec.encode(
        plans = listOf(plan()),
        preferences = AdaptivePreferenceEntity(),
        decisions = listOf(decision()),
        rehearsals = listOf(rehearsal(completedAtMillis = 800L)),
    )

    private fun decodeFailure(adaptive: JSONObject): Throwable? = runCatching {
        AdaptiveRestorePayloadCodec.decodeIfPresent(
            JSONObject().put(AdaptiveRestorePayloadCodec.JsonKey, adaptive),
            2_000L,
        )
    }.exceptionOrNull()

    private fun preferencesJson(): JSONObject = JSONObject()
        .put("personalSuggestionsEnabled", true)
        .put("gameSuggestionsEnabled", true)
        .put("readingSuggestionsEnabled", true)
        .put("momentPlanSuggestionsEnabled", true)
        .put("randomisedExplorationEnabled", true)
        .put("updatedAtMillis", 0L)

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
        rehearsedAtMillis = 800L,
    )

    private fun decision() = AdaptiveDecisionEntity(
        decisionId = DecisionId,
        protectionIncidentToken = "private-incident-token",
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

    private fun rehearsal(
        completedAtMillis: Long? = null,
    ) = MomentPlanRehearsalEntity(
        rehearsalId = RehearsalId,
        planId = PlanId,
        planUpdatedAtMillisAtStart = 500L,
        mode = "Guided",
        startedAtMillis = 700L,
        completedAtMillis = completedAtMillis,
        dismissedAtMillis = null,
    )

    private fun pathShiftCycle() = PathShiftCycleEntity(
        cycleId = PathShiftCycleId,
        createdAtMillis = 100L,
        lookbackStartedAtMillis = 1L,
        lookbackEndedAtMillis = 100L,
        forecastWindowStartedAtMillis = 200L,
        forecastWindowEndsAtMillis = 2_000L,
        forecastPolicyVersion = 1,
        evidenceStrength = "EarlyEstimate",
        inputProtectedMomentCount = 7,
        inputDistinctDayCount = 5,
        estimatedLowerCount = 2,
        estimatedUpperCount = 5,
        commonWindowStartMinute = 1_320,
        commonWindowEndMinute = 1_440,
        preparedPlanId = PlanId,
        preparedPlanContentRevisionId = PreparedRevisionId,
        preparedAtMillis = 150L,
        reviewFinalisedAtMillis = null,
        observedProtectedMomentCount = 0,
        preparedPlanSelectedCount = 0,
        preparedPlanStartedCount = 0,
        preparedPlanCompletedCount = 0,
        preparedPlanDismissedCount = 0,
        wrongTimingCount = 0,
        repeatDetectedCount = 0,
        status = "Active",
        cancelledAtMillis = null,
    )

    private fun coachSuggestion(
        id: String = "66666666-6666-4666-8666-666666666666",
        status: String = "Prepared",
        expiresAtMillis: Long = 3_000L,
        acceptedAtMillis: Long? = null,
        acceptedStartMinute: Int? = null,
        acceptedEndMinute: Int? = null,
    ) = ProtectionCoachSuggestionEntity(
        suggestionId = id,
        policyVersion = 1,
        suggestionType = "CreateEveningWindow",
        createdAtMillis = 1_000L,
        expiresAtMillis = expiresAtMillis,
        status = status,
        presentedAtMillis = if (status == "Presented") 1_100L else null,
        acceptedAtMillis = acceptedAtMillis,
        dismissedAtMillis = if (status == "Dismissed") 1_200L else null,
        suppressedAtMillis = if (status == "Suppressed") 1_300L else null,
        evidenceWindowStartedAtMillis = 100L,
        evidenceWindowEndedAtMillis = 900L,
        evidenceProtectedMomentCount = 7,
        evidenceDistinctDayCount = 5,
        broadWindowStartMinute = 1_320,
        broadWindowEndMinute = 1_439,
        suggestedStartMinute = 1_320,
        suggestedEndMinute = 1_439,
        acceptedStartMinute = acceptedStartMinute,
        acceptedEndMinute = acceptedEndMinute,
        onboardingReasonCode = null,
        relatedMomentPlanId = null,
        relatedMomentPlanContentRevisionId = null,
    )

    private companion object {
        const val PlanId = "11111111-1111-4111-8111-111111111111"
        const val DecisionId = "22222222-2222-4222-8222-222222222222"
        const val RehearsalId = "33333333-3333-4333-8333-333333333333"
        const val PathShiftCycleId = "44444444-4444-4444-8444-444444444444"
        const val PreparedRevisionId = "55555555-5555-4555-8555-555555555555"
    }
}
