package com.impulsive.app.backend.domain.engine.adaptive

import com.impulsive.app.backend.domain.model.adaptive.EngagementOutcome
import com.impulsive.app.backend.domain.model.adaptive.FamiliarStepEvidenceRecord
import com.impulsive.app.backend.domain.model.adaptive.FamiliarStepMatchResult
import com.impulsive.app.backend.domain.model.adaptive.FamiliarStepNoMatchReason
import com.impulsive.app.backend.domain.model.adaptive.FamiliarStepRouteIdentity
import com.impulsive.app.backend.domain.model.adaptive.FeedbackCode
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentCue
import com.impulsive.app.backend.domain.model.adaptive.MomentIntensity
import com.impulsive.app.backend.domain.model.adaptive.RepeatObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FamiliarStepMatcherTest {
    @Test
    fun firstAttemptAndDisabledPreferenceNeverMatch() {
        assertReason(input().copy(momentIntensity = MomentIntensity.FirstAttempt), FamiliarStepNoMatchReason.FirstAttempt)
        assertReason(input().copy(personalSuggestionsEnabled = false), FamiliarStepNoMatchReason.PersonalSuggestionsDisabled)
    }

    @Test
    fun qualificationRequiresFourComparableThreeFavourableAndStrictMajority() {
        assertReason(input(evidence = evidence(3, 3)), FamiliarStepNoMatchReason.InsufficientEvidence)
        assertReason(input(evidence = evidence(4, 2)), FamiliarStepNoMatchReason.NoFavourableMajority)
        val match = FamiliarStepMatcher.match(input(evidence = evidence(4, 3)))
            as FamiliarStepMatchResult.Match
        assertEquals(4, match.candidate.comparableCount)
        assertEquals(3, match.candidate.favourableCount)
    }

    @Test
    fun wrongTimingPoorEngagementAndRepeatAreNeverFavourable() {
        val records = listOf(
            record(1).copy(feedbackCode = FeedbackCode.WrongTiming),
            record(2).copy(feedbackCode = FeedbackCode.DidNotHelp),
            record(3).copy(engagementOutcome = EngagementOutcome.Dismissed),
            record(4).copy(repeatObservation = RepeatObservation.RepeatDetected),
        )
        assertReason(input(evidence = records), FamiliarStepNoMatchReason.NoFavourableMajority)
    }

    @Test
    fun cueMatchedGroupWinsOnlyWithFourComparableRecords() {
        val cueRecords = evidence(4, 3, cue = MomentCue.Boredom)
        val broadBetter = evidence(6, 6, identity = gameIdentity, cue = MomentCue.Stress, offset = 20)
        val match = FamiliarStepMatcher.match(
            input(evidence = cueRecords + broadBetter, cue = MomentCue.Boredom),
        ) as FamiliarStepMatchResult.Match
        assertEquals(pauseIdentity, match.candidate.routeIdentity)
        assertEquals(MomentCue.Boredom, match.candidate.matchedCue)
    }

    @Test
    fun deterministicSortUsesRateCountsRecencyThenStableIdentity() {
        val lowerRate = evidence(5, 3, identity = pauseIdentity)
        val higherRate = evidence(4, 3, identity = gameIdentity, offset = 20)
        repeat(5) {
            val match = FamiliarStepMatcher.match(input(evidence = lowerRate + higherRate))
                as FamiliarStepMatchResult.Match
            assertEquals(gameIdentity, match.candidate.routeIdentity)
        }
    }

    @Test
    fun emptyEvidenceIsInsufficientRatherThanNoEligibleRoute() {
        assertReason(input(evidence = emptyList()), FamiliarStepNoMatchReason.InsufficientEvidence)
    }

    @Test
    fun typedSafetyAndStalenessReasonsAreReturned() {
        assertReason(input(eligible = emptySet()), FamiliarStepNoMatchReason.NoEligibleRoute)
        assertReason(input().copy(privacySafeEvidence = false), FamiliarStepNoMatchReason.PrivacyUnsafeEvidence)
        assertReason(input().copy(currentProtocolIdentities = emptySet()), FamiliarStepNoMatchReason.StaleProtocol)
        val planRecords = evidence(4, 3, identity = planIdentity)
        assertReason(
            input(evidence = planRecords).copy(currentMomentPlanRevisions = mapOf("plan" to "new")),
            FamiliarStepNoMatchReason.StalePlanRevision,
        )
        assertTrue(FamiliarStepQualificationPolicy.MaximumInspectedRecords == 30)
    }

    private fun assertReason(input: FamiliarStepMatchInput, reason: FamiliarStepNoMatchReason) {
        assertEquals(FamiliarStepMatchResult.NoMatch(reason), FamiliarStepMatcher.match(input))
    }

    private fun input(
        evidence: List<FamiliarStepEvidenceRecord> = evidence(4, 3),
        cue: MomentCue? = null,
        eligible: Set<InterventionFamily> = setOf(
            InterventionFamily.ShortPause,
            InterventionFamily.PivotGame,
            InterventionFamily.MomentPlan,
        ),
    ) = FamiliarStepMatchInput(
        momentIntensity = MomentIntensity.RepeatedAttempt,
        personalSuggestionsEnabled = true,
        eligibleInterventions = eligible,
        currentMomentCue = cue,
        evidence = evidence,
        currentProtocolIdentities = setOf("pause" to 1, "game" to 1, "plan-protocol" to 1),
        currentMomentPlanRevisions = mapOf("plan" to "revision"),
    )

    private fun evidence(
        count: Int,
        favourable: Int,
        identity: FamiliarStepRouteIdentity = pauseIdentity,
        cue: MomentCue? = null,
        offset: Int = 0,
    ) = List(count) { index ->
        record((offset + index).toLong(), identity, cue).let { record ->
            if (index < favourable) record
            else record.copy(feedbackCode = FeedbackCode.DidNotHelp)
        }
    }

    private fun record(
        id: Long,
        identity: FamiliarStepRouteIdentity = pauseIdentity,
        cue: MomentCue? = null,
    ) = FamiliarStepEvidenceRecord(
        decisionId = "decision-$id",
        routeIdentity = identity,
        momentCue = cue,
        feedbackCode = FeedbackCode.Helped,
        engagementOutcome = EngagementOutcome.Completed,
        repeatObservation = RepeatObservation.NoRepeatDetected,
        decisionAtMillis = id,
        finalisedAtMillis = id + 100,
    )

    private companion object {
        val pauseIdentity = FamiliarStepRouteIdentity(InterventionFamily.ShortPause, "pause", 1)
        val gameIdentity = FamiliarStepRouteIdentity(InterventionFamily.PivotGame, "game", 1)
        val planIdentity = FamiliarStepRouteIdentity(
            InterventionFamily.MomentPlan,
            "plan-protocol",
            1,
            "plan",
            "revision",
        )
    }
}
