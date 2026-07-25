package com.impulsive.app.backend.domain.model.protection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SafeSearchPolicyTest {
    @Test
    fun `maps supported search and media domains`() {
        assertEquals(
            "forcesafesearch.google.com",
            SafeSearchPolicy.safeSearchHostFor("www.google.com"),
        )
        assertEquals("strict.bing.com", SafeSearchPolicy.safeSearchHostFor("bing.com"))
        assertEquals(
            "safe.duckduckgo.com",
            SafeSearchPolicy.safeSearchHostFor("duckduckgo.com"),
        )
        assertEquals(
            "restrict.youtube.com",
            SafeSearchPolicy.safeSearchHostFor("youtubei.googleapis.com"),
        )
    }

    @Test
    fun `lookup is case insensitive and unmapped domains are untouched`() {
        assertEquals(
            "forcesafesearch.google.com",
            SafeSearchPolicy.safeSearchHostFor("WWW.GOOGLE.CO.UK"),
        )
        assertNull(SafeSearchPolicy.safeSearchHostFor("example.com"))
        assertNull(SafeSearchPolicy.safeSearchHostFor("images.google.com"))
    }

    @Test
    fun `maps exact Brave Search host`() {
        assertEquals(
            "forcesafe.search.brave.com",
            SafeSearchPolicy.safeSearchHostFor("search.brave.com"),
        )
    }

    @Test
    fun `maps Brave Search host case insensitively`() {
        assertEquals(
            "forcesafe.search.brave.com",
            SafeSearchPolicy.safeSearchHostFor("SEARCH.BRAVE.COM"),
        )
    }

    @Test
    fun `does not map Brave Search subdomains or safe target`() {
        assertNull(SafeSearchPolicy.safeSearchHostFor("imgs.search.brave.com"))
        assertNull(SafeSearchPolicy.safeSearchHostFor("cdn.search.brave.com"))
        assertNull(SafeSearchPolicy.safeSearchHostFor("forcesafe.search.brave.com"))
        assertNull(SafeSearchPolicy.safeSearchHostFor("example.search.brave.com"))
    }
}
