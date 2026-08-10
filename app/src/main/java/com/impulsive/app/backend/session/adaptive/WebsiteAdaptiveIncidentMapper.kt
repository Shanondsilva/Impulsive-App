package com.impulsive.app.backend.session.adaptive

import com.impulsive.app.backend.data.local.preferences.WebsiteProtectionIncidentPolicy
import com.impulsive.app.backend.data.local.preferences.WebsiteProtectionIncidentRecord
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSourceKind

enum class WebsiteAdaptiveIncidentRejection {
    InvalidIncident,
    ClockRegression,
    StaleIncident,
}

sealed interface WebsiteAdaptiveIncidentMappingResult {
    data class Accepted(val incident: AdaptiveAdmittedIncident) :
        WebsiteAdaptiveIncidentMappingResult

    data class Rejected(val reason: WebsiteAdaptiveIncidentRejection) :
        WebsiteAdaptiveIncidentMappingResult
}

object WebsiteAdaptiveIncidentMapper {
    fun map(
        record: WebsiteProtectionIncidentRecord,
        nowEpochMillis: Long,
        lastAdmittedIncidentToken: String? = null,
        activeCycleIncidentToken: String? = null,
    ): WebsiteAdaptiveIncidentMappingResult {
        val valid = WebsiteProtectionIncidentPolicy.validate(record)
            ?: return rejected(WebsiteAdaptiveIncidentRejection.InvalidIncident)
        if (nowEpochMillis < valid.lastAdultActivityAtEpochMillis) {
            return rejected(WebsiteAdaptiveIncidentRejection.ClockRegression)
        }
        if (!WebsiteProtectionIncidentPolicy.isFresh(valid, nowEpochMillis)) {
            return rejected(WebsiteAdaptiveIncidentRejection.StaleIncident)
        }

        val token = AdaptiveIncidentTokenFactory.create(
            AdaptiveIncidentSignal(
                source = AdaptiveProtectionSource.VpnWebsite,
                incidentStartedAtMillis = valid.incidentStartedAtEpochMillis,
                ephemeralSourceIdentity = valid.packageName + '\u0000' + valid.blockedDomain,
            ),
        )
        val outcome = when (token) {
            activeCycleIncidentToken -> AdaptiveIncidentAdmissionOutcome.ActiveCycle
            lastAdmittedIncidentToken -> AdaptiveIncidentAdmissionOutcome.Duplicate
            else -> AdaptiveIncidentAdmissionOutcome.Admitted
        }
        return WebsiteAdaptiveIncidentMappingResult.Accepted(
            AdaptiveAdmittedIncident(
                sourceKind = AdaptiveSourceKind.Website,
                incidentToken = token,
                detectedAtMillis = valid.incidentStartedAtEpochMillis,
                admissionReason = AdaptiveIncidentAdmissionReason.ProtectionAttempt,
                admissionOutcome = outcome,
            ),
        )
    }

    private fun rejected(reason: WebsiteAdaptiveIncidentRejection) =
        WebsiteAdaptiveIncidentMappingResult.Rejected(reason)
}
