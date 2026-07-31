package com.impulsive.app.backend.domain.protectioncoach

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.floor

data class ProtectionCoachIncidentEvidence(
    val incidentId: String,
    val createdAtMillis: Long,
    val isGenuineRootIncident: Boolean = true,
    val isDuplicate: Boolean = false,
    val isFollowUpDecision: Boolean = false,
)

data class ExistingProtectionWindow(
    val startMinute: Int,
    val endMinute: Int,
) {
    init {
        requireValidMinute(startMinute)
        requireValidMinute(endMinute)
    }

    fun covers(start: Int, end: Int): Boolean =
        startMinute <= start && endMinute >= end
}

data class SmartProtectionWindowRequest(
    val incidents: List<ProtectionCoachIncidentEvidence>,
    val nowMillis: Long,
    val zoneId: ZoneId,
    val existingWindows: List<ExistingProtectionWindow> = emptyList(),
    val activeEquivalentSuggestion: Boolean = false,
    val suppressed: Boolean = false,
    val lastDismissedAtMillis: Long? = null,
    val acceptedEquivalentStillCovers: Boolean = false,
    val policy: ProtectionCoachPolicy = ProtectionCoachPolicy(),
)

sealed interface SmartProtectionWindowResult {
    data class Suggestion(
        val suggestionType: ProtectionCoachSuggestionType,
        val evidence: ProtectionCoachEvidence,
        val suggestedStartMinute: Int,
        val suggestedEndMinute: Int,
        val explanation: String,
    ) : SmartProtectionWindowResult

    data class Unavailable(
        val reason: ProtectionCoachUnavailableReason,
    ) : SmartProtectionWindowResult
}

object SmartProtectionWindowPolicy {
    private const val MinimumMoments = 7
    private const val MinimumDistinctDays = 5
    private const val MinimumLookbackDays = 14
    private const val MinimumBucketIncidents = 3
    private const val BucketMinutes = 120
    private const val BucketShare = 0.30

    fun evaluate(request: SmartProtectionWindowRequest): SmartProtectionWindowResult {
        if (request.suppressed) return unavailable(ProtectionCoachUnavailableReason.SuppressedByUser)
        if (request.activeEquivalentSuggestion) return unavailable(ProtectionCoachUnavailableReason.DuplicateActiveSuggestion)
        if (request.acceptedEquivalentStillCovers) {
            return unavailable(ProtectionCoachUnavailableReason.EquivalentScheduleAlreadyCoversWindow)
        }
        if (request.lastDismissedAtMillis != null &&
            request.nowMillis - request.lastDismissedAtMillis < request.policy.dismissalCooldownMillis
        ) {
            return unavailable(ProtectionCoachUnavailableReason.DismissalCooldown)
        }
        val eligible = request.incidents
            .filter { it.isGenuineRootIncident && !it.isDuplicate && !it.isFollowUpDecision }
            .distinctBy { it.incidentId }
            .filter { it.createdAtMillis <= request.nowMillis + 5 * 60 * 1_000L }
        if (eligible.size < MinimumMoments) return unavailable(ProtectionCoachUnavailableReason.InsufficientRootMoments)
        val dates = eligible.map { localDate(it.createdAtMillis, request.zoneId) }.toSet()
        if (dates.size < MinimumDistinctDays) return unavailable(ProtectionCoachUnavailableReason.InsufficientDistinctDays)
        val minMillis = eligible.minOf { it.createdAtMillis }
        if (daysBetween(localDate(minMillis, request.zoneId), localDate(request.nowMillis, request.zoneId)) < MinimumLookbackDays) {
            return unavailable(ProtectionCoachUnavailableReason.InsufficientLookback)
        }

        val bucket = eligible
            .groupBy { bucketStartMinute(localMinute(it.createdAtMillis, request.zoneId)) }
            .mapValues { (_, values) -> values.size }
            .entries
            .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
            .first()
        val share = bucket.value.toDouble() / eligible.size.toDouble()
        if (bucket.value < MinimumBucketIncidents || share < BucketShare) {
            return unavailable(ProtectionCoachUnavailableReason.NoStrongTimeBucket)
        }

        val broadStart = bucket.key
        val broadEnd = (broadStart + BucketMinutes).coerceAtMost(1_439)
        val suggestedStart = roundDownThirty(broadStart)
        val suggestedEnd = roundUpThirty(broadEnd).coerceAtMost(1_439)
        if (request.existingWindows.any { it.covers(suggestedStart, suggestedEnd) }) {
            return unavailable(ProtectionCoachUnavailableReason.EquivalentScheduleAlreadyCoversWindow)
        }

        return SmartProtectionWindowResult.Suggestion(
            suggestionType = typeFor(suggestedStart),
            evidence = ProtectionCoachEvidence(
                evidenceWindowStartedAtMillis = minMillis,
                evidenceWindowEndedAtMillis = request.nowMillis,
                protectedMomentCount = eligible.size,
                distinctDayCount = dates.size,
                broadWindowStartMinute = broadStart,
                broadWindowEndMinute = broadEnd,
            ),
            suggestedStartMinute = suggestedStart,
            suggestedEndMinute = suggestedEnd,
            explanation = "A pattern Impulsive noticed: ${eligible.size} protected moments across ${dates.size} days clustered in a broad local time window.",
        )
    }

    private fun unavailable(reason: ProtectionCoachUnavailableReason) =
        SmartProtectionWindowResult.Unavailable(reason)

    private fun localDate(millis: Long, zoneId: ZoneId): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate()

    private fun localMinute(millis: Long, zoneId: ZoneId): Int {
        val time = Instant.ofEpochMilli(millis).atZone(zoneId).toLocalTime()
        return time.hour * 60 + time.minute
    }

    private fun bucketStartMinute(minute: Int): Int =
        (floor(minute / BucketMinutes.toDouble()).toInt() * BucketMinutes).coerceIn(0, 1_320)

    private fun daysBetween(start: LocalDate, end: LocalDate): Long =
        java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1

    private fun roundDownThirty(minute: Int): Int = (minute / 30) * 30
    private fun roundUpThirty(minute: Int): Int = ((minute + 29) / 30) * 30

    private fun typeFor(startMinute: Int): ProtectionCoachSuggestionType = when {
        startMinute < 12 * 60 -> ProtectionCoachSuggestionType.CreateMorningWindow
        startMinute >= 18 * 60 -> ProtectionCoachSuggestionType.CreateEveningWindow
        else -> ProtectionCoachSuggestionType.StartProtectionEarlier
    }
}
