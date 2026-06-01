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

/** Greeting prefix — derived from timeOfDayForHour so boundaries stay in sync. */
fun greetingForHour(hour: Int): String = when (timeOfDayForHour(hour)) {
    TimeOfDay.Morning -> "Good morning"
    TimeOfDay.Afternoon -> "Good afternoon"
    TimeOfDay.Evening -> "Good evening"
    TimeOfDay.Night -> "Good night"
}

fun currentGreeting(): String = greetingForHour(LocalTime.now().hour)

/** Which scene time to show, given the theme mode.
 *  [currentHour] should come from the same ticking source as [shouldUseDarkTheme] so
 *  the two helpers always agree when AsPerTime is active. */
fun resolveSceneTime(mode: ThemeMode, systemInDark: Boolean, currentHour: Int = LocalTime.now().hour): TimeOfDay = when (mode) {
    ThemeMode.Light -> TimeOfDay.Afternoon
    ThemeMode.Dark -> TimeOfDay.Night
    ThemeMode.System -> if (systemInDark) TimeOfDay.Night else TimeOfDay.Afternoon
    ThemeMode.AsPerTime -> timeOfDayForHour(currentHour)
}

/** Whether the dark UI theme should apply, given the theme mode.
 *  [currentHour] should come from a single ticking source hoisted in MainActivity so
 *  the value stays consistent across recompositions and re-evaluates at minute boundaries. */
fun shouldUseDarkTheme(mode: ThemeMode, systemInDark: Boolean, currentHour: Int = LocalTime.now().hour): Boolean = when (mode) {
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
    ThemeMode.System -> systemInDark
    ThemeMode.AsPerTime -> currentHour >= 21 || currentHour < 5
}
