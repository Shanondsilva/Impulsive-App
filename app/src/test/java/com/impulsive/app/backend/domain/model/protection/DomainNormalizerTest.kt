package com.impulsive.app.backend.domain.model.protection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DomainNormalizerTest {
    @Test
    fun `canonicalizes case trailing separators and IDNs`() {
        assertEquals("example.com", normalizeDomainOrNull("EXAMPLE.COM"))
        assertEquals("example.com", normalizeDomainOrNull("example.com."))
        assertEquals("xn--bcher-kva.de", normalizeDomainOrNull("bücher.de"))
        assertEquals("xn--bcher-kva.de", normalizeDomainOrNull("XN--BCHER-KVA.DE"))
        assertEquals("xn--bcher-kva.de", normalizeDomainOrNull("bücher.de。"))
    }

    @Test
    fun `rejects blank URLs paths wildcards and malformed labels`() {
        listOf(
            "",
            "   ",
            "https://example.com",
            "example.com/path",
            "*.example.com",
            "example..com",
            "com",
            "127.0.0.1",
            "example.com..",
            "${"a".repeat(64)}.com",
        ).forEach { value ->
            assertNull(value, normalizeDomainOrNull(value))
        }
    }
}
