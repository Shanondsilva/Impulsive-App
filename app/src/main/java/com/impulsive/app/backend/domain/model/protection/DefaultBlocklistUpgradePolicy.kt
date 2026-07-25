package com.impulsive.app.backend.domain.model.protection

internal data class ExistingBlockedDomainSnapshot(
    val domain: String,
    val isDefault: Boolean,
    val addedByUser: Boolean,
)

internal data class DefaultBlocklistUpgradePlan(
    val entriesToInsert: List<DefaultBlockedDomainEntry>,
    val entriesToPromote: List<DefaultBlockedDomainEntry>,
    val versionToPersist: Int?,
)

internal fun planDefaultBlocklistUpgrade(
    storedVersion: Int,
    asset: DefaultBlocklistAsset,
    existing: List<ExistingBlockedDomainSnapshot>,
): DefaultBlocklistUpgradePlan {
    if (asset.version <= storedVersion) {
        return DefaultBlocklistUpgradePlan(
            entriesToInsert = emptyList(),
            entriesToPromote = emptyList(),
            versionToPersist = null,
        )
    }

    val existingByDomain = existing.mapNotNull { snapshot ->
        normalizeDomainOrNull(snapshot.domain)?.let { it to snapshot }
    }.toMap()
    val entriesToInsert = mutableListOf<DefaultBlockedDomainEntry>()
    val entriesToPromote = mutableListOf<DefaultBlockedDomainEntry>()

    asset.entries.forEach { entry ->
        val current = existingByDomain[entry.domain]
        when {
            current == null -> entriesToInsert += entry
            !current.isDefault || current.addedByUser -> entriesToPromote += entry
        }
    }

    return DefaultBlocklistUpgradePlan(
        entriesToInsert = entriesToInsert.sortedBy(DefaultBlockedDomainEntry::domain),
        entriesToPromote = entriesToPromote.sortedBy(DefaultBlockedDomainEntry::domain),
        versionToPersist = asset.version,
    )
}
