package com.impulsive.app.core.util

import java.time.LocalTime

enum class ThemeMode { Light, Dark, System, AsPerTime }

enum class TimeOfDay { Morning, Afternoon, Evening, Night }

/** Morning 5..11, Afternoon 12..16, Evening 17..20, Night 21..4. */
fun timeOfDayForHour(hour: Int): TimeOfDay = when (hour) {
    in 5..11 -> TimeOfDay.Morning
    in 12..16 -> TimeOfDay.Afternoon
    in 17..20 -> TimeOfDay.Evening
    else -> TimeOfDay.Night
}

/** Greeting prefix for the given hour. */
fun greetingForHour(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..20 -> "Good evening"
    else -> "Good night"
}

fun currentGreeting(): String = greetingForHour(LocalTime.now().hour)

/** Which scene time to show, given the theme mode. */
fun resolveSceneTime(mode: ThemeMode, systemInDark: Boolean): TimeOfDay = when (mode) {
    ThemeMode.Light -> TimeOfDay.Afternoon
    ThemeMode.Dark -> TimeOfDay.Night
    ThemeMode.System -> if (systemInDark) TimeOfDay.Night else TimeOfDay.Afternoon
    ThemeMode.AsPerTime -> timeOfDayForHour(LocalTime.now().hour)
}

/** Whether the dark UI theme should apply, given the theme mode. */
fun shouldUseDarkTheme(mode: ThemeMode, systemInDark: Boolean): Boolean = when (mode) {
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
    ThemeMode.System -> systemInDark
    ThemeMode.AsPerTime -> {
        val h = LocalTime.now().hour
        h >= 21 || h < 5
    }
}
