package com.impulsive.app.backend.domain.engine.adaptive

enum class AdaptiveHistoryRetentionPolicy(
    val consumerLabel: String,
    private val retentionDays: Long?,
) {
    NinetyDays("90 days", 90L),
    SixMonths("6 months", 183L),
    OneYear("1 year", 365L),
    KeepUntilReset("Keep until I reset it", null),
    ;

    fun cutoffMillis(nowMillis: Long): Long? {
        if (nowMillis < 0L) return null
        val days = retentionDays ?: return null
        val duration = days * MillisPerDay
        return (nowMillis - duration).coerceAtLeast(0L)
    }

    private companion object {
        const val MillisPerDay = 86_400_000L
    }
}
