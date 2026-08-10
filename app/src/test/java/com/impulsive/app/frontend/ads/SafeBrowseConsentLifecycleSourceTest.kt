package com.impulsive.app.frontend.ads

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pre-major correction gate: the UMP consent manager is now a single process-wide instance
 * with atomic-guarded request/form sequencing and no raw SDK error exposure. Locks that
 * rewrite at the source level, and locks every call-site that must consume it correctly
 * (AppNavHost owns the launch-time info update; SafeBrowseRoute only ever shows the form
 * from a resolved state; Settings never constructs its own manager or requests an update).
 */
class SafeBrowseConsentLifecycleSourceTest {
    private val consentSource = File(
        "src/main/java/com/impulsive/app/frontend/ads/SafeBrowseConsentManager.kt",
    ).readText()
    private val navHostSource = File(
        "src/main/java/com/impulsive/app/frontend/navigation/AppNavHost.kt",
    ).readText()
    private val safeBrowseRouteSource = File(
        "src/main/java/com/impulsive/app/frontend/screens/safebrowse/SafeBrowseRoute.kt",
    ).readText()
    private val settingsScreenSource = File(
        "src/main/java/com/impulsive/app/frontend/screens/settings/SettingsScreen.kt",
    ).readText()

    @Test
    fun providerObjectExists() {
        assertTrue(consentSource.contains("object SafeBrowseConsentManagerProvider"))
        assertTrue(consentSource.contains("fun get(context: Context): SafeBrowseConsentManager"))
    }

    @Test
    fun managerConstructorIsInternal() {
        assertTrue(consentSource.contains("class SafeBrowseConsentManager internal constructor("))
    }

    @Test
    fun infoRequestInFlightUsesAtomicBoolean() {
        assertTrue(consentSource.contains("private val infoRequestInFlight = AtomicBoolean(false)"))
    }

    @Test
    fun infoUpdateCompletedUsesAtomicBoolean() {
        assertTrue(consentSource.contains("private val infoUpdateCompleted = AtomicBoolean(false)"))
    }

    @Test
    fun infoUpdateSucceededUsesAtomicBoolean() {
        assertTrue(consentSource.contains("private val infoUpdateSucceeded = AtomicBoolean(false)"))
    }

    @Test
    fun requiredFormAttemptedUsesAtomicBoolean() {
        assertTrue(consentSource.contains("private val requiredFormAttempted = AtomicBoolean(false)"))
    }

    @Test
    fun requiredFormShowingUsesAtomicBoolean() {
        assertTrue(consentSource.contains("private val requiredFormShowing = AtomicBoolean(false)"))
    }

    @Test
    fun requestConsentInfoUpdateUsesCompareAndSet() {
        val functionIndex = consentSource.indexOf("fun requestConsentInfoUpdate(activity: Activity)")
        val nextFunctionIndex = consentSource.indexOf("fun retryConsentInfoUpdate", functionIndex)
        val block = consentSource.substring(functionIndex, nextFunctionIndex)
        assertTrue(block.contains("infoRequestInFlight.compareAndSet(false, true)"))
    }

    @Test
    fun requiredFormUsesCompareAndSetForBothAttemptAndShowingGuards() {
        val functionIndex = consentSource.indexOf("fun showRequiredFormIfAppropriate(activity: Activity)")
        assertTrue(functionIndex >= 0)
        val block = consentSource.substring(functionIndex, consentSource.indexOf("fun canRequestAds", functionIndex))
        assertTrue(block.contains("requiredFormAttempted.compareAndSet(false, true)"))
        assertTrue(block.contains("requiredFormShowing.compareAndSet(false, true)"))
    }

    @Test
    fun everyFailurePathChecksCanRequestAdsBeforePublishingFailed() {
        val functionIndex = consentSource.indexOf("private fun publishStateAfterFailure(stableMessage: String)")
        assertTrue(functionIndex >= 0)
        val block = consentSource.substring(functionIndex, consentSource.indexOf("applyDebugSettingsIfConfigured", functionIndex))
        assertTrue(block.contains("consentInformation.canRequestAds()"))

        // Every failure branch in the class must route through publishStateAfterFailure
        // rather than setting SafeBrowseConsentState.Failed directly -- the only legitimate
        // assignment is the one inside publishStateAfterFailure itself.
        val directFailedAssignments = Regex("_state\\.value\\s*=\\s*SafeBrowseConsentState\\.Failed")
            .findAll(consentSource)
            .count()
        assertEquals(1, directFailedAssignments)
    }

