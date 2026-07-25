package com.impulsive.app.backend.domain.model.protection

import java.util.Locale

internal data class DefaultBlockedDomainEntry(
    val domain: String,
    val category: String,
)

internal data class DefaultBlocklistAsset(
    val version: Int,
    val entries: List<DefaultBlockedDomainEntry>,
)

internal fun parseDefaultBlocklistAsset(text: String): DefaultBlocklistAsset {
    var version: Int? = null
    var sawMeaningfulLine = false
    val entries = mutableListOf<DefaultBlockedDomainEntry>()
    val domains = mutableSetOf<String>()
    val categoryPattern = Regex("[a-z][a-z0-9_-]{0,31}")

    text.lineSequence().forEachIndexed { index, rawLine ->
        val lineNumber = index + 1
        val line = rawLine.trim()

        if (line.isEmpty() || line.startsWith('#')) {
            return@forEachIndexed
        }

        if (!sawMeaningfulLine) {
            sawMeaningfulLine = true
            val parsedVersion = Regex("version=([0-9]+)")
                .matchEntire(line)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
                ?: throw assetError(lineNumber, "invalid version declaration")

            require(parsedVersion > 0) {
                assetErrorMessage(lineNumber, "version must be positive")
            }
            version = parsedVersion
            return@forEachIndexed
        }

        if (line.startsWith("version=")) {
            throw assetError(lineNumber, "multiple version declarations")
        }

        val values = line.split('\t')
        if (values.size != 2) {
            throw assetError(lineNumber, "expected domain and category")
        }

        val domain = normalizeDomainOrNull(values[0])
            ?: throw assetError(lineNumber, "invalid domain")
        val category = values[1].trim().lowercase(Locale.ROOT)

        if (!categoryPattern.matches(category)) {
            throw assetError(lineNumber, "invalid category")
        }

        if (!domains.add(domain)) {
            throw assetError(lineNumber, "duplicate canonical domain")
        }

        entries += DefaultBlockedDomainEntry(domain = domain, category = category)
    }

    val parsedVersion = version
        ?: throw IllegalArgumentException("Invalid default blocklist asset: missing version")

    return DefaultBlocklistAsset(
        version = parsedVersion,
        entries = entries.sortedBy(DefaultBlockedDomainEntry::domain),
    )
}

private fun assetError(lineNumber: Int, reason: String): IllegalArgumentException =
    IllegalArgumentException(assetErrorMessage(lineNumber, reason))

private fun assetErrorMessage(lineNumber: Int, reason: String): String =
    "Invalid default blocklist asset at line $lineNumber: $reason"
