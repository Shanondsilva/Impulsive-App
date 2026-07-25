package com.impulsive.app.backend.data.restore.cloud

import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudRecoveryDeletionCoordinatorTest {
    @Test
    fun `deletes every matching Drive recovery file`() = runBlocking {
        val deletedIds = mutableListOf<String>()
        val result =
            deleteAllCloudRecoveryFiles(
                accessToken = "ephemeral-token",
                findByName = { _, _ ->
                    listOf(
                        DriveAppDataFile("old", CloudRecoveryDriveFileName, null, null),
                        DriveAppDataFile("new", CloudRecoveryDriveFileName, null, null),
                    )
                },
                deleteById = { _, fileId -> deletedIds += fileId },
            )

        assertEquals(CloudRecoveryDeletionResult.Success(2), result)
        assertEquals(listOf("old", "new"), deletedIds)
    }

    @Test
    fun `authorization errors require fresh authorization`() = runBlocking {
        val result =
            deleteAllCloudRecoveryFiles(
                accessToken = "ephemeral-token",
                findByName = { _, _ -> throw DriveAppDataHttpException.Unauthorized(401, null) },
                deleteById = { _, _ -> error("not reached") },
            )

        assertEquals(CloudRecoveryDeletionResult.AuthorizationRequired, result)
    }

    @Test
    fun `other Drive errors fail without reporting deletion success`() = runBlocking {
        val result =
            deleteAllCloudRecoveryFiles(
                accessToken = "ephemeral-token",
                findByName = { _, _ -> throw IOException("offline") },
                deleteById = { _, _ -> error("not reached") },
            )

        assertTrue(result is CloudRecoveryDeletionResult.Failed)
    }
}