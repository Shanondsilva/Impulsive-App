package com.impulsive.app.backend.domain.engine.adaptive

import com.impulsive.app.backend.domain.model.adaptive.AdaptiveAssignment
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveDecision
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveReasonCode
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSourceKind
import com.impulsive.app.backend.domain.model.adaptive.AssignmentMode
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentCue
import com.impulsive.app.backend.domain.model.adaptive.MomentIntensity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveDecisionExplanationTest {
    @Test
    fun explanationReflectsStoredReasonCodeAndPolicyVersion() {
        val explanation = AdaptiveDecisionExplanationBuilder.build(
            decision(
                reason = AdaptiveReasonCode.RecentHelpfulFeedback,
                policyVersion = 7,
            ),
        )

        assertEquals(
            "This option fitted some of your recent recorded moments.",
            explanation.whySuggested,
        )
        assertEquals(7, explanation.recommendationPolicyVersion)
    }

    @Test
    fun explanationListsOnlyApplicableFactors() {
        val explanation = AdaptiveDecisionExplanationBuilder.build(
            decision(reason = AdaptiveReasonCode.RecentCompletionPattern),
        )

        assertEquals(
            listOf(
                "This was a repeated protected moment.",
                "Enabled support families: Pivot Games, Reset Reading.",
                "Recent completion history.",
            ),
            explanation.factorsUsed,
        )
        assertFalse(explanation.factorsUsed.any { "feedback" in it.lowercase() })
        assertFalse(explanation.factorsUsed.any { "practice" in it.lowercase() })
    }

    @Test
    fun selectedCueIsIncludedWithoutAddingUnrelatedFactors() {
        val explanation = AdaptiveDecisionExplanationBuilder.build(
            decision(
                reason = AdaptiveReasonCode.CueMatchedMomentPlan,
                cue = MomentCue.Boredom,
            ),
        )

        assertTrue(explanation.factorsUsed.contains("The cue you selected: Boredom."))
        assertFalse(explanation.factorsUsed.any { "recent practice" in it.lowercase() })
    }

    @Test
    fun prohibitedDataSourcesAreAlwaysListedAsUnused() {
        assertEquals(
            listOf(
                "The protected app or website identity.",
                "A URL or domain.",
                "Journal content.",
                "Your account email.",
                "A cloud behavioural profile.",
            ),
            AdaptiveDecisionExplanationBuilder.build(decision()).factorsNotUsed,
        )
    }

    @Test
    fun privateIncidentTokenNeverAppearsInExplanation() {
        val rendered = AdaptiveDecisionExplanationBuilder.build(decision()).allCopy()

        assertFalse(rendered.contains(PrivateIncidentToken))
        assertFalse(rendered.contains("com.private.example"))
        assertFalse(rendered.contains("private.example/path"))
    }

    @Test
    fun consumerCopyContainsNoScoresCausalOrMedicalClaims() {
        val rendered = AdaptiveDecisionExplanationBuilder.build(
            decision(reason = AdaptiveReasonCode.RecentHelpfulFeedback),
        ).allCopy().lowercase()

        listOf(
            "utility",
            "probability",
            "confidence",
            "statistical significance",
            "caused",
            "causal",
            "clinical",
            "medical",
            "treatment",
            "diagnosis",
            "best intervention",
        ).forEach { forbidden ->
            assertFalse("Found forbidden wording: $forbidden", forbidden in rendered)
        }
    }

    @Test
    fun historicalFutureProtocolDisplaysGenericallyAndDoesNotExecute() {
        val explanation = AdaptiveDecisionExplanationBuilder.build(
            decision(protocolId = "pivot_game", protocolVersion = 99),
        )

        assertEquals(
            "Pivot Game (historical version)",
            explanation.historicalProtocolDisplay,
        )
        assertNull(
            InterventionProtocolRegistry.resolveExecutable(
                InterventionProtocolId("pivot_game"),
                InterventionProtocolVersion(99),
            ),
        )
    }

    @Test
    fun unknownProtocolIdentifierHasOnlyGenericHistoricalDisplay() {
        val explanation = AdaptiveDecisionExplanationBuilder.build(
            decision(protocolId = "retired_support", protocolVersion = 1),
        )

        assertEquals(
            "Historical personal support",
            explanation.historicalProtocolDisplay,
        )
    }

    private fun AdaptiveDecisionExplanation.allCopy(): String =
        listOfNotNull(
            whySuggested,
            factorsUsed.joinToString(),
            factorsNotUsed.joinToString(),
            recommendationPolicyVersion.toString(),
            historicalProtocolDisplay,
        ).joinToString(" ")

    private fun decision(
        reason: AdaptiveReasonCode = AdaptiveReasonCode.StableFallback,
        policyVersion: Int = 1,
        cue: MomentCue? = null,
        protocolId: String? = "pivot_game",
        protocolVersion: Int? = 1,
    ) = AdaptiveDecision(
        decisionId = DecisionId,
        protectionIncidentToken = PrivateIncidentToken,
        sourceKind = AdaptiveSourceKind.App,
        createdAtMillis = 1_000L,
        momentWindowStartedAtMillis = 1_000L,
        momentCue = cue,
        baselineUrgeRating = null,
        assignment = AdaptiveAssignment(
            momentIntensity = MomentIntensity.RepeatedAttempt,
            assignmentMode = AssignmentMode.AdaptiveSuggestion,
            eligibleInterventions = setOf(
                InterventionFamily.PivotReading,
                InterventionFamily.PivotGame,
            ),
            assignedSuggestion = InterventionFamily.PivotGame,
            selectionProbability = null,
            reasonCode = reason,
        ),
        observationDeadlineAtMillis = 2_000L,
        recommendationPolicyVersion = policyVersion,
        assignedProtocolId = protocolId,
        assignedProtocolVersion = protocolVersion,
    )

    private companion object {
        const val DecisionId = "00000000-0000-0000-0000-000000000401"
        const val PrivateIncidentToken =
            "opaque-com.private.example-private.example/path"
    }
}
