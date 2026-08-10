package com.impulsive.app.backend.session.adaptive

import com.impulsive.app.backend.domain.model.adaptive.EngagementOutcome
import com.impulsive.app.backend.domain.model.adaptive.FamiliarStepCandidate
import com.impulsive.app.backend.domain.model.adaptive.FamiliarStepEvidenceRecord
import com.impulsive.app.backend.domain.model.adaptive.FamiliarStepRouteIdentity
import com.impulsive.app.backend.domain.model.adaptive.FeedbackCode
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentCue
import com.impulsive.app.backend.domain.model.adaptive.RepeatObservation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FamiliarStepServicesTest {
    @Test
    fun explanationUsesExactCountsDisclaimerAndAllPrivacyExclusions() {
        val explanation = FamiliarStepExplanationService.explain(
            FamiliarStepCandidate(GameIdentity, 4, 3, MomentCue.Stress, 100L),
        )

        assertEquals(4, explanation.comparableCount)
        assertEquals(3, explanation.favourableCount)
        assertEquals(MomentCue.Stress, explanation.broadMomentCue)
        assertTrue(explanation.observedPatternDisclaimer.contains("not a prediction"))
        assertEquals(FamiliarStepExcludedData.entries.toSet(), explanation.excludedData)
        assertEquals(FamiliarStepConsideredSignal.entries.toSet(), explanation.consideredSignals)
    }

    @Test
    fun historyIsBoundedQualifiedAndContainsNoProtectedSourceShape() = runBlocking {
        val decisions = FakeDecisionRepository().apply {
            familiarStepEvidence = evidence(30, 23)
        }
        val snapshot = FamiliarStepHistoryService(
            decisions,
            FakeMomentPlanRepository(),
        ).snapshot()

        assertTrue(snapshot.items.isNotEmpty())
        assertTrue(snapshot.items.size <= FamiliarStepHistoryService.MaximumHistoryItems)
        assertTrue(snapshot.items.all { it.comparableCount >= 4 && it.favourableCount >= 3 })
        val forbidden = setOf(
            "incidenttoken", "package", "url", "domain", "pagetitle", "searchterm",
            "query", "dns", "privatemode", "currency",
        )
        val fieldNames = FamiliarStepHistoryItem::class.java.declaredFields
            .map { it.name.lowercase() }
            .toSet()
        assertFalse(fieldNames.any { field -> forbidden.any(field::contains) })
    }

    @Test
    fun resetUsesExistingResetPathAndDisableIsImmediatelyAuthoritative() = runBlocking {
        val data = FakeAdaptiveDataRepository()
        val cache = CountingCache()
        val reset = AdaptiveResetCoordinator(
            FakeDecisionRepository(),
            data,
            FakeScheduler(),
            AdaptiveSafeLogger { _, _ -> },
        )
        val preferences = FakePreferenceRepository()
        val controls = FamiliarStepControls(reset, preferences, FakeClock(), cache)

        assertEquals(AdaptiveLifecycleResult.Applied, controls.clearAdaptiveHistory())
        assertEquals(1, data.clearLearningCalls)
        controls.disablePersonalSuggestions()
        assertFalse(preferences.current.personalSuggestionsEnabled)
        controls.setPersonalSuggestionsEnabled(true)
        assertTrue(preferences.current.personalSuggestionsEnabled)
        assertEquals(3, cache.clears)
    }

    private fun evidence(count: Int, favourable: Int) = List(count) { index ->
        FamiliarStepEvidenceRecord(
            decisionId = "history-$index",
            routeIdentity = GameIdentity,
            momentCue = if (index < 10) MomentCue.Stress else MomentCue.Boredom,
            feedbackCode = if (index < favourable) FeedbackCode.Helped else FeedbackCode.DidNotHelp,
            engagementOutcome = EngagementOutcome.Completed,
            repeatObservation = RepeatObservation.NoRepeatDetected,
            decisionAtMillis = index.toLong(),
            finalisedAtMillis = index + 100L,
        )
    }

    private class CountingCache : FamiliarStepDerivedCache {
        var clears = 0
        override fun clear() {
            clears++
        }
    }

    private companion object {
        val GameIdentity = FamiliarStepRouteIdentity(
            InterventionFamily.PivotGame,
            "pivot_game",
            1,
        )
    }
}
