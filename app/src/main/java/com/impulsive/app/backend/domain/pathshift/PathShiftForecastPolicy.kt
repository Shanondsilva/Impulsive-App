package com.impulsive.app.backend.domain.pathshift

import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSourceKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

object PathShiftForecastPolicyVersion {
    const val Current: Int = 1
}

enum class PathShiftEvidenceStrength {
    Insufficient,
    EarlyEstimate,
    CautiousEstimate,
}

enum class PathShiftUnavailableReason {
    EmptyHistory,
    TooFewProtectedMoments,
    TooFewDistinctDays,
    HistorySpanTooShort,
    InvalidTimestamp,
    UnsupportedPolicyVersion,
}

data class PathShiftProtectedMoment(
    val incidentToken: String,
    val occurredAtMillis: Long,
    val sourceKind: AdaptiveSourceKind,
)

data class PathShiftForecastInput(
    val protectedMoments: List<PathShiftProtectedMoment>,
    val generatedAtMillis: Long,
    val zoneId: ZoneId,
    val policyVersion: Int = PathShiftForecastPolicyVersion.Current,
)

data class PathShiftTimeWindow(
    val startMinuteInclusive: Int,
    val endMinuteExclusive: Int,
) {
    init {
        require(startMinuteInclusive in 0 until MinutesPerDay)
        require(endMinuteExclusive in 1..MinutesPerDay)
        require(endMinuteExclusive - startMinuteInclusive == MinutesPerBucket)
    }

    companion object {
        const val MinutesPerBucket = 120
        const val MinutesPerDay = 24 * 60
    }
}

data class PathShiftForecastFactors(
    val protectedMomentCount: Int,
    val distinctDayCount: Int,
    val lookbackDays: Int,
    val weeklyBucketCounts: List<Int>,
    val commonTimeWindow: PathShiftTimeWindow?,
    val policyVersion: Int,
)

sealed interface PathShiftForecastResult {
    val evidenceStrength: PathShiftEvidenceStrength

    data class Available(
        val estimatedLowerCount: Int,
        val estimatedUpperCount: Int,
        val forecastWindowStartedAtMillis: Long,
        val forecastWindowEndsAtMillis: Long,
        val lookbackStartedAtMillis: Long,
        val lookbackEndedAtMillis: Long,
        val factors: PathShiftForecastFactors,
        override val evidenceStrength: PathShiftEvidenceStrength,
    ) : PathShiftForecastResult {
        init {
            require(estimatedLowerCount >= 0)
            require(estimatedUpperCount > estimatedLowerCount)
            require(forecastWindowEndsAtMillis > forecastWindowStartedAtMillis)
            require(lookbackEndedAtMillis > lookbackStartedAtMillis)
            require(evidenceStrength != PathShiftEvidenceStrength.Insufficient)
        }
    }

    data class Unavailable(
        val reason: PathShiftUnavailableReason,
        val eligibleProtectedMomentCount: Int,
        val distinctDayCount: Int,
    ) : PathShiftForecastResult {
        override val evidenceStrength: PathShiftEvidenceStrength =
            PathShiftEvidenceStrength.Insufficient
    }
}

/**
 * Transparent PathShift policy v1.
 *
 * Root moments are unique incident tokens whose source is App or Website.
 * ExplicitUserSupport records are follow-up support decisions and are excluded.
 *
 * For four oldest-to-newest local-calendar seven-day buckets with counts c1..c4,
 * expected = (1*c1 + 2*c2 + 3*c3 + 4*c4) / 10.
 * weighted absolute deviation = sum(weight * abs(count - expected)) / 10.
 * The integer range is floor(expected-buffer)..ceil(expected+buffer), where
 * buffer=max(1, ceil(deviation)). Consumer values are capped at 99.
 */
