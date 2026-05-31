package com.impulsive.app.backend.domain.model.protection

import com.impulsive.app.backend.domain.model.release.ReleasePlanDefaults
import com.impulsive.app.backend.domain.model.release.ReleasePlanState
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class ProtectionWindowSnapshot(
    val now: LocalDateTime,
    val nextWindowStart: LocalDateTime,
    val pausedWindowStart: LocalDateTime?,
    val pausedWindowEnd: LocalDateTime?,
    val timeUntilNextWindow: Duration,
) {
    val isProtectionPaused: Boolean = pausedWindowStart != null && pausedWindowEnd != null
}

object ProtectionWindowEvaluator {
    fun evaluate(
        now: LocalDateTime,
        releasePlan: ReleasePlanState,
        adjustedNextReleaseWindow: LocalDateTime?,
    ): ProtectionWindowSnapshot {
        val releaseWindowDuration = Duration.ofMinutes(ReleasePlanDefaults.ReleaseWindowMinutes)
        val adjustedWindowStart = adjustedNextReleaseWindow
            ?.takeIf { it.isBefore(releasePlan.nextReleaseWindow) && now.isBefore(it.plus(releaseWindowDuration)) }
        val effectiveNextWindowStart = adjustedWindowStart
            ?.takeIf { it.isAfter(now) }
            ?: releasePlan.nextReleaseWindow
        val candidateWindowStarts = buildList {
            addAll(releasePlan.plannedWindowsToday)
            adjustedWindowStart?.let(::add)
            add(releasePlan.nextReleaseWindow)
        }.distinct()
        val pausedWindowStart = candidateWindowStarts.firstOrNull { windowStart ->
            !now.isBefore(windowStart) && now.isBefore(windowStart.plus(releaseWindowDuration))
        }
        val pausedWindowEnd = pausedWindowStart?.plus(releaseWindowDuration)

        return ProtectionWindowSnapshot(
            now = now,
            nextWindowStart = if (pausedWindowStart == null) {
                effectiveNextWindowStart
            } else {
                releasePlan.nextReleaseWindow
            },
            pausedWindowStart = pausedWindowStart,
            pausedWindowEnd = pausedWindowEnd,
            timeUntilNextWindow = Duration.between(now, effectiveNextWindowStart).coerceAtLeast(Duration.ZERO),
        )
    }
}

fun LocalDateTime.toProtectionWindowKey(): String = toString()

fun LocalDateTime.toImpulsiveCompactTime(): String =
    toLocalTime().format(DateTimeFormatter.ofPattern("h:mm a"))

private fun Duration.coerceAtLeast(minimum: Duration): Duration =
    if (this < minimum) minimum else this
