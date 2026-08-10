package com.impulsive.app.frontend.ads

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeBrowseConsentSourceTest {
    private val consentSource = File(
        "src/main/java/com/impulsive/app/frontend/ads/SafeBrowseConsentManager.kt",
    ).readText()

    private val adSource = File(
        "src/main/java/com/impulsive/app/frontend/ads/SafeBrowseRewardedAdController.kt",
    ).readText()

    @Test
    fun consentManagerUsesTheOfficialUmpFlow() {
        listOf(
            "requestConsentInfoUpdate",
            "loadAndShowConsentFormIfRequired",
            "canRequestAds",
            "privacyOptionsRequirementStatus",
            "showPrivacyOptionsForm",
        ).forEach { required ->
            assertTrue("missing $required", consentSource.contains(required))
        }
    }

    @Test
    fun mobileAdsIsNotInitialisedInsideTheConsentManager() {
        assertFalse(consentSource.contains("MobileAds.initialize"))
    }

    @Test
    fun adControllerOnlyInitialisesMobileAdsWhenConsentAllowsRequests() {
        val preloadIndex = adSource.indexOf("fun preload(eligibility: SafeBrowseAdEligibility)")
        val nextFunctionIndex = adSource.indexOf("private fun ensureMobileAdsInitialized", preloadIndex)
        val preloadBlock = adSource.substring(preloadIndex, nextFunctionIndex)
        assertTrue(preloadBlock.contains("eligibility.eligibleToPreload"))

        val eligibilityIndex = adSource.indexOf("data class SafeBrowseAdEligibility")
        val eligibilityBlock = adSource.substring(eligibilityIndex, adSource.indexOf("internal const val", eligibilityIndex))
        assertTrue(eligibilityBlock.contains("canRequestAds"))
    }

    @Test
    fun consentManagerDoesNotCreateACustomConsentForm() {
        listOf("AlertDialog", "Dialog(", "CustomConsentForm").forEach { forbidden ->
            assertFalse(consentSource.contains(forbidden))
        }
    }

    @Test
    fun infoUpdateAndFormPresentationAreSeparateFunctions() {
        assertTrue(consentSource.contains("fun requestConsentInfoUpdate(activity: Activity)"))
        assertTrue(consentSource.contains("fun showRequiredFormIfAppropriate(activity: Activity)"))
        assertFalse(consentSource.contains("fun requestConsent(activity: Activity)"))
    }

    @Test
    fun infoUpdateRunsAtMostOncePerLaunchAndGuardsConcurrentRequests() {
        assertTrue(consentSource.contains("infoUpdateCompleted"))
        assertTrue(consentSource.contains("infoRequestInFlight"))
        val functionIndex = consentSource.indexOf("fun requestConsentInfoUpdate(activity: Activity)")
        val nextFunctionIndex = consentSource.indexOf("fun retryConsentInfoUpdate", functionIndex)
        val functionBlock = consentSource.substring(functionIndex, nextFunctionIndex)
        assertTrue(functionBlock.contains("if (infoUpdateCompleted.get())"))
        assertTrue(functionBlock.contains("infoRequestInFlight.compareAndSet(false, true)"))
    }

    @Test
    fun debugConsentGeographyRequiresAnExplicitOptInNotJustDebuggable() {
        // A debug build alone must never force EEA debug geography -- both `isDebuggable`
        // AND the explicit BuildConfig flag must be true.
        assertTrue(consentSource.contains("BuildConfig.IMPULSIVE_UMP_DEBUG_EEA"))
        assertTrue(consentSource.contains("!isDebuggable || !BuildConfig.IMPULSIVE_UMP_DEBUG_EEA"))
        assertTrue(consentSource.contains("BuildConfig.IMPULSIVE_UMP_TEST_DEVICE_HASH"))
    }

    @Test
    fun sharedInstanceIsExposedThroughACompositionLocal() {
        assertTrue(consentSource.contains("val LocalSafeBrowseConsentManager"))
        assertTrue(consentSource.contains("staticCompositionLocalOf<SafeBrowseConsentManager?>"))
    }
}
