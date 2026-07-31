package com.impulsive.app.pathshift

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSourceKind
import com.impulsive.app.backend.domain.pathshift.PathShiftForecastInput
import com.impulsive.app.backend.domain.pathshift.PathShiftForecastPolicy
import com.impulsive.app.backend.domain.pathshift.PathShiftForecastResult
import com.impulsive.app.backend.domain.pathshift.PathShiftProtectedMoment
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PathShiftForecastTimezoneInstrumentedTest {
    @Test
    fun DSTBoundaryUsesInjectedAndroidTimezone() {
        val zone = ZoneId.of("Europe/London")
        val now = ZonedDateTime.of(LocalDate.of(2026, 3, 30), LocalTime.NOON, zone)
        val moments = listOf(27L, 24L, 20L, 16L, 12L, 8L, 2L).mapIndexed { i, days ->
            PathShiftProtectedMoment(
                incidentToken = "incident-$i",
                occurredAtMillis = now.minusDays(days).withHour(10).toInstant().toEpochMilli(),
                sourceKind = AdaptiveSourceKind.App,
            )
        }
        assertTrue(
            PathShiftForecastPolicy().calculate(
                PathShiftForecastInput(
                    protectedMoments = moments,
                    generatedAtMillis = now.toInstant().toEpochMilli(),
                    zoneId = zone,
                ),
            ) is PathShiftForecastResult.Available,
        )
    }
}
