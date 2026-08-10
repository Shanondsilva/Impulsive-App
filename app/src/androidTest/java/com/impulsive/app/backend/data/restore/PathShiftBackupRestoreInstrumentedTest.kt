package com.impulsive.app.backend.data.restore

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.impulsive.app.backend.data.local.entity.AdaptivePreferenceEntity
import com.impulsive.app.pathshift.pathShiftCycleEntity
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PathShiftBackupRestoreInstrumentedTest {
    @Test
    fun schemaThreeRestoresCycleAndLegacySchemaTwoRestoresWithoutOne() {
        val cycle = pathShiftCycleEntity()
        val schemaThree = AdaptiveRestorePayloadCodec.encode(
            plans = emptyList(),
            preferences = AdaptivePreferenceEntity(pathShiftEnabled = true),
            decisions = emptyList(),
            rehearsals = emptyList(),
            pathShiftCycles = listOf(cycle),
        )
        val restoredThree = AdaptiveRestorePayloadCodec.decodeIfPresent(
            JSONObject().put(AdaptiveRestorePayloadCodec.JsonKey, schemaThree),
            1_000L,
        )!!
        assertEquals(cycle, restoredThree.pathShiftCycles.single())
        assertTrue(restoredThree.preferences.pathShiftEnabled)

        val schemaTwo = AdaptiveRestorePayloadCodec.encode(
            plans = emptyList(),
            preferences = AdaptivePreferenceEntity(),
            decisions = emptyList(),
            rehearsals = emptyList(),
        ).apply {
            put("formatVersion", 2)
            remove("pathShiftCycles")
            getJSONObject("preferences").remove("pathShiftEnabled")
        }
        val restoredTwo = AdaptiveRestorePayloadCodec.decodeIfPresent(
            JSONObject().put(AdaptiveRestorePayloadCodec.JsonKey, schemaTwo),
            1_000L,
        )!!
        assertTrue(restoredTwo.pathShiftCycles.isEmpty())
        assertTrue(
            restoredTwo
                .preferences
                .pathShiftEnabled,
        )

        val schemaThreeFalse =
            AdaptiveRestorePayloadCodec.encode(
                plans = emptyList(),
                preferences = AdaptivePreferenceEntity(),
                decisions = emptyList(),
                rehearsals = emptyList(),
            ).apply {
                getJSONObject(
                    "preferences",
                )
                    .put(
                        "pathShiftEnabled",
                        false,
                    )
            }
        val restoredThreeFalse =
            AdaptiveRestorePayloadCodec.decodeIfPresent(
                JSONObject()
                    .put(
                        AdaptiveRestorePayloadCodec.JsonKey,
                        schemaThreeFalse,
                    ),
                1_000L,
            )!!
        assertTrue(
            restoredThreeFalse
                .preferences
                .pathShiftEnabled,
        )
    }
}
