package com.impulsive.app.backend.session.adaptive

import com.impulsive.app.backend.data.local.preferences.WebsiteProtectionIncidentPolicy
import com.impulsive.app.backend.data.local.preferences.WebsiteProtectionIncidentRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebsiteAdaptiveIncidentAdmissionResolverTest {
    @Test
    fun persistedDecisionSurvivesResolverAndServiceRecreation() = runBlocking {
        val persistedDecisions = mutableMapOf<String, String>()

        val firstResolver = resolver(persistedDecisions)

        val first = firstResolver.resolve(
            record = record(),
            nowEpochMillis = 10_001L,
            lastAdmittedIncidentToken = null,
            activeCycleIncidentToken = null,
        ) as WebsiteAdaptiveIncidentAdmissionResolution.Admit

        persistedDecisions[first.incident.incidentToken] = "decision-1"

        /*
         * A new resolver represents a recreated service/process. No
         * process-local last-token value is supplied.
         */
        val recreatedResolver = resolver(persistedDecisions)

        val restored = recreatedResolver.resolve(
            record = record(),
            nowEpochMillis = 10_001L,
            lastAdmittedIncidentToken = null,
            activeCycleIncidentToken = null,
        ) as WebsiteAdaptiveIncidentAdmissionResolution.Duplicate

        assertEquals(first.incident.incidentToken, restored.incidentToken)

        assertEquals("decision-1", restored.decisionId)
    }

    @Test
    fun inMemoryDuplicateWithoutPersistedDecisionIsRetryable() = runBlocking {
        val lookup = mutableMapOf<String, String>()

        val resolver = resolver(lookup)

        val first = resolver.resolve(
            record = record(),
            nowEpochMillis = 10_001L,
            lastAdmittedIncidentToken = null,
            activeCycleIncidentToken = null,
        ) as WebsiteAdaptiveIncidentAdmissionResolution.Admit

        val retry = resolver.resolve(
            record = record(),
            nowEpochMillis = 10_001L,
            lastAdmittedIncidentToken = first.incident.incidentToken,
            activeCycleIncidentToken = null,
        ) as WebsiteAdaptiveIncidentAdmissionResolution.Admit

        assertEquals(first.incident.incidentToken, retry.incident.incidentToken)

        assertEquals(
            AdaptiveIncidentAdmissionOutcome.Admitted,
            retry.incident.admissionOutcome,
        )
    }

    @Test
    fun activeCycleDoesNotRequirePersistedDecisionLookup() = runBlocking {
        var lookupCount = 0

        val resolver = WebsiteAdaptiveIncidentAdmissionResolver {
            lookupCount += 1

            null
        }

        val first = resolver.resolve(
            record = record(),
            nowEpochMillis = 10_001L,
            lastAdmittedIncidentToken = null,
            activeCycleIncidentToken = null,
        ) as WebsiteAdaptiveIncidentAdmissionResolution.Admit

        lookupCount = 0

        val active = resolver.resolve(
            record = record(),
            nowEpochMillis = 10_001L,
            lastAdmittedIncidentToken = null,
            activeCycleIncidentToken = first.incident.incidentToken,
        )

        assertTrue(active is WebsiteAdaptiveIncidentAdmissionResolution.ActiveCycle)

        assertEquals(0, lookupCount)
    }

    @Test
    fun lookupFailureFailsClosed() = runBlocking {
        val resolver = WebsiteAdaptiveIncidentAdmissionResolver {
            throw IllegalStateException("database unavailable")
        }

        assertEquals(
            WebsiteAdaptiveIncidentAdmissionResolution.PersistenceUnavailable,
            resolver.resolve(
                record = record(),
                nowEpochMillis = 10_001L,
                lastAdmittedIncidentToken = null,
                activeCycleIncidentToken = null,
            ),
        )
    }

    @Test
    fun staleIncidentIsRejectedBeforeDecisionLookup() = runBlocking {
        var lookupCount = 0

        val resolver = WebsiteAdaptiveIncidentAdmissionResolver {
            lookupCount += 1

            null
        }

        val result = resolver.resolve(
            record = record(),
            nowEpochMillis = 10_000L + WebsiteProtectionIncidentPolicy.IncidentFreshnessMillis + 1L,
            lastAdmittedIncidentToken = null,
            activeCycleIncidentToken = null,
        )

        assertEquals(
            WebsiteAdaptiveIncidentAdmissionResolution.Rejected(
                WebsiteAdaptiveIncidentRejection.StaleIncident,
            ),
            result,
        )

        assertEquals(0, lookupCount)
    }

    @Test
    fun resolutionNeverContainsRawWebsiteIdentity() {
        val fieldNames = WebsiteAdaptiveIncidentAdmissionResolution.Duplicate::class.java
            .declaredFields
            .map { it.name }

        listOf(
            "domain",
            "host",
            "url",
            "query",
            "title",
            "package",
        ).forEach { forbidden ->
            assertFalse(
                fieldNames.any { it.contains(forbidden, ignoreCase = true) },
            )
        }
    }

    private fun resolver(
        persisted: Map<String, String>,
    ): WebsiteAdaptiveIncidentAdmissionResolver =
        WebsiteAdaptiveIncidentAdmissionResolver { incidentToken ->
            persisted[incidentToken]
        }

    private fun record(): WebsiteProtectionIncidentRecord = WebsiteProtectionIncidentRecord(
        packageName = "com.example.browser",
        sourceLabel = "Browser",
        blockedDomain = "example.com",
        lastAdultActivityAtEpochMillis = 10_000L,
        incidentStartedAtEpochMillis = 10_000L,
    )
}
