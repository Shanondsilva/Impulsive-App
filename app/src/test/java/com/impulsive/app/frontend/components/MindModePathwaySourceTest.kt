package com.impulsive.app.frontend.components

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MindModePathwaySourceTest {
    private val sheet = source(
        "frontend/components/MindModeStatusSheet.kt",
    )
    private val adaptiveModels = source(
        "backend/domain/model/adaptive/AdaptiveMomentModels.kt",
    )

    @Test
    fun explainerVisualEnumHasExactlyTheFourStableIdentifiers() {
        val enumBlock = sheet.substring(
            sheet.indexOf("private enum class MindModeExplainerVisual {"),
            sheet.indexOf("private data class MindModeExplainerStep("),
        )
        assertTrue(enumBlock.contains("Notice,"))
        assertTrue(enumBlock.contains("Pause,"))
        assertTrue(enumBlock.contains("Pivot,"))
        assertTrue(enumBlock.contains("Understand,"))
    }

    @Test
    fun lottieSelectionUsesTheEnumAndNeverATitleString() {
        val lottieFunction = sheet.substring(
            sheet.indexOf("private fun mindModeStepLottieRawRes("),
            sheet.indexOf("private fun MindModeExplainerCarousel("),
        )
        assertTrue(lottieFunction.contains("visual: MindModeExplainerVisual"))
        assertTrue(lottieFunction.contains("): Int = when (visual) {"))
        assertFalse(lottieFunction.contains("stepTitle"))
        assertFalse(sheet.contains("stepTitle: String"))
        assertFalse(sheet.contains("fun mindModeStepLottieRawRes(stepTitle"))
    }

    @Test
    fun displayTextIsNeverUsedToSelectAnimationsOrSpecialVisuals() {
        assertFalse(sheet.contains("stepTitle == \"Trigger\""))
        assertFalse(sheet.contains("stepTitle == \"Control\""))
        assertFalse(sheet.contains("if (stepTitle =="))
        assertTrue(sheet.contains("visual == MindModeExplainerVisual.Notice"))
        assertTrue(sheet.contains("visual == MindModeExplainerVisual.Understand"))
        assertTrue(sheet.contains("mindModeStepLottieRawRes(visual)"))
    }

    @Test
    fun sharedModelContainsExactlyTheFourCurrentInterventionFamilies() {
        val model = sheet.substring(
            sheet.indexOf("private val MindModePathway = MindModePathwayModel("),
            sheet.indexOf("private fun MindModeDecisionTreeVisual("),
        )
        val familyBlock = model.substring(
            model.indexOf("supportFamilies = listOf("),
            model.indexOf("outcome = MindModePathwayStage("),
        )
        assertTrue(familyBlock.contains("title = \"Short Pause\""))
        assertTrue(familyBlock.contains("title = \"Pivot Game\""))
        assertTrue(familyBlock.contains("title = \"Reset Reading\""))
        assertTrue(familyBlock.contains("title = \"Moment Plan\""))
        assertEquals(4, familyBlock.split("MindModeSupportFamily(").size - 1)
    }

    @Test
    fun pivotGameDetailIncludesEveryIndividualGameNameIncludingRhythm() {
        val model = sheet.substring(
            sheet.indexOf("private val MindModePathway = MindModePathwayModel("),
            sheet.indexOf("private fun MindModeDecisionTreeVisual("),
        )
        assertTrue(model.contains("detail = \"Reflex • Block • SkyStack • Rhythm\""))
    }

    @Test
    fun visualAndLargeFontListBothReadFromTheSharedPathwayModel() {
        val visualFunction = sheet.substring(
            sheet.indexOf("private fun MindModeDecisionTreeVisual("),
            sheet.indexOf("private fun MindModeDecisionTreeList("),
        )
        val listFunction = sheet.substring(
            sheet.indexOf("private fun MindModeDecisionTreeList("),
            sheet.indexOf("private fun mindModeStepLottieRawRes("),
        )
        assertTrue(visualFunction.contains("val pathway = MindModePathway"))
        assertTrue(listFunction.contains("val pathway = MindModePathway"))
        assertFalse(listFunction.contains("\"Trigger\" to"))
        assertFalse(listFunction.contains("\"Pivot task\" to"))
    }

    @Test
    fun staleImplementationShorthandIsRemoved() {
        assertFalse(sheet.contains("Piano steps"))
        assertFalse(sheet.contains("Wait cut + LP"))
        assertFalse(sheet.contains("Mind picks a task"))
        assertFalse(sheet.contains("control restored"))
    }

    @Test
    fun newTruthfulOutcomeAndLearningCopyIsPresent() {
        assertTrue(sheet.contains("Outcome recorded"))
        assertTrue(sheet.contains("Private learning"))
    }

    @Test
    fun reducedMotionAndLargeFontFallbackLogicRemainsPresent() {
        assertTrue(sheet.contains("Settings.Global.ANIMATOR_DURATION_SCALE"))
        assertTrue(sheet.contains("reducedMotion"))
        assertTrue(sheet.contains("LocalDensity.current.fontScale >= 1.6f"))
        assertTrue(sheet.contains("MindModeDecisionTreeList("))
    }

    @Test
    fun copyAvoidsOverstatedOrClinicalClaims() {
        val lowercase = sheet.lowercase()
        listOf(
            "cure",
            "treatment",
            "prevention",
            "guaranteed",
            "control restored",
            "safe action",
            "clinically proven",
        ).forEach { forbidden ->
            assertFalse(lowercase.contains(forbidden))
        }
    }

    @Test
    fun adaptiveDomainFileKeepsItsOriginalInterventionFamiliesUnchanged() {
        assertTrue(adaptiveModels.contains("enum class InterventionFamily(val eligibilityBit: Int) {"))
        assertTrue(adaptiveModels.contains("ShortPause(1 shl 0),"))
        assertTrue(adaptiveModels.contains("PivotGame(1 shl 1),"))
        assertTrue(adaptiveModels.contains("PivotReading(1 shl 2),"))
        assertTrue(adaptiveModels.contains("MomentPlan(1 shl 3),"))
    }

    private fun source(relativePath: String): String =
        File("src/main/java/com/impulsive/app/$relativePath").readText()
}
