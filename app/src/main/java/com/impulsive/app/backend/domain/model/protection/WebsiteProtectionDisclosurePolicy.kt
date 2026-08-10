package com.impulsive.app.backend.domain.model.protection

internal object WebsiteProtectionDisclosurePolicy {

    const val CurrentVersion:
        Int =
        1

    fun isCurrent(
        acceptedVersion:
            Int,
    ): Boolean =
        acceptedVersion >=
            CurrentVersion
}
