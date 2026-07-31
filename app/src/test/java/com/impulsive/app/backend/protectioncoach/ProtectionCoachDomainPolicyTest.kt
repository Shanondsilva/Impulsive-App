package com.impulsive.app.backend.protectioncoach

import com.impulsive.app.backend.domain.model.adaptive.FeedbackCode
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.protectioncoach.ExistingProtectionWindow
import com.impulsive.app.backend.domain.protectioncoach.OnboardingColdStartPriorPolicy
import com.impulsive.app.backend.domain.protectioncoach.OnboardingColdStartPriorRequest
import com.impulsive.app.backend.domain.protectioncoach.OnboardingColdStartPriorState
import com.impulsive.app.backend.domain.protectioncoach.ProtectionCoachEvidence
import com.impulsive.app.backend.domain.protectioncoach.ProtectionCoachIncidentEvidence
import com.impulsive.app.backend.domain.protectioncoach.ProtectionCoachOnboardingReason
import com.impulsive.app.backend.domain.protectioncoach.ProtectionCoachPolicy
import com.impulsive.app.backend.domain.protectioncoach.ProtectionCoachPolicyVersion
import com.impulsive.app.backend.domain.protectioncoach.ProtectionCoachSuggestion
import com.impulsive.app.backend.domain.protectioncoach.ProtectionCoachSuggestionId
import com.impulsive.app.backend.domain.protectioncoach.ProtectionCoachSuggestionStatus
import com.impulsive.app.backend.domain.protectioncoach.ProtectionCoachSuggestionType
import com.impulsive.app.backend.domain.protectioncoach.ProtectionCoachUnavailableReason
import com.impulsive.app.backend.domain.protectioncoach.SmartProtectionWindowPolicy
import com.impulsive.app.backend.domain.protectioncoach.SmartProtectionWindowRequest
import com.impulsive.app.backend.domain.protectioncoach.SmartProtectionWindowResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class ProtectionCoachDomainPolicyTest {
    @Test
    fun suggestionIdsAreUuidAndPolicyVersionIsExplicit() {
        val id = ProtectionCoachSuggestionId.create()
        val suggestion = ProtectionCoachSuggestion(
            suggestionId = id,
            policyVersion = ProtectionCoachPolicyVersion.Current,
            suggestionType = ProtectionCoachSuggestionType.ReviewProtectedApps,
            createdAtMillis = 1_000L,
            expiresAtMillis = 2_000L,
        )

        assertNotNull(java.util.UUID.fromString(suggestion.suggestionId.value))
        assertEquals(1, suggestion.policyVersion)
    }

    @Test(expected = IllegalArgumentException::class)
    fun acceptedAndDismissedCannotCoexist() {
        ProtectionCoachSuggestion(
            suggestionId = ProtectionCoachSuggestionId.create(),
            suggestionType = ProtectionCoachSuggestionType.CreateEveningWindow,
            createdAtMillis = 1L,
            expiresAtMillis = 2L,
            status = ProtectionCoachSuggestionStatus.Accepted,
            acceptedAtMillis = 3L,
            dismissedAtMillis = 4L,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun acceptedEditedTimesRequireAcceptedWithEditsStatus() {
        ProtectionCoachSuggestion(
            suggestionId = ProtectionCoachSuggestionId.create(),
            suggestionType = ProtectionCoachSuggestionType.CreateEveningWindow,
            createdAtMillis = 1L,
            expiresAtMillis = 2L,
            status = ProtectionCoachSuggestionStatus.AcceptedWithEdits,
            acceptedAtMillis = 3L,
            acceptedStartMinute = 22 * 60,
            acceptedEndMinute = null,
        )
    }

    @Test
    fun priorActsOnlyAsColdStartTieBreaker() {
        val result = OnboardingColdStartPriorPolicy.select(
            OnboardingColdStartPriorRequest(
                eligibleInterventions = setOf(
                    InterventionFamily.PivotGame,
                    InterventionFamily.PivotReading,
                ),
                tiedCandidates = listOf(
                    InterventionFamily.PivotReading,
                    InterventionFamily.PivotGame,
                ),
                preferredFamilies = setOf(InterventionFamily.PivotGame),
                hasStrongRealEvidence = false,
                wrongTimingEvidencePresent = false,
                cueMatchedMomentPlanPresent = false,
                isRandomisedExploration = false,
                state = OnboardingColdStartPriorState(),
            ),
        )

        assertEquals(InterventionFamily.PivotGame, result.selected)
        assertTrue(result.affectedTieBreak)
        assertEquals("This option matched a preference you chose during setup.", result.explanation)
    }

    @Test
    fun priorCannotEnableDisabledFamiliesOrOverrideEvidence() {
        val result = OnboardingColdStartPriorPolicy.select(
            OnboardingColdStartPriorRequest(
                eligibleInterventions = setOf(InterventionFamily.PivotReading),
                tiedCandidates = listOf(
                    InterventionFamily.PivotReading,
                    InterventionFamily.PivotGame,
                ),
                disabledInterventions = setOf(InterventionFamily.PivotGame),
                preferredFamilies = setOf(InterventionFamily.PivotGame),
                hasStrongRealEvidence = true,
                wrongTimingEvidencePresent = false,
                cueMatchedMomentPlanPresent = false,
                isRandomisedExploration = false,
                state = OnboardingColdStartPriorState(),
            ),
        )

        assertNull(result.selected)
        assertFalse(result.affectedTieBreak)
    }

    @Test
    fun priorExpiresAfterDecayLimits() {
        assertTrue(
            OnboardingColdStartPriorPolicy.isExpired(
                OnboardingColdStartPriorState(genuineRootProtectedMomentCount = 10),
            ),
        )
        assertTrue(
            OnboardingColdStartPriorPolicy.isExpired(
                OnboardingColdStartPriorState(substantiveFeedbackCount = 3),
            ),
        )
        assertTrue(
            OnboardingColdStartPriorPolicy.isExpired(
                OnboardingColdStartPriorState(evidenceQualityReachedEarlyPattern = true),
            ),
        )
        assertFalse(OnboardingColdStartPriorPolicy.shouldCountFeedback(FeedbackCode.NotProvided))
    }

    @Test
    fun smartWindowRequiresEvidenceGate() {
        val result = SmartProtectionWindowPolicy.evaluate(
            SmartProtectionWindowRequest(
                incidents = List(6) { eveningIncident(it) },
                nowMillis = millis(LocalDate.of(2026, 1, 20), LocalTime.NOON),
                zoneId = ZoneId.of("Europe/London"),
            ),
        )

        assertEquals(
            SmartProtectionWindowResult.Unavailable(
                ProtectionCoachUnavailableReason.InsufficientRootMoments,
            ),
            result,
        )
    }

    @Test
    fun smartWindowExcludesDuplicatesAndFollowUps() {
        val incidents = List(7) { eveningIncident(it) } +
            eveningIncident(100, duplicate = true) +
            eveningIncident(101, followUp = true)

        val result = SmartProtectionWindowPolicy.evaluate(
            SmartProtectionWindowRequest(
                incidents = incidents,
                nowMillis = millis(LocalDate.of(2026, 1, 20), LocalTime.NOON),
                zoneId = ZoneId.of("Europe/London"),
            ),
        ) as SmartProtectionWindowResult.Suggestion

        assertEquals(7, result.evidence.protectedMomentCount)
    }

    @Test
    fun smartWindowRoundsAndSkipsAdequateSchedule() {
        val request = SmartProtectionWindowRequest(
            incidents = List(7) { eveningIncident(it) },
            nowMillis = millis(LocalDate.of(2026, 1, 20), LocalTime.NOON),
            zoneId = ZoneId.of("Europe/London"),
            existingWindows = listOf(ExistingProtectionWindow(22 * 60, 23 * 60 + 59)),
        )

        assertEquals(
            SmartProtectionWindowResult.Unavailable(
                ProtectionCoachUnavailableReason.EquivalentScheduleAlreadyCoversWindow,
            ),
            SmartProtectionWindowPolicy.evaluate(request),
        )
    }

    @Test
    fun smartWindowDismissalAndSuppressionPreventRepeat() {
        val incidents = List(7) { eveningIncident(it) }
        val now = millis(LocalDate.of(2026, 1, 20), LocalTime.NOON)

        assertEquals(
            SmartProtectionWindowResult.Unavailable(
                ProtectionCoachUnavailableReason.DismissalCooldown,
            ),
            SmartProtectionWindowPolicy.evaluate(
                SmartProtectionWindowRequest(
                    incidents = incidents,
                    nowMillis = now,
                    zoneId = ZoneId.of("Europe/London"),
                    lastDismissedAtMillis = now - ProtectionCoachPolicy().dismissalCooldownMillis + 1,
                ),
            ),
        )
        assertEquals(
            SmartProtectionWindowResult.Unavailable(
                ProtectionCoachUnavailableReason.SuppressedByUser,
            ),
            SmartProtectionWindowPolicy.evaluate(
                SmartProtectionWindowRequest(
                    incidents = incidents,
                    nowMillis = now,
                    zoneId = ZoneId.of("Europe/London"),
                    suppressed = true,
                ),
            ),
        )
    }

    @Test
    fun evidenceStoresNoSourceIdentity() {
        val evidence = ProtectionCoachEvidence(
            protectedMomentCount = 7,
            distinctDayCount = 5,
            onboardingReason = ProtectionCoachOnboardingReason.LateNight,
        )

        assertEquals(7, evidence.protectedMomentCount)
    }

    private fun eveningIncident(
        index: Int,
        duplicate: Boolean = false,
        followUp: Boolean = false,
    ): ProtectionCoachIncidentEvidence =
        ProtectionCoachIncidentEvidence(
            incidentId = "incident-$index",
            createdAtMillis = millis(
                LocalDate.of(2026, 1, 1).plusDays(index.toLong()),
                LocalTime.of(22, 20),
            ),
            isDuplicate = duplicate,
            isFollowUpDecision = followUp,
        )

    private fun millis(date: LocalDate, time: LocalTime): Long =
        LocalDateTime.of(date, time)
            .atZone(ZoneId.of("Europe/London"))
            .toInstant()
            .toEpochMilli()
}
