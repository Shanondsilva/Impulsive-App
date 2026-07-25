package com.impulsive.app.backend.domain.model.protection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultBlocklistUpgradePolicyTest {
    @Test
    fun `fresh install inserts every asset entry and persists version`() {
        val plan = planDefaultBlocklistUpgrade(
            storedVersion = 0,
            asset = asset(1, "example.com", "new-default.com"),
            existing = emptyList(),
        )

        assertEquals(listOf("example.com", "new-default.com"), domains(plan.entriesToInsert))
        assertTrue(plan.entriesToPromote.isEmpty())
        assertEquals(1, plan.versionToPersist)
    }

    @Test
    fun `same version and downgrade perform no work`() {
        listOf(
            planDefaultBlocklistUpgrade(1, asset(1, "example.com"), emptyList()),
            planDefaultBlocklistUpgrade(3, asset(2, "example.com"), emptyList()),
        ).forEach { plan ->
            assertTrue(plan.entriesToInsert.isEmpty())
            assertTrue(plan.entriesToPromote.isEmpty())
            assertNull(plan.versionToPersist)
        }
    }

    @Test
    fun `upgrade inserts only a newly shipped default`() {
        val plan = planDefaultBlocklistUpgrade(
            storedVersion = 1,
            asset = asset(2, "example.com", "new-default.com"),
            existing = listOf(existing("example.com", isDefault = true, addedByUser = false)),
        )

        assertEquals(listOf("new-default.com"), domains(plan.entriesToInsert))
        assertTrue(plan.entriesToPromote.isEmpty())
        assertEquals(2, plan.versionToPersist)
    }

    @Test
    fun `custom entry outside asset survives without an operation`() {
        val plan = planDefaultBlocklistUpgrade(
            storedVersion = 1,
            asset = asset(2, "example.com"),
            existing = listOf(existing("my-custom.example", false, true)),
        )

        assertEquals(listOf("example.com"), domains(plan.entriesToInsert))
        assertTrue(plan.entriesToPromote.isEmpty())
    }

    @Test
    fun `custom entry becoming shipped default is promoted in place`() {
        val plan = planDefaultBlocklistUpgrade(
            storedVersion = 1,
            asset = asset(2, "example.com"),
            existing = listOf(existing("EXAMPLE.COM.", false, true)),
        )

        assertTrue(plan.entriesToInsert.isEmpty())
        assertEquals(listOf("example.com"), domains(plan.entriesToPromote))
    }

    @Test
    fun `existing default remains untouched`() {
        val plan = planDefaultBlocklistUpgrade(
            storedVersion = 1,
            asset = asset(2, "example.com"),
            existing = listOf(existing("example.com", true, false)),
        )

        assertTrue(plan.entriesToInsert.isEmpty())
        assertTrue(plan.entriesToPromote.isEmpty())
        assertEquals(2, plan.versionToPersist)
    }

    private fun asset(version: Int, vararg domains: String) = DefaultBlocklistAsset(
        version = version,
        entries = domains.map { DefaultBlockedDomainEntry(it, "adult") },
    )

    private fun existing(domain: String, isDefault: Boolean, addedByUser: Boolean) =
        ExistingBlockedDomainSnapshot(domain, isDefault, addedByUser)

    private fun domains(entries: List<DefaultBlockedDomainEntry>) = entries.map { it.domain }
}
