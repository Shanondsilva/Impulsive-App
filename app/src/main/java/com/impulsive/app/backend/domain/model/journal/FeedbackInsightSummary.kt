package com.impulsive.app.backend.domain.model.journal

/** A short 7-day summary of feedback, shown only once enough days have data. */
data class FeedbackInsightSummary(
    val goodDays: Int,
    val windowDays: Int,
    val typicalTime: String?,
)

/**
 * Builds the insight summary from stored daily insights. Returns null until at least
 * seven days of feedback data exist, so nothing shows before then. Uses the most
 * recent seven days.
 */
fun buildFeedbackInsightSummary(insights: List<FeedbackDayInsight>): FeedbackInsightSummary? {
    if (insights.size < 7) return null
    val recent = insights.sortedBy { it.date }.takeLast(7)
    val goodDays = recent.count { it.feel == "positive" }
    val typicalTime = recent.mapNotNull { it.timeNote }
        .groupingBy { it }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key
    return FeedbackInsightSummary(
        goodDays = goodDays,
        windowDays = recent.size,
        typicalTime = typicalTime,
    )
}