    @Test
    fun rawUmpFormErrorMessageIsNeverExposed() {
        assertFalse(consentSource.contains("formError.message"))
        assertFalse(consentSource.contains("formError?.message"))
    }

    @Test
    fun debugEeaGeographyRequiresANonBlankTestDeviceHash() {
        val functionIndex = consentSource.indexOf("applyDebugSettingsIfConfigured(activity: Activity)")
        assertTrue(functionIndex >= 0)
        val block = consentSource.substring(functionIndex)
        assertTrue(block.contains("testDeviceHash.isEmpty()"))
        assertTrue(block.contains("!isDebuggable || !BuildConfig.IMPULSIVE_UMP_DEBUG_EEA || testDeviceHash.isEmpty()"))
        assertTrue(block.contains("addTestDeviceHashedId(testDeviceHash)"))
    }

    @Test
    fun retryConsentInfoUpdateExists() {
        assertTrue(consentSource.contains("fun retryConsentInfoUpdate(activity: Activity)"))
        val functionIndex = consentSource.indexOf("fun retryConsentInfoUpdate(activity: Activity)")
        val block = consentSource.substring(functionIndex, consentSource.indexOf("fun showRequiredFormIfAppropriate", functionIndex))
        assertTrue(block.contains("infoUpdateCompleted.set(false)"))
        assertTrue(block.contains("requestConsentInfoUpdate(activity)"))
    }

    @Test
    fun requiredFormRequiresASuccessfulInfoUpdateFirst() {
        val functionIndex = consentSource.indexOf("fun showRequiredFormIfAppropriate(activity: Activity)")
        val block = consentSource.substring(functionIndex, consentSource.indexOf("fun canRequestAds", functionIndex))
        assertTrue(block.contains("!infoUpdateCompleted.get() || !infoUpdateSucceeded.get()"))
    }

    @Test
    fun appNavHostRequestsConsentInfoOnceAtTheRoot() {
        assertTrue(navHostSource.contains("SafeBrowseConsentManagerProvider"))
        assertTrue(navHostSource.contains("requestConsentInfoUpdate"))
    }

    @Test
    fun appNavHostNeverShowsTheConsentForm() {
        assertFalse(navHostSource.contains("showRequiredFormIfAppropriate"))
        assertFalse(navHostSource.contains("showPrivacyOptionsForm"))
    }

    @Test
    fun safeBrowseRouteNeverConstructsAManager() {
        assertFalse(safeBrowseRouteSource.contains("SafeBrowseConsentManager("))
        assertTrue(safeBrowseRouteSource.contains("requireNotNull(LocalSafeBrowseConsentManager.current)"))
    }

    @Test
    fun settingsNeverConstructsAManager() {
        assertFalse(settingsScreenSource.contains("SafeBrowseConsentManager("))
    }

    @Test
    fun safeBrowseRouteNeverRequestsAndShowsInTheSameCallback() {
        // ON_RESUME must only refresh access -- never call requestConsentInfoUpdate or
        // showRequiredFormIfAppropriate as a pair from the same lifecycle event.
        val resumeIndex = safeBrowseRouteSource.indexOf("Lifecycle.Event.ON_RESUME")
        assertTrue(resumeIndex >= 0)
        val resumeBlockEnd = safeBrowseRouteSource.indexOf("lifecycleOwner.lifecycle.addObserver(observer)", resumeIndex)
        val resumeBlock = safeBrowseRouteSource.substring(resumeIndex, resumeBlockEnd)
        assertFalse(resumeBlock.contains("requestConsentInfoUpdate"))
        assertFalse(resumeBlock.contains("showRequiredFormIfAppropriate"))
        assertTrue(resumeBlock.contains("accessViewModel.refresh()"))
    }

    @Test
    fun settingsNeverCallsRequestConsentInfoUpdate() {
        assertFalse(settingsScreenSource.contains("requestConsentInfoUpdate"))
    }

    @Test
    fun onlyTheProviderConstructsSafeBrowseConsentManager() {
        val constructions = Regex("SafeBrowseConsentManager\\(").findAll(consentSource).count()
        // Exactly one constructor call site: inside SafeBrowseConsentManagerProvider.get().
        assertEquals(1, constructions)

        assertFalse(navHostSource.contains("SafeBrowseConsentManager("))
        assertFalse(safeBrowseRouteSource.contains("SafeBrowseConsentManager("))
        assertFalse(settingsScreenSource.contains("SafeBrowseConsentManager("))
    }
}
