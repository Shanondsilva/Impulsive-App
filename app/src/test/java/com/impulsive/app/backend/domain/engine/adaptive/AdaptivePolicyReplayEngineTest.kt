package com.impulsive.app.backend.domain.engine.adaptive

import com.impulsive.app.backend.domain.model.adaptive.AdaptiveAssignment
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveDecision
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveReasonCode
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSourceKind
import com.impulsive.app.backend.domain.model.adaptive.AssignmentMode
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentIntensity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptivePolicyReplayEngineTest {
    @Test
    fun syntheticScenarioReplayIsDeterministic() {
        val engine = currentEngine()
        val scenario = repeatedScenario(draw = 0.0, index = 1)

        assertEquals(engine.replay(scenario), engine.replay(scenario))
    }

    @Test
    fun recordedAndReplayedAssignmentsCanBeCompared() {
        val result = currentEngine().replay(
            repeatedScenario(
                recorded = InterventionFamily.MomentPlan,
                draw = 0.99,
            ),
        ) as AdaptiveReplayResult.Compared

        assertEquals(InterventionFamily.MomentPlan, result.difference.recordedAssignedFamily)
        assertEquals(InterventionFamily.PivotGame, result.difference.replayedAssignedFamily)
        assertTrue(result.difference.selectionDiffers)
    }

    @Test
    fun matchingAssignmentIsReportedWithoutDifference() {
        val result = currentEngine().replay(
            repeatedScenario(recorded = InterventionFamily.PivotGame),
        ) as AdaptiveReplayResult.Compared

        assertFalse(result.difference.selectionDiffers)
    }

    @Test
    fun missingHistoricalContextReturnsExplicitInsufficientContext() {
        val result = AdaptiveHistoricalReplayReconstructor.reconstruct(
            historicalDecision(),
            context = null,
        )

        assertTrue(result is AdaptiveReplayResult.InsufficientContext)
    }

    @Test
    fun incompleteHistoricalContextIsNeverGuessed() {
        val result = AdaptiveHistoricalReplayReconstructor.reconstruct(
            historicalDecision(),
            AdaptiveHistoricalReplayContext(
                request = repeatedScenario().request,
                allPolicyInputsReconstructedExactly = false,
            ),
        )

        assertTrue(result is AdaptiveReplayResult.InsufficientContext)
    }

    @Test
    fun mismatchedHistoricalEligibilityIsInsufficient() {
        val decision = historicalDecision()
        val result = AdaptiveHistoricalReplayReconstructor.reconstruct(
            decision,
            AdaptiveHistoricalReplayContext(
                request = repeatedScenario().request.copy(
                    productEligibleInterventions =
                        setOf(InterventionFamily.PivotGame),
                ),
                allPolicyInputsReconstructedExactly = true,
            ),
        )

        assertTrue(result is AdaptiveReplayResult.InsufficientContext)
    }

    @Test
    fun completeHistoricalContextCanReplay() {
        val decision = historicalDecision()
        val request = repeatedScenario().request.copy(
            productEligibleInterventions = decision.assignment.eligibleInterventions,
        )

        val result = AdaptiveHistoricalReplayReconstructor.reconstruct(
            decision,
            AdaptiveHistoricalReplayContext(
                request,
                allPolicyInputsReconstructedExactly = true,
            ),
        )

        assertTrue(result is AdaptiveReplayResult.Compared)
    }

    @Test
    fun aggregateCountsAndPercentageAreCorrect() {
        val engine = currentEngine()
        val comparedSame = engine.replay(
            repeatedScenario(recorded = InterventionFamily.PivotGame),
        )
        val comparedDifferent = engine.replay(
            repeatedScenario(
                id = "different",
                recorded = InterventionFamily.PivotReading,
            ),
        )
        val aggregate = engine.aggregate(
            listOf(
                comparedSame,
                comparedDifferent,
                AdaptiveReplayResult.InsufficientContext("missing"),
            ),
        )

        assertEquals(2, aggregate.replayableScenarios)
        assertEquals(1, aggregate.insufficientContextScenarios)
        assertEquals(50.0, aggregate.familyDifferencePercentage, 0.0)
        assertEquals(2, aggregate.interventionFamilyDistribution.values.sum())
        assertEquals(2, aggregate.reasonCodeDistribution.values.sum())
    }

    @Test
    fun aggregateWithNoReplayableScenarioHasZeroPercentage() {
        val aggregate = currentEngine().aggregate(
            listOf(AdaptiveReplayResult.InsufficientContext("missing")),
        )

        assertEquals(0.0, aggregate.familyDifferencePercentage, 0.0)
    }

    @Test
    fun replayDifferenceContainsOnlyGenericPolicyFields() {
        val fields = AdaptiveReplayDifference::class.java.declaredFields
            .map { it.name.lowercase() }

        listOf(
            "url",
            "domain",
            "package",
            "plantext",
            "futuretext",
            "journal",
            "feedback",
            "utility",
            "sourceidentity",
        ).forEach { forbidden ->
            assertFalse(fields.any { forbidden in it })
        }
    }

    @Test
    fun candidatePolicyVersionIsReportedWithoutChangingRecordedVersion() {
        val engine = AdaptivePolicyReplayEngine(99, CurrentAdaptiveReplayPolicy())
        val result = engine.replay(repeatedScenario()) as AdaptiveReplayResult.Compared

        assertEquals(1, result.difference.recordedPolicyVersion)
        assertEquals(99, result.difference.candidatePolicyVersion)
    }

    @Test
    fun candidateFixtureCanProveComparisonMechanics() {
        val candidate = AdaptiveReplayPolicy { scenario ->
            scenario.request.toStableReadingAssignment()
        }
        val result = AdaptivePolicyReplayEngine(2, candidate)
            .replay(repeatedScenario()) as AdaptiveReplayResult.Compared

        assertEquals(InterventionFamily.PivotReading, result.difference.replayedAssignedFamily)
        assertTrue(result.difference.selectionDiffers)
    }

    @Test
    fun replayAfterEngineRecreationDoesNotMutateScenario() {
        val scenario = repeatedScenario()
        val first = currentEngine().replay(scenario)
        val second = currentEngine().replay(scenario)

        assertEquals(first, second)
        assertEquals(InterventionFamily.PivotGame, scenario.recordedAssignedFamily)
    }

    private fun currentEngine() = AdaptivePolicyReplayEngine(
        AdaptiveRecommendationPolicyVersion.Current,
        CurrentAdaptiveReplayPolicy(),
    )

    private fun repeatedScenario(
        id: String = "repeated",
        recorded: InterventionFamily? = InterventionFamily.PivotGame,
        draw: Double = 0.99,
        index: Int = 0,
    ) = AdaptiveReplayScenario(
        scenarioId = id,
        request = AdaptiveRecommendationRequest(
            momentIntensity = MomentIntensity.RepeatedAttempt,
            selectedCue = null,
        ),
        recordedAssignedFamily = recorded,
        recordedReason = AdaptiveReasonCode.InsufficientEvidenceExploration,
        recordedPolicyVersion = 1,
        deterministicDraw = draw,
        deterministicIndex = index,
    )

    private fun historicalDecision(): AdaptiveDecision {
        val scenario = repeatedScenario()
        return AdaptiveDecision(
            decisionId = DecisionId,
            protectionIncidentToken = "opaque-private-token",
            sourceKind = AdaptiveSourceKind.App,
            createdAtMillis = 1L,
            momentWindowStartedAtMillis = 1L,
            momentCue = null,
            baselineUrgeRating = null,
            assignment = AdaptiveAssignment(
                momentIntensity = MomentIntensity.RepeatedAttempt,
                assignmentMode = AssignmentMode.AdaptiveSuggestion,
                eligibleInterventions = scenario.request.productEligibleInterventions,
                assignedSuggestion = InterventionFamily.PivotGame,
                selectionProbability = null,
                reasonCode = AdaptiveReasonCode.InsufficientEvidenceExploration,
            ),
            observationDeadlineAtMillis = 2L,
        )
    }

    private fun AdaptiveRecommendationRequest.toStableReadingAssignment() =
        AdaptiveAssignment(
            momentIntensity = momentIntensity,
            assignmentMode = AssignmentMode.AdaptiveSuggestion,
            eligibleInterventions = productEligibleInterventions,
            assignedSuggestion = InterventionFamily.PivotReading,
            selectionProbability = null,
            reasonCode = AdaptiveReasonCode.InsufficientEvidenceExploration,
        )

    private companion object {
        const val DecisionId = "00000000-0000-0000-0000-000000007101"
    }
}
