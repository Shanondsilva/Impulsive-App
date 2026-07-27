package com.impulsive.app.backend.data.local.preferences

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebsiteProtectionIncidentPolicyTest {

    @Test
    fun `new adult incident starts active lease at zero`() {
        val record = newFriction(now = 0L)

        assertEquals(WebsiteProtectionIncidentPhase.Friction, record.phase)
        assertEquals(0L, record.accumulatedFrictionMillis)
        assertEquals(0L, record.lastAdultActivityAtEpochMillis)
        assertEquals(0L, record.activeSegmentStartedAtEpochMillis)
        assertNull(record.pausedAtEpochMillis)
        assertNull(record.cooldownStartedAtEpochMillis)
        assertNull(record.cooldownUntilEpochMillis)
        assertEquals(0L, record.incidentStartedAtEpochMillis)
    }

    @Test
    fun `foreground browser with one old adult dns event cannot accumulate indefinitely`() {
        val record = newFriction(now = 0L)

        val withinLease = WebsiteProtectionIncidentPolicy.reconcile(
            record = record,
            foregroundPackage = BrowserPackage,
            nowEpochMillis = 15_000L,
        )!!
        val afterLease = WebsiteProtectionIncidentPolicy.reconcile(
            record = record,
            foregroundPackage = BrowserPackage,
            nowEpochMillis = 15_001L,
        )

        assertEquals(15_000L, withinLease.frictionElapsedMillis(15_000L))
        assertEquals(WebsiteProtectionIncidentPhase.Friction, withinLease.phase)
        assertNull(afterLease)
    }

    @Test
    fun `valid adult activity plus foreground time accumulates during lease`() {
        val record = newFriction(now = 0L)

        val active = WebsiteProtectionIncidentPolicy.reconcile(
            record = record,
            foregroundPackage = BrowserPackage,
            nowEpochMillis = 10_000L,
        )!!

        assertEquals(10_000L, active.frictionElapsedMillis(10_000L))
        assertEquals(20_000L, active.frictionRemainingMillis(10_000L))
    }

    @Test
    fun `adult dns activity within fifteen seconds refreshes lease and preserves progress`() {
        val refreshed = newFriction(now = 0L)
            .adultActivityAt(10_000L)

        val stillActiveAfterOriginalLease = WebsiteProtectionIncidentPolicy.reconcile(
            record = refreshed,
            foregroundPackage = BrowserPackage,
            nowEpochMillis = 20_000L,
        )!!

        assertEquals(10_000L, refreshed.frictionElapsedMillis(10_000L))
        assertEquals(10_000L, refreshed.lastAdultActivityAtEpochMillis)
        assertEquals(20_000L, stillActiveAfterOriginalLease.frictionElapsedMillis(20_000L))
    }

    @Test
    fun `leaving at any accumulated value below thirty seconds pauses exact progress`() {
        assertPausedProgressAt(5_000L)
        assertPausedProgressAt(16_000L)
        assertPausedProgressAt(22_000L)
        assertPausedProgressAt(28_000L)
    }

    @Test
    fun `returning with adult activity within fifteen seconds resumes previous progress`() {
        val paused = newFriction(now = 0L).pauseAt(5_000L)

        val resumed = paused.adultActivityAt(12_000L)
        val progressed = WebsiteProtectionIncidentPolicy.reconcile(
            record = resumed,
            foregroundPackage = BrowserPackage,
            nowEpochMillis = 15_000L,
        )!!

        assertEquals(5_000L, resumed.accumulatedFrictionMillis)
        assertEquals(12_000L, resumed.activeSegmentStartedAtEpochMillis)
        assertNull(resumed.pausedAtEpochMillis)
        assertEquals(8_000L, progressed.frictionElapsedMillis(15_000L))
    }

    @Test
    fun `more than fifteen seconds without adult activity resets progress`() {
        val paused = newFriction(now = 0L).pauseAt(5_000L)

        val expired = WebsiteProtectionIncidentPolicy.reconcile(
            record = paused,
            foregroundPackage = BrowserPackage,
            nowEpochMillis = 15_001L,
        )
        val fresh = WebsiteProtectionIncidentPolicy.onAdultActivity(
            record = paused,
            sourceLabel = "Brave",
            blockedDomain = "another.example",
            nowEpochMillis = 15_001L,
        )

        assertNull(expired)
        assertEquals(0L, fresh.accumulatedFrictionMillis)
        assertEquals(15_001L, fresh.lastAdultActivityAtEpochMillis)
    }

    @Test
    fun `accumulated valid activity reaching thirty seconds starts exactly seven minute cooldown`() {
        val cooldown = newFriction(now = 0L)
            .adultActivityAt(10_000L)
            .adultActivityAt(20_000L)
            .adultActivityAt(30_000L)

        assertEquals(WebsiteProtectionIncidentPhase.Cooldown, cooldown.phase)
        assertEquals(30_000L, cooldown.cooldownStartedAtEpochMillis)
        assertEquals(450_000L, cooldown.cooldownUntilEpochMillis)
        assertEquals(0L, cooldown.incidentStartedAtEpochMillis)
        assertTrue(cooldown.isCooldownActive(449_999L))
        assertFalse(cooldown.isCooldownActive(450_000L))
    }

    @Test
    fun `adult activity during existing cooldown does not extend cooldown`() {
        val cooldown = cooldownRecord(
            startedAt = 30_000L,
        )

        val repeated = WebsiteProtectionIncidentPolicy.onAdultActivity(
            record = cooldown,
            sourceLabel = "Brave",
            blockedDomain = "another.example",
            nowEpochMillis = 100_000L,
        )

        assertEquals(450_000L, repeated.cooldownUntilEpochMillis)
        assertEquals("another.example", repeated.blockedDomain)
    }

    @Test
    fun `cooldown expires exactly at seven minutes`() {
        val cooldown = cooldownRecord(
            startedAt = 30_000L,
        )

        assertEquals(450_000L, cooldown.cooldownUntilEpochMillis)
        assertTrue(cooldown.isCooldownActive(449_999L))
        assertFalse(cooldown.isCooldownActive(450_000L))
        assertNull(
            WebsiteProtectionIncidentPolicy.reconcile(
                record = cooldown,
                foregroundPackage = BrowserPackage,
                nowEpochMillis = 450_000L,
            ),
        )
    }

    @Test
    fun `adult activity updates and friction to cooldown preserve first incident timestamp`() {
        val initial = newFriction(now = 5_000L)
        val refreshed = initial.adultActivityAt(15_000L)
        val cooldown = refreshed
            .adultActivityAt(25_000L)
            .adultActivityAt(35_000L)

        assertEquals(5_000L, refreshed.incidentStartedAtEpochMillis)
        assertEquals(WebsiteProtectionIncidentPhase.Cooldown, cooldown.phase)
        assertEquals(5_000L, cooldown.incidentStartedAtEpochMillis)
    }

    @Test
    fun `legacy incident start migration uses required safe precedence`() {
        assertEquals(
            1_000L,
            deriveStart(
                explicit = 1_000L,
                active = 2_000L,
                lastAdult = 3_000L,
                cooldown = 40_000L,
                accumulated = 30_000L,
            ),
        )
        assertEquals(2_000L, deriveStart(active = 2_000L, lastAdult = 3_000L))
        assertEquals(3_000L, deriveStart(lastAdult = 3_000L))
        assertEquals(
            10_000L,
            deriveStart(cooldown = 40_000L, accumulated = 30_000L),
        )
        assertEquals(99_000L, deriveStart())
    }

    @Test
    fun `incident start is encoded and legacy decode derives safe fallback`() {
        val source = File(
            "src/main/java/com/impulsive/app/backend/data/local/preferences/" +
                "WebsiteProtectionIncidentDataSource.kt",
        ).readText()

        assertTrue(source.contains("IncidentStartedAtKey"))
        assertTrue(source.contains("record.incidentStartedAtEpochMillis"))
        assertTrue(source.contains("optionalLong(json, IncidentStartedAtKey)"))
        assertTrue(source.contains("deriveWebsiteProtectionIncidentStartedAt("))
    }

    @Test
    fun `friction record without adult activity timestamp is rejected`() {
        val malformed = newFriction(now = 0L).copy(
            lastAdultActivityAtEpochMillis = null,
        )

        assertNull(
            WebsiteProtectionIncidentPolicy.validate(
                malformed,
            ),
        )
    }

    private fun deriveStart(
        explicit: Long? = null,
        active: Long? = null,
        lastAdult: Long? = null,
        cooldown: Long? = null,
        accumulated: Long = 0L,
    ): Long =
        deriveWebsiteProtectionIncidentStartedAt(
            explicitIncidentStartedAtEpochMillis = explicit,
            activeSegmentStartedAtEpochMillis = active,
            lastAdultActivityAtEpochMillis = lastAdult,
            cooldownStartedAtEpochMillis = cooldown,
            accumulatedFrictionMillis = accumulated,
            nowEpochMillis = 99_000L,
        )

    private fun assertPausedProgressAt(
        nowEpochMillis: Long,
    ) {
        val active = activeFrictionCovering(
            nowEpochMillis = nowEpochMillis,
        )

        val paused = WebsiteProtectionIncidentPolicy.reconcile(
            record = active,
            foregroundPackage = null,
            nowEpochMillis = nowEpochMillis,
        )!!

        assertEquals(nowEpochMillis, paused.accumulatedFrictionMillis)
        assertNull(paused.activeSegmentStartedAtEpochMillis)
        assertEquals(nowEpochMillis, paused.pausedAtEpochMillis)
    }

    private fun activeFrictionCovering(
        nowEpochMillis: Long,
    ): WebsiteProtectionIncidentRecord =
        newFriction(now = 0L).copy(
            lastAdultActivityAtEpochMillis =
                (nowEpochMillis - 1_000L).coerceAtLeast(0L),
        )

    private fun newFriction(
        packageName: String = BrowserPackage,
        now: Long,
    ): WebsiteProtectionIncidentRecord =
        WebsiteProtectionIncidentPolicy.createFriction(
            packageName = packageName,
            sourceLabel = "Brave",
            blockedDomain = "adult.example",
            nowEpochMillis = now,
        )

    private fun WebsiteProtectionIncidentRecord.adultActivityAt(
        nowEpochMillis: Long,
    ): WebsiteProtectionIncidentRecord =
        WebsiteProtectionIncidentPolicy.onAdultActivity(
            record = this,
            sourceLabel = "Brave",
            blockedDomain = "another.example",
            nowEpochMillis = nowEpochMillis,
        )

    private fun WebsiteProtectionIncidentRecord.pauseAt(
        nowEpochMillis: Long,
    ): WebsiteProtectionIncidentRecord =
        WebsiteProtectionIncidentPolicy.reconcile(
            record = this,
            foregroundPackage = null,
            nowEpochMillis = nowEpochMillis,
        )!!

    private fun cooldownRecord(
        startedAt: Long,
    ): WebsiteProtectionIncidentRecord =
        WebsiteProtectionIncidentRecord(
            packageName = BrowserPackage,
            sourceLabel = "Brave",
            blockedDomain = "adult.example",
            phase = WebsiteProtectionIncidentPhase.Cooldown,
            accumulatedFrictionMillis = WebsiteProtectionIncidentPolicy.FrictionMillis,
            lastAdultActivityAtEpochMillis = null,
            activeSegmentStartedAtEpochMillis = null,
            pausedAtEpochMillis = null,
            cooldownStartedAtEpochMillis = startedAt,
            cooldownUntilEpochMillis = startedAt + WebsiteProtectionIncidentPolicy.CooldownMillis,
            incidentStartedAtEpochMillis =
                startedAt - WebsiteProtectionIncidentPolicy.FrictionMillis,
        )

    private companion object {
        const val BrowserPackage = "com.brave.browser"
    }
}