class PathShiftForecastPolicy {
    fun calculate(input: PathShiftForecastInput): PathShiftForecastResult {
        if (input.policyVersion != PathShiftForecastPolicyVersion.Current) {
            return unavailable(input, PathShiftUnavailableReason.UnsupportedPolicyVersion)
        }
        if (input.generatedAtMillis < 0L) {
            return unavailable(input, PathShiftUnavailableReason.InvalidTimestamp)
        }

        val now = Instant.ofEpochMilli(input.generatedAtMillis)
        val today = now.atZone(input.zoneId).toLocalDate()
        val forecastStart = today.plusDays(1).atStartOfDay(input.zoneId)
        val forecastEnd = forecastStart.plusDays(ForecastDays.toLong())
        val lookbackEnd = forecastStart
        val lookbackStart = lookbackEnd.minusDays(LookbackDays.toLong())
        val futureToleranceEnd = now.plus(FutureToleranceMinutes, ChronoUnit.MINUTES)

        if (input.protectedMoments.any {
                it.incidentToken.isBlank() ||
                    it.occurredAtMillis < 0L ||
                    Instant.ofEpochMilli(it.occurredAtMillis).isAfter(futureToleranceEnd)
            }
        ) {
            return unavailable(input, PathShiftUnavailableReason.InvalidTimestamp)
        }

        val roots = input.protectedMoments
            .asSequence()
            .filter { it.sourceKind != AdaptiveSourceKind.ExplicitUserSupport }
            .filter {
                val instant = Instant.ofEpochMilli(it.occurredAtMillis)
                !instant.isBefore(lookbackStart.toInstant()) &&
                    instant.isBefore(lookbackEnd.toInstant()) &&
                    !instant.isAfter(now)
            }
            .distinctBy { it.incidentToken }
            .sortedBy { it.occurredAtMillis }
            .toList()

        if (roots.isEmpty()) {
            return unavailable(input, PathShiftUnavailableReason.EmptyHistory)
        }

        val localDates = roots.map {
            Instant.ofEpochMilli(it.occurredAtMillis).atZone(input.zoneId).toLocalDate()
        }
        val distinctDayCount = localDates.toSet().size
        if (roots.size < MinimumProtectedMoments) {
            return PathShiftForecastResult.Unavailable(
                reason = PathShiftUnavailableReason.TooFewProtectedMoments,
                eligibleProtectedMomentCount = roots.size,
                distinctDayCount = distinctDayCount,
            )
        }
        if (distinctDayCount < MinimumDistinctDays) {
            return PathShiftForecastResult.Unavailable(
                reason = PathShiftUnavailableReason.TooFewDistinctDays,
                eligibleProtectedMomentCount = roots.size,
                distinctDayCount = distinctDayCount,
            )
        }
        val spanDays = ChronoUnit.DAYS.between(localDates.first(), localDates.last())
        if (spanDays < MinimumHistorySpanDays) {
            return PathShiftForecastResult.Unavailable(
                reason = PathShiftUnavailableReason.HistorySpanTooShort,
                eligibleProtectedMomentCount = roots.size,
                distinctDayCount = distinctDayCount,
            )
        }

        val bucketCounts = MutableList(WeeklyWeights.size) { 0 }
        roots.forEach { moment ->
            val date = Instant.ofEpochMilli(moment.occurredAtMillis)
                .atZone(input.zoneId)
                .toLocalDate()
            val dayOffset = ChronoUnit.DAYS.between(
                lookbackStart.toLocalDate(),
                date,
            ).toInt()
            val bucket = (dayOffset / DaysPerBucket).coerceIn(0, bucketCounts.lastIndex)
            bucketCounts[bucket] += 1
        }

        val expected = bucketCounts.indices.sumOf { index ->
            bucketCounts[index].toDouble() * WeeklyWeights[index]
        } / WeeklyWeights.sum()
        if (!expected.isFinite() || expected < 0.0) {
            return unavailable(input, PathShiftUnavailableReason.InvalidTimestamp)
        }
        val deviation = bucketCounts.indices.sumOf { index ->
            WeeklyWeights[index] * abs(bucketCounts[index] - expected)
        } / WeeklyWeights.sum()
        val buffer = max(MinimumRangeBuffer, ceil(deviation).toInt())
        var lower = floor(expected - buffer).toInt().coerceAtLeast(0)
            .coerceAtMost(MaximumConsumerCount)
        var upper = ceil(expected + buffer).toInt().coerceAtLeast(0)
            .coerceAtMost(MaximumConsumerCount)
        if (upper <= lower) {
            if (lower < MaximumConsumerCount) {
                upper = lower + 1
            } else {
                lower = MaximumConsumerCount - 1
            }
        }

        val evidenceStrength = if (
            roots.size >= CautiousMinimumProtectedMoments &&
            distinctDayCount >= CautiousMinimumDistinctDays &&
            spanDays >= CompleteLookbackSpanDays
        ) {
            PathShiftEvidenceStrength.CautiousEstimate
        } else {
            PathShiftEvidenceStrength.EarlyEstimate
        }

        return PathShiftForecastResult.Available(
            estimatedLowerCount = lower,
            estimatedUpperCount = upper,
            forecastWindowStartedAtMillis = forecastStart.toInstant().toEpochMilli(),
            forecastWindowEndsAtMillis = forecastEnd.toInstant().toEpochMilli(),
            lookbackStartedAtMillis = lookbackStart.toInstant().toEpochMilli(),
            lookbackEndedAtMillis = lookbackEnd.toInstant().toEpochMilli(),
            factors = PathShiftForecastFactors(
                protectedMomentCount = roots.size,
                distinctDayCount = distinctDayCount,
                lookbackDays = LookbackDays,
                weeklyBucketCounts = bucketCounts.toList(),
                commonTimeWindow = commonWindow(roots, input.zoneId),
                policyVersion = input.policyVersion,
            ),
            evidenceStrength = evidenceStrength,
        )
    }

