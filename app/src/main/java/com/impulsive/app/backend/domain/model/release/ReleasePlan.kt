package com.impulsive.app.backend.domain.model.release

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

data class ReleasePlanState(
    val selectedDailyUrgeCount: Int,
    val activeDayStart: LocalTime,
    val activeDayEnd: LocalTime,
    val plannedWindowsToday: List<LocalDateTime>,
    val nextReleaseWindow: LocalDateTime,
    val adjustedNextReleaseWindow: LocalDateTime,
    val timeUntilNextReleaseWindow: Duration,
    val isInsideReleaseWindow: Boolean,
    val currentWindowIndex: Int?,
)

object ReleasePlanDefaults {
    val ActiveDayStart: LocalTime = LocalTime.of(7, 0)
    val ActiveDayEnd: LocalTime = LocalTime.of(23, 0)
    const val ReleaseWindowMinutes = 15L
    const val MinimumWakeBufferMinutes = 60L
    const val MinimumSleepBufferMinutes = 90L
}

fun calculateReleasePlan(
    selectedDailyUrgeCount: Int,
    now: LocalDateTime = LocalDateTime.now(),
    activeDayStart: LocalTime = ReleasePlanDefaults.ActiveDayStart,
    activeDayEnd: LocalTime = ReleasePlanDefaults.ActiveDayEnd,
    releaseWindowDuration: Duration = Duration.ofMinutes(ReleasePlanDefaults.ReleaseWindowMinutes),
): ReleasePlanState {
    val count = selectedDailyUrgeCount.coerceAtLeast(1)
    val today = now.toLocalDate()
    val plannedWindowsToday = plannedWindowsForDate(
        date = today,
        count = count,
        activeDayStart = activeDayStart,
        activeDayEnd = activeDayEnd,
    )
    val currentWindowIndex = plannedWindowsToday.indexOfFirst { windowStart ->
        !now.isBefore(windowStart) && now.isBefore(windowStart.plus(releaseWindowDuration))
    }.takeIf { it >= 0 }
    val nextToday = plannedWindowsToday.firstOrNull { it.isAfter(now) }
    val nextWindow = nextToday ?: plannedWindowsForDate(
        date = today.plusDays(1),
        count = count,
        activeDayStart = activeDayStart,
        activeDayEnd = activeDayEnd,
    ).first()

    return ReleasePlanState(
        selectedDailyUrgeCount = count,
        activeDayStart = activeDayStart,
        activeDayEnd = activeDayEnd,
        plannedWindowsToday = plannedWindowsToday,
        nextReleaseWindow = nextWindow,
        adjustedNextReleaseWindow = nextWindow,
        timeUntilNextReleaseWindow = Duration.between(now, nextWindow).coerceAtLeast(Duration.ZERO),
        isInsideReleaseWindow = currentWindowIndex != null,
        currentWindowIndex = currentWindowIndex,
    )
}

fun plannedWindowsForDate(
    date: LocalDate,
    count: Int,
    activeDayStart: LocalTime = ReleasePlanDefaults.ActiveDayStart,
    activeDayEnd: LocalTime = ReleasePlanDefaults.ActiveDayEnd,
): List<LocalDateTime> {
    val activeStart = LocalDateTime.of(date, activeDayStart)
    val activeEnd = activeEndForDate(date, activeDayStart, activeDayEnd)
    val activeMinutes = Duration.between(activeStart, activeEnd).toMinutes().coerceAtLeast(1L)
    val boundedCount = count.coerceAtLeast(1)
    val wakeBuffer = ReleasePlanDefaults.MinimumWakeBufferMinutes
    val sleepBuffer = ReleasePlanDefaults.MinimumSleepBufferMinutes
    val earliest = activeStart.plusMinutes(wakeBuffer)
    val latest = activeEnd.minusMinutes(sleepBuffer)

    if (boundedCount == 1 || !latest.isAfter(earliest)) {
        return listOf(roundToNearestFive(activeStart.plusMinutes(activeMinutes / 2)))
    }

    val comfortableStart = activeStart.plusMinutes(maxOf(wakeBuffer, 90L))
    val comfortableEnd = activeEnd.minusMinutes(maxOf(sleepBuffer, 210L))
    val start = if (comfortableEnd.isAfter(comfortableStart)) comfortableStart else earliest
    val end = if (comfortableEnd.isAfter(comfortableStart)) comfortableEnd else latest
    val spanMinutes = Duration.between(start, end).toMinutes().coerceAtLeast(1L)
    val step = spanMinutes.toDouble() / (boundedCount - 1).toDouble()

    return List(boundedCount) { index ->
        val minutesFromStart = (step * index).roundToInt().toLong()
        roundToNearestFive(start.plusMinutes(minutesFromStart))
            .coerceInDateTime(earliest, latest)
    }
}

fun LocalTime.toMinuteOfDay(): Int = hour * 60 + minute

fun minuteOfDayToLocalTime(minuteOfDay: Int): LocalTime {
    val minute = minuteOfDay.coerceIn(0, 24 * 60 - 1)
    return LocalTime.of(minute / 60, minute % 60)
}

fun ReleasePlanState.formattedPlannedWindows(): List<String> =
    plannedWindowsToday.map { it.toLocalTime().formatReleaseTime() }

fun ReleasePlanState.formattedTimeUntilNextWindow(): String =
    "Next window in ${timeUntilNextReleaseWindow.formatCompactDuration()}"

fun ReleasePlanState.formattedTodaysWindow(): String =
    "Window time: ${ReleasePlanDefaults.ReleaseWindowMinutes} mins"

private fun activeEndForDate(
    date: LocalDate,
    activeDayStart: LocalTime,
    activeDayEnd: LocalTime,
): LocalDateTime {
    val endDate = if (activeDayEnd.isAfter(activeDayStart)) date else date.plusDays(1)
    return LocalDateTime.of(endDate, activeDayEnd)
}

private fun roundToNearestFive(dateTime: LocalDateTime): LocalDateTime {
    val minute = dateTime.minute
    val roundedMinute = ((minute / 5.0).roundToInt() * 5)
    val withoutSeconds = dateTime.withSecond(0).withNano(0)
    return if (roundedMinute == 60) {
        withoutSeconds.plusHours(1).withMinute(0)
    } else {
        withoutSeconds.withMinute(roundedMinute)
    }
}

private fun LocalDateTime.coerceInDateTime(
    minimum: LocalDateTime,
    maximum: LocalDateTime,
): LocalDateTime = when {
    isBefore(minimum) -> minimum
    isAfter(maximum) -> maximum
    else -> this
}

private fun Duration.coerceAtLeast(minimum: Duration): Duration =
    if (this < minimum) minimum else this

private fun Duration.formatCompactDuration(): String {
    val totalMinutes = toMinutes().coerceAtLeast(0L)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}

private fun LocalTime.formatReleaseTime(): String =
    format(DateTimeFormatter.ofPattern("h:mm a"))
