package com.impulsive.app.backend.domain.model.tasks

import java.time.LocalDateTime

data class PatternBreakSession(
    val startedAt: LocalDateTime,
    val endedAt: LocalDateTime,
    val durationSec: Int,
    val score: Int,
    val accuracy: Int,
    val bestStreak: Int,
    val attempts: Int,
    val correctAnswers: Int,
    val validCompletion: Boolean,
    val rewardWaitReductionMinutes: Int,
    val rewardLevelPoints: Int,
    val wasFirstTimeReward: Boolean,
    val wasSameDayRepeat: Boolean,
    val appliedWaitReduction: Boolean,
)
