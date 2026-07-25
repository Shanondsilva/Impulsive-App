package com.impulsive.app.backend.domain.model.protection

/**
 * Metadata for the bundled mandatory website-protection defaults.
 *
 * The actual domains live in the versioned application asset and
 * are reconciled into the local blocked_domain table by
 * BlockedDomainRepository.
 *
 * Bundled defaults are mandatory. Product UI may delete only
 * entries explicitly added by the user.
 */
object DefaultBlockedDomains {
    const val AssetPath = "blocklists/default_blocked_domains.tsv"
    const val CategoryAdult = "adult"
}
