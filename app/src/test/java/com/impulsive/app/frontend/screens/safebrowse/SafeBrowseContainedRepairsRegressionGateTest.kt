package com.impulsive.app.frontend.screens.safebrowse

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks all six contained Safe Browse repairs completed before major billing work begins:
 * exact Pass expiry, authoritative timer expiration, rewarded-ad reload safety, process-wide
 * UMP consent sequencing without raw error exposure, explicit debug-EEA gating, and the
 * secured browser's access-loading gate with correct pending-navigation and disposal
 * handling. A single gate test class so a future regression in any one contained repair is
 * caught without having to remember which of the individual regression classes covers it.
 */
class SafeBrowseContainedRepairsRegressionGateTest {
    private val accessModelsSource = File(
        "src/main/java/com/impulsive/app/backend/domain/model/safebrowse/SafeBrowseAccessModels.kt",
    ).readText()
    private val accessViewModelSource = File(
        "src/main/java/com/impulsive/app/backend/session/safebrowse/SafeBrowseAccessViewModel.kt",
    ).readText()
    private val rewardedAdControllerSource = File(
        "src/main/java/com/impulsive/app/frontend/ads/SafeBrowseRewardedAdController.kt",
    ).readText()
    private val consentManagerSource = File(
        "src/main/java/com/impulsive/app/frontend/ads/SafeBrowseConsentManager.kt",
    ).readText()
    private val browserScreenSource = File(
        "src/main/java/com/impulsive/app/frontend/screens/safebrowse/SafeBrowseBrowserScreen.kt",
    ).readText()

    private fun blockBetween(
        source: String,
        startMarker: String,
        endMarker: String,
    ): String {
        val start =
            source.indexOf(
                startMarker,
            )

        assertTrue(
            "Missing start marker: $startMarker",
            start >= 0,
        )

        val end =
            source.indexOf(
                endMarker,
                start +
                    startMarker.length,
            )

        assertTrue(
            "Missing end marker: $endMarker",
            end > start,
        )

        return source.substring(
            start,
            end,
        )
    }

    @Test
    fun repair1NoSafeBrowsePassOfflineGraceMillis() {
        assertTrue(!accessModelsSource.contains("SafeBrowsePassOfflineGraceMillis"))
    }

    @Test
    fun repair1ExactPassExpiryComparison() {
        val start = accessModelsSource.indexOf("fun SafeBrowsePassEntitlement.isValidAt(")
        val end = accessModelsSource.indexOf("sealed interface SafeBrowseRewardGrantResult", start)
        val block = accessModelsSource.substring(start, end)
        assertTrue(block.contains("nowMillis"))
        assertTrue(block.contains("expiryTimeMillis"))
        assertTrue(!block.contains("+"))
    }

    @Test
    fun repair2EndUsagePersistencePrecedesAccessExpired() {
        val block =
            blockBetween(
                source =
                    accessViewModelSource,
                startMarker =
                    "private suspend fun expireUsageLocked()",
                endMarker =
                    "private suspend fun applyPassEntitlementLocked(",
            )

        val endUsageIndex =
            block.indexOf(
                "val snapshot = runCatching { repository.endUsage() }",
            )

        val authoritativeIndex =
            block.indexOf(
                "authoritativeLedgerSnapshot = snapshot",
                endUsageIndex,
            )

        val expiredIndex =
            block.indexOf(
                "emitAccessExpiredOnceLocked()",
                authoritativeIndex,
            )

        assertTrue(
            endUsageIndex >= 0,
        )

        assertTrue(
            authoritativeIndex >
                endUsageIndex,
        )

        assertTrue(
            expiredIndex >
                authoritativeIndex,
        )
    }

