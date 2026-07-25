package com.impulsive.app.backend.domain.model.premium

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumEntitlementPolicyTest {

    @Test
    fun `default entitlement uses neutral source`() {
        assertEquals(EntitlementSource.None, PremiumEntitlement().source)
    }

    @Test
    fun `neutral entitlement never grants paid feature`() {
        val entitlement = PremiumEntitlement(
            tier = PremiumTier.Basic,
            source = EntitlementSource.None,
            expiryTimeMillis = Long.MAX_VALUE,
        )

        assertFalse(
            entitlement.hasFeatureAt(
                feature = PremiumFeature.VpnWebsiteBlocker,
                nowMillis = 100_000L,
                allowDebugEntitlement = true,
            ),
        )
    }

    @Test
    fun `active Play Billing entitlement grants included feature`() {
        val entitlement = PremiumEntitlement(
            tier = PremiumTier.Basic,
            source = EntitlementSource.PlayBilling,
            expiryTimeMillis = 200_000L,
        )

        assertTrue(
            entitlement.hasFeatureAt(
                PremiumFeature.VpnWebsiteBlocker,
                nowMillis = 100_000L,
                allowDebugEntitlement = false,
            ),
        )
    }

    @Test
    fun `Play Billing entitlement remains active within offline grace`() {
        val entitlement = playEntitlement(expiryTimeMillis = 100_000L)
        val nowMillis = 100_000L +
            PremiumEntitlementPolicy.OfflineGraceMillis -
            1L

        assertTrue(entitlement.hasVpnFeatureAt(nowMillis))
    }

    @Test
    fun `Play Billing entitlement is inactive at exact offline grace boundary`() {
        val expiryTimeMillis = 100_000L
        val entitlement = playEntitlement(expiryTimeMillis)
        val nowMillis = expiryTimeMillis + PremiumEntitlementPolicy.OfflineGraceMillis

        assertFalse(entitlement.hasVpnFeatureAt(nowMillis))
    }

    @Test
    fun `Play Billing entitlement is inactive after offline grace`() {
        val expiryTimeMillis = 100_000L
        val entitlement = playEntitlement(expiryTimeMillis)
        val nowMillis = expiryTimeMillis +
            PremiumEntitlementPolicy.OfflineGraceMillis +
            1L

        assertFalse(entitlement.hasVpnFeatureAt(nowMillis))
    }

    @Test
    fun `Play Billing entitlement with missing expiry is inactive`() {
        assertFalse(playEntitlement(expiryTimeMillis = 0L).hasVpnFeatureAt(100_000L))
    }

    @Test
    fun `free tier remains inactive with future Play expiry`() {
        val entitlement = PremiumEntitlement(
            tier = PremiumTier.Free,
            source = EntitlementSource.PlayBilling,
            expiryTimeMillis = Long.MAX_VALUE,
        )

        assertFalse(entitlement.hasVpnFeatureAt(100_000L))
    }

    @Test
    fun `Debug entitlement grants access when debug is allowed`() {
        val entitlement = PremiumEntitlement(
            tier = PremiumTier.Basic,
            source = EntitlementSource.Debug,
        )

        assertTrue(
            entitlement.hasFeatureAt(
                PremiumFeature.VpnWebsiteBlocker,
                nowMillis = 100_000L,
                allowDebugEntitlement = true,
            ),
        )
    }

    @Test
    fun `Debug entitlement cannot grant access when debug is disallowed`() {
        val entitlement = PremiumEntitlement(
            tier = PremiumTier.Basic,
            source = EntitlementSource.Debug,
        )

        assertFalse(
            entitlement.hasFeatureAt(
                PremiumFeature.VpnWebsiteBlocker,
                nowMillis = 100_000L,
                allowDebugEntitlement = false,
            ),
        )
    }

    @Test
    fun `tier hierarchy still limits valid entitlement features`() {
        val entitlement = playEntitlement(expiryTimeMillis = 200_000L)

        assertTrue(entitlement.hasVpnFeatureAt(nowMillis = 100_000L))
        assertFalse(
            entitlement.hasFeatureAt(
                PremiumFeature.BodyMode,
                nowMillis = 100_000L,
                allowDebugEntitlement = false,
            ),
        )
    }

    private fun playEntitlement(expiryTimeMillis: Long) = PremiumEntitlement(
        tier = PremiumTier.Basic,
        source = EntitlementSource.PlayBilling,
        expiryTimeMillis = expiryTimeMillis,
    )

    private fun PremiumEntitlement.hasVpnFeatureAt(nowMillis: Long): Boolean =
        hasFeatureAt(
            PremiumFeature.VpnWebsiteBlocker,
            nowMillis = nowMillis,
            allowDebugEntitlement = false,
        )
}
