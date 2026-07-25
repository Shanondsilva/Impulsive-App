package com.impulsive.app.backend.data.restore.cloud

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudRecoveryRestoreTransportTest {
    @Test
    fun `Google account falls back from Drive NotFound to Storage`() =
        runBlocking {
            val drive = FakeTransport(
                kind = CloudRecoveryTransportKind.DriveAppData,
                requiresDriveAuthorization = true,
                downloadOutcome = CloudRecoveryTransportOutcome.NotFound,
            )
            val storage = FakeTransport(
                kind = CloudRecoveryTransportKind.FirebaseStorage,
                requiresDriveAuthorization = false,
                downloadOutcome = CloudRecoveryTransportOutcome.Success(byteArrayOf(1, 2, 3)),
            )
            val requestedKinds = mutableListOf<CloudRecoveryTransportKind>()

            val outcome = downloadCloudRecoveryEnvelope(
                hasGoogleProvider = true,
                driveAccessToken = "drive-token",
                transportProvider = CloudRecoveryUploadTransportProvider { kind ->
                    requestedKinds += kind
                    when (kind) {
                        CloudRecoveryTransportKind.DriveAppData -> drive
                        CloudRecoveryTransportKind.FirebaseStorage -> storage
                    }
                },
            )

            assertTrue(outcome is CloudRecoveryTransportOutcome.Success)
            assertTrue((outcome as CloudRecoveryTransportOutcome.Success).value.contentEquals(byteArrayOf(1, 2, 3)))
            assertEquals(
                listOf(
                    CloudRecoveryTransportKind.DriveAppData,
                    CloudRecoveryTransportKind.FirebaseStorage,
                ),
                requestedKinds,
            )
            assertEquals("drive-token", drive.downloadAccessToken)
            assertEquals(null, storage.downloadAccessToken)
        }

    @Test
    fun `non Google account never constructs Drive transport or requests authorization`() =
        runBlocking {
            val storage = FakeTransport(
                kind = CloudRecoveryTransportKind.FirebaseStorage,
                requiresDriveAuthorization = false,
                downloadOutcome = CloudRecoveryTransportOutcome.Success(byteArrayOf(4)),
            )

            val outcome = downloadCloudRecoveryEnvelope(
                hasGoogleProvider = false,
                driveAccessToken = "must-not-be-used",
                transportProvider = CloudRecoveryUploadTransportProvider { kind ->
                    assertEquals(CloudRecoveryTransportKind.FirebaseStorage, kind)
                    storage
                },
            )

            assertTrue(outcome is CloudRecoveryTransportOutcome.Success)
            assertEquals(null, storage.downloadAccessToken)
        }

    private class FakeTransport(
        override val kind: CloudRecoveryTransportKind,
        override val requiresDriveAuthorization: Boolean,
        private val downloadOutcome: CloudRecoveryTransportOutcome<ByteArray>,
    ) : CloudRecoveryTransport {
        var downloadAccessToken: String? = null

        override suspend fun upload(
            envelopeBytes: ByteArray,
            driveAccessToken: String?,
        ): CloudRecoveryTransportOutcome<Unit> =
            CloudRecoveryTransportOutcome.PermanentFailure(
                UnsupportedOperationException(),
            )

        override suspend fun download(
            driveAccessToken: String?,
        ): CloudRecoveryTransportOutcome<ByteArray> {
            downloadAccessToken = driveAccessToken
            return downloadOutcome
        }

        override suspend fun deleteAll(
            driveAccessToken: String?,
        ): CloudRecoveryTransportOutcome<Int> =
            CloudRecoveryTransportOutcome.PermanentFailure(
                UnsupportedOperationException(),
            )
    }
}
