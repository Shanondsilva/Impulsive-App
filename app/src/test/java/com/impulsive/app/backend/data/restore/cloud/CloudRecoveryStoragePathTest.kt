package com.impulsive.app.backend.data.restore.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CloudRecoveryStoragePathTest {
    @Test
    fun `storage path is scoped to Firebase UID`() {
        assertEquals(
            "cloud_recovery/abc/impulsive_cloud_recovery_v1.json",
            cloudRecoveryStoragePath("abc"),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank UID is rejected`() {
        cloudRecoveryStoragePath("   ")
    }

    @Test
    fun `Firebase uploads declare JSON content type`() {
        val source =
            File(
                "src/main/java/com/impulsive/app/backend/data/restore/cloud/" +
                    "FirebaseStorageCloudRecoveryTransport.kt",
            ).readText()

        assertEquals("application/json", CloudRecoveryStorageContentType)
        assertTrue(source.contains(".putBytes(envelopeBytes, uploadMetadata)"))
        assertTrue(
            Regex(
                """private val uploadMetadata\s*=\s*""" +
                    """StorageMetadata\.Builder\(\)\s*""" +
                    """\.setContentType\(CloudRecoveryStorageContentType\)\s*""" +
                    """\.build\(\)""",
            ).containsMatchIn(source),
        )
    }
}
