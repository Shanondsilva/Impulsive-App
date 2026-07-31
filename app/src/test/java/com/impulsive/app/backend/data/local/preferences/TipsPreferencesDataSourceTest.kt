package com.impulsive.app.backend.data.local.preferences

import com.impulsive.app.backend.domain.tips.ImpulsiveTipId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TipsPreferencesDataSourceTest {
    @Test
    fun idHistoryRoundTripsStableIdsOnly() {
        val ids = listOf(ImpulsiveTipId("first_tip"), ImpulsiveTipId("second_tip"))
        assertEquals(
            ids,
            TipsPreferencesDataSource.decodeIds(TipsPreferencesDataSource.encodeIds(ids)),
        )
    }

    @Test
    fun invalidRouteLikeValuesAreDroppedDuringDecode() {
        val decoded = TipsPreferencesDataSource.decodeIds("safe_tip\u001Fprivate/path")
        assertEquals(listOf(ImpulsiveTipId("safe_tip")), decoded)
    }

    @Test
    fun shownEpochDaysRoundTripWithoutTipText() {
        val values = linkedMapOf(ImpulsiveTipId("first_tip") to 123L)
        val encoded = TipsPreferencesDataSource.encodeShown(values)
        assertEquals(values, TipsPreferencesDataSource.decodeShown(encoded))
        assertFalse(encoded.contains("summary"))
    }

    @Test
    fun malformedEpochEntriesAreIgnored() {
        assertTrue(TipsPreferencesDataSource.decodeShown("tip\u001Enot_a_number").isEmpty())
    }

    @Test
    fun historyDecodeIsBoundedToSixtyFourEntries() {
        val raw = (0 until 80).joinToString("\u001F") { "tip_$it" }
        val decoded = TipsPreferencesDataSource.decodeIds(raw)
        assertEquals(64, decoded.size)
        assertEquals("tip_16", decoded.first().value)
    }
}
