package com.impulsive.app.frontend.settings

import com.impulsive.app.frontend.screens.settings.familiarStepHistorySummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FamiliarStepHistorySummaryTest {
    @Test
    fun loadingStateDoesNotExposeAFalseCount() {
        val summary =
            familiarStepHistorySummary(
                qualifiedCount = 8,
                loading = true,
                personalSuggestionsEnabled = true,
            )

        assertEquals(
            "Checking which support steps have worked consistently on this device.",
            summary,
        )

        assertFalse(summary.contains("8 familiar"))
    }

    @Test
    fun disabledSuggestionsAreRepresentedTruthfully() {
        assertEquals(
            "Personal suggestions are off. Existing local support history remains private on this device.",
            familiarStepHistorySummary(
                qualifiedCount = 3,
                loading = false,
                personalSuggestionsEnabled = false,
            ),
        )
    }

    @Test
    fun emptyHistoryAvoidsClaimingThatSomethingWorked() {
        assertEquals(
            "No familiar steps have qualified yet. Impulsive only learns from repeated helpful outcomes.",
            familiarStepHistorySummary(
                qualifiedCount = 0,
                loading = false,
                personalSuggestionsEnabled = true,
            ),
        )
    }

    @Test
    fun oneQualifiedItemUsesSingularCopy() {
        assertEquals(
            "1 familiar step currently qualifies from repeated helpful outcomes on this device.",
            familiarStepHistorySummary(
                qualifiedCount = 1,
                loading = false,
                personalSuggestionsEnabled = true,
            ),
        )
    }

    @Test
    fun multipleQualifiedItemsUsePluralCopy() {
        assertEquals(
            "4 familiar steps currently qualify from repeated helpful outcomes on this device.",
            familiarStepHistorySummary(
                qualifiedCount = 4,
                loading = false,
                personalSuggestionsEnabled = true,
            ),
        )
    }

    @Test
    fun negativeCountFailsClosedAsEmpty() {
        assertEquals(
            "No familiar steps have qualified yet. Impulsive only learns from repeated helpful outcomes.",
            familiarStepHistorySummary(
                qualifiedCount = -1,
                loading = false,
                personalSuggestionsEnabled = true,
            ),
        )
    }
}