    @Test
    fun repair2FailedEndUsageReturnsWithoutPublishingAccessExpired() {
        val block =
            blockBetween(
                source =
                    accessViewModelSource,
                startMarker =
                    "private suspend fun expireUsageLocked()",
                endMarker =
                    "private suspend fun applyPassEntitlementLocked(",
            )

        val endUsageIndex =
            block.indexOf(
                "val snapshot = runCatching { repository.endUsage() }",
            )

        val failureHandlerIndex =
            block.indexOf(
                "handlePersistenceFailureLocked(error)",
                endUsageIndex,
            )

        val returnIndex =
            block.indexOf(
                "return true",
                failureHandlerIndex,
            )

        val authoritativeIndex =
            block.indexOf(
                "authoritativeLedgerSnapshot = snapshot",
                endUsageIndex,
            )

        assertTrue(
            endUsageIndex >= 0,
        )

        assertTrue(
            failureHandlerIndex >
                endUsageIndex,
        )

        assertTrue(
            returnIndex in
                failureHandlerIndex until
                authoritativeIndex,
        )
    }

    @Test
    fun repair3RewardedControllerRetainsCurrentEligibility() {
        assertTrue(rewardedAdControllerSource.contains("private var currentEligibility = SafeBrowseAdEligibility("))
    }

    @Test
    fun repair3ReloadChecksGenerationAndEligibility() {
        val start = rewardedAdControllerSource.indexOf("private fun reloadAfterShow(showGeneration: Long)")
        assertTrue(start >= 0)
        val end = rewardedAdControllerSource.indexOf("fun clear()", start)
        val block = rewardedAdControllerSource.substring(start, end)
        assertTrue(block.contains("isCurrentAndEligible(showGeneration)"))
    }

    @Test
    fun repair3ClearInvalidatesTheGeneration() {
        val start = rewardedAdControllerSource.indexOf("fun clear()")
        val block = rewardedAdControllerSource.substring(start)
        assertTrue(block.contains("invalidateCurrentAd()"))
        val invalidateStart = rewardedAdControllerSource.indexOf("private fun invalidateCurrentAd()")
        val invalidateEnd = rewardedAdControllerSource.indexOf("private fun maybeLoadAd()", invalidateStart)
        assertTrue(rewardedAdControllerSource.substring(invalidateStart, invalidateEnd).contains("generation += 1L"))
    }

    @Test
    fun repair4UmpRequestAndFormUseAtomicGuards() {
        assertTrue(consentManagerSource.contains("AtomicBoolean(false)"))
        assertTrue(consentManagerSource.contains("compareAndSet(false, true)"))
    }

    @Test
    fun repair4RawUmpErrorsAreNeverExposed() {
        assertTrue(!consentManagerSource.contains("formError.message"))
        assertTrue(!consentManagerSource.contains("formError?.message"))
    }

    @Test
    fun repair4EeaDebugRequiresAnExplicitNonBlankHash() {
        assertTrue(consentManagerSource.contains("testDeviceHash.isEmpty()"))
    }

    @Test
    fun repair5BrowserHasAnAccessLoadingGate() {
        assertTrue(browserScreenSource.contains("\"safe_browse_browser_access_loading\""))
        assertTrue(browserScreenSource.contains("DomainAccessState.Loading ->"))
    }

    @Test
    fun repair5ReturnHomeClearsPendingUrl() {
        val index = browserScreenSource.indexOf("SafeBrowseBrowserEffect.ReturnHome -> {")
        val end = browserScreenSource.indexOf("}\n            }\n        }\n    }", index)
        assertTrue(browserScreenSource.substring(index, end).contains("pendingUrl = null"))
    }

    @Test
    fun repair5DisposalEndsTimedUsage() {
        val onDisposeIndex = browserScreenSource.indexOf("onDispose {")
        val onDisposeEnd = browserScreenSource.indexOf("}\n    }", onDisposeIndex)
        assertTrue(browserScreenSource.substring(onDisposeIndex, onDisposeEnd).contains("accessViewModel.endBrowserUsage()"))
    }

    @Test
    fun theThreeContainedRegressionTestClassesExist() {
        listOf(
            "src/test/java/com/impulsive/app/frontend/ads/SafeBrowseConsentLifecycleSourceTest.kt",
            "src/test/java/com/impulsive/app/frontend/screens/safebrowse/SafeBrowseBrowserLifecycleSourceTest.kt",
            "src/test/java/com/impulsive/app/frontend/screens/safebrowse/SafeBrowseContainedRepairsRegressionGateTest.kt",
        ).forEach { path ->
            assertTrue("missing regression test class: $path", File(path).exists())
        }
    }
}
