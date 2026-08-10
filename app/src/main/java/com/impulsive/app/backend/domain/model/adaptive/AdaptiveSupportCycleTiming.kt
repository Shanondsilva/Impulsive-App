package com.impulsive.app.backend.domain.model.adaptive

/**
 * Authoritative timing constants for one protected Support Cycle.
 *
 * Every protected Support Cycle runs for the same fixed total duration. There
 * is no attempt-dependent ladder: a repeated protected Moment does not receive
 * a shorter pool than the first.
 *
 * The remaining-time thresholds below divide that fixed budget into the
 * protected Moment's phases. They live here so games, UI and policy all read
 * one source rather than each keeping a private clock or duplicated literal.
 */
object AdaptiveSupportCycleTiming {
    /** Total budget of every protected Support Cycle. */
    const val TotalDurationMillis: Long = 90_000L

    /** Remaining time at which dense gameplay gives way to settling. */
    const val SettlingStartsAtRemainingMillis: Long = 45_000L

    /** Remaining time at which settling gives way to the Moment Plan. */
    const val MomentPlanStartsAtRemainingMillis: Long = 20_000L
}
