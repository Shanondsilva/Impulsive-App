package com.impulsive.app.backend.data.local.preferences

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebsiteProtectionIncidentPolicyTest {

    @Test
    fun `first adult event creates an immediately eligible incident`() {
        val record =
            newIncident(
                now = 1_000L,
            )

        assertEquals(
            BrowserPackage,
            record.packageName,
        )
        assertEquals(
            "Brave",
            record.sourceLabel,
        )
        assertEquals(
            "adult.example",
            record.blockedDomain,
        )
        assertEquals(
            1_000L,
            record.lastAdultActivityAtEpochMillis,
        )
        assertEquals(
            1_000L,
            record.incidentStartedAtEpochMillis,
        )
        assertEquals(
            record,
            WebsiteProtectionIncidentPolicy.reconcile(
                record = record,
                foregroundPackage = BrowserPackage,
                nowEpochMillis = 1_000L,
            ),
        )
    }

    @Test
    fun `matching foreground browser receives a recent incident`() {
        val record =
            newIncident(
                now = 10_000L,
            )

        assertEquals(
            record,
            WebsiteProtectionIncidentPolicy.reconcile(
                record = record,
                foregroundPackage = BrowserPackage,
                nowEpochMillis = 20_000L,
            ),
        )
    }

    @Test
    fun `freshness boundary is inclusive and then expires`() {
        val record =
            newIncident(
                now = 0L,
            )

        assertEquals(
            record,
            WebsiteProtectionIncidentPolicy.reconcile(
                record = record,
                foregroundPackage = BrowserPackage,
                nowEpochMillis =
                    WebsiteProtectionIncidentPolicy
                        .IncidentFreshnessMillis,
            ),
        )

        assertNull(
            WebsiteProtectionIncidentPolicy.reconcile(
                record = record,
                foregroundPackage = BrowserPackage,
                nowEpochMillis =
                    WebsiteProtectionIncidentPolicy
                        .IncidentFreshnessMillis +
                        1L,
            ),
        )
    }

    @Test
    fun `refresh preserves first incident timestamp and updates activity`() {
        val initial =
            newIncident(
                now = 5_000L,
            )

        val refreshed =
            WebsiteProtectionIncidentPolicy.onAdultActivity(
                record = initial,
                sourceLabel = "Chrome",
                blockedDomain = "another.example",
                nowEpochMillis = 12_000L,
            )

        assertEquals(
            5_000L,
            refreshed.incidentStartedAtEpochMillis,
        )
        assertEquals(
            12_000L,
            refreshed.lastAdultActivityAtEpochMillis,
        )
        assertEquals(
            "Chrome",
            refreshed.sourceLabel,
        )
        assertEquals(
            "another.example",
            refreshed.blockedDomain,
        )
    }

    @Test
    fun `activity after stale incident creates a new incident`() {
        val stale =
            newIncident(
                now = 0L,
            )

        val refreshed =
            WebsiteProtectionIncidentPolicy.onAdultActivity(
                record = stale,
                sourceLabel = "Chrome",
                blockedDomain = "new.example",
                nowEpochMillis =
                    WebsiteProtectionIncidentPolicy
                        .IncidentFreshnessMillis +
                        1L,
            )

        assertEquals(
            WebsiteProtectionIncidentPolicy
                .IncidentFreshnessMillis +
                1L,
            refreshed.incidentStartedAtEpochMillis,
        )
        assertEquals(
            refreshed.incidentStartedAtEpochMillis,
            refreshed.lastAdultActivityAtEpochMillis,
        )
    }

    @Test
    fun `null foreground clears pending incident eligibility`() {
        assertNull(
            WebsiteProtectionIncidentPolicy.reconcile(
                record =
                    newIncident(
                        now = 0L,
                    ),
                foregroundPackage = null,
                nowEpochMillis = 1_000L,
            ),
        )
    }

    @Test
    fun `different foreground package clears pending incident eligibility`() {
        assertNull(
            WebsiteProtectionIncidentPolicy.reconcile(
                record =
                    newIncident(
                        now = 0L,
                    ),
                foregroundPackage =
                    "com.android.launcher",
                nowEpochMillis =
                    1_000L,
            ),
        )
    }

    @Test
    fun `clock rollback does not treat future activity as fresh`() {
        val record =
            newIncident(
                now = 10_000L,
            )

        assertNull(
            WebsiteProtectionIncidentPolicy.reconcile(
                record = record,
                foregroundPackage = BrowserPackage,
                nowEpochMillis = 9_999L,
            ),
        )
    }

    @Test
    fun `malformed timestamps are rejected`() {
        val malformed =
            WebsiteProtectionIncidentRecord(
                packageName =
                    BrowserPackage,
                sourceLabel =
                    "Brave",
                blockedDomain =
                    "adult.example",
                lastAdultActivityAtEpochMillis =
                    5_000L,
                incidentStartedAtEpochMillis =
                    6_000L,
            )

        assertNull(
            WebsiteProtectionIncidentPolicy.validate(
                malformed,
            ),
        )
    }

    @Test
    fun `blank package is rejected`() {
        val malformed =
            WebsiteProtectionIncidentRecord(
                packageName =
                    "   ",
                sourceLabel =
                    "Brave",
                blockedDomain =
                    "adult.example",
                lastAdultActivityAtEpochMillis =
                    5_000L,
                incidentStartedAtEpochMillis =
                    5_000L,
            )

        assertNull(
            WebsiteProtectionIncidentPolicy.validate(
                malformed,
            ),
        )
    }

    @Test
    fun `v4 persistence rejects obsolete timer schema`() {
        val source =
            File(
                "src/main/java/com/impulsive/app/backend/data/local/preferences/" +
                    "WebsiteProtectionIncidentDataSource.kt",
            ).readText()

        assertTrue(
            source.contains(
                "\"website_protection_incidents_v4\"",
            ),
        )
        assertTrue(
            source.contains(
                "\"website_protection_incidents_v3\"",
            ),
        )
        assertTrue(
            source.contains(
                ".clear()",
            ),
        )

        assertFalse(
            source.contains(
                "WebsiteProtectionIncidentPhase",
            ),
        )
        assertFalse(
            source.contains(
                "FrictionMillis",
            ),
        )
        assertFalse(
            source.contains(
                "ResumeGraceMillis",
            ),
        )
        assertFalse(
            source.contains(
                "CooldownMillis",
            ),
        )
        assertFalse(
            source.contains(
                "accumulatedFrictionMillis",
            ),
        )
        assertFalse(
            source.contains(
                "activeSegmentStartedAtEpochMillis",
            ),
        )
        assertFalse(
            source.contains(
                "pausedAtEpochMillis",
            ),
        )
        assertFalse(
            source.contains(
                "cooldownStartedAtEpochMillis",
            ),
        )
        assertFalse(
            source.contains(
                "cooldownUntilEpochMillis",
            ),
        )
    }

    @Test
    fun `only immediate incident fields are persisted`() {
        val source =
            File(
                "src/main/java/com/impulsive/app/backend/data/local/preferences/" +
                    "WebsiteProtectionIncidentDataSource.kt",
            ).readText()

        assertTrue(
            source.contains(
                "record.packageName",
            ),
        )
        assertTrue(
            source.contains(
                "record.sourceLabel",
            ),
        )
        assertTrue(
            source.contains(
                "record.blockedDomain",
            ),
        )
        assertTrue(
            source.contains(
                "record.lastAdultActivityAtEpochMillis",
            ),
        )
        assertTrue(
            source.contains(
                "record.incidentStartedAtEpochMillis",
            ),
        )
    }

    private fun newIncident(
        now: Long,
    ): WebsiteProtectionIncidentRecord =
        WebsiteProtectionIncidentPolicy
            .createImmediateIncident(
                packageName =
                    BrowserPackage,
                sourceLabel =
                    "Brave",
                blockedDomain =
                    "adult.example",
                nowEpochMillis =
                    now,
            )

    private companion object {
        const val BrowserPackage =
            "com.brave.browser"
    }
}
