package com.impulsive.app.backend.domain.model.safebrowse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeBrowseNavigationPolicyTest {

    private fun snapshot(vararg domains: String) =
        SafeBrowsePolicySnapshot.from(domains.toSet())

    private fun evaluate(url: String, vararg blockedDomains: String) =
        SafeBrowseNavigationPolicy.evaluate(url, snapshot(*blockedDomains))

    private fun blockedReason(decision: SafeBrowseNavigationDecision): SafeBrowseBlockedReason =
        (decision as SafeBrowseNavigationDecision.Block).reason

    @Test
    fun allowsAndCanonicalisesValidHttpsUrl() {
        val decision = evaluate("https://Example.COM/path?q=value#section")
        assertEquals(
            SafeBrowseNavigationDecision.Allow(
                canonicalUrl = "https://example.com/path?q=value#section",
                displayHost = "example.com",
            ),
            decision,
        )
    }

    @Test
    fun addsRootPathWhenPathIsMissing() {
        val decision = evaluate("https://example.com")
        assertTrue(decision is SafeBrowseNavigationDecision.Allow)
        assertEquals(
            "https://example.com/",
            (decision as SafeBrowseNavigationDecision.Allow).canonicalUrl,
        )
    }

    @Test
    fun allowsExplicitStandardHttpsPortButRemovesItFromCanonicalUrl() {
        val decision = evaluate("https://example.com:443/path")
        assertTrue(decision is SafeBrowseNavigationDecision.Allow)
        assertEquals(
            "https://example.com/path",
            (decision as SafeBrowseNavigationDecision.Allow).canonicalUrl,
        )
    }

    @Test
    fun blocksEmptyInput() {
        assertEquals(SafeBrowseBlockedReason.EmptyUrl, blockedReason(evaluate("")))
        assertEquals(SafeBrowseBlockedReason.EmptyUrl, blockedReason(evaluate("   ")))
    }

    @Test
    fun blocksMalformedAndControlCharacterUrls() {
        assertEquals(
            SafeBrowseBlockedReason.InvalidUrl,
            blockedReason(evaluate("https://exa mple.com")),
        )
        assertEquals(
            SafeBrowseBlockedReason.InvalidUrl,
            blockedReason(evaluate("https://example.com/\nunsafe")),
        )
        assertEquals(
            SafeBrowseBlockedReason.InvalidUrl,
            blockedReason(evaluate("https:\\\\example.com")),
        )
    }

    @Test
    fun blocksEveryUnsupportedScheme() {
        val acceptableReasons = setOf(
            SafeBrowseBlockedReason.UnsupportedScheme,
            SafeBrowseBlockedReason.InvalidUrl,
        )
        listOf(
            "http://example.com",
            "file:///tmp/file",
            "content://example/path",
            "javascript:alert(1)",
            "data:text/html,test",
            "blob:https://example.com/value",
            "intent://example",
            "mailto:user@example.com",
            "tel:123",
            "market://details?id=test",
        ).forEach { url ->
            val reason = blockedReason(evaluate(url))
            assertTrue("$url produced unexpected reason $reason", reason in acceptableReasons)
        }
        // HTTP specifically must never be allowed through as anything other than a blocked scheme.
        assertEquals(
            SafeBrowseBlockedReason.UnsupportedScheme,
            blockedReason(evaluate("http://example.com")),
        )
    }

    @Test
    fun blocksEmbeddedCredentials() {
        assertEquals(
            SafeBrowseBlockedReason.CredentialsNotAllowed,
            blockedReason(evaluate("https://user:password@example.com/")),
        )
    }

    @Test
    fun blocksNonStandardPorts() {
        assertEquals(
            SafeBrowseBlockedReason.NonStandardPort,
            blockedReason(evaluate("https://example.com:8443/")),
        )
    }

    @Test
    fun blocksMissingOrInvalidHosts() {
        val acceptableReasons = setOf(
            SafeBrowseBlockedReason.MissingHost,
            SafeBrowseBlockedReason.InvalidHost,
        )
        listOf(
            "https:///missing",
            "https://localhost/",
            "https://127.0.0.1/",
            "https://[::1]/",
        ).forEach { url ->
            val reason = blockedReason(evaluate(url))
            assertTrue("$url produced unexpected reason $reason", reason in acceptableReasons)
        }
    }

    @Test
    fun blocksExactAdultDomain() {
        val decision = evaluate("https://pornhub.com/", "pornhub.com")
        assertEquals(SafeBrowseBlockedReason.BlockedDomain, blockedReason(decision))
    }

    @Test
    fun blocksAdultSubdomain() {
        val decision = evaluate("https://www.pornhub.com/watch", "pornhub.com")
        assertEquals(SafeBrowseBlockedReason.BlockedDomain, blockedReason(decision))
    }

    @Test
    fun doesNotFalseMatchDeceptiveSiblingDomain() {
        val decision = evaluate("https://pornhub.com.example.org/", "pornhub.com")
        assertTrue(decision is SafeBrowseNavigationDecision.Allow)
    }

    @Test
    fun usesAllExistingUserAndDefaultBlockedDomains() {
        val blocked = snapshot("pornhub.com", "custom-blocked-site.org")

        val exactOne = SafeBrowseNavigationPolicy.evaluate("https://pornhub.com/", blocked)
        val subOne = SafeBrowseNavigationPolicy.evaluate("https://www.pornhub.com/", blocked)
        val exactTwo = SafeBrowseNavigationPolicy.evaluate("https://custom-blocked-site.org/", blocked)
        val subTwo = SafeBrowseNavigationPolicy.evaluate(
            "https://sub.custom-blocked-site.org/",
            blocked,
        )

        assertEquals(SafeBrowseBlockedReason.BlockedDomain, blockedReason(exactOne))
        assertEquals(SafeBrowseBlockedReason.BlockedDomain, blockedReason(subOne))
        assertEquals(SafeBrowseBlockedReason.BlockedDomain, blockedReason(exactTwo))
        assertEquals(SafeBrowseBlockedReason.BlockedDomain, blockedReason(subTwo))
    }

    @Test
    fun snapshotCanonicalisesItsDomainSet() {
        val result = SafeBrowsePolicySnapshot.from(
            setOf(
                "Example.COM.",
                "  PORNHUB.COM  ",
                "invalid value",
            ),
        )
        assertEquals(
            setOf("example.com", "pornhub.com"),
            result.blockedDomains,
        )
    }

    @Test
    fun blocksLocalNetworkHostsBySuffixBoundary() {
        listOf(
            "https://app.localhost/",
            "https://router.local/",
            "https://device.home.arpa/",
            "https://server.internal/",
            "https://router.lan/",
            "https://sample.test/",
            "https://name.invalid/",
            "https://host.example/",
        ).forEach { url ->
            assertEquals(
                "$url should be blocked as a local-network host",
                SafeBrowseBlockedReason.LocalNetworkHost,
                blockedReason(evaluate(url)),
            )
        }

        // Bare "localhost" has no dot, so it is already rejected one step earlier by
        // normalizeDomainOrNull (which requires at least two labels) -- it never reaches
        // the local-network-host check, but it is still correctly blocked either way.
        assertTrue(evaluate("https://localhost/") is SafeBrowseNavigationDecision.Block)
    }

    @Test
    fun doesNotFalseMatchPublicDomainsResemblingReservedSuffixes() {
        listOf(
            "https://example.com/",
            "https://example.org/",
            "https://internal.example.com/",
            "https://mylocal.example.com/",
            "https://notlocalhost.com/",
            "https://testing.co/",
        ).forEach { url ->
            val decision = evaluate(url)
            assertTrue(
                "$url should be allowed, was $decision",
                decision is SafeBrowseNavigationDecision.Allow,
            )
        }
    }

    @Test
    fun preservesEncodedPathWithoutDoubleEncoding() {
        val decision = evaluate("https://example.com/a%20safe%20path")
        assertTrue(decision is SafeBrowseNavigationDecision.Allow)
        assertEquals(
            "https://example.com/a%20safe%20path",
            (decision as SafeBrowseNavigationDecision.Allow).canonicalUrl,
        )
    }
}
