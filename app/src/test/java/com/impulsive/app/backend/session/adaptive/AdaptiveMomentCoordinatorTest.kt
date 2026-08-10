package com.impulsive.app.backend.session.adaptive

import com.impulsive.app.backend.domain.engine.adaptive.AdaptiveRecommendationPolicyVersion
import com.impulsive.app.backend.domain.model.adaptive.AdaptivePreferences
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveReasonCode
import com.impulsive.app.backend.domain.model.adaptive.AssignmentMode
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentCue
import com.impulsive.app.backend.domain.model.adaptive.MomentIntensity
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanActionType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveMomentCoordinatorTest {
    @Test
    fun newDecisionRecordsPolicyAndAssignedProtocolPassport() = runBlocking {
        val decisions = FakeDecisionRepository()

        coordinatorHarness(decisions = decisions).coordinate(incident())

        val passport = decisions.stored.single()
        assertEquals(
            AdaptiveRecommendationPolicyVersion.Current,
            passport.recommendationPolicyVersion,
        )
        // First-attempt protocol is now the game; Short Pause is retired.
        assertEquals("pivot_game", passport.assignedProtocolId)
        assertEquals(1, passport.assignedProtocolVersion)
        assertEquals(
            InterventionFamily.PivotGame.eligibilityBit,
            passport.assignment.eligibleInterventionsMask,
        )
    }

    @Test
    fun momentPlanAssignmentRecordsEligibleCountProtocolAndExactRevision() = runBlocking {
        val plan = momentPlan()
        val decisions = FakeDecisionRepository()
        val coordinator = coordinatorHarness(
            decisions = decisions,
            plans = FakeMomentPlanRepository(listOf(plan)),
        )
        coordinator.coordinate(incident(token = "first"))

        coordinator.coordinate(
            incident(
                token = "second",
                at = 1_000_100L,
                allowed = setOf(InterventionFamily.MomentPlan),
            ),
        )

        val passport = decisions.stored.last()
        assertEquals(1, passport.eligibleMomentPlanCount)
        assertEquals("moment_plan_text", passport.assignedProtocolId)
        assertEquals(1, passport.assignedProtocolVersion)
        assertEquals(plan.planId, passport.assignment.momentPlanId)
        assertEquals(
            plan.contentRevisionId,
            passport.assignment.assignedPlanContentRevisionId,
        )
    }

    @Test
    fun followUpDecisionReceivesSeparatePassport() = runBlocking {
        val decisions = FakeDecisionRepository()
        val coordinator = coordinatorHarness(decisions = decisions)

        coordinator.coordinate(incident(token = "first"))
        coordinator.coordinate(
            incident(
                token = "second",
                at = 1_000_100L,
                allowed = setOf(InterventionFamily.PivotGame),
            ),
        )

        assertEquals(2, decisions.stored.size)
        assertFalse(decisions.stored[0].decisionId == decisions.stored[1].decisionId)
        assertEquals("pivot_game", decisions.stored[0].assignedProtocolId)
        assertEquals("pivot_game", decisions.stored[1].assignedProtocolId)
    }

    @Test
    fun noPreviousDecisionProducesFirstAttempt() = runBlocking {
        val result = coordinatorHarness().coordinate(incident())
        assertEquals(MomentIntensity.FirstAttempt, result.presentation.momentIntensity)
    }

    @Test
    fun incidentLessThanTwentyMinutesLaterProducesRepeatedAttempt() = runBlocking {
        val decisions = FakeDecisionRepository()
        val coordinator = coordinatorHarness(decisions = decisions)
        coordinator.coordinate(incident(token = "one", at = 1_000_000L))
        val result = coordinator.coordinate(
            incident(token = "two", at = 2_199_999L),
        )
        assertEquals(MomentIntensity.RepeatedAttempt, result.presentation.momentIntensity)
    }

    @Test
    fun incidentExactlyTwentyMinutesLaterProducesFirstAttempt() = runBlocking {
        val decisions = FakeDecisionRepository()
        val coordinator = coordinatorHarness(decisions = decisions)
        coordinator.coordinate(incident(token = "one", at = 1_000_000L))
        val result = coordinator.coordinate(
            incident(token = "two", at = 2_200_000L),
        )
        assertEquals(MomentIntensity.FirstAttempt, result.presentation.momentIntensity)
    }

    @Test
    fun earlyEpochIncidentStillFindsEarlierDecision() = runBlocking {
        val decisions = FakeDecisionRepository()
        val coordinator = coordinatorHarness(decisions = decisions)
        coordinator.coordinate(incident(token = "one", at = 100L))
        val result = coordinator.coordinate(incident(token = "two", at = 200L))
        assertEquals(MomentIntensity.RepeatedAttempt, result.presentation.momentIntensity)
    }

    @Test
    fun futureTimestampFailsSafely() = runBlocking {
        val result = coordinatorHarness(clock = FakeClock(1_000L)).coordinate(
            incident(at = 1_001L),
        )
        assertFalse(result.persisted)
        assertEquals(AdaptiveMomentFailure.InvalidIncident, result.failure)
    }

    @Test
    fun negativeTimestampFailsSafely() = runBlocking {
        val result = coordinatorHarness().coordinate(incident(at = -1L))
        assertTrue(result.presentation.stableFallback)
        assertFalse(result.persisted)
    }

    @Test
    fun blankIncidentTokenFailsSafely() = runBlocking {
        val result = coordinatorHarness().coordinate(incident(token = " "))
        assertEquals(AdaptiveMomentFailure.InvalidIncident, result.failure)
    }

    @Test
    fun duplicateIncidentReturnsSameDecision() = runBlocking {
        val decisions = FakeDecisionRepository()
        val coordinator = coordinatorHarness(decisions = decisions)
        val first = coordinator.coordinate(incident())
        val second = coordinator.coordinate(incident())
        assertEquals(first.presentation.decisionId, second.presentation.decisionId)
        assertTrue(second.duplicateIncident)
    }

    @Test
    fun serviceStyleRepeatedInvocationCreatesOnlyOneDecision() = runBlocking {
        val decisions = FakeDecisionRepository()
        val coordinator = coordinatorHarness(decisions = decisions)
        repeat(10) { coordinator.coordinate(incident()) }
        assertEquals(1, decisions.stored.size)
        assertEquals(1, decisions.insertCalls)
    }

    @Test
    /**
     * A first attempt used to be answered with a Short Pause. Short Pause is
     * retired as an active intervention, so minimum friction is now the game.
     */
    fun firstAttemptAlwaysSelectsTheGame() = runBlocking {
        val result = coordinatorHarness().coordinate(incident())
        assertEquals(InterventionFamily.PivotGame, result.presentation.assignedIntervention)
        assertEquals(AssignmentMode.MinimumFriction, result.presentation.assignmentMode)
    }

    @Test
    fun firstAttemptNeverRandomises() = runBlocking {
        val result = coordinatorHarness(
            random = FakeRandomisationSource(
                doubles = ArrayDeque(listOf(0.0)),
                ints = ArrayDeque(listOf(2)),
            ),
        ).coordinate(incident())
        assertEquals(AssignmentMode.MinimumFriction, result.presentation.assignmentMode)
        assertNull(
            FakeDecisionRepository().stored.firstOrNull()
                ?.assignment?.selectionProbability,
        )
    }

    @Test
    fun personalSuggestionsDisabledReturnsRepeatedFallback() = runBlocking {
        val decisions = FakeDecisionRepository()
        val preferences = FakePreferenceRepository(
            AdaptivePreferences(personalSuggestionsEnabled = false),
        )
        val coordinator = coordinatorHarness(decisions, preferences)
        coordinator.coordinate(incident(token = "one"))
        val result = coordinator.coordinate(incident(token = "two", at = 1_000_100L))
        assertTrue(result.presentation.stableFallback)
        assertTrue(result.persisted)
    }

    @Test
    fun disabledGameIsExcluded() = runBlocking {
        val result = repeatedResult(
            preferences = AdaptivePreferences(gameSuggestionsEnabled = false),
            allowed = setOf(InterventionFamily.PivotGame),
        )
        assertFalse(InterventionFamily.PivotGame in result.presentation.eligibleInterventions)
    }

    @Test
    fun disabledReadingIsExcluded() = runBlocking {
        val result = repeatedResult(
            preferences = AdaptivePreferences(readingSuggestionsEnabled = false),
            allowed = setOf(InterventionFamily.PivotReading),
        )
        assertFalse(InterventionFamily.PivotReading in result.presentation.eligibleInterventions)
    }

    @Test
    fun disabledMomentPlanPreferenceExcludesMomentPlan() = runBlocking {
        val result = repeatedResult(
            preferences = AdaptivePreferences(momentPlanSuggestionsEnabled = false),
            allowed = setOf(InterventionFamily.MomentPlan),
            plans = listOf(momentPlan()),
        )
        assertFalse(InterventionFamily.MomentPlan in result.presentation.eligibleInterventions)
    }

    @Test
    fun noEnabledValidPlanExcludesMomentPlan() = runBlocking {
        val result = repeatedResult(
            allowed = setOf(InterventionFamily.MomentPlan),
            plans = listOf(momentPlan(enabled = false)),
        )
        assertTrue(result.presentation.eligibleInterventions.isEmpty())
    }

    @Test
    fun invalidPlanActionExcludesMomentPlan() = runBlocking {
        val result = repeatedResult(
            allowed = setOf(InterventionFamily.MomentPlan),
            plans = listOf(
                momentPlan(
                    actionType = MomentPlanActionType.OpenImpulsiveDestination,
                    target = "arbitrary",
                ),
            ),
        )
        assertFalse(InterventionFamily.MomentPlan in result.presentation.eligibleInterventions)
    }

    @Test
    fun noRepeatedCandidateReturnsStableFallback() = runBlocking {
        val result = repeatedResult(allowed = emptySet())
        assertEquals(AssignmentMode.StableFallback, result.presentation.assignmentMode)
        assertNull(result.presentation.assignedIntervention)
    }

    @Test
    fun oneCandidateUsesOnlyEligibleReason() = runBlocking {
        val result = repeatedResult(allowed = setOf(InterventionFamily.PivotGame))
        assertEquals(InterventionFamily.PivotGame, result.presentation.assignedIntervention)
        assertEquals(AdaptiveReasonCode.OnlyEligibleIntervention, result.presentation.reasonCode)
    }

    @Test
    fun randomisedProbabilityIsHalfForTwoCandidates() = runBlocking {
        val decisions = FakeDecisionRepository()
        val coordinator = coordinatorHarness(
            decisions = decisions,
            random = FakeRandomisationSource(
                doubles = ArrayDeque(listOf(0.0)),
                ints = ArrayDeque(listOf(0)),
            ),
        )
        coordinator.coordinate(incident(token = "one"))
        val result = coordinator.coordinate(
            incident(
                token = "two",
                at = 1_000_100L,
                allowed = setOf(
                    InterventionFamily.PivotGame,
                    InterventionFamily.PivotReading,
                ),
            ),
        )
        assertEquals(AssignmentMode.RandomisedSuggestion, result.presentation.assignmentMode)
        assertEquals(0.5, decisions.stored.last().assignment.selectionProbability!!, 0.0)
    }

    @Test
    fun randomisedProbabilityIsThirdForThreeCandidates() = runBlocking {
        val decisions = FakeDecisionRepository()
        val coordinator = coordinatorHarness(
            decisions = decisions,
            plans = FakeMomentPlanRepository(listOf(momentPlan())),
            random = FakeRandomisationSource(
                doubles = ArrayDeque(listOf(0.0)),
                ints = ArrayDeque(listOf(2)),
            ),
        )
        coordinator.coordinate(incident(token = "one"))
        coordinator.coordinate(incident(token = "two", at = 1_000_100L))
        assertEquals(1.0 / 3.0, decisions.stored.last().assignment.selectionProbability!!, 0.0)
    }

    @Test
    fun adaptivePolicyReceivesOnlyEligibleCandidates() = runBlocking {
        val result = repeatedResult(
            allowed = setOf(InterventionFamily.PivotReading),
            random = FakeRandomisationSource(doubles = ArrayDeque(listOf(0.9))),
        )
        assertEquals(setOf(InterventionFamily.PivotReading), result.presentation.eligibleInterventions)
    }

    @Test
    fun matchingPreferredCueSelectsCorrectValidPlan() = runBlocking {
        val matching = momentPlan(
            id = java.util.UUID.nameUUIDFromBytes("matching".toByteArray()).toString(),
            cue = MomentCue.Stress,
            preferred = true,
        )
        val result = repeatedResult(
            cue = MomentCue.Stress,
            allowed = setOf(InterventionFamily.MomentPlan),
            plans = listOf(momentPlan(cue = MomentCue.Boredom), matching),
        )
        assertEquals(matching.planId, result.presentation.selectedMomentPlanId)
    }

    @Test
    fun skippedCueDoesNotInventMatch() = runBlocking {
        val preferred = momentPlan(
            id = java.util.UUID.nameUUIDFromBytes("preferred".toByteArray()).toString(),
            cue = MomentCue.Stress,
            preferred = true,
        )
        val result = repeatedResult(
            cue = null,
            allowed = setOf(InterventionFamily.MomentPlan),
            plans = listOf(preferred),
        )
        assertNull(result.presentation.confirmedCue)
        assertEquals(preferred.planId, result.presentation.selectedMomentPlanId)
    }

    @Test
    fun disabledPreferredPlanIsNeverSelected() = runBlocking {
        val disabled = momentPlan(
            id = java.util.UUID.nameUUIDFromBytes("disabled".toByteArray()).toString(),
            cue = MomentCue.Stress,
            enabled = false,
            preferred = true,
        )
        val enabled = momentPlan(
            id = java.util.UUID.nameUUIDFromBytes("enabled".toByteArray()).toString(),
            cue = MomentCue.Stress,
        )
        val result = repeatedResult(
            cue = MomentCue.Stress,
            allowed = setOf(InterventionFamily.MomentPlan),
            plans = listOf(disabled, enabled),
        )
        assertEquals(enabled.planId, result.presentation.selectedMomentPlanId)
    }

    @Test
    fun laterIncidentMarksPriorOpenDecisionRepeatedBeforeCreation() = runBlocking {
        val decisions = FakeDecisionRepository()
        val coordinator = coordinatorHarness(decisions = decisions)
        coordinator.coordinate(incident(token = "one"))
        coordinator.coordinate(incident(token = "two", at = 1_000_100L))
        assertEquals(
            com.impulsive.app.backend.domain.model.adaptive.RepeatObservation.RepeatDetected,
            decisions.stored.first().repeatObservation,
        )
        assertEquals(1_000_100L, decisions.stored.first().firstRepeatAtMillis)
    }

    private suspend fun repeatedResult(
        preferences: AdaptivePreferences = AdaptivePreferences(),
        allowed: Set<InterventionFamily>,
        plans: List<com.impulsive.app.backend.domain.model.adaptive.MomentPlan> = emptyList(),
        cue: MomentCue? = null,
        random: FakeRandomisationSource = FakeRandomisationSource(),
    ): AdaptiveMomentCoordinationResult {
        val decisions = FakeDecisionRepository()
        val coordinator = coordinatorHarness(
            decisions = decisions,
            preferences = FakePreferenceRepository(preferences),
            plans = FakeMomentPlanRepository(plans),
            random = random,
        )
        coordinator.coordinate(incident(token = "one", cue = cue))
        return coordinator.coordinate(
            incident(
                token = "two",
                at = 1_000_100L,
                cue = cue,
                allowed = allowed,
            ),
        )
    }
}
