package com.impulsive.app.frontend.screens.onboarding

import com.impulsive.app.R

internal data class ReduceOption(
    val id: String,
    val label: String,
    val icon: OnboardingOptionIcon,
)

internal data class TriggerOption(
    val id: String,
    val label: String,
    val icon: OnboardingOptionIcon,
)

internal data class TimingOption(
    val id: String,
    val label: String,
    val icon: OnboardingOptionIcon,
)

internal data class StartingPointSummaryItem(
    val title: String,
    val value: String,
    val emphasized: Boolean = false,
)

internal enum class OnboardingOptionIcon {
    PrivateHabit,
    CompulsiveScrolling,
    LateNightPhone,
    BrowserHabit,
    SomethingElse,
    LateAtNight,
    RightAfterWaking,
    AloneOnPhone,
    WhenBored,
    WhenStressed,
    TroubleSleeping,
    SocialMedia,
    BrowserSearch,
    MemoryOrThought,
    BoredomTrigger,
    BeingAlone,
    StressTrigger,
    NoticeTriggers,
    CutDownLittle,
    DailyResetHabit,
    CutDownHalf,
    Shield,
    Incognito,
    Social,
    Loop,
    Moon,
    Search,
    Stress,
    Boredom,
    Lonely,
    Heart,
    Morning,
    Afternoon,
    Evening,
    Work,
    Person,
    Notice,
    Pause,
    Target,
    Boundary,
    CheckIn,
    Lock,
    Swipe,
    Globe,
    Add,
    Smartphone,
    SleepTrouble,
    Thought,
    SelfImprovement,
    Eye,
    TrendingDown,
    EventRepeat,
    PieChart,
}

internal val OnboardingOptionIcon.drawableResId: Int?
    get() = when (this) {
        OnboardingOptionIcon.PrivateHabit -> R.drawable.ic_private_habit
        OnboardingOptionIcon.CompulsiveScrolling -> R.drawable.ic_compulsive_scrolling
        OnboardingOptionIcon.LateNightPhone -> R.drawable.ic_late_night_phone
        OnboardingOptionIcon.BrowserHabit -> R.drawable.ic_browser_habit
        OnboardingOptionIcon.SomethingElse -> R.drawable.ic_something_else
        OnboardingOptionIcon.LateAtNight -> R.drawable.ic_late_at_night
        OnboardingOptionIcon.RightAfterWaking -> R.drawable.ic_right_after_waking
        OnboardingOptionIcon.AloneOnPhone -> R.drawable.ic_alone_on_phone
        OnboardingOptionIcon.WhenBored -> R.drawable.ic_when_bored
        OnboardingOptionIcon.WhenStressed -> R.drawable.ic_when_stressed
        OnboardingOptionIcon.TroubleSleeping -> R.drawable.ic_trouble_sleeping
        OnboardingOptionIcon.SocialMedia -> R.drawable.ic_social_media
        OnboardingOptionIcon.BrowserSearch -> R.drawable.ic_browser_search
        OnboardingOptionIcon.MemoryOrThought -> R.drawable.ic_memory_or_thought
        OnboardingOptionIcon.BoredomTrigger -> R.drawable.ic_boredom
        OnboardingOptionIcon.BeingAlone -> R.drawable.ic_being_alone
        OnboardingOptionIcon.StressTrigger -> R.drawable.ic_stress
        OnboardingOptionIcon.NoticeTriggers -> R.drawable.ic_notice_triggers
        OnboardingOptionIcon.CutDownLittle -> R.drawable.ic_cut_down_little
        OnboardingOptionIcon.DailyResetHabit -> R.drawable.ic_daily_reset_habit
        OnboardingOptionIcon.CutDownHalf -> R.drawable.ic_cut_down_half
        else -> null
    }

internal val ReduceOptions = listOf(
    ReduceOption(id = "private_habit", label = "A private habit", icon = OnboardingOptionIcon.PrivateHabit),
    ReduceOption(id = "compulsive_scrolling", label = "Compulsive scrolling", icon = OnboardingOptionIcon.CompulsiveScrolling),
    ReduceOption(id = "late_night_phone", label = "Late-night phone use", icon = OnboardingOptionIcon.LateNightPhone),
    ReduceOption(id = "browser_habit", label = "A browser habit", icon = OnboardingOptionIcon.BrowserHabit),
    ReduceOption(id = "something_else", label = "Something else", icon = OnboardingOptionIcon.SomethingElse),
)

internal val TriggerOptions = listOf(
    TriggerOption(id = "social_media", label = "Social media", icon = OnboardingOptionIcon.SocialMedia),
    TriggerOption(id = "browser_search", label = "A browser search", icon = OnboardingOptionIcon.BrowserSearch),
    TriggerOption(id = "memory_or_thought", label = "A memory or thought", icon = OnboardingOptionIcon.MemoryOrThought),
    TriggerOption(id = "boredom", label = "Boredom", icon = OnboardingOptionIcon.BoredomTrigger),
    TriggerOption(id = "being_alone", label = "Being alone", icon = OnboardingOptionIcon.BeingAlone),
    TriggerOption(id = "stress", label = "Stress", icon = OnboardingOptionIcon.StressTrigger),
)

internal val TimingOptions = listOf(
    TimingOption(id = "late_at_night", label = "Late at night", icon = OnboardingOptionIcon.LateAtNight),
    TimingOption(id = "right_after_waking", label = "Right after waking", icon = OnboardingOptionIcon.RightAfterWaking),
    TimingOption(id = "alone_on_phone", label = "Alone on my phone", icon = OnboardingOptionIcon.AloneOnPhone),
    TimingOption(id = "when_bored", label = "When bored", icon = OnboardingOptionIcon.WhenBored),
    TimingOption(id = "when_stressed", label = "When stressed", icon = OnboardingOptionIcon.WhenStressed),
    TimingOption(id = "trouble_sleeping", label = "Trouble sleeping", icon = OnboardingOptionIcon.TroubleSleeping),
)

internal val WeekOneOptions = listOf(
    ReduceOption(id = "notice_triggers", label = "Notice my cues", icon = OnboardingOptionIcon.NoticeTriggers),
    ReduceOption(id = "cut_down_a_little", label = "Cut down a little", icon = OnboardingOptionIcon.CutDownLittle),
    ReduceOption(id = "daily_reset_habit", label = "Build one daily reset habit", icon = OnboardingOptionIcon.DailyResetHabit),
    ReduceOption(id = "cut_down_by_half", label = "Cut down by half", icon = OnboardingOptionIcon.CutDownHalf),
)
