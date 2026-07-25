package com.impulsive.app.backend.data.restore.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveAppDataAuthorizationTest {
    @Test
    fun `nonblank token with exact appdata grant is authorized`() {
        val result = validateDriveAuthorization(
            accessToken = "access-token",
            grantedScopes = listOf(DriveAppDataScope),
            hasResolution = false,
        )

        assertTrue(result is DriveAuthorizationResult.Authorized)
        assertEquals("access-token", (result as DriveAuthorizationResult.Authorized).accessToken)
    }

    @Test
    fun `blank token fails`() {
        val result = validateDriveAuthorization(
            accessToken = "   ",
            grantedScopes = listOf(DriveAppDataScope),
            hasResolution = false,
        )

        assertTrue(result is DriveAuthorizationResult.Failed)
    }

    @Test
    fun `null token fails`() {
        val result = validateDriveAuthorization(
            accessToken = null,
            grantedScopes = listOf(DriveAppDataScope),
            hasResolution = false,
        )

        assertTrue(result is DriveAuthorizationResult.Failed)
    }

    @Test
    fun `missing appdata grant fails`() {
        val result = validateDriveAuthorization(
            accessToken = "access-token",
            grantedScopes = emptyList(),
            hasResolution = false,
        )

        assertTrue(result is DriveAuthorizationResult.Failed)
    }

    @Test
    fun `broader and unrelated drive scopes do not replace appdata grant`() {
        val result = validateDriveAuthorization(
            accessToken = "access-token",
            grantedScopes = listOf(
                "https://www.googleapis.com/auth/drive",
                "https://www.googleapis.com/auth/drive.file",
                "https://www.googleapis.com/auth/drive.readonly",
            ),
            hasResolution = false,
        )

        assertTrue(result is DriveAuthorizationResult.Failed)
    }
}