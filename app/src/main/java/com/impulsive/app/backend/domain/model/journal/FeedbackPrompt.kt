package com.impulsive.app.backend.domain.model.journal

import java.time.LocalDate

/**
 * The end-of-day feedback question set. One question is shown per day, rotating, and
 * each has exactly two gentle answers, the first being the better day, the second an
 * honest harder day.
 */
object FeedbackPrompt {

    data class DailyQuestion(
        val question: String,
        val positiveAnswer: String,
        val honestAnswer: String,
    )

    private val questions = listOf(
        DailyQuestion("Did today feel easier or harder than usual?", "Easier", "Harder"),
        DailyQuestion("Did Impulsive help you today?", "Yes, it helped", "Not really"),
        DailyQuestion("How were your urges today?", "Manageable", "Strong"),
        DailyQuestion("Did you step away when you wanted to?", "Yes, I did", "I struggled"),
        DailyQuestion("How did today feel overall?", "Good day", "Tough day"),
        DailyQuestion("Did you make a little progress today?", "Yes", "Not today"),
        DailyQuestion("Were you proud of one thing today?", "Yes", "Not today"),
    )

    val count: Int get() = questions.size

    fun indexForDate(date: LocalDate = LocalDate.now()): Int =
        (date.toEpochDay() % questions.size).toInt()

    fun questionAt(index: Int): DailyQuestion =
        questions[index.coerceIn(0, questions.size - 1)]

    /** Builds the body text saved into the feedback note from a tapped answer. */
    fun answerNoteBody(questionIndex: Int, answerIndex: Int): String {
        val q = questionAt(questionIndex)
        val answer = if (answerIndex == 0) q.positiveAnswer else q.honestAnswer
        return q.question + "\n" + answer
    }
}
