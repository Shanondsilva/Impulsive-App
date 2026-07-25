package com.impulsive.app.backend.service.protection

import org.junit.Assert.assertEquals
import org.junit.Test

class DoHFailoverPolicyTest {

    private val primary = DoHFailoverPolicy.Endpoint.Primary
    private val fallback = DoHFailoverPolicy.Endpoint.Fallback

    @Test
    fun startsOnPrimaryAndStaysUnderThreshold() {
        val policy = DoHFailoverPolicy()
        assertEquals(primary, policy.endpointForNextQuery(0L))
        policy.recordResult(primary, success = false, nowMillis = 0L)
        policy.recordResult(primary, success = false, nowMillis = 0L)
        assertEquals(primary, policy.endpointForNextQuery(0L))
    }

    @Test
    fun switchesToFallbackAfterThreeConsecutivePrimaryFailures() {
        val policy = DoHFailoverPolicy()
        repeat(3) { policy.recordResult(primary, success = false, nowMillis = 1_000L) }
        assertEquals(fallback, policy.endpointForNextQuery(1_000L))
    }

    @Test
    fun probesPrimaryAfterIntervalAndReturnsOnSuccess() {
        val policy = DoHFailoverPolicy()
        repeat(3) { policy.recordResult(primary, success = false, nowMillis = 0L) }
        assertEquals(fallback, policy.endpointForNextQuery(59_999L))
        assertEquals(primary, policy.endpointForNextQuery(60_000L))
        policy.recordResult(primary, success = true, nowMillis = 60_000L)
        assertEquals(primary, policy.endpointForNextQuery(60_001L))
    }

    @Test
    fun failedProbeStaysOnFallbackForAnotherInterval() {
        val policy = DoHFailoverPolicy()
        repeat(3) { policy.recordResult(primary, success = false, nowMillis = 0L) }
        assertEquals(primary, policy.endpointForNextQuery(60_000L))
        policy.recordResult(primary, success = false, nowMillis = 60_000L)
        assertEquals(fallback, policy.endpointForNextQuery(60_001L))
        assertEquals(fallback, policy.endpointForNextQuery(119_999L))
        assertEquals(primary, policy.endpointForNextQuery(120_000L))
    }

    @Test
    fun fallbackFailureAllowsImmediatePrimaryProbe() {
        val policy = DoHFailoverPolicy()
        repeat(3) { policy.recordResult(primary, success = false, nowMillis = 100_000L) }
        assertEquals(fallback, policy.endpointForNextQuery(100_001L))
        policy.recordResult(fallback, success = false, nowMillis = 100_001L)
        assertEquals(primary, policy.endpointForNextQuery(100_002L))
    }

    @Test
    fun primarySuccessResetsFailureCount() {
        val policy = DoHFailoverPolicy()
        policy.recordResult(primary, success = false, nowMillis = 0L)
        policy.recordResult(primary, success = false, nowMillis = 0L)
        policy.recordResult(primary, success = true, nowMillis = 0L)
        policy.recordResult(primary, success = false, nowMillis = 0L)
        policy.recordResult(primary, success = false, nowMillis = 0L)
        assertEquals(primary, policy.endpointForNextQuery(0L))
    }
}
