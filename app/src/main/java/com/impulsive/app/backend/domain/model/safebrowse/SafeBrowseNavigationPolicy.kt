package com.impulsive.app.backend.domain.model.safebrowse

import com.impulsive.app.backend.domain.model.protection.BlockedDomainMatcher
import com.impulsive.app.backend.domain.model.protection.normalizeDomainOrNull
import java.net.URI
import java.util.Locale

/**
 * Immutable local policy data used to evaluate Safe Browse requests.
 *
 * The set contains only canonical domains. It does not contain full URLs,
 * browsing history, query text or page titles.
 */
data class SafeBrowsePolicySnapshot(
    val blockedDomains: Set<String>,
) {
    companion object {
        fun from(blockedDomains: Set<String>): SafeBrowsePolicySnapshot =
            SafeBrowsePolicySnapshot(
                blockedDomains = blockedDomains
                    .mapNotNull(::normalizeDomainOrNull)
                    .toSet(),
            )
    }
}

enum class SafeBrowseBlockedReason {
    EmptyUrl,
    InvalidUrl,
    UnsupportedScheme,
    MissingHost,
    CredentialsNotAllowed,
    NonStandardPort,
    InvalidHost,
    LocalNetworkHost,
    BlockedDomain,
}

/**
 * Special-use suffixes reserved by RFC 6761/8375 conventions that never resolve to a
 * public website. Matched by exact name or suffix boundary only -- never by substring,
 * and never by resolving DNS.
 */
private val NonPublicHostSuffixes = setOf(
    "localhost",
    "local",
    "home.arpa",
    "internal",
    "lan",
    "test",
    "invalid",
    "example",
)

private fun isNonPublicHost(normalizedHost: String): Boolean =
    NonPublicHostSuffixes.any { suffix ->
        normalizedHost == suffix || normalizedHost.endsWith(".$suffix")
    }

/**
 * Result of evaluating one proposed Safe Browse request.
 */
sealed interface SafeBrowseNavigationDecision {
    data class Allow(
        val canonicalUrl: String,
        val displayHost: String,
    ) : SafeBrowseNavigationDecision

    data class Block(
        val reason: SafeBrowseBlockedReason,
        val displayHost: String? = null,
    ) : SafeBrowseNavigationDecision
}

/**
 * Pure, deterministic and locally testable Safe Browse URL policy.
 *
 * This class does not perform network access, persistence, logging or UI work.
 */
object SafeBrowseNavigationPolicy {
    fun evaluate(
        rawUrl: String,
        snapshot: SafeBrowsePolicySnapshot,
    ): SafeBrowseNavigationDecision {
        val input = rawUrl.trim()

        if (input.isEmpty()) {
            return block(SafeBrowseBlockedReason.EmptyUrl)
        }

        if (
            input.contains('\\') ||
            input.any { character ->
                character.code < 0x20 || character.code == 0x7F
            }
        ) {
            return block(SafeBrowseBlockedReason.InvalidUrl)
        }

        val parsed = runCatching { URI(input) }.getOrNull()
            ?: return block(SafeBrowseBlockedReason.InvalidUrl)

        if (parsed.isOpaque) {
            return block(SafeBrowseBlockedReason.InvalidUrl)
        }

        val scheme = parsed.scheme?.lowercase(Locale.ROOT)
            ?: return block(SafeBrowseBlockedReason.UnsupportedScheme)

        if (scheme != "https") {
            return block(SafeBrowseBlockedReason.UnsupportedScheme)
        }

        if (parsed.rawUserInfo != null) {
            return block(SafeBrowseBlockedReason.CredentialsNotAllowed)
        }

        if (parsed.port != -1 && parsed.port != 443) {
            return block(SafeBrowseBlockedReason.NonStandardPort)
        }

        val rawHost = parsed.host
            ?: return block(SafeBrowseBlockedReason.MissingHost)

        val normalizedHost = normalizeDomainOrNull(rawHost)
            ?: return block(SafeBrowseBlockedReason.InvalidHost)

        if (isNonPublicHost(normalizedHost)) {
            return SafeBrowseNavigationDecision.Block(
                reason = SafeBrowseBlockedReason.LocalNetworkHost,
                displayHost = normalizedHost,
            )
        }

        val matchedBlockedDomain = BlockedDomainMatcher.matchedBlockedEntry(
            hostname = normalizedHost,
            blockedDomains = snapshot.blockedDomains,
        )

        if (matchedBlockedDomain != null) {
            return SafeBrowseNavigationDecision.Block(
                reason = SafeBrowseBlockedReason.BlockedDomain,
                displayHost = normalizedHost,
            )
        }

        return SafeBrowseNavigationDecision.Allow(
            canonicalUrl = canonicalHttpsUrl(
                parsed = parsed,
                normalizedHost = normalizedHost,
            ),
            displayHost = normalizedHost,
        )
    }

    private fun canonicalHttpsUrl(
        parsed: URI,
        normalizedHost: String,
    ): String = buildString {
        append("https://")
        append(normalizedHost)

        val path = parsed.rawPath

        if (path.isNullOrEmpty()) {
            append('/')
        } else {
            append(path)
        }

        parsed.rawQuery?.let { query ->
            append('?')
            append(query)
        }

        parsed.rawFragment?.let { fragment ->
            append('#')
            append(fragment)
        }
    }

    private fun block(reason: SafeBrowseBlockedReason): SafeBrowseNavigationDecision =
        SafeBrowseNavigationDecision.Block(reason = reason)
}
