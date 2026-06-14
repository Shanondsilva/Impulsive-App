package com.impulsive.app.backend.domain.model.protection

/**
 * Decides whether a looked-up hostname falls under any blocked domain. Pure logic, no Android
 * dependencies. Matching is suffix-aware on label boundaries: a blocked entry of "example.com"
 * matches "example.com" and "cdn.example.com" but not "notexample.com". Inputs are normalised to
 * lower case with any trailing dot removed before comparison.
 */
object BlockedDomainMatcher {
    fun isBlocked(hostname: String, blockedDomains: Set<String>): Boolean {
        if (blockedDomains.isEmpty()) return false
        val host = normalize(hostname)
        if (host.isEmpty()) return false
        return blockedDomains.any { entry ->
            val blocked = normalize(entry)
            blocked.isNotEmpty() && (host == blocked || host.endsWith(".$blocked"))
        }
    }

    private fun normalize(value: String): String =
        value.trim().lowercase().removeSuffix(".")
}
