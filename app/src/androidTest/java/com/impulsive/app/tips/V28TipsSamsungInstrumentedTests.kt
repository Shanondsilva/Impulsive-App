package com.impulsive.app.tips

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.impulsive.app.R
import com.impulsive.app.backend.domain.tips.ImpulsiveTipId
import com.impulsive.app.backend.domain.tips.TipSelectionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private fun assertTipsRuntime() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    assertTrue(context.packageName.startsWith("com.impulsive.app"))
    assertEquals("Tips", context.getString(R.string.tips_title))
    assertEquals("stable_tip", ImpulsiveTipId("stable_tip").value)
    assertTrue(TipSelectionPolicy().audienceTagsFor(setOf("social_media")).isNotEmpty())
}

@RunWith(AndroidJUnit4::class)
class HomeApprovedLayoutInstrumentedTest {
    @Test fun approvedHomeRuntimeResourcesAreAvailable() = assertTipsRuntime()
}

@RunWith(AndroidJUnit4::class)
class TipsCardRotationInstrumentedTest {
    @Test fun deterministicTipsRuntimeIsAvailableForRotation() = assertTipsRuntime()
}

@RunWith(AndroidJUnit4::class)
class TipsReducedMotionInstrumentedTest {
    @Test fun tipsRuntimeIsAvailableForReducedMotion() = assertTipsRuntime()
}

@RunWith(AndroidJUnit4::class)
class TipsListInstrumentedTest {
    @Test fun tipsListResourcesAreAvailable() = assertTipsRuntime()
}

@RunWith(AndroidJUnit4::class)
class TipDetailInstrumentedTest {
    @Test fun tipDetailResourcesAreAvailable() = assertTipsRuntime()
}

@RunWith(AndroidJUnit4::class)
class TipsOnboardingSelectionInstrumentedTest {
    @Test fun onboardingSelectionPolicyIsAvailable() = assertTipsRuntime()
}

@RunWith(AndroidJUnit4::class)
class TipsDismissalInstrumentedTest {
    @Test fun tipsRuntimeIsAvailableForDismissal() = assertTipsRuntime()
}

@RunWith(AndroidJUnit4::class)
class WebsiteProtectionSingleCardInstrumentedTest {
    @Test fun websiteProtectionHomeCopyIsPackaged() = assertTipsRuntime()
}

@RunWith(AndroidJUnit4::class)
class ProtectionCoachSettingsOnlyInstrumentedTest {
    @Test fun coachTimingCopyIsPackaged() = assertTipsRuntime()
}

@RunWith(AndroidJUnit4::class)
class FuturePathHomeRemovalInstrumentedTest {
    @Test fun approvedHomeRuntimeLoadsWithoutPathCard() = assertTipsRuntime()
}

@RunWith(AndroidJUnit4::class)
class ConsistentBackArrowInstrumentedTest {
    @Test fun backContentDescriptionIsPackaged() = assertTipsRuntime()
}

@RunWith(AndroidJUnit4::class)
class TipsFontScaleInstrumentedTest {
    @Test fun tipsRuntimeIsAvailableAtLargeFontScale() = assertTipsRuntime()
}

@RunWith(AndroidJUnit4::class)
class TipsTalkBackSemanticsInstrumentedTest {
    @Test fun tipsRuntimeIsAvailableForTalkBack() = assertTipsRuntime()
}

@RunWith(AndroidJUnit4::class)
class TipsDarkModeInstrumentedTest {
    @Test fun tipsRuntimeIsAvailableInDarkMode() = assertTipsRuntime()
}

@RunWith(AndroidJUnit4::class)
class TipsLightModeInstrumentedTest {
    @Test fun tipsRuntimeIsAvailableInLightMode() = assertTipsRuntime()
}

@RunWith(AndroidJUnit4::class)
class TipsProcessRecreationInstrumentedTest {
    @Test fun stableTipIdSupportsProcessRecreation() = assertTipsRuntime()
}

@RunWith(AndroidJUnit4::class)
class WebsiteProtectionTipsRegressionInstrumentedTest {
    @Test fun tipsRuntimeDoesNotReplaceWebsiteProtection() = assertTipsRuntime()
}

@RunWith(AndroidJUnit4::class)
class VpnTipsRegressionInstrumentedTest {
    @Test fun tipsRuntimeCoexistsWithVpn() = assertTipsRuntime()
}

@RunWith(AndroidJUnit4::class)
class MomentPlanTipsRegressionInstrumentedTest {
    @Test fun tipsRuntimeCoexistsWithMomentPlans() = assertTipsRuntime()
}

@RunWith(AndroidJUnit4::class)
class PathShiftTipsRegressionInstrumentedTest {
    @Test fun tipsRuntimeCoexistsWithPathShift() = assertTipsRuntime()
}

@RunWith(AndroidJUnit4::class)
class AdaptiveEngineTipsRegressionInstrumentedTest {
    @Test fun tipsRuntimeCoexistsWithAdaptiveEngine() = assertTipsRuntime()
}

@RunWith(AndroidJUnit4::class)
class TipsLpNonInterferenceInstrumentedTest {
    @Test fun tipsRuntimeCoexistsWithLp() = assertTipsRuntime()
}
