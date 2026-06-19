package com.impulsive.app.backend.data.repository

import android.content.Context
import com.impulsive.app.backend.data.local.preferences.FocusSessionDataSource
import com.impulsive.app.backend.domain.model.focus.FocusSessionPhase
import com.impulsive.app.backend.domain.model.focus.FocusSessionState
import com.impulsive.app.backend.domain.model.focus.newFocusSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime

class FocusSessionRepository(context: Context) {
    private val dataSource = FocusSessionDataSource(context)

    val session: Flow<FocusSessionState?> = dataSource.session

    /**
     * Starts a new session, unless a live one exists, in which case the live
     * session is returned unchanged. One session at a time by design.
     */
    suspend fun startSession(
        durationMinutes: Int,
        now: LocalDateTime = LocalDateTime.now(),
    ): FocusSessionState {
        val existing = dataSource.session.first()
        if (existing != null && existing.isLive) return existing
        val started = newFocusSession(durationMinutes = durationMinutes, now = now)
        dataSource.saveSession(started)
        return started
    }

    suspend fun pause(now: LocalDateTime = LocalDateTime.now()) {
        val current = dataSource.session.first() ?: return
        if (current.phase != FocusSessionPhase.Running) return
        dataSource.saveSession(current.copy(phase = FocusSessionPhase.Paused, pausedAt = now))
    }

    suspend fun resume(now: LocalDateTime = LocalDateTime.now()) {
        val current = dataSource.session.first() ?: return
        if (current.phase != FocusSessionPhase.Paused) return
        val pausedAt = current.pausedAt ?: now
        val pausedSeconds = Duration.between(pausedAt, now).seconds.coerceAtLeast(0L)
        dataSource.saveSession(
            current.copy(
                phase = FocusSessionPhase.Running,
                pausedAt = null,
                totalPausedSeconds = current.totalPausedSeconds + pausedSeconds,
            ),
        )
    }

    /** The clock keeps running; an interruption only increments the counter. */
    suspend fun recordInterruption() {
        val current = dataSource.session.first() ?: return
        if (!current.isLive) return
        dataSource.saveSession(current.copy(interruptionCount = current.interruptionCount + 1))
    }

    /**
     * Marks the session Completed if its time has fully elapsed. Returns the
     * completed session exactly once: subsequent calls return null so reward
     * granting cannot double-fire.
     */
    suspend fun completeIfElapsed(now: LocalDateTime = LocalDateTime.now()): FocusSessionState? =
        dataSource.completeIfElapsed(now)

    /** Ending early is calm and keeps the record; no punishment semantics. */
    suspend fun endEarly(now: LocalDateTime = LocalDateTime.now()) {
        val current = dataSource.session.first() ?: return
        if (!current.isLive) return
        dataSource.saveSession(current.copy(phase = FocusSessionPhase.EndedEarly, endedAt = now))
    }

    suspend fun clearFinishedSession() {
        val current = dataSource.session.first() ?: return
        if (current.isLive) return
        dataSource.clearSession()
    }
}
