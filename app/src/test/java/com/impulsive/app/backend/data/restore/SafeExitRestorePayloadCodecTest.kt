package com.impulsive.app.backend.data.restore

import com.impulsive.app.backend.data.local.entity.SafeExitEntity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeExitRestorePayloadCodecTest {
    @Test
    fun canonicalRecordsRoundTripInDeterministicOrder() {
        val older =
            entity(
                sourceKey =
                    "reset_reading:1001",
                source =
                    "reset_reading",
                sourceId =
                    "1001",
                completedAt =
                    "2026-08-02T10:00",
            )

        val newer =
            entity(
                sourceKey =
                    "moment_plan:decision-2",
                source =
                    "moment_plan",
                sourceId =
                    "decision-2",
                completedAt =
                    "2026-08-03T10:00",
            )

        val encoded =
            SafeExitRestorePayloadCodec
                .encode(
                    listOf(
                        older,
                        newer,
                    ),
                )

        val restored =
            SafeExitRestorePayloadCodec
                .decodeIfPresent(
                    JSONObject()
                        .put(
                            SafeExitRestorePayloadCodec
                                .JsonKey,
                            encoded,
                        ),
                )

        assertNotNull(
            restored,
        )

        assertEquals(
            listOf(
                newer,
                older,
            ),
            restored!!.records,
        )

        assertEquals(
            SafeExitRestorePayloadCodec
                .CurrentFormatVersion,
            encoded.getInt(
                "formatVersion",
            ),
        )
    }

    @Test
    fun missingExtensionRemainsBackwardCompatible() {
        assertNull(
            SafeExitRestorePayloadCodec
                .decodeIfPresent(
                    JSONObject(),
                ),
        )
    }

    @Test
    fun presentEmptyExtensionIsAnExplicitEmptySnapshot() {
        val payload =
            JSONObject()
                .put(
                    SafeExitRestorePayloadCodec
                        .JsonKey,
                    SafeExitRestorePayloadCodec
                        .encode(
                            emptyList(),
                        ),
                )

        val restored =
            SafeExitRestorePayloadCodec
                .decodeIfPresent(
                    payload,
                )

        assertNotNull(
            restored,
        )
        assertTrue(
            restored!!
                .records
                .isEmpty(),
        )
    }

    @Test
    fun encodedRowsContainOnlySourceKeyAndCompletedAt() {
        val encoded =
            SafeExitRestorePayloadCodec
                .encode(
                    listOf(
                        entity(
                            sourceKey =
                                "pivot_game:REFLEX_OVERRIDE:1002",
                            source =
                                "pivot_game",
                            sourceId =
                                "REFLEX_OVERRIDE:1002",
                            completedAt =
                                "2026-08-03T10:05",
                        ),
                    ),
                )

        val row =
            encoded
                .getJSONArray(
                    "records",
                )
                .getJSONArray(
                    0,
                )

        assertEquals(
            2,
            row.length(),
        )
        assertEquals(
            "pivot_game:REFLEX_OVERRIDE:1002",
            row.getString(
                0,
            ),
        )
        assertEquals(
            "2026-08-03T10:05",
            row.getString(
                1,
            ),
        )
    }

    @Test
    fun duplicateSourceKeysAreRejected() {
        val record =
            entity(
                sourceKey =
                    "reset_reading:1003",
                source =
                    "reset_reading",
                sourceId =
                    "1003",
                completedAt =
                    "2026-08-03T10:10",
            )

        assertThrows(
            IllegalArgumentException::class.java,
        ) {
            SafeExitRestorePayloadCodec
                .encode(
                    listOf(
                        record,
                        record,
                    ),
                )
        }
    }

    @Test
    fun duplicateSourceKeysInImportedJsonAreRejected() {
        val row =
            JSONArray()
                .put(
                    "moment_plan:decision-4",
                )
                .put(
                    "2026-08-03T10:15",
                )

        val payload =
            JSONObject()
                .put(
                    SafeExitRestorePayloadCodec
                        .JsonKey,
                    JSONObject()
                        .put(
                            "formatVersion",
                            1,
                        )
                        .put(
                            "records",
                            JSONArray()
                                .put(
                                    row,
                                )
                                .put(
                                    JSONArray(
                                        row.toString(),
                                    ),
                                ),
                        ),
                )

        assertThrows(
            IllegalArgumentException::class.java,
        ) {
            SafeExitRestorePayloadCodec
                .decodeIfPresent(
                    payload,
                )
        }
    }

    @Test
    fun unknownSourceIsRejected() {
        val payload =
            payloadWithRow(
                sourceKey =
                    "unknown:1005",
                completedAt =
                    "2026-08-03T10:20",
            )

        assertThrows(
            IllegalArgumentException::class.java,
        ) {
            SafeExitRestorePayloadCodec
                .decodeIfPresent(
                    payload,
                )
        }
    }

    @Test
    fun malformedDateIsRejected() {
        val payload =
            payloadWithRow(
                sourceKey =
                    "reset_reading:1006",
                completedAt =
                    "not-a-date",
            )

        assertThrows(
            IllegalArgumentException::class.java,
        ) {
            SafeExitRestorePayloadCodec
                .decodeIfPresent(
                    payload,
                )
        }
    }

    @Test
    fun nonCanonicalSourceKeyIsRejected() {
        val payload =
            payloadWithRow(
                sourceKey =
                    "reset_reading: 1007 ",
                completedAt =
                    "2026-08-03T10:25",
            )

        assertThrows(
            IllegalArgumentException::class.java,
        ) {
            SafeExitRestorePayloadCodec
                .decodeIfPresent(
                    payload,
                )
        }
    }

    @Test
    fun unsupportedFormatVersionIsRejected() {
        val payload =
            JSONObject()
                .put(
                    SafeExitRestorePayloadCodec
                        .JsonKey,
                    JSONObject()
                        .put(
                            "formatVersion",
                            2,
                        )
                        .put(
                            "records",
                            JSONArray(),
                        ),
                )

        assertThrows(
            IllegalArgumentException::class.java,
        ) {
            SafeExitRestorePayloadCodec
                .decodeIfPresent(
                    payload,
                )
        }
    }

    private fun payloadWithRow(
        sourceKey: String,
        completedAt: String,
    ): JSONObject {
        return JSONObject()
            .put(
                SafeExitRestorePayloadCodec
                    .JsonKey,
                JSONObject()
                    .put(
                        "formatVersion",
                        1,
                    )
                    .put(
                        "records",
                        JSONArray()
                            .put(
                                JSONArray()
                                    .put(
                                        sourceKey,
                                    )
                                    .put(
                                        completedAt,
                                    ),
                            ),
                    ),
            )
    }

    private fun entity(
        sourceKey: String,
        source: String,
        sourceId: String,
        completedAt: String,
    ): SafeExitEntity {
        return SafeExitEntity(
            sourceKey =
                sourceKey,
            source =
                source,
            sourceId =
                sourceId,
            completedAt =
                completedAt,
        )
    }
}
