package com.impulsive.app.util

import java.util.Calendar

object WeekUtil {

    /**
     * Returns the epoch millis of Monday 00:00:00.000 for the current week (local time).
     */
    fun currentWeekStart(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        // DAY_OF_WEEK: 1=Sun, 2=Mon, ... 7=Sat
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1 (Sun) – 7 (Sat)
        val daysFromMonday = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - Calendar.MONDAY
        cal.add(Calendar.DATE, -daysFromMonday)
        return cal.timeInMillis
    }

    /**
     * Returns the epoch millis of Monday 00:00 for the week that contains [timestampMs].
     */
    fun weekStartFor(timestampMs: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestampMs
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val daysFromMonday = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - Calendar.MONDAY
        cal.add(Calendar.DATE, -daysFromMonday)
        return cal.timeInMillis
    }

    /**
     * Returns the epoch millis for the NEXT Monday 00:00 after the given week start.
     */
    fun nextWeekStart(currentWeekStart: Long): Long =
        currentWeekStart + 7L * 24 * 60 * 60 * 1000

    /**
     * Returns true if today is Sunday.
     */
    fun isSunday(): Boolean =
        Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY

    /**
     * Calculates the delay in ms until the next Sunday at [hour]:00 local time.
     * If today is Sunday but the time has already passed, returns delay until NEXT Sunday.
     */
    fun msUntilNextSunday(hour: Int = 20): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance()
        target.set(Calendar.HOUR_OF_DAY, hour)
        target.set(Calendar.MINUTE, 0)
        target.set(Calendar.SECOND, 0)
        target.set(Calendar.MILLISECOND, 0)

        // Roll forward to the next Sunday
        val daysUntilSunday = (Calendar.SUNDAY - target.get(Calendar.DAY_OF_WEEK) + 7) % 7
        target.add(Calendar.DATE, if (daysUntilSunday == 0 && target.before(now)) 7 else daysUntilSunday)
        return maxOf(0L, target.timeInMillis - now.timeInMillis)
    }
}
