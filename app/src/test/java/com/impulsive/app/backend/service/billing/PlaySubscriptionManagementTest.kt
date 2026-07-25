package com.impulsive.app.backend.service.billing

import com.impulsive.app.backend.domain.model.premium.BillingPeriod
import com.impulsive.app.backend.domain.model.premium.EntitlementSource
import com.impulsive.app.backend.domain.model.premium.PremiumEntitlement
import com.impulsive.app.backend.domain.model.premium.PremiumTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PlaySubscriptionManagementTest {

    @Test
    fun `active monthly Play entitlement resolves monthly product`() {
        assertEquals(
            BillingManager.PlusProductId,
            activePlaySubscriptionProductId(
                entitlement = activePlayEntitlement(BillingPeriod.Monthly),
                nowMillis = 100_000L,
            ),
        )
    }

    @Test
    fun `active yearly Play entitlement resolves yearly product`() {
        assertEquals(
            BillingManager.PlusYearlyProductId,
            activePlaySubscriptionProductId(
                entitlement = activePlayEntitlement(BillingPeriod.Yearly),
                nowMillis = 100_000L,
            ),
        )
    }

    @Test
    fun `free entitlement has no manageable product`() {
        assertNull(
            activePlaySubscriptionProductId(
                entitlement = activePlayEntitlement(BillingPeriod.Monthly).copy(
                    tier = PremiumTier.Free,
                ),
                nowMillis = 100_000L,
            ),
        )
    }

    @Test
    fun `Debug entitlement has no manageable product`() {
        assertNull(
            activePlaySubscriptionProductId(
                entitlement = activePlayEntitlement(BillingPeriod.Monthly).copy(
                    source = EntitlementSource.Debug,
                ),
                nowMillis = 100_000L,
            ),
        )
    }

    @Test
    fun `expired Play entitlement has no manageable product`() {
        assertNull(
            activePlaySubscriptionProductId(
                entitlement = activePlayEntitlement(BillingPeriod.Monthly).copy(
                    expiryTimeMillis = 99_999L,
                ),
                nowMillis = 100_000L,
            ),
        )
    }

    @Test
    fun `exact expiry boundary has no manageable product`() {
        assertNull(
            activePlaySubscriptionProductId(
                entitlement = activePlayEntitlement(BillingPeriod.Monthly).copy(
                    expiryTimeMillis = 100_000L,
                ),
                nowMillis = 100_000L,
            ),
        )
    }

    @Test
    fun `missing billing period has no manageable product`() {
        assertNull(
            activePlaySubscriptionProductId(
                entitlement = activePlayEntitlement(period = null),
                nowMillis = 100_000L,
            ),
        )
    }

    @Test
    fun `monthly management URL uses existing product and app package`() {
        assertEquals(
            "https://play.google.com/store/account/subscriptions" +
                "?sku=impulsive_plus_monthly&package=com.impulsive.app",
            buildGooglePlaySubscriptionManagementUrl(
                packageName = "com.impulsive.app",
                productId = BillingManager.PlusProductId,
            ),
        )
    }

    @Test
    fun `yearly management URL uses existing product and app package`() {
        assertEquals(
            "https://play.google.com/store/account/subscriptions" +
                "?sku=impulsive_plus_yearly&package=com.impulsive.app",
            buildGooglePlaySubscriptionManagementUrl(
                packageName = "com.impulsive.app",
                productId = BillingManager.PlusYearlyProductId,
            ),
        )
    }

    @Test
    fun `management URL trims inputs`() {
        assertEquals(
            "https://play.google.com/store/account/subscriptions" +
                "?sku=impulsive_plus_monthly&package=com.impulsive.app",
            buildGooglePlaySubscriptionManagementUrl(
                packageName = "  com.impulsive.app  ",
                productId = "  impulsive_plus_monthly  ",
            ),
        )
    }

    @Test
    fun `management URL encodes query values`() {
        assertEquals(
            "https://play.google.com/store/account/subscriptions" +
                "?sku=plus+monthly%2Fyearly&package=com.impulsive+app",
            buildGooglePlaySubscriptionManagementUrl(
                packageName = "com.impulsive app",
                productId = "plus monthly/yearly",
            ),
        )
    }

    @Test
    fun `blank package is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            buildGooglePlaySubscriptionManagementUrl(
                packageName = "   ",
                productId = BillingManager.PlusProductId,
            )
        }
    }

    @Test
    fun `blank product is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            buildGooglePlaySubscriptionManagementUrl(
                packageName = "com.impulsive.app",
                productId = "   ",
            )
        }
    }

    private fun activePlayEntitlement(period: BillingPeriod?) = PremiumEntitlement(
        tier = PremiumTier.Basic,
        period = period,
        source = EntitlementSource.PlayBilling,
        expiryTimeMillis = 200_000L,
    )
}
