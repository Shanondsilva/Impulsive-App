package com.impulsive.app.backend.session.game

/**
 * Arithmetic for support-cycle time that survives process death.
 *
 * Pure Kotlin and deliberately strict: an impossible checkpoint fails closed
 * rather than being coerced, so a corrupt value can never hand the player extra
 * support time.
 */
internal object SnakeSupportElapsedPolicy {

    /** Time still available to play after honouring an earlier checkpoint. */
    fun remainingAfterCheckpoint(
        authoritativeAvailableMillis: Long,
        checkpointMillis: Long,
    ): Long {
        require(authoritativeAvailableMillis > 0L) {
            "authoritativeAvailableMillis must be positive"
        }
        require(checkpointMillis >= 0L) { "checkpointMillis must not be negative" }
        require(checkpointMillis <= authoritativeAvailableMillis) {
            "checkpoint claims more consumed time than the allocation"
        }

        return authoritativeAvailableMillis - checkpointMillis
    }

    /**
     * Total support time consumed: the pre-process-death baseline plus the
     * current board attempt. This is reported to the support cycle, and is
     * deliberately not the value used for Snake completion validity.
     */
    fun totalConsumed(
        checkpointMillis: Long,
        currentRoundElapsedMillis: Long,
        authoritativeAvailableMillis: Long,
    ): Long {
        require(checkpointMillis >= 0L) { "checkpointMillis must not be negative" }
        require(currentRoundElapsedMillis >= 0L) {
            "currentRoundElapsedMillis must not be negative"
        }
        require(authoritativeAvailableMillis > 0L) {
            "authoritativeAvailableMillis must be positive"
        }
        require(checkpointMillis <= authoritativeAvailableMillis) {
            "checkpoint claims more consumed time than the allocation"
        }

        val total = Math.addExact(checkpointMillis, currentRoundElapsedMillis)

        require(total <= authoritativeAvailableMillis) {
            "consumed support time cannot exceed the allocation"
        }

        return total
    }
}
