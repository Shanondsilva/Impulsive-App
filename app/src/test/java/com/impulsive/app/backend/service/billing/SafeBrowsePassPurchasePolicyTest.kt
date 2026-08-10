package com.impulsive.app.backend.service.billing

import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowsePassEntitlement
import org.junit.Assert.assertEquals
import org.junit.Test

class SafeBrowsePassPurchasePolicyTest {

    @Test
    fun inactiveEntitlementAllowsMonthlyInitialPurchase() {
        val intent = resolveSafeBrowsePassPurchaseIntent(
            entitlement = SafeBrowsePassEntitlement(active = false),
            requestedPeriod = SafeBrowsePassPeriod.Monthly,
            nowMillis = 100_000L,
        )

        assertEquals(SafeBrowsePassPurchaseIntent.InitialPurchase, intent)
    }

    @Test
    fun inactiveEntitlementAllowsPrepaidInitialPurchase() {
        val intent = resolveSafeBrowsePassPurchaseIntent(
            entitlement = SafeBrowsePassEntitlement(active = false),
            requestedPeriod = SafeBrowsePassPeriod.Prepaid,
            nowMillis = 100_000L,
        )

        assertEquals(SafeBrowsePassPurchaseIntent.InitialPurchase, intent)
    }

    @Test
    fun expiredEntitlementAllowsInitialPurchase() {
        val intent = resolveSafeBrowsePassPurchaseIntent(
            entitlement = SafeBrowsePassEntitlement(
                active = true,
                basePlanId = "monthly",
                expiryTimeMillis = 50_000L,
                isPrepaid = false,
            ),
            requestedPeriod = SafeBrowsePassPeriod.Monthly,
            nowMillis = 100_000L,
        )

        assertEquals(SafeBrowsePassPurchaseIntent.InitialPurchase, intent)
    }

    @Test
    fun activeAutoRenewingPassBlocksAnotherPurchase() {
        val intent = resolveSafeBrowsePassPurchaseIntent(
            entitlement = SafeBrowsePassEntitlement(
                active = true,
                basePlanId = "monthly",
                expiryTimeMillis = 200_000L,
                isPrepaid = false,
            ),
            requestedPeriod = SafeBrowsePassPeriod.Monthly,
            nowMillis = 100_000L,
        )

        assertEquals(SafeBrowsePassPurchaseIntent.AlreadyActive, intent)
    }

    @Test
    fun activePrepaidPassBlocksMonthlySwitch() {
        val intent = resolveSafeBrowsePassPurchaseIntent(
            entitlement = SafeBrowsePassEntitlement(
                active = true,
                basePlanId = "prepaid-30",
                expiryTimeMillis = 200_000L,
                isPrepaid = true,
            ),
            requestedPeriod = SafeBrowsePassPeriod.Monthly,
            nowMillis = 100_000L,
        )

        assertEquals(SafeBrowsePassPurchaseIntent.AlreadyActive, intent)
    }

    @Test
    fun activePrepaidPassWithBasePlanAllowsTopUp() {
        val intent = resolveSafeBrowsePassPurchaseIntent(
            entitlement = SafeBrowsePassEntitlement(
                active = true,
                basePlanId = "prepaid-30",
                expiryTimeMillis = 200_000L,
                isPrepaid = true,
            ),
            requestedPeriod = SafeBrowsePassPeriod.Prepaid,
            nowMillis = 100_000L,
        )

        assertEquals(
            SafeBrowsePassPurchaseIntent.PrepaidTopUp(requiredBasePlanId = "prepaid-30"),
            intent,
        )
    }

    @Test
    fun activePrepaidPassWithoutBasePlanRequiresRefresh() {
        val intent = resolveSafeBrowsePassPurchaseIntent(
            entitlement = SafeBrowsePassEntitlement(
                active = true,
                basePlanId = null,
                expiryTimeMillis = 200_000L,
                isPrepaid = true,
            ),
            requestedPeriod = SafeBrowsePassPeriod.Prepaid,
            nowMillis = 100_000L,
        )

        assertEquals(SafeBrowsePassPurchaseIntent.RefreshRequired, intent)
    }

    @Test
    fun activePrepaidPassWithBlankBasePlanRequiresRefresh() {
        val intent = resolveSafeBrowsePassPurchaseIntent(
            entitlement = SafeBrowsePassEntitlement(
                active = true,
                basePlanId = "   ",
                expiryTimeMillis = 200_000L,
                isPrepaid = true,
            ),
            requestedPeriod = SafeBrowsePassPeriod.Prepaid,
            nowMillis = 100_000L,
        )

        assertEquals(SafeBrowsePassPurchaseIntent.RefreshRequired, intent)
    }

    @Test
    fun exactExpiryIsTreatedAsInactive() {
        val intent = resolveSafeBrowsePassPurchaseIntent(
            entitlement = SafeBrowsePassEntitlement(
                active = true,
                basePlanId = "monthly",
                expiryTimeMillis = 100_000L,
                isPrepaid = false,
            ),
            requestedPeriod = SafeBrowsePassPeriod.Monthly,
            nowMillis = 100_000L,
        )

        assertEquals(SafeBrowsePassPurchaseIntent.InitialPurchase, intent)
    }
}
