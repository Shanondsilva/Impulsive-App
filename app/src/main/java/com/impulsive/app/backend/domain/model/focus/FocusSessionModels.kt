package com.impulsive.app.backend.domain.model.focus

import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

const val MinFocusMinutes = 5
const val MaxFocusMinutes = 1440
val DefaultFocusDurationOptions = listOf(15, 25, 45, 60)

enum class FocusSessionPhase {
    Running,
    Paused,
    Completed,
    EndedEarly,
}

data class FocusSessionState(
    val sessionId: String,
    val durationMinutes: Int,
    val startedAt: LocalDateTime,
    val phase: FocusSessionPhase,
    val pausedAt: LocalDateTime? = null,
    val totalPausedSeconds: Long = 0L,
    val interruptionCount: Int = 0,
    val endedAt: LocalDateTime? = null,
) {
    val isLive: Boolean
        get() = phase == FocusSessionPhase.Running || phase == FocusSessionPhase.Paused
}

fun newFocusSession(
    durationMinutes: Int,
    now: LocalDateTime = LocalDateTime.now(),
): FocusSessionState = FocusSessionState(
    sessionId = UUID.randomUUID().toString(),
    durationMinutes = durationMinutes.coerceIn(MinFocusMinutes, MaxFocusMinutes),
    startedAt = now,
    phase = FocusSessionPhase.Running,
)

/**
 * Seconds of focus actually elapsed: wall time since start minus accumulated
 * pause time. While paused, the clock is frozen at the moment pause began.
 */
fun FocusSessionState.elapsedFocusSeconds(now: LocalDateTime): Long {
    val effectiveNow = if (phase == FocusSessionPhase.Paused && pausedAt != null) pausedAt else now
    val wallSeconds = Duration.between(startedAt, effectiveNow).seconds
    return (wallSeconds - totalPausedSeconds).coerceAtLeast(0L)
}

fun FocusSessionState.remainingSeconds(now: LocalDateTime): Long =
    (durationMinutes * 60L - elapsedFocusSeconds(now)).coerceAtLeast(0L)

fun FocusSessionState.isElapsed(now: LocalDateTime): Boolean =
    remainingSeconds(now) == 0L

/** Progress from 0f to 1f for progress bars. */
fun FocusSessionState.progressFraction(now: LocalDateTime): Float {
    val total = durationMinutes * 60f
    if (total <= 0f) return 1f
    return (elapsedFocusSeconds(now) / total).coerceIn(0f, 1f)
}

fun FocusSessionState.formattedRemaining(now: LocalDateTime): String {
    val remaining = remainingSeconds(now)
    return "%d:%02d".format(remaining / 60, remaining % 60)
}

/** 25 minutes = 40 LP. Linear in duration, floored and capped to stay sane. */
fun focusCompletionLevelPoints(durationMinutes: Int): Int =
    (durationMinutes * 8 / 5).coerceIn(20, 120)

/**
 * A completed focus session also logs a score that feeds the control-points total
 * and the recent timeline. 25 minutes = 500. Linear in duration, floored and capped.
 */
fun focusCompletionScore(durationMinutes: Int): Int =
    (durationMinutes * 20).coerceIn(100, 2000)
