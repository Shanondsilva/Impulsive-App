package com.impulsive.app.backend.service.billing

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeBrowsePassAccountWriteGuardSourceTest {
    private val billingSource = File(
        "src/main/java/com/impulsive/app/backend/service/billing/BillingManager.kt",
    ).readText()
    private val mainActivitySource = File(
        "src/main/java/com/impulsive/app/MainActivity.kt",
    ).readText()
    private val repositorySource = File(
        "src/main/java/com/impulsive/app/backend/data/repository/SafeBrowsePassRepository.kt",
    ).readText()
    private val authFactorySource = File(
        "src/main/java/com/impulsive/app/backend/data/repository/AuthRepositoryFactory.kt",
    ).readText()
    private val accessDataSourceSource = File(
        "src/main/java/com/impulsive/app/backend/data/local/preferences/SafeBrowseAccessDataSource.kt",
    ).readText()

    private fun blockBetween(
        source: String,
        startMarker: String,
        endMarker: String,
    ): String {
        val start = source.indexOf(startMarker)
        val end = source.indexOf(endMarker, start)

        assertTrue("Missing start marker: $startMarker", start >= 0)
        assertTrue("Missing end marker: $endMarker", end > start)

        return source.substring(start, end)
    }

    @Test
    fun mainActivityUsesTheDirectAuthStateCallback() {
        assertTrue(mainActivitySource.contains("onAuthenticationStateChanged("))
        assertFalse(mainActivitySource.contains("shouldReconcileBillingAfterAuthChange"))
    }

    @Test
    fun billingManagerDefinesTheCalledAuthenticationEntryPoint() {
        assertTrue(
            billingSource.contains(
                "fun onAuthenticationStateChanged(",
            ),
        )

        assertTrue(
            mainActivitySource.contains(
                "billingManager.onAuthenticationStateChanged(",
            ),
        )
    }

    @Test
    fun authenticationTransitionsUseLatestGenerationAndReconcileEveryUid() {
        val block = blockBetween(
            source = billingSource,
            startMarker = "fun onAuthenticationStateChanged(",
            endMarker = "fun onAppForegrounded()",
        )

        assertTrue(block.contains("authenticationGeneration.incrementAndGet()"))
        assertTrue(block.contains("authenticationMutex.withLock"))
        assertTrue(
            Regex("generation\\s*!=\\s*authenticationGeneration\\.get\\(\\)")
                .findAll(block)
                .count() >= 2,
        )
        assertTrue(block.contains("currentNonAnonymousFirebaseUid() !="))
        assertTrue(block.contains(".onAccountChanged("))
        assertTrue(block.contains("normalisedUid"))
        assertTrue(block.contains("lastAuthenticatedSafeBrowsePassUid"))
        assertFalse(block.contains("onAccountChanged(null)"))
    }

    @Test
    fun staleRepositoryWriteIsRemovedAfterAccountSwitch() {
        val block = blockBetween(
            source = repositorySource,
            startMarker = "suspend fun setVerifiedEntitlement(",
            endMarker = "suspend fun onAccountChanged(",
        )

        assertTrue(block.contains("val stillCurrent"))
        assertTrue(block.contains("dataSource.clearUnlessOwnedBy("))
        assertTrue(block.contains("return stillCurrent"))
    }

    @Test
    fun guestOnlyRepositoryClearsPassWithoutConstructingFirebaseRepository() {
        val guestStart = authFactorySource.indexOf("private class GuestOnlyAuthRepository(")

        assertTrue(guestStart >= 0)

        val guestBlock = authFactorySource.substring(guestStart)

        assertTrue(guestBlock.contains("SafeBrowsePassEntitlementDataSource"))
        assertTrue(guestBlock.contains("clearSafeBrowsePassCache()"))
        assertFalse(guestBlock.contains("SafeBrowsePassRepository(appContext)"))
    }

    @Test
    fun suppressedRewardClearsBalanceLeaseAndBothBaselines() {
        val block = blockBetween(
            source = accessDataSourceSource,
            startMarker = "suspend fun grantReward(",
            endMarker = "suspend fun clearTimedAccessForPassActivation()",
        )

        assertTrue(block.contains("if (!grantTimedAccess)"))
        assertTrue(block.contains("preferences[RemainingMillisKey]"))
        assertTrue(block.contains("preferences[LeaseActiveKey]"))
        assertTrue(block.contains("LeaseBaselineElapsedKey"))
        assertTrue(block.contains("LeaseBaselineEpochKey"))
        assertTrue(block.contains("Duplicate("))
        assertTrue(block.contains("if (grantTimedAccess)"))
    }

    @Test
    fun billingClientIsStillSingular() {
        assertEquals(1, Regex("BillingClient\\.newBuilder").findAll(billingSource).count())
    }

    @Test
    fun purchaseTokenIsNotPersistedOrLoggedInThePassCacheLayer() {
        val source = File("src/main/java/com/impulsive/app/backend/data/local/preferences/SafeBrowsePassEntitlementDataSource.kt").readText()
        val repository = File("src/main/java/com/impulsive/app/backend/data/repository/SafeBrowsePassRepository.kt").readText()
        assertFalse(source.contains("purchaseToken", ignoreCase = true))
        assertFalse(repository.contains("purchaseToken", ignoreCase = true))
    }
}