    private fun commonWindow(
        roots: List<PathShiftProtectedMoment>,
        zoneId: ZoneId,
    ): PathShiftTimeWindow? {
        val counts = IntArray(PathShiftTimeWindow.MinutesPerDay / PathShiftTimeWindow.MinutesPerBucket)
        roots.forEach { moment ->
            val localTime = Instant.ofEpochMilli(moment.occurredAtMillis)
                .atZone(zoneId)
                .toLocalTime()
            val minute = localTime.hour * 60 + localTime.minute
            counts[minute / PathShiftTimeWindow.MinutesPerBucket] += 1
        }
        val maximum = counts.maxOrNull() ?: return null
        val winner = counts.indexOfFirst { it == maximum }
        val count = counts[winner]
        if (count < CommonWindowMinimumMoments) return null
        if (count.toDouble() / roots.size < CommonWindowMinimumShare) return null
        val start = winner * PathShiftTimeWindow.MinutesPerBucket
        return PathShiftTimeWindow(
            startMinuteInclusive = start,
            endMinuteExclusive = start + PathShiftTimeWindow.MinutesPerBucket,
        )
    }

    private fun unavailable(
        input: PathShiftForecastInput,
        reason: PathShiftUnavailableReason,
    ): PathShiftForecastResult.Unavailable = PathShiftForecastResult.Unavailable(
        reason = reason,
        eligibleProtectedMomentCount = input.protectedMoments.count {
            it.sourceKind != AdaptiveSourceKind.ExplicitUserSupport
        },
        distinctDayCount = input.protectedMoments
            .filter { it.occurredAtMillis >= 0L }
            .mapNotNull { runCatching { localDate(it.occurredAtMillis, input.zoneId) }.getOrNull() }
            .toSet()
            .size,
    )

    private fun localDate(millis: Long, zoneId: ZoneId): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate()

    companion object {
        const val LookbackDays = 28
        const val ForecastDays = 7
        const val DaysPerBucket = 7
        const val MinimumProtectedMoments = 7
        const val MinimumDistinctDays = 5
        const val MinimumHistorySpanDays = 14L
        const val CautiousMinimumProtectedMoments = 14
        const val CautiousMinimumDistinctDays = 10
        const val CompleteLookbackSpanDays = 27L
        const val CommonWindowMinimumMoments = 3
        const val CommonWindowMinimumShare = 0.30
        const val MinimumRangeBuffer = 1
        const val MaximumConsumerCount = 99
        const val FutureToleranceMinutes = 5L
        val WeeklyWeights: List<Double> = listOf(1.0, 2.0, 3.0, 4.0)
    }
}
