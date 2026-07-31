package com.impulsive.app.backend.domain.engine.adaptive

import com.impulsive.app.backend.domain.model.adaptive.AdaptiveAssignment
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveDecision
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveReasonCode
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSourceKind
import com.impulsive.app.backend.domain.model.adaptive.AssignmentMode
import com.impulsive.app.backend.domain.model.adaptive.FeedbackCode
import com.impulsive.app.backend.domain.model.adaptive.ImpulsiveDestination
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentCue
import com.impulsive.app.backend.domain.model.adaptive.MomentIntensity
import com.impulsive.app.backend.domain.model.adaptive.MomentPlan
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanActionType
import com.impulsive.app.backend.domain.model.adaptive.RepeatObservation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveModelValidatorTest {
    @Test
    fun validModelsHaveNoValidationIssues() {
        assertTrue(AdaptiveModelValidator.validate(validPlan()).isEmpty())
        assertTrue(AdaptiveModelValidator.validate(validDecision()).isEmpty())
    }

    @Test
    fun textLimitsAreEnforcedByUnicodeCharacterCount() {
        val oversized = validPlan().copy(
            title = "x".repeat(61),
            actionText = "x".repeat(161),
            futureCueText = "x".repeat(181),
        )

        val fields = AdaptiveModelValidator.validate(oversized).map { it.field }.toSet()

        assertTrue("title" in fields)
        assertTrue("actionText" in fields)
        assertTrue("futureCueText" in fields)
    }

    @Test
    fun urgeRatingAndSelectionProbabilityRangesAreEnforced() {
        val invalid = validDecision().copy(
            baselineUrgeRating = 11,
            assignment = validDecision().assignment.copy(selectionProbability = 0.0),
        )

        val fields = AdaptiveModelValidator.validate(invalid).map { it.field }.toSet()

        assertTrue("baselineUrgeRating" in fields)
        assertTrue("selectionProbability" in fields)
    }

    @Test
    fun completionAndDismissalCannotBothBeRecorded() {
        val invalid = validDecision().copy(
            completedAtMillis = 1_300L,
            dismissedAtMillis = 1_400L,
        )

        val issues = AdaptiveModelValidator.validate(invalid)

        assertTrue(issues.any { it.field == "outcome" })
    }

    @Test
    fun timestampsCannotMoveBackwards() {
        val invalid = validDecision().copy(
            presentedAtMillis = 900L,
            startedAtMillis = 800L,
            observationDeadlineAtMillis = 999L,
        )

        val fields = AdaptiveModelValidator.validate(invalid).map { it.field }.toSet()

        assertTrue("presentedAtMillis" in fields)
        assertTrue("startedAtMillis" in fields)
        assertTrue("observationDeadlineAtMillis" in fields)
    }

    @Test
    fun actionTargetsUseExplicitAllowlistAndRejectUrls() {
        val approved = validPlan().copy(
            actionType = MomentPlanActionType.OpenImpulsiveDestination,
            actionTarget = ImpulsiveDestination.Journal.storageValue,
        )
        val arbitraryUrl = approved.copy(actionTarget = "https://example.com")

        assertTrue(AdaptiveModelValidator.validate(approved).isEmpty())
        assertFalse(AdaptiveModelValidator.validate(arbitraryUrl).isEmpty())
    }

    @Test
    fun selectedApplicationRequiresPackageNameRatherThanIntentOrDeepLink() {
        val packageAction = validPlan().copy(
            actionType = MomentPlanActionType.LaunchSelectedApp,
            actionTarget = "com.example.project",
        )
        val unrestrictedIntent = packageAction.copy(
            actionTarget = "intent://project#Intent;scheme=https;end",
        )

        assertTrue(AdaptiveModelValidator.validate(packageAction).isEmpty())
        assertFalse(AdaptiveModelValidator.validate(unrestrictedIntent).isEmpty())
    }

    @Test
    fun planCollectionEnforcesEnabledLimitUniqueIdsAndOnePreferredPlanPerCue() {
        val plans = (1..7).map { index ->
            validPlan().copy(
                planId = "00000000-0000-0000-0000-${index.toString().padStart(12, '0')}",
                preferredForCue = index <= 2,
            )
        }
        val issues = AdaptiveModelValidator.validate(plans)

        assertTrue(issues.any { "six enabled plans" in it.message })
        assertTrue(issues.any { "preferred for each cue" in it.message })

        val duplicateIdPlans = listOf(validPlan(), validPlan())
        assertTrue(
            AdaptiveModelValidator.validate(duplicateIdPlans).any {
                "duplicate plan IDs" in it.message
            },
        )
    }

    @Test
    fun finalisedObservationRequiresResolvedRepeatSignal() {
        val invalid = validDecision().copy(
            repeatObservation = RepeatObservation.NotFinalised,
            observationFinalisedAtMillis = 2_300L,
        )

        assertTrue(
            AdaptiveModelValidator.validate(invalid).any {
                it.field == "repeatObservation"
            },
        )
    }

    private fun validPlan(): MomentPlan = MomentPlan(
        planId = "00000000-0000-0000-0000-000000000001",
        title = "My project",
        momentCue = MomentCue.Boredom,
        actionText = "Open my project for two minutes.",
        futureCueText = "Tomorrow morning, I want to feel clear.",
        actionType = MomentPlanActionType.TextOnly,
        actionTarget = null,
        enabled = true,
        preferredForCue = true,
        createdAtMillis = 1_000L,
        updatedAtMillis = 1_100L,
    )

    private fun validDecision(): AdaptiveDecision = AdaptiveDecision(
        decisionId = "00000000-0000-0000-0000-000000000010",
        protectionIncidentToken = "incident-10",
        sourceKind = AdaptiveSourceKind.App,
        createdAtMillis = 1_000L,
        momentWindowStartedAtMillis = 1_000L,
        momentCue = MomentCue.Boredom,
        baselineUrgeRating = 5,
        assignment = AdaptiveAssignment(
            momentIntensity = MomentIntensity.RepeatedAttempt,
            assignmentMode = AssignmentMode.AdaptiveSuggestion,
            eligibleInterventions = setOf(
                InterventionFamily.PivotGame,
                InterventionFamily.PivotReading,
            ),
            assignedSuggestion = InterventionFamily.PivotGame,
            selectionProbability = null,
            reasonCode = AdaptiveReasonCode.InsufficientEvidenceExploration,
        ),
        presentedAtMillis = 1_100L,
        startedAtMillis = 1_200L,
        completedAtMillis = 1_300L,
        feedbackCode = FeedbackCode.Helped,
        feedbackUpdatedAtMillis = 1_400L,
        repeatObservation = RepeatObservation.NoRepeatDetected,
        observationDeadlineAtMillis = 2_200L,
        observationFinalisedAtMillis = 2_300L,
    )
}
