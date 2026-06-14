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
        )

        assertTrue(result.canEnable)
        assertTrue(result.blockers.isEmpty())
    }

    @Test
    fun blocksOnPrivateDnsOnly() {
        val result = DnsFilterGateEvaluator.evaluate(
            privateDnsBypassesFilter = true,
            anotherVpnActive = false,
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
}
