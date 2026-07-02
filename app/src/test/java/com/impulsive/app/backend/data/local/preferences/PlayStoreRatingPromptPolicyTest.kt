package com.impulsive.app.backend.data.local.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayStoreRatingPromptPolicyTest {
    @Test
    fun firstUseDayStartsStreakWithoutEligibility() {
        val result =
            PlayStoreRatingPromptPolicy
                .recordUse(
                    previous =
                        PlayStoreRatingPromptState(),
                    currentEpochDay = 100L,
                    chanceRoll = 0,
                )

        assertEquals(
            1,
            result.consecutiveUseDays,
        )

        assertNull(
            result.eligiblePromptEpochDay,
        )
    }

    @Test
    fun secondConsecutiveDayDoesNotEvaluate() {
        val previous =
            PlayStoreRatingPromptState(
                lastUseEpochDay = 100L,
                consecutiveUseDays = 1,
            )

        val result =
            PlayStoreRatingPromptPolicy
                .recordUse(
                    previous = previous,
                    currentEpochDay = 101L,
                    chanceRoll = 0,
                )

        assertEquals(
            2,
            result.consecutiveUseDays,
        )

        assertNull(
            result
                .lastEligibilityCheckEpochDay,
        )

        assertNull(
            result.eligiblePromptEpochDay,
        )
    }

    @Test
    fun thirdConsecutiveDayRollBelowTwentyIsEligible() {
        val previous =
            PlayStoreRatingPromptState(
                lastUseEpochDay = 101L,
                consecutiveUseDays = 2,
            )

        val result =
            PlayStoreRatingPromptPolicy
                .recordUse(
                    previous = previous,
                    currentEpochDay = 102L,
                    chanceRoll = 19,
                )

        assertEquals(
            3,
            result.consecutiveUseDays,
        )

        assertEquals(
            102L,
            result
                .lastEligibilityCheckEpochDay,
        )

        assertEquals(
            102L,
            result
                .eligiblePromptEpochDay,
        )
    }

    @Test
    fun rollTwentyIsNotEligible() {
        val previous =
            PlayStoreRatingPromptState(
                lastUseEpochDay = 101L,
                consecutiveUseDays = 2,
            )

        val result =
            PlayStoreRatingPromptPolicy
                .recordUse(
                    previous = previous,
                    currentEpochDay = 102L,
                    chanceRoll = 20,
                )

        assertEquals(
            3,
            result.consecutiveUseDays,
        )

        assertEquals(
            102L,
            result
                .lastEligibilityCheckEpochDay,
        )

        assertNull(
            result.eligiblePromptEpochDay,
        )
    }

    @Test
    fun repeatedUseOnSameDayDoesNotChangeResult() {
        val previous =
            PlayStoreRatingPromptState(
                lastUseEpochDay = 102L,
                consecutiveUseDays = 3,
                lastEligibilityCheckEpochDay =
                    102L,
                eligiblePromptEpochDay =
                    102L,
            )

        val result =
            PlayStoreRatingPromptPolicy
                .recordUse(
                    previous = previous,
                    currentEpochDay = 102L,
                    chanceRoll = 99,
                )

        assertEquals(
            previous,
            result,
        )
    }

    @Test
    fun missedDayResetsStreak() {
        val previous =
            PlayStoreRatingPromptState(
                lastUseEpochDay = 100L,
                consecutiveUseDays = 5,
                eligiblePromptEpochDay =
                    100L,
            )

        val result =
            PlayStoreRatingPromptPolicy
                .recordUse(
                    previous = previous,
                    currentEpochDay = 102L,
                    chanceRoll = 0,
                )

        assertEquals(
            1,
            result.consecutiveUseDays,
        )

        assertNull(
            result.eligiblePromptEpochDay,
        )
    }

    @Test
    fun showLaterBlocksForSevenDays() {
        val eligible =
            PlayStoreRatingPromptState(
                lastUseEpochDay = 102L,
                consecutiveUseDays = 3,
                eligiblePromptEpochDay =
                    102L,
            )

        val snoozed =
            PlayStoreRatingPromptPolicy
                .showLater(
                    previous = eligible,
                    currentEpochDay = 102L,
                )

        assertEquals(
            109L,
            snoozed
                .snoozedUntilEpochDay,
        )

        assertNull(
            snoozed.eligiblePromptEpochDay,
        )

        val blocked =
            PlayStoreRatingPromptPolicy
                .recordUse(
                    previous = snoozed,
                    currentEpochDay = 108L,
                    chanceRoll = 0,
                )

        assertNull(
            blocked.eligiblePromptEpochDay,
        )
    }

    @Test
    fun snoozeAllowsEvaluationOnSeventhDay() {
        val previous =
            PlayStoreRatingPromptState(
                lastUseEpochDay = 108L,
                consecutiveUseDays = 9,
                snoozedUntilEpochDay =
                    109L,
            )

        val result =
            PlayStoreRatingPromptPolicy
                .recordUse(
                    previous = previous,
                    currentEpochDay = 109L,
                    chanceRoll = 0,
                )

        assertEquals(
            109L,
            result
                .eligiblePromptEpochDay,
        )
    }

    @Test
    fun neverShowAgainSuppressesEligibility() {
        val suppressed =
            PlayStoreRatingPromptPolicy
                .neverShowAgain(
                    previous =
                        PlayStoreRatingPromptState(
                            lastUseEpochDay =
                                101L,
                            consecutiveUseDays =
                                2,
                            eligiblePromptEpochDay =
                                101L,
                        ),
                )

        assertTrue(
            suppressed.neverShowAgain,
        )

        assertTrue(
            suppressed
                .isPermanentlySuppressed,
        )

        val result =
            PlayStoreRatingPromptPolicy
                .recordUse(
                    previous = suppressed,
                    currentEpochDay = 102L,
                    chanceRoll = 0,
                )

        assertNull(
            result.eligiblePromptEpochDay,
        )
    }

    @Test
    fun ratedActionSuppressesFutureEligibility() {
        val rated =
            PlayStoreRatingPromptPolicy
                .ratedOnPlayStore(
                    previous =
                        PlayStoreRatingPromptState(
                            eligiblePromptEpochDay =
                                102L,
                        ),
                )

        assertTrue(
            rated.ratedOnPlayStore,
        )

        assertTrue(
            rated
                .isPermanentlySuppressed,
        )

        assertFalse(
            rated.isEligibleOn(102L),
        )
    }
}
