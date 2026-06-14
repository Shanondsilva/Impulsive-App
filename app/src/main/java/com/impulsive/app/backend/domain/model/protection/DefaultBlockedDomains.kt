package com.impulsive.app.backend.domain.model.protection

/**
 * Starter blocklist used to seed the blocked_domain table the first time protection is enabled.
 * This is a small placeholder set of well known adult domains. A larger curated list will be
 * bundled as an asset and loaded in a later step. The category lets the UI group entries later.
 */
object DefaultBlockedDomains {
    data class Entry(
        val domain: String,
        val category: String,
    )

    val starter: List<Entry> = listOf(
        Entry("pornhub.com", "adult"),
        Entry("xvideos.com", "adult"),
        Entry("xnxx.com", "adult"),
        Entry("xhamster.com", "adult"),
        Entry("redtube.com", "adult"),
        Entry("youporn.com", "adult"),
        Entry("spankbang.com", "adult"),
        Entry("onlyfans.com", "adult"),
    )
}
