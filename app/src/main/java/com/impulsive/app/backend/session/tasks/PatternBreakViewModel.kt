package com.impulsive.app.backend.session.tasks

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.impulsive.app.backend.data.repository.PatternBreakSessionRepository
import com.impulsive.app.backend.data.repository.ScoreRepository
import com.impulsive.app.backend.domain.model.score.ScoreGameType
import com.impulsive.app.backend.domain.model.score.ScoreSessionOutcome
import com.impulsive.app.backend.domain.model.score.ScoreSessionRecord
import com.impulsive.app.backend.domain.model.tasks.PatternBreakSession
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class PatternBreakViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = PatternBreakSessionRepository(application)
    private val scoreRepository = ScoreRepository(application)

    fun saveSession(session: PatternBreakSession) {
        viewModelScope.launch {
            repository.saveSession(session)
            scoreRepository.recordSession(
                ScoreSessionRecord(
                    gameType = ScoreGameType.PatternBreak,
                    score = session.score.coerceAtLeast(0),
                    startedAt = session.startedAt,
                    completedAt = session.endedAt,
                    durationSec = session.durationSec.coerceAtLeast(0),
                    outcome = if (session.validCompletion) {
                        ScoreSessionOutcome.Completed
                    } else {
                        ScoreSessionOutcome.Abandoned
                    },
                    validCompletion = session.validCompletion,
                ),
            )
        }
    }
}
