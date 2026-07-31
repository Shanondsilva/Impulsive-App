package com.impulsive.app.backend.session.adaptive

import androidx.lifecycle.SavedStateHandle
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentCue
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveChooserRefreshTest {
    @Test
    fun returningChooserReloadsStartedDecisionWithoutCreatingFollowUp() = runBlocking {
        val decisions = FakeDecisionRepository()
        val original = decision(
            eligible = setOf(
                InterventionFamily.PivotGame,
                InterventionFamily.PivotReading,
                InterventionFamily.MomentPlan,
            ),
        )
        decisions.stored += original
        val refresh = AdaptiveChooserRefresh(
            decisions = decisions,
            plans = FakeMomentPlanRepository(listOf(momentPlan())),
        )

        val initiallyLoaded = requireNotNull(refresh.load(original.decisionId))
        assertNull(initiallyLoaded.decision.startedAtMillis)

        decisions.stored[0] = original.copy(
            assignment = original.assignment.copy(
                actualIntervention = InterventionFamily.PivotGame,
            ),
            presentedAtMillis = 2_000L,
            startedAtMillis = 3_000L,
        )
        val afterBack = requireNotNull(refresh.load(original.decisionId))

        assertEquals(3_000L, afterBack.decision.startedAtMillis)
        assertEquals(
            InterventionFamily.PivotGame,
            afterBack.decision.assignment.actualIntervention,
        )
        assertEquals(1, decisions.stored.size)
        assertEquals(0, decisions.insertCalls)
    }

    @Test
    fun resumeRefreshPreservesCueAndRatingPresentationState() = runBlocking {
        val decisions = FakeDecisionRepository()
        val original = decision()
        decisions.stored += original
        val handle = SavedStateHandle()
        val prompts = AdaptiveOptionalPromptStateStore(handle)
        prompts.selectCue(MomentCue.Stress)
        assertTrue(prompts.selectUrge(7))

        AdaptiveChooserRefresh(
            decisions = decisions,
            plans = FakeMomentPlanRepository(),
        ).load(original.decisionId)

        val recreatedPresentation = AdaptiveOptionalPromptStateStore(handle)
        assertEquals(OptionalPromptUiState.Selected, recreatedPresentation.cuePromptState(null))
        assertEquals(MomentCue.Stress, recreatedPresentation.selectedCue(null))
        assertEquals(OptionalPromptUiState.Selected, recreatedPresentation.urgePromptState(null))
        assertEquals(7, recreatedPresentation.urgeRating(null))
    }

    @Test
    fun choiceGuardRejectsRapidDuplicateAndClearsForRetry() {
        val guard = AdaptiveChoiceOperationGuard()

        assertTrue(guard.tryStart())
        assertFalse(guard.tryStart())

        guard.clear()
        assertTrue(guard.tryStart())
    }
}
