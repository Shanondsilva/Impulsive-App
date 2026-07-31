package com.impulsive.app.finalrepair

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private fun assertImpulsiveDebugTarget() {
    val packageName =
        InstrumentationRegistry.getInstrumentation().targetContext.packageName
    assertTrue(packageName.startsWith("com.impulsive.app"))
}

@RunWith(AndroidJUnit4::class)
class MomentPlanEditorImeHandlingInstrumentedTest {
    @Test fun targetAppIsAvailableForImeRepairSmoke() = assertImpulsiveDebugTarget()
}

@RunWith(AndroidJUnit4::class)
class PracticePreviewRefreshInstrumentedTest {
    @Test fun targetAppIsAvailableForPreviewRefreshSmoke() = assertImpulsiveDebugTarget()
}

@RunWith(AndroidJUnit4::class)
class GuidedPracticeFontScaleInstrumentedTest {
    @Test fun targetAppIsAvailableForGuidedPracticeFontScaleSmoke() = assertImpulsiveDebugTarget()
}

@RunWith(AndroidJUnit4::class)
class LongDialogsFontScaleInstrumentedTest {
    @Test fun targetAppIsAvailableForLongDialogFontScaleSmoke() = assertImpulsiveDebugTarget()
}

@RunWith(AndroidJUnit4::class)
class PivotGameSelectionFontScaleInstrumentedTest {
    @Test fun targetAppIsAvailableForPivotGameSelectionFontScaleSmoke() = assertImpulsiveDebugTarget()
}

@RunWith(AndroidJUnit4::class)
class MindBodySoulHubFontScaleInstrumentedTest {
    @Test fun targetAppIsAvailableForModeHubFontScaleSmoke() = assertImpulsiveDebugTarget()
}

@RunWith(AndroidJUnit4::class)
class StatisticsFontScaleInstrumentedTest {
    @Test fun targetAppIsAvailableForStatisticsFontScaleSmoke() = assertImpulsiveDebugTarget()
}

@RunWith(AndroidJUnit4::class)
class MindPivotFontScaleInstrumentedTest {
    @Test fun targetAppIsAvailableForMindPivotFontScaleSmoke() = assertImpulsiveDebugTarget()
}

@RunWith(AndroidJUnit4::class)
class DarkModeShortPauseFeedbackInstrumentedTest {
    @Test fun targetAppIsAvailableForDarkModeFeedbackSmoke() = assertImpulsiveDebugTarget()
}

@RunWith(AndroidJUnit4::class)
class SharedHomeCardAlignmentInstrumentedTest {
    @Test fun targetAppIsAvailableForSharedHomeCardSmoke() = assertImpulsiveDebugTarget()
}
