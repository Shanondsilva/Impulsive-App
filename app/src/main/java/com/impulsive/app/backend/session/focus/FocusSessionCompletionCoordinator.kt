package com.impulsive.app.backend.session.focus

import android.content.Context
import com.impulsive.app.backend.data.repository.FocusSessionRepository
import com.impulsive.app.backend.data.repository.ScoreRepository
import com.impulsive.app.backend.data.repository.TaskRewardRepository
import com.impulsive.app.backend.domain.model.focus.FocusSessionState
import com.impulsive.app.backend.domain.model.focus.focusCompletionScore
import com.impulsive.app.backend.domain.model.score.ScoreGameType
import com.impulsive.app.backend.domain.model.score.ScoreSessionOutcome
import com.impulsive.app.backend.domain.model.score.ScoreSessionRecord
import java.time.LocalDateTime
import java.time.ZoneId

class FocusSessionCompletionCoordinator(
    private val focusSessionRepository: FocusSessionRepository,
    private val taskRewardRepository: TaskRewardRepository,
    private val scoreRepository: ScoreRepository,
) {
    constructor(context: Context) : this(
        focusSessionRepository = FocusSessionRepository(context),
        taskRewardRepository = TaskRewardRepository(context),
        scoreRepository = ScoreRepository(context),
    )

    suspend fun completeIfElapsed(
        now: LocalDateTime = LocalDateTime.now(),
    ): FocusSessionState? {
        val completed = focusSessionRepository.completeIfElapsed(now) ?: return null
        val completedAt = completed.endedAt ?: now
        taskRewardRepository.awardFocusTimePointsIfEligible(
            focusSessionId = completed.sessionId,
            completedAtMillis = completedAt.toEpochMillisInUserZone(),
        )
        scoreRepository.recordSession(
            ScoreSessionRecord(
                gameType = ScoreGameType.FocusSession,
                score = focusCompletionScore(completed.durationMinutes),
                startedAt = completed.startedAt,
                completedAt = completed.endedAt ?: now,
                durationSec = completed.durationMinutes * 60,
                outcome = ScoreSessionOutcome.Completed,
                validCompletion = true,
            ),
        )
        return completed
    }

    private fun LocalDateTime.toEpochMillisInUserZone(): Long =
        atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
}
