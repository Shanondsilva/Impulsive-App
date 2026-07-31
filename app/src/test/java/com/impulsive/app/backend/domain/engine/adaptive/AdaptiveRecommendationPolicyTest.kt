package com.impulsive.app.backend.domain.engine.adaptive

import com.impulsive.app.backend.domain.model.adaptive.AdaptivePreferences
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveReasonCode
import com.impulsive.app.backend.domain.model.adaptive.AssignmentMode
import com.impulsive.app.backend.domain.model.adaptive.ImpulsiveDestination
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentCue
import com.impulsive.app.backend.domain.model.adaptive.MomentIntensity
import com.impulsive.app.backend.domain.model.adaptive.MomentPlan
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanActionType
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanRehearsal
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanRehearsalMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class AdaptiveRecommendationPolicyTest {
    @Test
    fun firstAttemptSelectsShortPauseWithoutRandomisation() {
        val random = SequenceRandomisationSource(doubles = listOf(0.0), ints = listOf(2))

        val result = AdaptiveRecommendationPolicy(random).recommend(
            repeatedRequest().copy(momentIntensity = MomentIntensity.FirstAttempt),
        )

        assertEquals(InterventionFamily.ShortPause, result.assignment.assignedSuggestion)
        assertEquals(AssignmentMode.MinimumFriction, result.assignment.assignmentMode)
        assertEquals(AdaptiveReasonCode.MinimumEffectiveFriction, result.assignment.reasonCode)
        assertEquals(0, random.doubleCalls)
        assertEquals(0, random.intCalls)
    }

    @Test
    fun disabledInterventionsAreExcluded() {
        val result = adaptivePolicy().recommend(
            repeatedRequest().copy(
                preferences = AdaptivePreferences(
                    gameSuggestionsEnabled = false,
                    readingSuggestionsEnabled = true,
                    momentPlanSuggestionsEnabled = false,
                    randomisedExplorationEnabled = false,
                ),
            ),
        )

        assertEquals(setOf(InterventionFamily.PivotReading), result.assignment.eligibleInterventions)
        assertEquals(InterventionFamily.PivotReading, result.assignment.assignedSuggestion)
        assertEquals(AdaptiveReasonCode.OnlyEligibleIntervention, result.assignment.reasonCode)
    }

    @Test
    fun missingMomentPlanIsExcluded() {
        val result = adaptivePolicy().recommend(
            repeatedRequest().copy(
                preferences = AdaptivePreferences(randomisedExplorationEnabled = false),
                momentPlans = emptyList(),
            ),
        )

        assertFalse(InterventionFamily.MomentPlan in result.assignment.eligibleInterventions)
    }

    @Test
    fun matchingPreferredMomentPlanReceivesDefinedBonuses() {
        val result = adaptivePolicy().recommend(
            repeatedRequest().copy(
                selectedCue = MomentCue.Boredom,
                preferences = AdaptivePreferences(randomisedExplorationEnabled = false),
                momentPlans = listOf(plan(cue = MomentCue.Boredom, preferred = true)),
            ),
        )

        val score = result.utilityByIntervention.getValue(InterventionFamily.MomentPlan)
        assertEquals(AdaptiveUtilityPolicy.CueMatchBonus, score.cueMatchBonus, 0.0)
        assertEquals(AdaptiveUtilityPolicy.PreferredPlanBonus, score.preferredPlanBonus, 0.0)
        assertEquals(InterventionFamily.MomentPlan, result.assignment.assignedSuggestion)
        assertEquals(AdaptiveReasonCode.CueMatchedMomentPlan, result.assignment.reasonCode)
    }

    @Test
    fun skippedCueAppliesNoMomentPlanBonus() {
        val result = adaptivePolicy().recommend(
            repeatedRequest().copy(
                selectedCue = null,
                preferences = AdaptivePreferences(randomisedExplorationEnabled = false),
                momentPlans = listOf(plan(cue = MomentCue.Boredom, preferred = true)),
            ),
        )

        val score = result.utilityByIntervention.getValue(InterventionFamily.MomentPlan)
        assertEquals(0.0, score.cueMatchBonus, 0.0)
        assertEquals(0.0, score.preferredPlanBonus, 0.0)
        assertEquals(InterventionFamily.PivotGame, result.assignment.assignedSuggestion)
    }

    @Test
    fun twoRecentGameSelectionsRotateSuggestion() {
        val result = adaptivePolicy().recommend(
            repeatedRequest().copy(
                preferences = AdaptivePreferences(
                    momentPlanSuggestionsEnabled = false,
                    randomisedExplorationEnabled = false,
                ),
                recentActualSelections = listOf(
                    InterventionFamily.PivotGame,
                    InterventionFamily.PivotGame,
                ),
            ),
        )

        assertEquals(InterventionFamily.PivotReading, result.assignment.assignedSuggestion)
        assertEquals(AdaptiveReasonCode.InterventionFatigueRotation, result.assignment.reasonCode)
    }

    @Test
    fun secureRandomInterfaceIsInjectedAndControlsExploration() {
        val random = SequenceRandomisationSource(doubles = listOf(0.10), ints = listOf(1))
        val result = AdaptiveRecommendationPolicy(random).recommend(repeatedRequest())

        assertEquals(AssignmentMode.RandomisedSuggestion, result.assignment.assignmentMode)
        assertEquals(InterventionFamily.PivotReading, result.assignment.assignedSuggestion)
        assertEquals(1, random.doubleCalls)
        assertEquals(1, random.intCalls)
    }

    @Test
    fun deterministicSeededTestRandomisationIsReproducible() {
        val first = SeededTestRandomisationSource(42L)
        val second = SeededTestRandomisationSource(42L)

        val firstValues = List(8) { first.nextInt(3) }
        val secondValues = List(8) { second.nextInt(3) }

        assertEquals(firstValues, secondValues)
        assertEquals(first.nextDouble(), second.nextDouble(), 0.0)
    }

    @Test
    fun randomisationBoundaryUsesValuesStrictlyBelowTwentyFivePercent() {
        val belowBoundary = AdaptiveRecommendationPolicy(
            SequenceRandomisationSource(doubles = listOf(0.249999), ints = listOf(0)),
        ).recommend(repeatedRequest())
        val atBoundary = AdaptiveRecommendationPolicy(
            SequenceRandomisationSource(doubles = listOf(0.25), ints = listOf(0)),
        ).recommend(repeatedRequest())

        assertEquals(AssignmentMode.RandomisedSuggestion, belowBoundary.assignment.assignmentMode)
        assertEquals(AssignmentMode.AdaptiveSuggestion, atBoundary.assignment.assignmentMode)
    }

    @Test
    fun exactAdaptiveTiesUseStableOrdering() {
        val result = adaptivePolicy().recommend(
            repeatedRequest().copy(
                preferences = AdaptivePreferences(
                    momentPlanSuggestionsEnabled = false,
                    randomisedExplorationEnabled = false,
                ),
            ),
        )

        assertEquals(InterventionFamily.PivotGame, result.assignment.assignedSuggestion)
    }

    @Test
    fun sameAdaptiveInputIsDeterministic() {
        val request = repeatedRequest().copy(
            selectedCue = MomentCue.Stress,
            preferences = AdaptivePreferences(randomisedExplorationEnabled = false),
            momentPlans = listOf(plan(cue = MomentCue.Stress, preferred = false)),
            recentActualSelections = listOf(InterventionFamily.PivotReading),
        )

        val first = adaptivePolicy().recommend(request)
        val second = adaptivePolicy().recommend(request)

        assertEquals(first, second)
    }

    @Test
    fun disabledMomentPlanIsNeverSuggested() {
        val result = adaptivePolicy().recommend(
            repeatedRequest().copy(
                selectedCue = MomentCue.Boredom,
                preferences = AdaptivePreferences(randomisedExplorationEnabled = false),
                momentPlans = listOf(
                    plan(cue = MomentCue.Boredom, preferred = true).copy(enabled = false),
                ),
            ),
        )

        assertFalse(InterventionFamily.MomentPlan in result.assignment.eligibleInterventions)
        assertNotEquals(InterventionFamily.MomentPlan, result.assignment.assignedSuggestion)
        assertNull(result.assignment.momentPlanId)
    }

    @Test
    fun unsafeMomentPlanIsNeverSuggested() {
        val unsafePlan = plan(cue = MomentCue.Boredom, preferred = true).copy(
            actionType = MomentPlanActionType.OpenImpulsiveDestination,
            actionTarget = "https://example.com",
        )

        val result = adaptivePolicy().recommend(
            repeatedRequest().copy(
                selectedCue = MomentCue.Boredom,
                preferences = AdaptivePreferences(randomisedExplorationEnabled = false),
                momentPlans = listOf(unsafePlan),
            ),
        )

        assertFalse(InterventionFamily.MomentPlan in result.assignment.eligibleInterventions)
    }

    @Test
    fun userOverrideIsRecordedSeparatelyWithoutReplacingAssignment() {
        val recommendation = adaptivePolicy().recommend(
            repeatedRequest().copy(
                preferences = AdaptivePreferences(
                    momentPlanSuggestionsEnabled = false,
                    randomisedExplorationEnabled = false,
                ),
            ),
        )

        val chosen = recommendation.assignment.recordActualChoice(
            InterventionFamily.PivotReading,
        )

        assertEquals(InterventionFamily.PivotGame, chosen.assignedSuggestion)
        assertEquals(InterventionFamily.PivotReading, chosen.actualIntervention)
        assertTrue(chosen.userOverrodeSuggestion)
        assertEquals(AssignmentMode.AdaptiveSuggestion, chosen.assignmentMode)
    }

    @Test
    fun randomisedSelectionStoresUniformConditionalProbability() {
        val result = AdaptiveRecommendationPolicy(
            SequenceRandomisationSource(doubles = listOf(0.0), ints = listOf(2)),
        ).recommend(
            repeatedRequest().copy(
                momentPlans = listOf(plan(cue = null, preferred = true)),
            ),
        )

        assertEquals(InterventionFamily.MomentPlan, result.assignment.assignedSuggestion)
        assertEquals(1.0 / 3.0, result.assignment.selectionProbability ?: 0.0, 0.0)
        assertEquals(
            InterventionFamily.PivotGame.eligibilityBit or
                InterventionFamily.PivotReading.eligibilityBit or
                InterventionFamily.MomentPlan.eligibilityBit,
            result.assignment.eligibleInterventionsMask,
        )
    }

    @Test
    fun recentPracticeBreaksTieBetweenEquivalentMatchingPlansOnly() {
        val recentPlan = plan(cue = MomentCue.Stress, preferred = false).copy(
            planId = "00000000-0000-0000-0000-000000000201",
            updatedAtMillis = 2_100L,
        )
        val newerPlan = recentPlan.copy(
            planId = "00000000-0000-0000-0000-000000000202",
            updatedAtMillis = 2_200L,
        )

        val result = adaptivePolicy().recommend(
            repeatedRequest().copy(
                selectedCue = MomentCue.Stress,
                preferences = AdaptivePreferences(randomisedExplorationEnabled = false),
                momentPlans = listOf(newerPlan, recentPlan),
                recentCompletedRehearsals = listOf(rehearsal(recentPlan)),
            ),
        )

        assertEquals(recentPlan.planId, result.assignment.momentPlanId)
        assertEquals(recentPlan.updatedAtMillis, result.assignment.momentPlanUpdatedAtMillis)
        assertEquals(
            AdaptiveReasonCode.CueMatchedRecentlyRehearsedPlan,
            result.assignment.reasonCode,
        )
    }

    @Test
    fun preferredMatchingPlanStillPrecedesRecentlyPractisedMatchingPlan() {
        val recentPlan = plan(cue = MomentCue.Stress, preferred = false).copy(
            planId = "00000000-0000-0000-0000-000000000301",
        )
        val preferredPlan = recentPlan.copy(
            planId = "00000000-0000-0000-0000-000000000302",
            preferredForCue = true,
        )

        val result = adaptivePolicy().recommend(
            repeatedRequest().copy(
                selectedCue = MomentCue.Stress,
                preferences = AdaptivePreferences(randomisedExplorationEnabled = false),
                momentPlans = listOf(recentPlan, preferredPlan),
                recentCompletedRehearsals = listOf(rehearsal(recentPlan)),
            ),
        )

        assertEquals(preferredPlan.planId, result.assignment.momentPlanId)
        assertEquals(AdaptiveReasonCode.CueMatchedMomentPlan, result.assignment.reasonCode)
    }

    @Test
    fun recentPracticeDoesNotAlterFamilyUtilityOrExplorationBoundary() {
        val plan = plan(cue = MomentCue.Stress, preferred = false)
        val base = repeatedRequest().copy(
            selectedCue = MomentCue.Stress,
            preferences = AdaptivePreferences(randomisedExplorationEnabled = false),
            momentPlans = listOf(plan),
        )

        val withoutPractice = adaptivePolicy().recommend(base)
        val withPractice = adaptivePolicy().recommend(
            base.copy(recentCompletedRehearsals = listOf(rehearsal(plan))),
        )

        assertEquals(withoutPractice.utilityByIntervention, withPractice.utilityByIntervention)
        assertEquals(
            withoutPractice.assignment.assignedSuggestion,
            withPractice.assignment.assignedSuggestion,
        )
        assertEquals(AdaptiveRecommendationPolicy.RandomisedExplorationRate, 0.25, 0.0)
    }

    private fun adaptivePolicy(): AdaptiveRecommendationPolicy =
        AdaptiveRecommendationPolicy(
            SequenceRandomisationSource(doubles = listOf(0.99), ints = listOf(0)),
        )

    private fun repeatedRequest(): AdaptiveRecommendationRequest =
        AdaptiveRecommendationRequest(
            momentIntensity = MomentIntensity.RepeatedAttempt,
            selectedCue = null,
        )

    private fun plan(
        cue: MomentCue?,
        preferred: Boolean,
    ): MomentPlan = MomentPlan(
        planId = "00000000-0000-0000-0000-000000000100",
        title = "Open my project",
        momentCue = cue,
        actionText = "Open my project for two minutes.",
        futureCueText = "Tomorrow morning, I want to feel clear and ready to work.",
        actionType = MomentPlanActionType.OpenImpulsiveDestination,
        actionTarget = ImpulsiveDestination.Focus.storageValue,
        enabled = true,
        preferredForCue = preferred,
        createdAtMillis = 1_000L,
        updatedAtMillis = 2_000L,
    )

    private fun rehearsal(plan: MomentPlan): MomentPlanRehearsal =
        MomentPlanRehearsal(
            rehearsalId = "00000000-0000-0000-0000-000000000900",
            planId = plan.planId,
            planUpdatedAtMillisAtStart = plan.updatedAtMillis,
            mode = MomentPlanRehearsalMode.Guided,
            startedAtMillis = 3_000L,
            completedAtMillis = 4_000L,
        )

    private class SequenceRandomisationSource(
        doubles: List<Double>,
        ints: List<Int>,
    ) : RandomisationSource {
        private val doubles = ArrayDeque(doubles)
        private val ints = ArrayDeque(ints)
        var doubleCalls: Int = 0
            private set
        var intCalls: Int = 0
            private set

        override fun nextDouble(): Double {
            doubleCalls += 1
            return doubles.removeFirst()
        }

        override fun nextInt(bound: Int): Int {
            intCalls += 1
            return ints.removeFirst().mod(bound)
        }
    }

    private class SeededTestRandomisationSource(seed: Long) : RandomisationSource {
        private val random = Random(seed)

        override fun nextDouble(): Double = random.nextDouble()

        override fun nextInt(bound: Int): Int = random.nextInt(bound)
    }
}
