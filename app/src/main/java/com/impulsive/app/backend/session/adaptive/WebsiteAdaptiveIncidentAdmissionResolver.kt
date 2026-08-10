package com.impulsive.app.backend.session.adaptive

import com.impulsive.app.backend.data.local.preferences.WebsiteProtectionIncidentRecord
import kotlinx.coroutines.CancellationException

/**
 * Restart-safe result of evaluating one fresh website protection incident.
 *
 * Persisted adaptive decisions are authoritative for duplicate admission.
 * The process-local last-token value is only a fast hint.
 */
sealed interface WebsiteAdaptiveIncidentAdmissionResolution {
    data class Admit(
        val incident: AdaptiveAdmittedIncident,
    ) : WebsiteAdaptiveIncidentAdmissionResolution

    data class Duplicate(
        val incidentToken: String,
        val decisionId: String,
    ) : WebsiteAdaptiveIncidentAdmissionResolution

    data class ActiveCycle(
        val incidentToken: String,
    ) : WebsiteAdaptiveIncidentAdmissionResolution

    data class Rejected(
        val reason: WebsiteAdaptiveIncidentRejection,
    ) : WebsiteAdaptiveIncidentAdmissionResolution

    /**
     * Deduplication could not be checked safely.
     *
     * The caller must use its normal quiet fallback rather than risk creating
     * a second adaptive decision.
     */
    data object PersistenceUnavailable : WebsiteAdaptiveIncidentAdmissionResolution
}

/**
 * Combines deterministic incident mapping with an authoritative persisted
 * decision lookup.
 *
 * A mapper-level Duplicate is not trusted by itself because the corresponding
 * decision may have failed to persist. Conversely, an Admitted result is
 * converted to Duplicate when the same token already exists after service or
 * process recreation.
 */
class WebsiteAdaptiveIncidentAdmissionResolver(
    private val findDecisionIdByIncidentToken: suspend (String) -> String?,
) {
    suspend fun resolve(
        record: WebsiteProtectionIncidentRecord,
        nowEpochMillis: Long,
        lastAdmittedIncidentToken: String?,
        activeCycleIncidentToken: String?,
    ): WebsiteAdaptiveIncidentAdmissionResolution {
        val mapped = WebsiteAdaptiveIncidentMapper.map(
            record = record,
            nowEpochMillis = nowEpochMillis,
            lastAdmittedIncidentToken = lastAdmittedIncidentToken,
            activeCycleIncidentToken = activeCycleIncidentToken,
        )

        return when (mapped) {
            is WebsiteAdaptiveIncidentMappingResult.Rejected ->
                WebsiteAdaptiveIncidentAdmissionResolution.Rejected(mapped.reason)

            is WebsiteAdaptiveIncidentMappingResult.Accepted ->
                resolveAccepted(mapped.incident)
        }
    }

    private suspend fun resolveAccepted(
        incident: AdaptiveAdmittedIncident,
    ): WebsiteAdaptiveIncidentAdmissionResolution {
        return when (incident.admissionOutcome) {
            AdaptiveIncidentAdmissionOutcome.ActiveCycle ->
                WebsiteAdaptiveIncidentAdmissionResolution.ActiveCycle(
                    incidentToken = incident.incidentToken,
                )

            /*
             * Both mapper-level Admitted and Duplicate require a persisted
             * lookup.
             *
             * Admitted may already exist after service recreation.
             * Duplicate may not exist when an earlier persistence attempt
             * failed and only the in-memory token was set.
             */
            AdaptiveIncidentAdmissionOutcome.Admitted,
            AdaptiveIncidentAdmissionOutcome.Duplicate,
            -> {
                val existingDecisionId = try {
                    findDecisionIdByIncidentToken(incident.incidentToken)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    return WebsiteAdaptiveIncidentAdmissionResolution.PersistenceUnavailable
                }

                if (existingDecisionId != null) {
                    WebsiteAdaptiveIncidentAdmissionResolution.Duplicate(
                        incidentToken = incident.incidentToken,
                        decisionId = existingDecisionId,
                    )
                } else {
                    WebsiteAdaptiveIncidentAdmissionResolution.Admit(
                        incident = incident.copy(
                            admissionOutcome = AdaptiveIncidentAdmissionOutcome.Admitted,
                        ),
                    )
                }
            }
        }
    }
}
