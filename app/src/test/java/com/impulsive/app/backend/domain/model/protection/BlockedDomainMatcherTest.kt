package com.impulsive.app.backend.domain.model.protection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockedDomainMatcherTest {
    private val blocked = setOf("example.com", "tracker.net", "xn--bcher-kva.de")

    @Test
    fun `exact domain and subdomains match`() {
        assertTrue(BlockedDomainMatcher.isBlocked("example.com", blocked))
        assertTrue(BlockedDomainMatcher.isBlocked("cdn.example.com", blocked))
        assertTrue(BlockedDomainMatcher.isBlocked("a.b.example.com", blocked))
    }

    @Test
    fun `lookalike boundary and superdomain attack do not match`() {
        assertFalse(BlockedDomainMatcher.isBlocked("notexample.com", blocked))
        assertFalse(BlockedDomainMatcher.isBlocked("example.com.evil.test", blocked))
        assertFalse(BlockedDomainMatcher.isBlocked("google.com", blocked))
    }

    @Test
    fun `normal unrelated websites remain unblocked`() {
        val adultDomains = setOf(
            "example-adult.test",
            "another-adult.test",
        )

        assertFalse(BlockedDomainMatcher.isBlocked("google.com", adultDomains))
        assertFalse(BlockedDomainMatcher.isBlocked("www.google.com", adultDomains))
        assertFalse(BlockedDomainMatcher.isBlocked("bbc.co.uk", adultDomains))
        assertFalse(BlockedDomainMatcher.isBlocked("youtube.com", adultDomains))
        assertFalse(BlockedDomainMatcher.isBlocked("example.com", adultDomains))
    }

    @Test
    fun `blocked synthetic adult domain and subdomains remain blocked`() {
        val adultDomains = setOf("example-adult.test")

        assertTrue(BlockedDomainMatcher.isBlocked("example-adult.test", adultDomains))
        assertTrue(BlockedDomainMatcher.isBlocked("www.example-adult.test", adultDomains))
        assertTrue(BlockedDomainMatcher.isBlocked("cdn.images.example-adult.test", adultDomains))
    }

    @Test
    fun `uppercase and trailing DNS dot normalize once`() {
        assertTrue(BlockedDomainMatcher.isBlocked("X.EXAMPLE.COM", blocked))
        assertTrue(BlockedDomainMatcher.isBlocked("x.example.com.", blocked))
    }

    @Test
    fun `Unicode and punycode IDN queries match canonical entry`() {
        assertEquals(
            "xn--bcher-kva.de",
            BlockedDomainMatcher.matchedBlockedEntry("shop.bücher.de", blocked),
        )
        assertEquals(
            "xn--bcher-kva.de",
            BlockedDomainMatcher.matchedBlockedEntry("shop.xn--bcher-kva.de", blocked),
        )
    }

    @Test
    fun `empty set blocks nothing`() {
        assertFalse(BlockedDomainMatcher.isBlocked("example.com", emptySet()))
    }
}
