package com.impulsive.app.backend.domain.model.release

import java.time.LocalDateTime

enum class WindowOutcomeStatus {
    Used,
    Skipped,
}

data class WindowOutcomeRecord(
    val windowStart: LocalDateTime,
    val status: WindowOutcomeStatus,
    val recordedAt: LocalDateTime,
)
