package com.impulsive.app.backend.data.restore

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.impulsive.app.backend.data.local.entity.AdaptivePreferenceEntity
import com.impulsive.app.backend.data.local.entity.MomentPlanEntity
import com.impulsive.app.backend.data.local.entity.PathShiftCycleEntity
import com.impulsive.app.backend.data.local.entity.ProtectionCoachSuggestionEntity
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProtectionCoachBackupPayloadCompatibilityInstrumentedTest {
    @Test
    fun payloadVersionsOneThroughFourDecodeSafely() {
        val versions = listOf(1, 2, 3, 4)
        versions.forEach { version ->
            val adaptive = AdaptiveRestorePayloadCodec.encode(
                plans = if (version == 3) listOf(plan()) else emptyList(),
                preferences = AdaptivePreferenceEntity(pathShiftEnabled = version >= 3),
                decisions = emptyList(),
                rehearsals = emptyList(),
                pathShiftCycles = if (version == 3) listOf(pathShiftCycle()) else emptyList(),
                protectionCoachSuggestions = if (version == 4) {
                    listOf(coachSuggestion())
                } else {
                    emptyList()
                },
                protectionMonitorTransitionCompleted = version == 4,
            ).apply {
                put("formatVersion", version)
                if (version < 3) {
                    remove("pathShiftCycles")
                    getJSONObject("preferences").remove("pathShiftEnabled")
                }
                if (version < 4) {
                    remove("protectionCoachSuggestions")
                    remove("protectionMonitorTransitionCompleted")
                    remove("suggestedSetupReviewed")
                    remove("onboardingColdStartPriorUsed")
                }
                if (version == 1) {
                    getJSONObject("preferences").remove("privateScreenProtectionEnabled")
                    getJSONObject("preferences").remove("historyRetentionPolicy")
                }
            }

            val decoded = AdaptiveRestorePayloadCodec.decodeIfPresent(
                JSONObject().put(AdaptiveRestorePayloadCodec.JsonKey, adaptive),
                2_000L,
            )!!

            assertEquals(version == 4, decoded.protectionMonitorTransitionCompleted)
            assertEquals(if (version == 4) 1 else 0, decoded.protectionCoachSuggestions.size)
        }
    }

    private fun coachSuggestion() = ProtectionCoachSuggestionEntity(
        suggestionId = "11111111-1111-4111-8111-111111111111",
        policyVersion = 1,
        suggestionType = "CreateEveningWindow",
        createdAtMillis = 100L,
        expiresAtMillis = 1_000L,
        status = "AcceptedWithEdits",
        presentedAtMillis = 200L,
        acceptedAtMillis = 300L,
        dismissedAtMillis = null,
        suppressedAtMillis = null,
        evidenceWindowStartedAtMillis = 1L,
        evidenceWindowEndedAtMillis = 99L,
        evidenceProtectedMomentCount = 7,
        evidenceDistinctDayCount = 5,
        broadWindowStartMinute = 1_320,
        broadWindowEndMinute = 1_439,
        suggestedStartMinute = 1_320,
        suggestedEndMinute = 1_439,
        acceptedStartMinute = 1_260,
        acceptedEndMinute = 1_410,
        onboardingReasonCode = null,
        relatedMomentPlanId = null,
        relatedMomentPlanContentRevisionId = null,
    )

    private fun plan() = MomentPlanEntity(
        planId = PlanId,
        title = "Pause",
        momentCue = "Stress",
        actionText = "Breathe",
        futureCueText = "Pause first",
        actionType = "TextOnly",
        actionTarget = null,
        enabled = true,
        preferredForCue = false,
        createdAtMillis = 100L,
        updatedAtMillis = 200L,
        rehearsedAtMillis = null,
        contentRevisionId = RevisionId,
    )

    private fun pathShiftCycle() = PathShiftCycleEntity(
        cycleId = "22222222-2222-4222-8222-222222222222",
        createdAtMillis = 100L,
        lookbackStartedAtMillis = 1L,
        lookbackEndedAtMillis = 99L,
        forecastWindowStartedAtMillis = 200L,
        forecastWindowEndsAtMillis = 1_000L,
        forecastPolicyVersion = 1,
        evidenceStrength = "EarlyEstimate",
        inputProtectedMomentCount = 7,
        inputDistinctDayCount = 5,
        estimatedLowerCount = 2,
        estimatedUpperCount = 4,
        commonWindowStartMinute = 1_320,
        commonWindowEndMinute = 1_439,
        preparedPlanId = PlanId,
        preparedPlanContentRevisionId = RevisionId,
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

    private companion object {
        const val PlanId = "33333333-3333-4333-8333-333333333333"
        const val RevisionId = "44444444-4444-4444-8444-444444444444"
    }
}
