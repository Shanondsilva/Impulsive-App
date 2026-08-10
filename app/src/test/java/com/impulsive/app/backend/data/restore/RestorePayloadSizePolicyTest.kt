package com.impulsive.app.backend.data.restore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RestorePayloadSizePolicyTest {
    @Test
    fun emptyPayloadIsAccepted() {
        val payload =
            ""

        assertSame(
            payload,
            RestorePayloadSizePolicy
                .requireWithinLimit(
                    payload,
                ),
        )
    }

    @Test
    fun exactEightMiBAsciiPayloadIsAccepted() {
        val payload =
            "a".repeat(
                RestorePayloadSizePolicy
                    .MaximumPayloadBytes,
            )

        assertEquals(
            RestorePayloadSizePolicy
                .MaximumPayloadBytes,
            payload
                .toByteArray(
                    Charsets.UTF_8,
                )
                .size,
        )

        assertSame(
            payload,
            RestorePayloadSizePolicy
                .requireWithinLimit(
                    payload,
                ),
        )
    }

    @Test
    fun eightMiBPlusOneAsciiByteIsRejected() {
        val payload =
            "a".repeat(
                RestorePayloadSizePolicy
                    .MaximumPayloadBytes +
                    1,
            )

        val error =
            assertThrows(
                IllegalArgumentException::class.java,
            ) {
                RestorePayloadSizePolicy
                    .requireWithinLimit(
                        payload,
                    )
            }

        assertEquals(
            RestorePayloadSizePolicy
                .OversizedPayloadMessage,
            error.message,
        )
    }

    @Test
    fun exactMultibyteUtf8BoundaryIsAccepted() {
        val payload =
            "é".repeat(
                RestorePayloadSizePolicy
                    .MaximumPayloadBytes /
                    2,
            )

        assertTrue(
            payload.length <
                RestorePayloadSizePolicy
                    .MaximumPayloadBytes,
        )

        assertEquals(
            RestorePayloadSizePolicy
                .MaximumPayloadBytes,
            payload
                .toByteArray(
                    Charsets.UTF_8,
                )
                .size,
        )

        assertSame(
            payload,
            RestorePayloadSizePolicy
                .requireWithinLimit(
                    payload,
                ),
        )
    }

    @Test
    fun multibytePayloadOneUtf8ByteOverLimitIsRejected() {
        val exactBoundary =
            "é".repeat(
                RestorePayloadSizePolicy
                    .MaximumPayloadBytes /
                    2,
            )

        val payload =
            exactBoundary + "a"

        assertTrue(
            payload.length <
                RestorePayloadSizePolicy
                    .MaximumPayloadBytes,
        )

        assertEquals(
            RestorePayloadSizePolicy
                .MaximumPayloadBytes +
                1,
            payload
                .toByteArray(
                    Charsets.UTF_8,
                )
                .size,
        )

        val error =
            assertThrows(
                IllegalArgumentException::class.java,
            ) {
                RestorePayloadSizePolicy
                    .requireWithinLimit(
                        payload,
                    )
            }

        assertEquals(
            RestorePayloadSizePolicy
                .OversizedPayloadMessage,
            error.message,
        )
    }
}