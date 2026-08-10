package com.impulsive.app.frontend.screens.safebrowse

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gate D8b: a rewarded ad must never be eligible to preload or show while a Safe Browse
 * Pass is active. `SafeBrowseAdEligibility.isLocked` is keyed on the domain
 * `SafeBrowseAccessState.Locked` case specifically, which `PassActive` never matches, so an
 * active Pass naturally makes the ad ineligible without any extra coordination code -- this
 * test locks that invariant in place.
 */
class SafeBrowseRoutePassAdCoordinationSourceTest {
    private val routeSource = File(
        "src/main/java/com/impulsive/app/frontend/screens/safebrowse/SafeBrowseRoute.kt",
    ).readText()

    @Test
    fun adEligibilityIsKeyedOnLockedSpecificallyNotOnTheAbsenceOfActive() {
        assertTrue(routeSource.contains("isLocked = domainAccessState is DomainAccessState.Locked"))
    }

    @Test
    fun passActiveNeverMarksTheRewardedAdAsAvailable() {
        val passActiveIndex = routeSource.indexOf("is DomainAccessState.PassActive -> SafeBrowseUiState(")
        assertTrue(passActiveIndex >= 0)
        val nextCaseIndex = routeSource.indexOf("is DomainAccessState.Error -> SafeBrowseUiState(", passActiveIndex)
        val passActiveBlock = routeSource.substring(passActiveIndex, nextCaseIndex)

        assertTrue(passActiveBlock.contains("rewardedUnlockAvailable = false"))
        assertFalse(passActiveBlock.contains("rewardedUnlockAvailable = adState"))
    }

    @Test
    fun passActiveStillAllowsOpeningTheBrowser() {
        val passActiveIndex = routeSource.indexOf("is DomainAccessState.PassActive -> SafeBrowseUiState(")
        val nextCaseIndex = routeSource.indexOf("is DomainAccessState.Error -> SafeBrowseUiState(", passActiveIndex)
        val passActiveBlock = routeSource.substring(passActiveIndex, nextCaseIndex)

        assertTrue(passActiveBlock.contains("browserOpeningAvailable = true"))
    }
}
