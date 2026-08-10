package com.impulsive.app.backend.session.adaptive

import com.impulsive.app.backend.data.local.preferences.WebsiteProtectionIncidentPolicy
import com.impulsive.app.backend.data.local.preferences.WebsiteProtectionIncidentRecord
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebsiteAdaptiveIncidentMapperTest {
    @Test
    fun duplicateWebsiteIncident_doesNotCreateSecondDecision() {
        val first = accepted(record())
        val duplicate = accepted(record(), lastToken = first.incidentToken)

        assertEquals(AdaptiveIncidentAdmissionOutcome.Admitted, first.admissionOutcome)
        assertEquals(AdaptiveIncidentAdmissionOutcome.Duplicate, duplicate.admissionOutcome)
        assertEquals(first.incidentToken, duplicate.incidentToken)
        assertEquals(AdaptiveSourceKind.Website, first.sourceKind)
        assertFalse(first.incidentToken.contains("example.com"))
        assertFalse(first.incidentToken.contains("browser"))
    }

    @Test
    fun activeCycleWebsiteIncident_doesNotCreateSecondDecision() {
        val first = accepted(record())
        val active = accepted(record(), activeToken = first.incidentToken)
        val later = accepted(
            record().copy(
                incidentStartedAtEpochMillis = 20_000L,
                lastAdultActivityAtEpochMillis = 20_000L,
            ),
            now = 20_001L,
        )

        assertEquals(AdaptiveIncidentAdmissionOutcome.ActiveCycle, active.admissionOutcome)
        assertNotEquals(first.incidentToken, later.incidentToken)
    }

    @Test
    fun laterWebsiteAttempt_canCreateNewDecision() {
        val first = accepted(record())
        val later = accepted(
            record().copy(
                incidentStartedAtEpochMillis = 20_000L,
                lastAdultActivityAtEpochMillis = 20_000L,
            ),
            now = 20_001L,
            lastToken = first.incidentToken,
        )
        assertEquals(AdaptiveIncidentAdmissionOutcome.Admitted, later.admissionOutcome)
    }

    @Test
    fun clockRegressionAndBrowserRestartStalenessFailClosed() {
        assertEquals(
            WebsiteAdaptiveIncidentMappingResult.Rejected(
                WebsiteAdaptiveIncidentRejection.ClockRegression,
            ),
            WebsiteAdaptiveIncidentMapper.map(record(), 9_999L),
        )
        assertEquals(
            WebsiteAdaptiveIncidentMappingResult.Rejected(
                WebsiteAdaptiveIncidentRejection.StaleIncident,
            ),
            WebsiteAdaptiveIncidentMapper.map(
                record(),
                10_000L + WebsiteProtectionIncidentPolicy.IncidentFreshnessMillis + 1L,
            ),
        )
    }

    @Test
    fun websiteAdmission_neverStoresRawDomainInAdaptiveState() {
        val fieldNames = AdaptiveAdmittedIncident::class.java.declaredFields.map { it.name }.toSet()
        listOf("url", "domain", "host", "title", "query", "search", "dns", "private", "package")
            .forEach { forbidden ->
                assertTrue(fieldNames.none { it.contains(forbidden, ignoreCase = true) })
            }
    }

    private fun accepted(
        record: WebsiteProtectionIncidentRecord,
        now: Long = 10_001L,
        lastToken: String? = null,
        activeToken: String? = null,
    ) = (WebsiteAdaptiveIncidentMapper.map(record, now, lastToken, activeToken)
        as WebsiteAdaptiveIncidentMappingResult.Accepted).incident

    private fun record() = WebsiteProtectionIncidentRecord(
        packageName = "com.example.browser",
        sourceLabel = "Browser",
        blockedDomain = "example.com",
        lastAdultActivityAtEpochMillis = 10_000L,
        incidentStartedAtEpochMillis = 10_000L,
    )
}
