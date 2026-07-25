package com.impulsive.app.backend.data.restore.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudRecoveryLocalMetadataStoreTest {
    @Test
    fun `valid metadata round trips through canonical json`() {
        val metadata =
            metadata()

        val parsed =
            parseCloudRecoveryLocalMetadataJson(
                buildCloudRecoveryLocalMetadataJson(
                    metadata,
                ).toByteArray(
                    Charsets.UTF_8,
                ),
            )

        assertEquals(
            metadata,
            parsed,
        )
    }

    @Test
    fun `wrong salt size is rejected before writing`() {
        val error =
            expectThrows<
                IllegalArgumentException
            > {
                buildCloudRecoveryLocalMetadataJson(
                    WrappedKeyMetadata(
                        kdfSalt =
                            ByteArray(
                                CloudRecoverySaltBytes - 1,
                            ),

                        wrappedDekIv =
                            ByteArray(
                                CloudRecoveryIvBytes,
                            ),

                        wrappedDekCipherText =
                            ByteArray(
                                CloudRecoveryDekBytes +
                                    CloudRecoveryGcmTagBytes,
                            ),
                    ),
                )
            }

        assertTrue(
            error.message
                .orEmpty()
                .contains(
                    "salt",
                ),
        )
    }

    @Test
    fun `wrong wrapped dek ciphertext size is rejected on read`() {
        val json =
            """
            {
              "formatVersion":1,
              "kdfSaltBase64":"${ByteArray(CloudRecoverySaltBytes).toCanonicalBase64()}",
              "wrappedDekIvBase64":"${ByteArray(CloudRecoveryIvBytes).toCanonicalBase64()}",
              "wrappedDekCipherTextBase64":"${ByteArray(CloudRecoveryDekBytes + CloudRecoveryGcmTagBytes - 1).toCanonicalBase64()}"
            }
            """.trimIndent()

        assertNull(
            parseCloudRecoveryLocalMetadataJson(
                json.toByteArray(
                    Charsets.UTF_8,
                ),
            ),
        )
    }

    @Test
    fun `non canonical base64 is rejected`() {
        val metadata =
            metadata()

        val canonical =
            buildCloudRecoveryLocalMetadataJson(
                metadata,
            )

        val nonCanonical =
            canonical.replace(
                metadata.kdfSalt
                    .toCanonicalBase64(),

                metadata.kdfSalt
                    .toCanonicalBase64()
                    .trimEnd(
                        '=',
                    ),
            )

        assertNull(
            parseCloudRecoveryLocalMetadataJson(
                nonCanonical.toByteArray(
                    Charsets.UTF_8,
                ),
            ),
        )
    }

    private fun metadata():
        WrappedKeyMetadata =
        WrappedKeyMetadata(
            kdfSalt =
                ByteArray(
                    CloudRecoverySaltBytes,
                ) { index ->
                    index.toByte()
                },

            wrappedDekIv =
                ByteArray(
                    CloudRecoveryIvBytes,
                ) { index ->
                    (index + 16)
                        .toByte()
                },

            wrappedDekCipherText =
                ByteArray(
                    CloudRecoveryDekBytes +
                        CloudRecoveryGcmTagBytes,
                ) { index ->
                    (index + 32)
                        .toByte()
                },
        )

    private inline fun <
        reified T : Throwable
    > expectThrows(
        block: () -> Unit,
    ): T {
        try {
            block()
        } catch (
            error:
                Throwable
        ) {
            if (
                error is T
            ) {
                return error
            }

            throw AssertionError(
                "Expected ${T::class.java.name}, got ${error.javaClass.name}.",
                error,
            )
        }

        throw AssertionError(
            "Expected ${T::class.java.name} to be thrown.",
        )
    }
}