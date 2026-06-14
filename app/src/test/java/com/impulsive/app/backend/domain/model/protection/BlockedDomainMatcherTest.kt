package com.impulsive.app.backend.domain.model.protection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockedDomainMatcherTest {
    private val list = setOf("example.com", "tracker.net")

    @Test
    fun matchesExactDomain() {
        assertTrue(BlockedDomainMatcher.isBlocked("example.com", list))
    }

    @Test
    fun matchesSubdomain() {
        assertTrue(BlockedDomainMatcher.isBlocked("cdn.example.com", list))
        assertTrue(BlockedDomainMatcher.isBlocked("a.b.example.com", list))
    }

    @Test
    fun doesNotMatchLookalikeBoundary() {
        assertFalse(BlockedDomainMatcher.isBlocked("notexample.com", list))
    }

    @Test
    fun doesNotMatchUnrelatedDomain() {
        assertFalse(BlockedDomainMatcher.isBlocked("google.com", list))
    }

    @Test
    fun normalisesCaseAndTrailingDot() {
        assertTrue(BlockedDomainMatcher.isBlocked("X.Example.COM.", list))
    }

    @Test
    fun normalisesBlockedEntry() {
        assertTrue(BlockedDomainMatcher.isBlocked("x.example.com", setOf("EXAMPLE.COM.")))
    }

    @Test
    fun emptyListBlocksNothing() {
        assertFalse(BlockedDomainMatcher.isBlocked("example.com", emptySet()))
    }
}
