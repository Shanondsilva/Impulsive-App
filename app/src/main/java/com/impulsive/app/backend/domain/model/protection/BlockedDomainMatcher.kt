package com.impulsive.app.backend.domain.model.protection

object BlockedDomainMatcher {
    fun isBlocked(hostname: String, blockedDomains: Set<String>): Boolean =
        matchedBlockedEntry(hostname, blockedDomains) != null

    fun matchedBlockedEntry(hostname: String, blockedDomains: Set<String>): String? {
        if (blockedDomains.isEmpty()) {
            return null
        }

        val normalizedHost = normalizeDomainOrNull(hostname) ?: return null
        var candidate = normalizedHost

        while (true) {
            if (candidate in blockedDomains) {
                return candidate
            }

            val firstDot = candidate.indexOf('.')
            if (firstDot < 0) {
                return null
            }

            candidate = candidate.substring(firstDot + 1)
            if (!candidate.contains('.')) {
                return null
            }
        }
    }
}
