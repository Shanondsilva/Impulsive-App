package com.impulsive.app.backend.domain.model.journal

import java.time.LocalDate

/** A single day's analysed feedback, stored once per day. */
data class FeedbackDayInsight(
    val date: String,
    val feel: String,
    val timeNote: String?,
    val text: String,
)

/**
 * Reads a feedback note conservatively. It detects a rough good or hard feel from a
 * small word list and a time of day if one was written. It never invents anything;
 * unclear parts are left blank and the person's own words are kept as is.
 */
object FeedbackAnalyzer {

    private val positiveWords = setOf(
        "good", "great", "helped", "help", "better", "progress", "proud", "calm",
        "easier", "managed", "manageable", "win", "clean", "resisted", "stepped",
    )
    private val negativeWords = setOf(
        "hard", "harder", "tough", "struggled", "struggle", "slip", "slipped",
        "relapse", "worse", "bad", "urge", "urges", "failed", "craving",
    )
    private val timeRegex = Regex(
        "\\b([01]?\\d|2[0-3])[:.][0-5]\\d\\b|\\b\\d{1,2}\\s?(am|pm)\\b",
        RegexOption.IGNORE_CASE,
    )

    fun analyze(date: LocalDate, body: String): FeedbackDayInsight {
        val lower = body.lowercase()
        val positives = positiveWords.count { lower.contains(it) }
        val negatives = negativeWords.count { lower.contains(it) }
        val feel = when {
            positives > negatives -> "positive"
            negatives > positives -> "negative"
            else -> "neutral"
        }
        val time = timeRegex.find(body)?.value
        return FeedbackDayInsight(
            date = date.toString(),
            feel = feel,
            timeNote = time,
            text = body.trim(),
        )
    }
}
