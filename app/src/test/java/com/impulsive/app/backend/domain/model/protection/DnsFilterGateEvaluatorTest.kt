package com.impulsive.app.backend.domain.model.protection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsFilterGateEvaluatorTest {
    @Test
    fun enablesWhenNothingBlocks() {
        val result = DnsFilterGateEvaluator.evaluate(
            privateDnsBypassesFilter = false,
            anotherVpnActive = false,
            lockdownModeActive = false,
        )

        assertTrue(result.canEnable)
        assertTrue(result.blockers.isEmpty())
    }

    @Test
    fun blocksOnPrivateDnsOnly() {
        val result = DnsFilterGateEvaluator.evaluate(
            privateDnsBypassesFilter = true,
            anotherVpnActive = false,
            lockdownModeActive = false,
        )

        assertFalse(result.canEnable)
        assertEquals(
            listOf(DnsFilterGateEvaluator.Blocker.PrivateDnsActive),
            result.blockers,
        )
    }

    @Test
    fun blocksOnAnotherVpnOnly() {
        val result = DnsFilterGateEvaluator.evaluate(
            privateDnsBypassesFilter = false,
            anotherVpnActive = true,
            lockdownModeActive = false,
        )

        assertFalse(result.canEnable)
        assertEquals(
            listOf(DnsFilterGateEvaluator.Blocker.AnotherVpnActive),
            result.blockers,
        )
    }

    @Test
    fun blocksOnBothInFixedOrder() {
        val result = DnsFilterGateEvaluator.evaluate(
            privateDnsBypassesFilter = true,
            anotherVpnActive = true,
            lockdownModeActive = false,
        )

        assertFalse(result.canEnable)
        assertEquals(
            listOf(
                DnsFilterGateEvaluator.Blocker.PrivateDnsActive,
                DnsFilterGateEvaluator.Blocker.AnotherVpnActive,
            ),
            result.blockers,
        )
    }

    @Test
    fun blocksOnLockdownModeOnly() {
        val result = DnsFilterGateEvaluator.evaluate(
            privateDnsBypassesFilter = false,
            anotherVpnActive = false,
            lockdownModeActive = true,
        )

        assertFalse(result.canEnable)
        assertEquals(
            listOf(DnsFilterGateEvaluator.Blocker.LockdownModeActive),
            result.blockers,
        )
    }

    @Test
    fun blocksOnAllInFixedOrder() {
        val result = DnsFilterGateEvaluator.evaluate(
            privateDnsBypassesFilter = true,
            anotherVpnActive = true,
            lockdownModeActive = true,
        )

        assertFalse(result.canEnable)
        assertEquals(
            listOf(
                DnsFilterGateEvaluator.Blocker.PrivateDnsActive,
                DnsFilterGateEvaluator.Blocker.AnotherVpnActive,
                DnsFilterGateEvaluator.Blocker.LockdownModeActive,
            ),
            result.blockers,
        )
    }
}
