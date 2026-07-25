package com.impulsive.app.backend.data.restore.cloud

import org.junit.Assert.assertEquals
import org.junit.Test

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
}