package com.impulsive.app.backend.service.billing

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contained repair 1 of 2: Safe Browse Pass access must end at its exact server-verified
 * expiry, never a three-day offline grace window. Impulsive Plus's own, separate offline
 * grace policy must remain completely untouched.
 */
class SafeBrowsePassExactExpirySourceTest {
    private val modelsSource = File(
        "src/main/java/com/impulsive/app/backend/domain/model/safebrowse/SafeBrowseAccessModels.kt",
    ).readText()
    private val billingManagerSource = File(
        "src/main/java/com/impulsive/app/backend/service/billing/BillingManager.kt",
    ).readText()

    @Test
    fun safeBrowsePassOfflineGraceConstantNoLongerExists() {
        assertFalse(modelsSource.contains("SafeBrowsePassOfflineGraceMillis"))
        assertFalse(billingManagerSource.contains("SafeBrowsePassOfflineGraceMillis"))
    }

    @Test
    fun safeBrowsePassIsValidAtNeverAddsToExpiry() {
        val start = modelsSource.indexOf("fun SafeBrowsePassEntitlement.isValidAt(")
        assertTrue(start >= 0)
        val end = modelsSource.indexOf("sealed interface SafeBrowseRewardGrantResult", start)
        val block = modelsSource.substring(start, end)

        assertFalse(block.contains("+"))
        assertFalse(block.contains("grace"))
        assertFalse(block.contains("Grace"))
        assertFalse(block.contains("Long.MAX_VALUE"))
        assertTrue(block.contains("nowMillis"))
        assertTrue(block.contains("expiryTimeMillis"))
    }

    @Test
    fun billingManagerUsesTheRenamedVerifiedExpiryFallback() {
        assertTrue(
            billingManagerSource.contains(
                "enforceSafeBrowsePassVerifiedExpiryAfterFailedRefresh",
            ),
        )
        assertFalse(
            billingManagerSource.contains(
                "enforceSafeBrowsePassOfflineGraceAfterFailedRefresh",
            ),
        )
    }

    @Test
    fun plusOfflineGraceFallbackRemainsPresentAndUnchanged() {
        assertTrue(billingManagerSource.contains("private suspend fun enforceOfflineGraceAfterFailedRefresh()"))
        // Plus's fallback derives its grace window through PremiumEntitlement.isValidAt(),
        // which internally reads PremiumEntitlementPolicy.OfflineGraceMillis in
        // PremiumModels.kt -- unaffected by this Safe Browse Pass-only repair.
        assertTrue(billingManagerSource.contains("cached.isValidAt("))
        assertTrue(billingManagerSource.contains("allowDebugEntitlement = false"))
    }

    @Test
    fun plusOfflineGracePolicyConstantIsUntouched() {
        val premiumModelsSource = File(
            "src/main/java/com/impulsive/app/backend/domain/model/premium/PremiumModels.kt",
        ).readText()
        assertTrue(premiumModelsSource.contains("OfflineGraceMillis"))
        assertTrue(
            premiumModelsSource.contains(
                "3L * 24L * 60L * 60L * 1_000L",
            ),
        )
    }
}
