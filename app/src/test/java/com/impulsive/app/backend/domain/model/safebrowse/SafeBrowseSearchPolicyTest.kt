package com.impulsive.app.backend.domain.model.safebrowse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeBrowseSearchPolicyTest {

    @Test
    fun blankQueryReturnsNull() {
        assertNull(SafeBrowseSearchPolicy.buildSearchUrl(""))
        assertNull(SafeBrowseSearchPolicy.buildSearchUrl("   "))
    }

    @Test
    fun overlongQueryReturnsNull() {
        val overlong = "a".repeat(SafeBrowseSearchPolicy.MaximumQueryLength + 1)
        assertNull(SafeBrowseSearchPolicy.buildSearchUrl(overlong))
    }

    @Test
    fun controlCharacterQueryReturnsNull() {
        val newline = "hello" + 0x0A.toChar() + "world"
        val tab = "hello" + 0x09.toChar() + "world"
        assertNull(SafeBrowseSearchPolicy.buildSearchUrl(newline))
        assertNull(SafeBrowseSearchPolicy.buildSearchUrl(tab))
    }

    @Test
    fun searchQueryIsUtf8Encoded() {
        val url = SafeBrowseSearchPolicy.buildSearchUrl("calm focus & sleep")
        assertTrue(url != null)
        assertTrue(url!!.contains("q=calm%20focus%20%26%20sleep"))
    }

    @Test
    fun generatedSearchAlwaysContainsSafeAndFilterParameters() {
        val url = SafeBrowseSearchPolicy.buildSearchUrl("weather")
        assertTrue(url != null)
        assertTrue(url!!.contains("safe=active"))
        assertTrue(url.contains("filter=1"))
    }

    @Test
    fun urlLookingInputRemainsSearchText() {
        val url = SafeBrowseSearchPolicy.buildSearchUrl("https://example.com")
        assertTrue(url != null)
        assertTrue(url!!.startsWith("https://www.google.com/search?"))
        assertTrue(url.contains("q=https%3A%2F%2Fexample.com"))
    }

    @Test
    fun enforcesSafeSearchOnGoogleSearch() {
        val result = SafeBrowseSearchPolicy.enforceSafeSearch(
            "https://www.google.com/search?q=test&safe=off&filter=0",
        )
        val safeCount = Regex("safe=active").findAll(result).count()
        val filterCount = Regex("filter=1").findAll(result).count()
        assertEquals(1, safeCount)
        assertEquals(1, filterCount)
        assertTrue(result.contains("q=test"))
        assertFalse(result.contains("safe=off"))
        assertFalse(result.contains("filter=0"))
    }

    @Test
    fun leavesNonGoogleUrlUnchanged() {
        val url = "https://example.com/search?q=test"
        assertEquals(url, SafeBrowseSearchPolicy.enforceSafeSearch(url))
    }

    @Test
    fun leavesDeceptiveGoogleSiblingUnchanged() {
        val url = "https://www.google.com.example.org/search?q=test"
        assertEquals(url, SafeBrowseSearchPolicy.enforceSafeSearch(url))
    }

    @Test
    fun leavesGoogleNonSearchPathUnchanged() {
        val url = "https://www.google.com/maps?q=test"
        assertEquals(url, SafeBrowseSearchPolicy.enforceSafeSearch(url))
    }

    @Test
    fun preservesPagingAndLanguageParameters() {
        val result = SafeBrowseSearchPolicy.enforceSafeSearch(
            "https://www.google.com/search?q=test&start=10&hl=en",
        )
        assertTrue(result.contains("q=test"))
        assertTrue(result.contains("start=10"))
        assertTrue(result.contains("hl=en"))
        assertTrue(result.contains("safe=active"))
        assertTrue(result.contains("filter=1"))
    }

    @Test
    fun preservesFragment() {
        val result = SafeBrowseSearchPolicy.enforceSafeSearch(
            "https://www.google.com/search?q=test#top",
        )
        assertTrue(result.endsWith("#top"))
    }
}
