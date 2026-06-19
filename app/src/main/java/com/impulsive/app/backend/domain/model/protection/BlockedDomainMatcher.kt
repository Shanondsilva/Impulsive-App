package com.impulsive.app.backend.domain.model.protection

/**
 * Decides whether a looked-up hostname falls under any blocked domain. Pure logic, no Android
 * dependencies. Matching is suffix-aware on label boundaries: a blocked entry of "example.com"
 * matches "example.com" and "cdn.example.com" but not "notexample.com". Inputs are normalised to
 * lower case with any trailing dot removed before comparison.
 */
object BlockedDomainMatcher {
    fun isBlocked(hostname: String, blockedDomains: Set<String>): Boolean =
        matchedBlockedEntry(hostname, blockedDomains) != null

    fun matchedBlockedEntry(hostname: String, blockedDomains: Set<String>): String? {
        if (blockedDomains.isEmpty()) return null
        val host = normalize(hostname)
        if (host.isEmpty()) return null
        for (entry in blockedDomains) {
            val blocked = normalize(entry)
            if (blocked.isNotEmpty() && (host == blocked || host.endsWith(".$blocked"))) {
                return blocked
            }
        }
        return null
    }

    private fun normalize(value: String): String =
        value.trim().lowercase().removeSuffix(".")
}
