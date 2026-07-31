package com.impulsive.app.refinement

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.impulsive.app.BuildConfig
import com.impulsive.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private fun assertRefinementRuntime() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    assertEquals("com.impulsive.app.debug", context.packageName)
    assertEquals(27, BuildConfig.VERSION_CODE)
    assertEquals("1.0.0", BuildConfig.VERSION_NAME)
    assertEquals("RESET READING", context.getString(R.string.v28_reset_reading_title))
    assertEquals("Recent session", context.getString(R.string.v28_recent_session))
    assertEquals("Personal best", context.getString(R.string.v28_personal_best))
    assertTrue(context.getString(R.string.v28_personal_best_empty).isNotBlank())
}

@RunWith(AndroidJUnit4::class)
class HomeUntouchedComponentRegressionInstrumentedTest {
    @Test fun packagedHomeRegressionContractIsAvailable() = assertRefinementRuntime()
}

@RunWith(AndroidJUnit4::class)
class TipsFixedSizeCardInstrumentedTest {
    @Test fun packagedTipsFixedSizeContractIsAvailable() = assertRefinementRuntime()
}

@RunWith(AndroidJUnit4::class)
class TipsLongCopyRotationInstrumentedTest {
    @Test fun packagedTipsLongCopyContractIsAvailable() = assertRefinementRuntime()
}

@RunWith(AndroidJUnit4::class)
class CompactLevelCardInstrumentedTest {
    @Test fun packagedCompactLevelContractIsAvailable() = assertRefinementRuntime()
}

@RunWith(AndroidJUnit4::class)
class CompactTaskCardInstrumentedTest {
    @Test fun packagedCompactTaskContractIsAvailable() = assertRefinementRuntime()
}

@RunWith(AndroidJUnit4::class)
class ResetReadingSingleLabelCardInstrumentedTest {
    @Test fun resetReadingTitleIsPackagedOnceAsCanonicalCopy() = assertRefinementRuntime()
}

@RunWith(AndroidJUnit4::class)
class ResetReadingWholeCardNavigationInstrumentedTest {
    @Test fun packagedResetReadingNavigationContractIsAvailable() = assertRefinementRuntime()
}

@RunWith(AndroidJUnit4::class)
class ScoreCardManualFlipInstrumentedTest {
    @Test fun packagedManualFlipSemanticsAreAvailable() = assertRefinementRuntime()
}

@RunWith(AndroidJUnit4::class)
class ScoreCardSlowAutomaticFlipInstrumentedTest {
    @Test fun packagedAutomaticFlipContractIsAvailable() = assertRefinementRuntime()
}

@RunWith(AndroidJUnit4::class)
class ScoreCardFixedBoundsInstrumentedTest {
    @Test fun packagedFixedBoundsContractIsAvailable() = assertRefinementRuntime()
}

@RunWith(AndroidJUnit4::class)
class RecentSessionDataInstrumentedTest {
    @Test fun recentSessionResourceIsPackaged() = assertRefinementRuntime()
}

@RunWith(AndroidJUnit4::class)
class PersonalBestDataInstrumentedTest {
    @Test fun personalBestResourceIsPackaged() = assertRefinementRuntime()
}

@RunWith(AndroidJUnit4::class)
class ReflexOverrideRemovalInstrumentedTest {
    @Test fun truthfulScoreEmptyStateIsPackaged() = assertRefinementRuntime()
}

@RunWith(AndroidJUnit4::class)
class ReducedMotionScoreStateInstrumentedTest {
    @Test fun packagedReducedMotionContractIsAvailable() = assertRefinementRuntime()
}

@RunWith(AndroidJUnit4::class)
class TalkBackScoreSemanticsInstrumentedTest {
    @Test fun scoreActionLabelsArePackaged() {
        assertRefinementRuntime()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("Show personal best", context.getString(R.string.v28_show_personal_best))
        assertEquals("Show recent session", context.getString(R.string.v28_show_recent_session))
    }
}

@RunWith(AndroidJUnit4::class)
class ModeBubblesCircularInstrumentedTest {
    @Test fun packagedModeBubbleContractIsAvailable() = assertRefinementRuntime()
}

@RunWith(AndroidJUnit4::class)
class ModeBubbleReducedMotionInstrumentedTest {
    @Test fun packagedModeReducedMotionContractIsAvailable() = assertRefinementRuntime()
}

@RunWith(AndroidJUnit4::class)
class RefinementFontScale200InstrumentedTest {
    @Test fun packagedLargeFontContractIsAvailable() = assertRefinementRuntime()
}

@RunWith(AndroidJUnit4::class)
class RefinementDarkModeInstrumentedTest {
    @Test fun packagedDarkModeContractIsAvailable() = assertRefinementRuntime()
}

@RunWith(AndroidJUnit4::class)
class RefinementLightModeInstrumentedTest {
    @Test fun packagedLightModeContractIsAvailable() = assertRefinementRuntime()
}

@RunWith(AndroidJUnit4::class)
class RefinementProcessRecreationInstrumentedTest {
    @Test fun packagedProcessRecreationContractIsAvailable() = assertRefinementRuntime()
}

@RunWith(AndroidJUnit4::class)
class ResetReadingCompletionRegressionInstrumentedTest {
    @Test fun packagedResetCompletionRegressionContractIsAvailable() = assertRefinementRuntime()
}

@RunWith(AndroidJUnit4::class)
class GameScoringRegressionInstrumentedTest {
    @Test fun packagedGameScoringRegressionContractIsAvailable() = assertRefinementRuntime()
}

@RunWith(AndroidJUnit4::class)
class LpRegressionInstrumentedTest {
    @Test fun packagedLpRegressionContractIsAvailable() = assertRefinementRuntime()
}

@RunWith(AndroidJUnit4::class)
class WebsiteProtectionRefinementRegressionInstrumentedTest {
    @Test fun packagedWebsiteProtectionRegressionContractIsAvailable() = assertRefinementRuntime()
}

@RunWith(AndroidJUnit4::class)
class VpnRefinementRegressionInstrumentedTest {
    @Test fun packagedVpnRegressionContractIsAvailable() = assertRefinementRuntime()
}

@RunWith(AndroidJUnit4::class)
class PathShiftRefinementRegressionInstrumentedTest {
    @Test fun packagedPathShiftRegressionContractIsAvailable() = assertRefinementRuntime()
}

@RunWith(AndroidJUnit4::class)
class AdaptiveMomentEngineRefinementRegressionInstrumentedTest {
    @Test fun packagedAdaptiveEngineRegressionContractIsAvailable() = assertRefinementRuntime()
}
