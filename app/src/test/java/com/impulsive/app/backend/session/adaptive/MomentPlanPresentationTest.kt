package com.impulsive.app.backend.session.adaptive

import com.impulsive.app.backend.domain.model.adaptive.ImpulsiveDestination
import com.impulsive.app.backend.domain.model.adaptive.MomentCue
import com.impulsive.app.backend.domain.model.adaptive.MomentPlan
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanActionType
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentPlanPresentationTest {

    @Test
    fun normalizeUserTextTrimsAndCollapsesRepeatedWhitespace() {
        assertEquals(
            "hello world",
            MomentPlanPresentation.normalizeUserText("  hello   world  \n"),
        )
    }

    @Test
    fun singleLetterIsNotAMeaningfulTitle() {
        assertFalse(MomentPlanPresentation.hasMeaningfulTitle("j"))
    }

    @Test
    fun punctuationOnlyIsNotAMeaningfulAction() {
        assertFalse(MomentPlanPresentation.hasMeaningfulAction("..."))
    }

    @Test
    fun whitespacePlusPunctuationIsNotMeaningful() {
        assertFalse(MomentPlanPresentation.hasMeaningfulTitle("  ... -- "))
        assertFalse(MomentPlanPresentation.hasMeaningfulAction(" .,;: "))
    }

    @Test
    fun twoUnicodeLettersAreAcceptedAsATitle() {
        assertTrue(MomentPlanPresentation.hasMeaningfulTitle("é字"))
    }

    @Test
    fun threeUnicodeLettersOrDigitsAreAcceptedAsAnAction() {
        assertTrue(MomentPlanPresentation.hasMeaningfulAction("é字5"))
    }

    @Test
    fun displayTitlePreservesAValidTitle() {
        val plan = plan(title = "Walk outside")
        assertEquals("Walk outside", MomentPlanPresentation.displayTitle(plan))
    }

    @Test
    fun displayTitleFallsBackForAnInvalidLegacyTitle() {
        val plan = plan(title = "j", cue = MomentCue.BeingAlone)
        assertEquals("Plan for being alone", MomentPlanPresentation.displayTitle(plan))
    }

    @Test
    fun displayTitleFallsBackToAnyDifficultMomentForNullCue() {
        val plan = plan(title = "...", cue = null)
        assertEquals("Plan for any difficult moment", MomentPlanPresentation.displayTitle(plan))
    }

    @Test
    fun displayActionPreservesAValidTextAction() {
        val plan = plan(action = "Walk for five minutes")
        assertEquals("Walk for five minutes", MomentPlanPresentation.displayAction(plan))
    }

    @Test
    fun displayActionFallsBackForPunctuationOnlyText() {
        val plan = plan(action = "...")
        assertEquals(
            "Open this plan to add a clearer next action.",
            MomentPlanPresentation.displayAction(plan),
        )
    }

    @Test
    fun displayActionUsesDestinationLabelForDestinationActions() {
        val plan = plan(
            type = MomentPlanActionType.OpenImpulsiveDestination,
            action = "Open Journal",
            target = ImpulsiveDestination.Journal.storageValue,
        )
        assertEquals("Open Journal", MomentPlanPresentation.displayAction(plan))
    }

    @Test
    fun displayActionUsesSelectedAppLabelWhenAvailable() {
        val plan = plan(
            type = MomentPlanActionType.LaunchSelectedApp,
            action = "Open Some App",
            target = "com.example.app",
        )
        assertEquals(
            "Open Focus Timer",
            MomentPlanPresentation.displayAction(plan, selectedAppLabel = "Focus Timer"),
        )
    }

    @Test
    fun selectedAppActionUsesCurrentLabelWhenAvailable() {
        val plan = plan(
            type = MomentPlanActionType.LaunchSelectedApp,
            action = "Open Old App",
            target = "com.example.app",
        )

        assertEquals(
            "Open Spotify",
            MomentPlanPresentation.displayAction(
                plan = plan,
                selectedAppLabel = "Spotify",
            ),
        )
    }

    @Test
    fun selectedAppActionPreservesStoredActionWhenLiveLabelIsUnavailable() {
        val plan = plan(
            type = MomentPlanActionType.LaunchSelectedApp,
            action = "Open Spotify",
            target = "com.spotify.music",
        )

        assertEquals(
            "Open Spotify",
            MomentPlanPresentation.displayAction(plan),
        )
    }

    @Test
    fun selectedAppActionNormalisesStoredActionWhenLiveLabelIsUnavailable() {
        val plan = plan(
            type = MomentPlanActionType.LaunchSelectedApp,
            action = "  Open   Spotify  ",
            target = "com.spotify.music",
        )

        assertEquals(
            "Open Spotify",
            MomentPlanPresentation.displayAction(plan),
        )
    }

    @Test
    fun selectedAppActionUsesGenericFallbackWhenNoUsefulLabelExists() {
        val plan = plan(
            type = MomentPlanActionType.LaunchSelectedApp,
            action = "...",
            target = "com.example.missing",
        )

        assertEquals(
            "Open selected app",
            MomentPlanPresentation.displayAction(plan),
        )
    }

    @Test
    fun shortPreviewNoLongerExposesPunctuationOnlyLegacyContent() {
        val plan = plan(action = "...", cue = MomentCue.Stress)
        val preview = MomentPlanPresentation.shortPreview(plan)
        assertFalse(preview.contains("..."))
        assertEquals("Stress → Open this plan to add a clearer next action.", preview)
    }

    @Test
    fun adaptiveModelValidatorDoesNotImposePresentationMinimums() {
        val validatorSource = File(
            "src/main/java/com/impulsive/app/backend/domain/engine/adaptive/AdaptiveModelValidator.kt",
        ).readText()
        assertFalse(validatorSource.contains("hasMeaningfulTitle"))
        assertFalse(validatorSource.contains("hasMeaningfulAction"))
        assertFalse(validatorSource.contains("hasMeaningfulFutureCue"))
        assertFalse(validatorSource.contains("MeaningfulCharacters"))
    }

    private fun plan(
        title: String = "Clear morning",
        action: String = "Open my project for two minutes",
        future: String = "Tomorrow morning, I want to feel clear.",
        cue: MomentCue? = MomentCue.Boredom,
        type: MomentPlanActionType = MomentPlanActionType.TextOnly,
        target: String? = null,
    ) = MomentPlan(
        planId = UUID.randomUUID().toString(),
        title = title,
        momentCue = cue,
        actionText = action,
        futureCueText = future,
        actionType = type,
        actionTarget = target,
        enabled = true,
        preferredForCue = false,
        createdAtMillis = 100L,
        updatedAtMillis = 200L,
    )
}
