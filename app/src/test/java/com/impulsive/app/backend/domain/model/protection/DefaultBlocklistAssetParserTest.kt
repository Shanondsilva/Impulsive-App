package com.impulsive.app.backend.domain.model.protection

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultBlocklistAssetParserTest {
    @Test
    fun `production v1 asset contains 64 valid mandatory defaults`() {
        val relativePath = "src/main/assets/blocklists/default_blocked_domains.tsv"
        val assetFile = sequenceOf(File(relativePath), File("app/$relativePath"))
            .firstOrNull(File::isFile)
            ?: error("Production default blocklist asset was not found.")
        val asset = parseDefaultBlocklistAsset(assetFile.readText(Charsets.UTF_8))
        val originalDefaults = setOf(
            "pornhub.com",
            "xvideos.com",
            "xnxx.com",
            "xhamster.com",
            "redtube.com",
            "youporn.com",
            "spankbang.com",
            "onlyfans.com",
        )

        assertEquals(1, asset.version)
        assertEquals(64, asset.entries.size)
        assertEquals(64, asset.entries.map { it.domain }.toSet().size)
        assertTrue(asset.entries.all { it.category == DefaultBlockedDomains.CategoryAdult })
        assertTrue(asset.entries.map { it.domain }.containsAll(originalDefaults))
    }

    @Test
    fun `valid asset ignores comments and normalizes sorted entries`() {
        val asset = parseDefaultBlocklistAsset(
            """
            # comment

            version=2
            EXAMPLE.COM	adult
            bücher.de	adult
            """.trimIndent(),
        )

        assertEquals(2, asset.version)
        assertEquals(
            listOf("example.com", "xn--bcher-kva.de"),
            asset.entries.map(DefaultBlockedDomainEntry::domain),
        )
        assertEquals(listOf("adult", "adult"), asset.entries.map { it.category })
    }

    @Test
    fun `missing invalid and duplicate versions are rejected`() {
        assertInvalid("example.com\tadult")
        assertInvalid("version=0\nexample.com\tadult")
        assertInvalid("version=1\nversion=2\nexample.com\tadult")
    }

    @Test
    fun `malformed rows domains categories and canonical duplicates are rejected`() {
        assertInvalid("version=1\nexample.com")
        assertInvalid("version=1\nhttps://example.com\tadult")
        assertInvalid("version=1\nexample.com\tAdult Content!")
        assertInvalid("version=1\nEXAMPLE.COM\tadult\nexample.com.\tadult")
    }

    private fun assertInvalid(text: String) {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            parseDefaultBlocklistAsset(text)
        }
    }
}
