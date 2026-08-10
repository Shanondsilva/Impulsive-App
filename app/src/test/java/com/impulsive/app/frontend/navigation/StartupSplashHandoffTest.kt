package com.impulsive.app.frontend.navigation

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupSplashHandoffTest {
    private val mainActivity = File("src/main/java/com/impulsive/app/MainActivity.kt").readText()

    private val onCreateBlock: String by lazy {
        val start = mainActivity.indexOf("override fun onCreate(savedInstanceState: Bundle?) {")
        val end = mainActivity.indexOf("override fun onNewIntent(intent: Intent) {", start)
        mainActivity.substring(start, end)
    }

    @Test
    fun installsSplashScreenAndKeepsItOnScreenUntilContentReady() {
        assertTrue(onCreateBlock.contains("val splashScreen = installSplashScreen()"))
        assertTrue(onCreateBlock.contains("splashScreen.setKeepOnScreenCondition"))
        assertTrue(onCreateBlock.contains("!startupContentReady"))
    }

    @Test
    fun declaresOnboardingViewModelAndCollectsItsStateInActivityScope() {
        assertTrue(
            mainActivity.contains(
                "private val onboardingViewModel: OnboardingViewModel by viewModels()",
            ),
        )
        assertTrue(
            onCreateBlock.contains(
                "val onboardingState by onboardingViewModel.state.collectAsStateWithLifecycle()",
            ),
        )
        assertTrue(onCreateBlock.contains("onboardingViewModel = onboardingViewModel"))
    }

    @Test
    fun appLockEnabledStartsNullRatherThanAssumingFalse() {
        assertTrue(onCreateBlock.contains("val appLockEnabled by produceState<Boolean?>("))
        assertTrue(onCreateBlock.contains("initialValue = null"))
    }

    @Test
    fun nativeSplashIsKeptWhileAppLockAndOnboardingStateAreUnknown() {
        val appLockUnknownIndex = onCreateBlock.indexOf("appLockEnabled == null -> Unit")
        val onboardingLoadingIndex = onCreateBlock.indexOf("onboardingState.isLoading -> Unit")
        val appNavHostIndex = onCreateBlock.indexOf("AppNavHost(")
        assertTrue(appLockUnknownIndex >= 0)
        assertTrue(onboardingLoadingIndex >= 0)
        assertTrue(appNavHostIndex >= 0)
        assertTrue(appLockUnknownIndex < appNavHostIndex)
        assertTrue(onboardingLoadingIndex < appNavHostIndex)
    }

    @Test
    fun noVisibleComposeLoadingSurfaceBetweenSplashAndRealContent() {
        // Strip comments: this file's own explanatory comments legitimately
        // reference the old "Loading Impulsive..." symptom being fixed.
        val withoutComments = onCreateBlock.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .lines()
            .joinToString("\n") { it.substringBefore("//") }
        assertFalse(withoutComments.contains("Loading Impulsive"))
        assertFalse(withoutComments.contains("CircularProgressIndicator"))
    }

    @Test
    fun lockedBranchRendersRealScreenBeforeReleasingSplash() {
        val lockedIndex = onCreateBlock.indexOf("locked -> {")
        val onboardingLoadingIndex = onCreateBlock.indexOf("onboardingState.isLoading -> Unit")
        assertTrue(lockedIndex >= 0)
        val lockedBlock = onCreateBlock.substring(lockedIndex, onboardingLoadingIndex)
        assertTrue(lockedBlock.contains("AppLockGateScreen"))
        assertTrue(lockedBlock.contains("startupContentReady = true"))
    }

    @Test
    fun realAppNavHostBranchReleasesSplashAfterComposing() {
        val elseIndex = onCreateBlock.indexOf("else -> {")
        assertTrue(elseIndex >= 0)
        val elseBlock = onCreateBlock.substring(elseIndex)
        val appNavHostIndex = elseBlock.indexOf("AppNavHost(")
        val readyIndex = elseBlock.indexOf("startupContentReady = true")
        assertTrue(appNavHostIndex >= 0)
        assertTrue(readyIndex > appNavHostIndex)
    }
}
