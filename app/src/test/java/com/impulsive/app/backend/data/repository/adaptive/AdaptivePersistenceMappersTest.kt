package com.impulsive.app.backend.data.repository.adaptive

import com.impulsive.app.backend.data.local.entity.AdaptiveDecisionEntity
import com.impulsive.app.backend.data.local.entity.AdaptivePreferenceEntity
import com.impulsive.app.backend.data.local.entity.MomentPlanEntity
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveAssignment
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveDecision
import com.impulsive.app.backend.domain.model.adaptive.AdaptivePreferences
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveReasonCode
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSourceKind
import com.impulsive.app.backend.domain.model.adaptive.AssignmentMode
import com.impulsive.app.backend.domain.model.adaptive.FeedbackCode
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentCue
import com.impulsive.app.backend.domain.model.adaptive.MomentIntensity
import com.impulsive.app.backend.domain.model.adaptive.MomentPlan
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanActionType
import com.impulsive.app.backend.domain.model.adaptive.RepeatObservation
import com.impulsive.app.backend.domain.engine.adaptive.AdaptiveHistoryRetentionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptivePersistenceMappersTest {
    @Test
    fun decisionRoundTripPreservesEverySupportedEnumValue() {
        AdaptiveSourceKind.entries.forEachIndexed { sourceIndex, source ->
            MomentIntensity.entries.forEachIndexed { intensityIndex, intensity ->
                val decision = validDecision(
                    id = sourceIndex * 10 + intensityIndex + 1,
                    source = source,
                    intensity = intensity,
                )
                assertEquals(decision, decision.toEntity().toDomain())
            }
        }

        AssignmentMode.entries.forEachIndexed { index, mode ->
            val decision = validDecision(id = 100 + index).copy(
                assignment = validDecision(id = 100 + index).assignment.copy(
                    assignmentMode = mode,
                ),
            )
            assertEquals(mode, decision.toEntity().toDomain().assignment.assignmentMode)
        }
        AdaptiveReasonCode.entries.forEachIndexed { index, reason ->
            val decision = validDecision(id = 200 + index).copy(
                assignment = validDecision(id = 200 + index).assignment.copy(
                    reasonCode = reason,
                ),
            )
            assertEquals(reason, decision.toEntity().toDomain().assignment.reasonCode)
        }
        FeedbackCode.entries.forEachIndexed { index, feedback ->
            val decision = validDecision(id = 300 + index).copy(feedbackCode = feedback)
            assertEquals(feedback, decision.toEntity().toDomain().feedbackCode)
        }
        MomentCue.entries.forEachIndexed { index, cue ->
            val decision = validDecision(id = 400 + index).copy(momentCue = cue)
            assertEquals(cue, decision.toEntity().toDomain().momentCue)
        }
        InterventionFamily.entries.forEachIndexed { index, intervention ->
            val base = validDecision(id = 500 + index)
            val decision = base.copy(
                assignment = base.assignment.copy(
                    eligibleInterventions = setOf(intervention),
                    assignedSuggestion = intervention,
                    actualIntervention = intervention,
                    momentPlanId = if (intervention == InterventionFamily.MomentPlan) {
                        PlanId
                    } else {
                        null
                    },
                ),
            )
            val mapped = decision.toEntity().toDomain()
            assertEquals(intervention, mapped.assignment.assignedSuggestion)
            assertEquals(intervention, mapped.assignment.actualIntervention)
            assertEquals(setOf(intervention), mapped.assignment.eligibleInterventions)
        }
        RepeatObservation.entries.forEachIndexed { index, repeat ->
            val base = validDecision(id = 600 + index)
            val decision = base.copy(
                repeatObservation = repeat,
                observationFinalisedAtMillis =
                    if (repeat == RepeatObservation.NotFinalised) null else 3_000L,
            )
            assertEquals(repeat, decision.toEntity().toDomain().repeatObservation)
        }
    }

    @Test
    fun momentPlanAndPreferenceRoundTripsPreserveSupportedValues() {
        MomentPlanActionType.entries.forEachIndexed { index, actionType ->
            val plan = validPlan(index + 1).copy(
                actionType = actionType,
                actionTarget = when (actionType) {
                    MomentPlanActionType.TextOnly -> null
                    MomentPlanActionType.OpenImpulsiveDestination -> "focus"
                    MomentPlanActionType.LaunchSelectedApp -> "com.example.project"
                },
            )
            assertEquals(plan, plan.toEntity().toDomain())
        }

        val preferences = AdaptivePreferences(
            personalSuggestionsEnabled = false,
            gameSuggestionsEnabled = true,
            readingSuggestionsEnabled = false,
            momentPlanSuggestionsEnabled = true,
            randomisedExplorationEnabled = false,
            privateScreenProtectionEnabled = false,
            historyRetentionPolicy = AdaptiveHistoryRetentionPolicy.OneYear,
        )
        assertEquals(
            preferences,
            preferences.toEntity(updatedAtMillis = 8_000L).toDomain(),
        )
    }

    @Test
    fun invalidRatingAndProbabilityAreRejectedBeforeEntityCreation() {
        val invalidRating = validDecision(id = 701).copy(baselineUrgeRating = 11)
        val invalidProbabilityBase = validDecision(id = 702)
        val invalidProbability = invalidProbabilityBase.copy(
            assignment = invalidProbabilityBase.assignment.copy(
                selectionProbability = 1.01,
            ),
        )

        assertFailsValidation { invalidRating.toEntity() }
        assertFailsValidation { invalidProbability.toEntity() }
    }

    @Test
    fun adaptiveEntitiesContainNoRawUrlDomainOrIdentityColumns() {
        val fields = listOf(
            AdaptiveDecisionEntity::class.java,
            MomentPlanEntity::class.java,
            AdaptivePreferenceEntity::class.java,
        ).flatMap { type ->
            type.declaredFields.map { field -> field.name.lowercase() }
        }
        val forbiddenFragments = listOf(
            "url",
            "domain",
            "search",
            "pagetitle",
            "pagecontent",
            "notification",
            "email",
            "firebaseuid",
            "medical",
        )

        forbiddenFragments.forEach { forbidden ->
            assertFalse(
                "Found forbidden adaptive column fragment: $forbidden",
                fields.any { forbidden in it },
            )
        }
    }

    @Test
    fun defaultPreferenceEntityUsesSingleRowAndEnabledDefaults() {
        val defaults = AdaptivePreferenceEntity()

        assertEquals(1, defaults.id)
        assertTrue(defaults.personalSuggestionsEnabled)
        assertTrue(defaults.gameSuggestionsEnabled)
        assertTrue(defaults.readingSuggestionsEnabled)
        assertTrue(defaults.momentPlanSuggestionsEnabled)
        assertTrue(defaults.randomisedExplorationEnabled)
        assertTrue(defaults.privateScreenProtectionEnabled)
        assertEquals("SixMonths", defaults.historyRetentionPolicy)
        assertEquals(0L, defaults.updatedAtMillis)
    }

    private fun validDecision(
        id: Int,
        source: AdaptiveSourceKind = AdaptiveSourceKind.App,
        intensity: MomentIntensity = MomentIntensity.RepeatedAttempt,
    ): AdaptiveDecision = AdaptiveDecision(
        decisionId = id.asUuid(),
        protectionIncidentToken = "incident-$id",
        sourceKind = source,
        createdAtMillis = 1_000L,
        momentWindowStartedAtMillis = 900L,
        momentCue = MomentCue.Boredom,
        baselineUrgeRating = 5,
        assignment = AdaptiveAssignment(
            momentIntensity = intensity,
            assignmentMode = AssignmentMode.AdaptiveSuggestion,
            eligibleInterventions = setOf(
                InterventionFamily.PivotGame,
                InterventionFamily.PivotReading,
            ),
            assignedSuggestion = InterventionFamily.PivotGame,
            selectionProbability = null,
            reasonCode = AdaptiveReasonCode.InsufficientEvidenceExploration,
            actualIntervention = InterventionFamily.PivotReading,
            userOverrodeSuggestion = true,
        ),
        presentedAtMillis = 1_100L,
        startedAtMillis = 1_200L,
        completedAtMillis = 1_300L,
        feedbackCode = FeedbackCode.Helped,
        feedbackUpdatedAtMillis = 1_400L,
        repeatObservation = RepeatObservation.NoRepeatDetected,
        observationDeadlineAtMillis = 2_200L,
        observationFinalisedAtMillis = 2_300L,
        recommendationPolicyVersion = 2,
        assignedProtocolId = "pivot_game",
        assignedProtocolVersion = 1,
        actualProtocolId = "reset_reading",
        actualProtocolVersion = 1,
        eligibleMomentPlanCount = 3,
    )

    private fun validPlan(id: Int): MomentPlan = MomentPlan(
        planId = id.asUuid(),
        title = "Open my project",
        momentCue = MomentCue.Boredom,
        actionText = "Open my project for two minutes.",
        futureCueText = "Tomorrow morning, I want to feel clear.",
        actionType = MomentPlanActionType.TextOnly,
        actionTarget = null,
        enabled = true,
        preferredForCue = true,
        createdAtMillis = 1_000L,
        updatedAtMillis = 2_000L,
        rehearsedAtMillis = 1_500L,
    )

    private fun assertFailsValidation(block: () -> Unit) {
        var failed = false
        try {
            block()
        } catch (_: IllegalArgumentException) {
            failed = true
        }
        assertTrue("Expected persistence validation to fail", failed)
    }

    private fun Int.asUuid(): String =
        "00000000-0000-0000-0000-${toString().padStart(12, '0')}"

    private companion object {
        const val PlanId = "00000000-0000-0000-0000-000000009999"
    }
}
