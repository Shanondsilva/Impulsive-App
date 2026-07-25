package com.impulsive.app.backend.data.restore.cloud

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudRecoveryTransportKindTest {
    @Test
    fun `Google provider selects Drive app data`() {
        assertEquals(
            CloudRecoveryTransportKind.DriveAppData,
            cloudRecoveryTransportKind(hasGoogleProvider = true),
        )
    }

    @Test
    fun `non Google provider selects Firebase Storage`() {
        assertEquals(
            CloudRecoveryTransportKind.FirebaseStorage,
            cloudRecoveryTransportKind(hasGoogleProvider = false),
        )
    }
}