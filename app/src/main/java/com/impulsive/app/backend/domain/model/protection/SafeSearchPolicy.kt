package com.impulsive.app.backend.domain.model.protection

/**
 * Maps search/media domains to the host whose address enforces SafeSearch.
 * Resolving the original name to the SafeSearch host's IP makes the search engine
 * itself filter adult results (especially images). Domains not present are untouched.
 */
object SafeSearchPolicy {
    private val map = mapOf(
        "www.google.com" to "forcesafesearch.google.com",
        "google.com" to "forcesafesearch.google.com",
        "www.bing.com" to "strict.bing.com",
        "bing.com" to "strict.bing.com",
        "duckduckgo.com" to "safe.duckduckgo.com",
        "search.brave.com" to "forcesafe.search.brave.com",
        "www.youtube.com" to "restrict.youtube.com",
        "youtube.com" to "restrict.youtube.com",
        "m.youtube.com" to "restrict.youtube.com",
        "youtubei.googleapis.com" to "restrict.youtube.com",
        "www.google.co.uk" to "forcesafesearch.google.com",
    )

    fun safeSearchHostFor(domain: String): String? = map[domain.lowercase()]
}
