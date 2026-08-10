package com.impulsive.app.backend.domain.model.safebrowse

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeBrowsePassEntitlementTest {

    @Test
    fun defaultEntitlementIsNeverValid() {
        assertFalse(SafeBrowsePassEntitlement().isValidAt(nowMillis = 0L))
        assertFalse(SafeBrowsePassEntitlement().isValidAt(nowMillis = Long.MAX_VALUE))
    }

    @Test
    fun inactiveEntitlementIsNeverValidRegardlessOfExpiry() {
        val entitlement = SafeBrowsePassEntitlement(
            active = false,
            expiryTimeMillis = Long.MAX_VALUE,
        )

        assertFalse(entitlement.isValidAt(nowMillis = 0L))
    }

    @Test
    fun activeEntitlementIsValidBeforeExpiry() {
        val entitlement = SafeBrowsePassEntitlement(
            active = true,
            expiryTimeMillis = 100_000L,
        )

        assertTrue(entitlement.isValidAt(nowMillis = 50_000L))
    }

    @Test
    fun zeroOrNegativeExpiryIsNeverValidEvenWhenActive() {
        val entitlement = SafeBrowsePassEntitlement(active = true, expiryTimeMillis = 0L)

        assertFalse(entitlement.isValidAt(nowMillis = 0L))
    }

    @Test
    fun activeEntitlementIsValidOneMillisecondBeforeExpiry() {
        val entitlement = SafeBrowsePassEntitlement(
            active = true,
            expiryTimeMillis = 100_000L,
        )

        assertTrue(
            entitlement.isValidAt(
                nowMillis = 99_999L,
            ),
        )
    }

    @Test
    fun activeEntitlementIsInvalidAtExactExpiry() {
        val entitlement = SafeBrowsePassEntitlement(
            active = true,
            expiryTimeMillis = 100_000L,
        )

        assertFalse(
            entitlement.isValidAt(
                nowMillis = 100_000L,
            ),
        )
    }

    @Test
    fun activeEntitlementIsInvalidAfterExpiry() {
        val entitlement = SafeBrowsePassEntitlement(
            active = true,
            expiryTimeMillis = 100_000L,
        )

        assertFalse(
            entitlement.isValidAt(
                nowMillis = 100_001L,
            ),
        )
    }

    @Test
    fun negativeCurrentTimeIsInvalid() {
        val entitlement = SafeBrowsePassEntitlement(
            active = true,
            expiryTimeMillis = Long.MAX_VALUE,
        )

        assertFalse(
            entitlement.isValidAt(
                nowMillis = -1L,
            ),
        )
    }
}
