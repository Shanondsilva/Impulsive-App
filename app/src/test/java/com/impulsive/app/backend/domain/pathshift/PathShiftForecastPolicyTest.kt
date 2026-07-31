package com.impulsive.app.backend.domain.pathshift

import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSourceKind
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PathShiftForecastPolicyTest {
    private val zone = ZoneId.of("Europe/London")
    private val now = ZonedDateTime.of(
        LocalDate.of(2026, 7, 29),
        LocalTime.NOON,
        zone,
    )
    private val policy = PathShiftForecastPolicy()

    @Test
    fun `empty history is safe`() {
        val result = calculate(emptyList())
        assertEquals(
            PathShiftUnavailableReason.EmptyHistory,
            (result as PathShiftForecastResult.Unavailable).reason,
        )
    }

    @Test
    fun `follow-up support decisions are excluded and duplicate roots counted once`() {
        val moments = sufficientEarlyMoments().toMutableList()
        moments += moment("follow-up", 20, AdaptiveSourceKind.ExplicitUserSupport)
        moments += moments.first().copy()
        val result = calculate(moments) as PathShiftForecastResult.Available
        assertEquals(7, result.factors.protectedMomentCount)
    }

    @Test
    fun `less than seven root incidents is insufficient`() {
        val result = calculate(sufficientEarlyMoments().take(6))
        assertEquals(
            PathShiftUnavailableReason.TooFewProtectedMoments,
            (result as PathShiftForecastResult.Unavailable).reason,
        )
    }

    @Test
    fun `less than five dates is insufficient`() {
        val moments = listOf(
            moment("a1", 20), moment("a2", 20),
            moment("b1", 18), moment("b2", 18),
            moment("c1", 16), moment("c2", 16),
            moment("d", 4),
        )
        val result = calculate(moments)
        assertEquals(
            PathShiftUnavailableReason.TooFewDistinctDays,
            (result as PathShiftForecastResult.Unavailable).reason,
        )
    }

    @Test
    fun `less than fourteen day span is insufficient`() {
        val moments = (0 until 7).map { moment("m$it", 13 - it * 2L) }
        val result = calculate(moments)
        assertEquals(
            PathShiftUnavailableReason.HistorySpanTooShort,
            (result as PathShiftForecastResult.Unavailable).reason,
        )
    }

    @Test
    fun `valid early estimate records policy and timezone calendar windows`() {
        val result = calculate(sufficientEarlyMoments()) as PathShiftForecastResult.Available
        assertEquals(PathShiftEvidenceStrength.EarlyEstimate, result.evidenceStrength)
        assertEquals(PathShiftForecastPolicyVersion.Current, result.factors.policyVersion)
        assertEquals(4, result.factors.weeklyBucketCounts.size)
        assertTrue(result.estimatedUpperCount > result.estimatedLowerCount)
    }

    @Test
    fun `complete history with fourteen incidents and ten dates is cautious`() {
        val days = listOf(27L, 25L, 23L, 21L, 19L, 17L, 15L, 13L, 11L, 9L, 7L, 5L, 2L, 0L)
        val moments = days.mapIndexed { index, day -> moment("c$index", day) }
        val result = calculate(moments) as PathShiftForecastResult.Available
        assertEquals(PathShiftEvidenceStrength.CautiousEstimate, result.evidenceStrength)
    }

    @Test
    fun `newer weekly buckets receive greater deterministic weight`() {
        val older = sufficientEarlyMoments() + listOf(
            moment("o1", 26), moment("o2", 25), moment("o3", 24),
        )
        val newer = sufficientEarlyMoments() + listOf(
            moment("n1", 2), moment("n2", 1), moment("n3", 0),
        )
        val first = calculate(older) as PathShiftForecastResult.Available
        val repeat = calculate(older) as PathShiftForecastResult.Available
        val recent = calculate(newer) as PathShiftForecastResult.Available
        assertEquals(first, repeat)
        assertTrue(recent.estimatedUpperCount > first.estimatedUpperCount)
    }

    @Test
    fun `variable history widens range more than stable history`() {
        val stable = (0 until 4).flatMap { week ->
            listOf(
                moment("s${week}a", 27L - week * 7L),
                moment("s${week}b", 26L - week * 7L),
            )
        }
        val variable = sufficientEarlyMoments() + (0 until 10).map {
            moment("v$it", it % 3L)
        }
        val stableResult = calculate(stable) as PathShiftForecastResult.Available
        val variableResult = calculate(variable) as PathShiftForecastResult.Available
        val stableWidth = stableResult.estimatedUpperCount - stableResult.estimatedLowerCount
        val variableWidth = variableResult.estimatedUpperCount - variableResult.estimatedLowerCount
        assertTrue(variableWidth > stableWidth)
        assertTrue(stableResult.estimatedLowerCount >= 0)
    }

    @Test
    fun `materially future timestamp is rejected`() {
        val future = sufficientEarlyMoments() + PathShiftProtectedMoment(
            incidentToken = "future",
            occurredAtMillis = now.plusMinutes(6).toInstant().toEpochMilli(),
            sourceKind = AdaptiveSourceKind.App,
        )
        val result = calculate(future)
        assertEquals(
            PathShiftUnavailableReason.InvalidTimestamp,
            (result as PathShiftForecastResult.Unavailable).reason,
        )
    }

    @Test
    fun `common window needs three moments and thirty percent share`() {
        val belowMinimum = sufficientEarlyMoments().mapIndexed { index, item ->
            item.copy(
                occurredAtMillis = atDaysAgo(
                    days = listOf(27L, 24L, 20L, 16L, 12L, 8L, 2L)[index],
                    hour = index * 2,
                ),
            )
        }
        assertNull(
            (calculate(belowMinimum) as PathShiftForecastResult.Available)
                .factors.commonTimeWindow,
        )

        val common = sufficientEarlyMoments().mapIndexed { index, item ->
            item.copy(
                occurredAtMillis = atDaysAgo(
                    days = listOf(27L, 24L, 20L, 16L, 12L, 8L, 2L)[index],
                    hour = if (index < 3) 22 else index * 2,
                ),
            )
        }
        assertNotNull(
            (calculate(common) as PathShiftForecastResult.Available)
                .factors.commonTimeWindow,
        )
    }

    @Test
    fun `common window tie uses earliest stable bucket`() {
        val hours = listOf(8, 8, 8, 20, 20, 20, 14)
        val moments = sufficientEarlyMoments().mapIndexed { index, item ->
            item.copy(
                occurredAtMillis = atDaysAgo(
                    days = listOf(27L, 24L, 20L, 16L, 12L, 8L, 2L)[index],
                    hour = hours[index],
                ),
            )
        }
        val result = calculate(moments) as PathShiftForecastResult.Available
        assertEquals(8 * 60, result.factors.commonTimeWindow?.startMinuteInclusive)
    }

    @Test
    fun `DST boundary uses injected local calendar safely`() {
        val dstNow = ZonedDateTime.of(
            LocalDate.of(2026, 3, 30),
            LocalTime.NOON,
            zone,
        )
        val moments = listOf(27L, 24L, 20L, 16L, 12L, 8L, 2L).mapIndexed { index, days ->
            PathShiftProtectedMoment(
                incidentToken = "dst$index",
                occurredAtMillis = dstNow.minusDays(days)
                    .withHour(1)
                    .toInstant()
                    .toEpochMilli(),
                sourceKind = AdaptiveSourceKind.Website,
            )
        }
        val result = policy.calculate(
            PathShiftForecastInput(
                protectedMoments = moments,
                generatedAtMillis = dstNow.toInstant().toEpochMilli(),
                zoneId = zone,
            ),
        )
        assertTrue(result is PathShiftForecastResult.Available)
    }

    @Test
    fun `very large count is bounded and input history is not mutated`() {
        val moments = (0 until 2_000).map { index ->
            moment("large$index", 27L - (index % 28))
        }
        val copy = moments.toList()
        val result = calculate(moments) as PathShiftForecastResult.Available
        assertTrue(result.estimatedUpperCount <= PathShiftForecastPolicy.MaximumConsumerCount)
        assertEquals(copy, moments)
    }

    private fun calculate(
        moments: List<PathShiftProtectedMoment>,
    ): PathShiftForecastResult = policy.calculate(
        PathShiftForecastInput(
            protectedMoments = moments,
            generatedAtMillis = now.toInstant().toEpochMilli(),
            zoneId = zone,
        ),
    )

    private fun sufficientEarlyMoments(): List<PathShiftProtectedMoment> =
        listOf(27L, 24L, 20L, 16L, 12L, 8L, 2L).mapIndexed { index, days ->
            moment("root$index", days)
        }

    private fun moment(
        token: String,
        daysAgo: Long,
        source: AdaptiveSourceKind = AdaptiveSourceKind.App,
    ): PathShiftProtectedMoment = PathShiftProtectedMoment(
        incidentToken = token,
        occurredAtMillis = atDaysAgo(daysAgo, hour = 22),
        sourceKind = source,
    )

    private fun atDaysAgo(days: Long, hour: Int): Long =
        now.minusDays(days)
            .withHour(
                if (days == 0L) {
                    hour.coerceIn(0, now.hour - 1)
                } else {
                    hour.coerceIn(0, 23)
                },
            )
            .withMinute(15)
            .toInstant()
            .toEpochMilli()
}
