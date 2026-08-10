package com.impulsive.app.backend.domain.model.safebrowse

import com.impulsive.app.backend.domain.model.protection.normalizeDomainOrNull
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Pure, deterministic policy that turns Safe Browse search text into a
 * SafeSearch-enabled Google search request, and re-enforces SafeSearch on any
 * Google search results URL reached afterwards.
 *
 * This does not replace [SafeBrowseNavigationPolicy]. SafeSearch is an
 * additional search-result filter, not the mandatory adult-domain blocklist.
 */
object SafeBrowseSearchPolicy {
    const val MaximumQueryLength = 200

    private val GoogleSearchHosts = setOf("google.com", "www.google.com")
    private const val GoogleSearchPath = "/search"

    fun buildSearchUrl(rawQuery: String): String? {
        val trimmed = rawQuery.trim()

        if (trimmed.isEmpty()) {
            return null
        }

        if (trimmed.length > MaximumQueryLength) {
            return null
        }

        if (trimmed.any { character -> character.code < 0x20 || character.code == 0x7F }) {
            return null
        }

        val encoded = URLEncoder.encode(trimmed, StandardCharsets.UTF_8.name())
            .replace("+", "%20")

        return "https://www.google.com/search?safe=active&filter=1&q=$encoded"
    }

    fun enforceSafeSearch(canonicalUrl: String): String {
        val parsed = runCatching { URI(canonicalUrl) }.getOrNull() ?: return canonicalUrl

        if (parsed.scheme?.lowercase(Locale.ROOT) != "https") {
            return canonicalUrl
        }

        val host = parsed.host ?: return canonicalUrl
        val normalizedHost = normalizeDomainOrNull(host) ?: return canonicalUrl

        if (normalizedHost !in GoogleSearchHosts) {
            return canonicalUrl
        }

        if (parsed.rawPath != GoogleSearchPath) {
            return canonicalUrl
        }

        val preservedParameters = parsed.rawQuery
            ?.split('&')
            ?.filter { it.isNotEmpty() }
            ?.filterNot { rawPair ->
                val key = rawPair.substringBefore('=')
                key == "safe" || key == "filter"
            }
            .orEmpty()

        val newQuery = (preservedParameters + listOf("safe=active", "filter=1"))
            .joinToString("&")

        return buildString {
            append("https://")
            append(normalizedHost)
            append(GoogleSearchPath)
            append('?')
            append(newQuery)

            parsed.rawFragment?.let { fragment ->
                append('#')
                append(fragment)
            }
        }
    }
}
