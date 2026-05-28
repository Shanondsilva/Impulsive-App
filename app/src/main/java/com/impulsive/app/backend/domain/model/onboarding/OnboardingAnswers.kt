package com.impulsive.app.backend.domain.model.onboarding

data class OnboardingAnswers(
    val name: String = "",
    val avatarId: String = "wave",
    val interrupting: List<String> = emptyList(),
    val timing: List<String> = emptyList(),
    val triggers: List<String> = emptyList(),
    val weekOneGoal: String? = null,
    val dailyRelapseUrgeCount: Int = 3,
    val activeDayStartMinute: Int = 7 * 60,
    val activeDayEndMinute: Int = 23 * 60,
    val plannedReleaseWindowMinutes: List<Int> = emptyList(),
)

data class OnboardingAnswerOption(
    val id: String,
    val label: String,
)

enum class OnboardingQuestionId {
    Interrupting,
    Timing,
    Triggers,
    WeekOneGoal,
}

enum class OnboardingSelectionMode {
    Multiple,
    Single,
}
