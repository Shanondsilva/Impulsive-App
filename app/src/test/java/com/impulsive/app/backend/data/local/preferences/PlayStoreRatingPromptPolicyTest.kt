package com.impulsive.app.backend.data.local.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayStoreRatingPromptPolicyTest {
    @Test
    fun firstUseDayStartsStreakWithoutEligibility() {
        val result = PlayStoreRatingPromptPolicy.recordUse(
            previous = PlayStoreRatingPromptState(),
            currentEpochDay = 100L,
            chanceRoll = 0,
        )

        assertEquals(1, result.consecutiveUseDays)
        assertNull(result.eligiblePromptEpochDay)
    }

    @Test
    fun secondConsecutiveDayDoesNotEvaluate() {
        val result = PlayStoreRatingPromptPolicy.recordUse(
            previous = PlayStoreRatingPromptState(
                lastUseEpochDay = 100L,
                consecutiveUseDays = 1,
            ),
            currentEpochDay = 101L,
            chanceRoll = 0,
        )

        assertEquals(2, result.consecutiveUseDays)
        assertNull(result.lastEligibilityCheckEpochDay)
        assertNull(result.eligiblePromptEpochDay)
    }

    @Test
    fun thirdConsecutiveDayRollBelowTwentyIsEligible() {
        val result = PlayStoreRatingPromptPolicy.recordUse(
            previous = PlayStoreRatingPromptState(
                lastUseEpochDay = 101L,
                consecutiveUseDays = 2,
            ),
            currentEpochDay = 102L,
            chanceRoll = 19,
        )

        assertEquals(3, result.consecutiveUseDays)
        assertEquals(102L, result.lastEligibilityCheckEpochDay)
        assertEquals(102L, result.eligiblePromptEpochDay)
    }

    @Test
    fun rollTwentyIsNotEligible() {
        val result = PlayStoreRatingPromptPolicy.recordUse(
            previous = PlayStoreRatingPromptState(
                lastUseEpochDay = 101L,
                consecutiveUseDays = 2,
            ),
            currentEpochDay = 102L,
            chanceRoll = 20,
        )

        assertEquals(3, result.consecutiveUseDays)
        assertEquals(102L, result.lastEligibilityCheckEpochDay)
        assertNull(result.eligiblePromptEpochDay)
    }

    @Test
    fun repeatedUseOnSameDayDoesNotChangeResult() {
        val previous = PlayStoreRatingPromptState(
            lastUseEpochDay = 102L,
            consecutiveUseDays = 3,
            lastEligibilityCheckEpochDay = 102L,
            eligiblePromptEpochDay = 102L,
        )

        assertEquals(
            previous,
            PlayStoreRatingPromptPolicy.recordUse(
                previous = previous,
                currentEpochDay = 102L,
                chanceRoll = 99,
            ),
        )
    }

    @Test
    fun missedDayResetsStreak() {
        val result = PlayStoreRatingPromptPolicy.recordUse(
            previous = PlayStoreRatingPromptState(
                lastUseEpochDay = 100L,
                consecutiveUseDays = 5,
                eligiblePromptEpochDay = 100L,
            ),
            currentEpochDay = 102L,
            chanceRoll = 0,
        )

        assertEquals(1, result.consecutiveUseDays)
        assertNull(result.eligiblePromptEpochDay)
    }

    @Test
    fun legacySnoozeRemainsRespected() {
        val previous = PlayStoreRatingPromptState(
            lastUseEpochDay = 108L,
            consecutiveUseDays = 9,
            snoozedUntilEpochDay = 109L,
        )

        assertFalse(previous.isEligibleOn(108L))
        val result = PlayStoreRatingPromptPolicy.recordUse(
            previous = previous,
            currentEpochDay = 109L,
            chanceRoll = 0,
        )
        assertEquals(109L, result.eligiblePromptEpochDay)
    }

    @Test
    fun legacyNeverShowSettingSuppressesAutomaticReview() {
        val previous = PlayStoreRatingPromptState(
            lastUseEpochDay = 101L,
            consecutiveUseDays = 2,
            eligiblePromptEpochDay = 102L,
            neverShowAgain = true,
        )

        assertTrue(previous.isPermanentlySuppressed)
        assertFalse(previous.isEligibleOn(102L))
        assertNull(
            PlayStoreRatingPromptPolicy.recordUse(
                previous = previous,
                currentEpochDay = 102L,
                chanceRoll = 0,
            ).eligiblePromptEpochDay,
        )
    }

    @Test
    fun legacyRatedSettingSuppressesAutomaticReview() {
        val previous = PlayStoreRatingPromptState(
            lastUseEpochDay = 101L,
            consecutiveUseDays = 2,
            eligiblePromptEpochDay = 102L,
            ratedOnPlayStore = true,
        )

        assertTrue(previous.isPermanentlySuppressed)
        assertFalse(previous.isEligibleOn(102L))
        assertNull(
            PlayStoreRatingPromptPolicy.recordUse(
                previous = previous,
                currentEpochDay = 102L,
                chanceRoll = 0,
            ).eligiblePromptEpochDay,
        )
    }

    @Test
    fun consumingEligibilityRecordsRequestDay() {
        val result = PlayStoreRatingPromptPolicy.consumeInAppReviewEligibility(
            previous = PlayStoreRatingPromptState(
                eligiblePromptEpochDay = 200L,
            ),
            currentEpochDay = 200L,
        )

        assertNotNull(result)
        assertNull(result?.eligiblePromptEpochDay)
        assertEquals(200L, result?.lastInAppReviewRequestEpochDay)
        assertFalse(result?.neverShowAgain ?: true)
        assertFalse(result?.ratedOnPlayStore ?: true)
    }

    @Test
    fun ineligibleStateCannotBeConsumed() {
        assertNull(
            PlayStoreRatingPromptPolicy.consumeInAppReviewEligibility(
                previous = PlayStoreRatingPromptState(
                    eligiblePromptEpochDay = 200L,
                ),
                currentEpochDay = 201L,
            ),
        )
    }

    @Test
    fun consumedRequestBlocksAnotherRequestDuringCooldown() {
        val currentEpochDay =
            200L + PlayStoreRatingPromptPolicy.MinimumDaysBetweenRequests - 1L
        val previous = PlayStoreRatingPromptState(
            eligiblePromptEpochDay = currentEpochDay,
            lastInAppReviewRequestEpochDay = 200L,
        )

        assertTrue(previous.isRequestCoolingDownOn(currentEpochDay))
        assertFalse(previous.isEligibleOn(currentEpochDay))
    }

    @Test
    fun requestCanBeEvaluatedAtCooldownBoundary() {
        val currentEpochDay =
            200L + PlayStoreRatingPromptPolicy.MinimumDaysBetweenRequests
        val result = PlayStoreRatingPromptPolicy.recordUse(
            previous = PlayStoreRatingPromptState(
                lastUseEpochDay = currentEpochDay - 1L,
                consecutiveUseDays = 20,
                lastInAppReviewRequestEpochDay = 200L,
            ),
            currentEpochDay = currentEpochDay,
            chanceRoll = 0,
        )

        assertEquals(currentEpochDay, result.eligiblePromptEpochDay)
    }

    @Test
    fun clockRollbackRemainsBlocked() {
        assertTrue(
            PlayStoreRatingPromptState(
                lastInAppReviewRequestEpochDay = 200L,
            ).isRequestCoolingDownOn(199L),
        )
    }

    @Test
    fun eligibilityHelperIncludesCooldown() {
        assertFalse(
            PlayStoreRatingPromptState(
                eligiblePromptEpochDay = 201L,
                lastInAppReviewRequestEpochDay = 200L,
            ).isEligibleOn(201L),
        )
    }
}
