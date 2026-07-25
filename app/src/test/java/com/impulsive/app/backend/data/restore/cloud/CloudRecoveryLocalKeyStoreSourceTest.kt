package com.impulsive.app.backend.data.restore.cloud

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudRecoveryLocalKeyStoreSourceTest {
    private val source =
        File(
            "src/main/java/com/impulsive/app/backend/data/restore/cloud/CloudRecoveryLocalKeyStore.kt",
        ).readText()

    @Test
    fun `encryption lets Android Keystore generate the IV`() {
        val storeSource =
            source.substring(
                source.indexOf(
                    "public fun store",
                ),
                source.indexOf(
                    "public fun load",
                ),
            )

        assertTrue(
            storeSource.contains(
                "cipher.init",
            ),
        )

        assertTrue(
            storeSource.contains(
                "Cipher.ENCRYPT_MODE",
            ),
        )

        assertTrue(
            storeSource.contains(
                "cipher.iv",
            ),
        )

        assertFalse(
            storeSource.contains(
                "GCMParameterSpec",
            ),
        )

        assertFalse(
            source.contains(
                "secureRandomIv",
            ),
        )
    }

    @Test
    fun `decryption still supplies the stored IV`() {
        val loadSource =
            source.substring(
                source.indexOf(
                    "public fun load",
                ),
                source.indexOf(
                    "public fun clear",
                ),
            )

        assertTrue(
            loadSource.contains(
                "Cipher.DECRYPT_MODE",
            ),
        )

        assertTrue(
            loadSource.contains(
                "GCMParameterSpec",
            ),
        )

        assertTrue(
            loadSource.contains(
                "local.iv",
            ),
        )
    }
}