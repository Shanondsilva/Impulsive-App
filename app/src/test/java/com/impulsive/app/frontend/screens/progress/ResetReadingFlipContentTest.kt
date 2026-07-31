package com.impulsive.app.frontend.screens.progress

import org.junit.Assert.assertEquals
import org.junit.Test

class ResetReadingFlipContentTest {

    @Test
    fun approvedMetrics_areMappedToCorrectFaces() {
        val content = buildResetReadingFlipContent(
            lastCompletedValue = "3 days ago",
            helpfulValue = "2",
            completedValue = "5",
            abandonedValue = "1",
        )

        assertEquals("3 days ago", content.front.firstValue)
        assertEquals("Last completed", content.front.firstLabel)
        assertEquals("2", content.front.secondValue)
        assertEquals("Helpful", content.front.secondLabel)

        assertEquals("5", content.back.firstValue)
        assertEquals("Completed", content.back.firstLabel)
        assertEquals("1", content.back.secondValue)
        assertEquals("Abandoned", content.back.secondLabel)
    }

    @Test
    fun eachFace_containsExactlyTwoMetrics() {
        val content = buildResetReadingFlipContent(
            lastCompletedValue = "Not yet",
            helpfulValue = "0",
            completedValue = "0",
            abandonedValue = "2",
        )

        val frontMetrics = listOf(
            content.front.firstValue,
            content.front.secondValue,
        )
        val backMetrics = listOf(
            content.back.firstValue,
            content.back.secondValue,
        )

        assertEquals(2, frontMetrics.size)
        assertEquals(2, backMetrics.size)
    }
}
