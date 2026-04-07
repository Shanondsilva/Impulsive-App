package com.impulsive.app.engine

/**
 * Pure stateless tapering logic. No Android dependencies — fully unit-testable.
 *
 * Business rules:
 * - Target decrements by 1 each week, floor is 1 (never zero)
 * - A taper is only suggested when the user stayed at or under their limit
 * - If the user exceeded their limit, we hold steady (stall) this week
 */
object TaperingEngine {

    /**
     * Returns the recommended session limit for the next week.
     * Floor is always 1 — we never suggest zero.
     */
    fun calculateNextTarget(currentAllowed: Int): Int =
        maxOf(1, currentAllowed - 1)

    /**
     * Returns true if the taper should be suggested.
     * Only recommended when usedSessions <= allowedSessions (user did not exceed limit).
     */
    fun shouldTaper(usedSessions: Int, allowedSessions: Int): Boolean =
        usedSessions <= allowedSessions

    /**
     * Convenience: returns the next target if tapering, or the same target if stalling.
     */
    fun resolveNextTarget(currentAllowed: Int, usedSessions: Int): Int =
        if (shouldTaper(usedSessions, currentAllowed))
            calculateNextTarget(currentAllowed)
        else
            currentAllowed
}
