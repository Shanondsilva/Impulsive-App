package com.impulsive.app.backend.data.restore.cloud

import com.impulsive.app.backend.data.local.entity.CloudRestoreProofType
import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingCloudRestoreAuthorizationStoreTest {
    @Test
    fun validAuthorizationRoundTrips() = withStore { store, _ ->
        val authorization = authorization()

        store.write(authorization)

        assertEquals(authorization, store.read())
    }

    @Test
    fun timestampSurvivesRoundTrip() = withStore { store, _ ->
        store.write(authorization(authorisedAtMillis = 9_876_543_210L))

        assertEquals(
            9_876_543_210L,
            store.read()?.authorisedAtMillis,
        )
    }

    @Test
    fun malformedJsonIsDeleted() =
        invalidFileIsDeleted("{not-json")

    @Test
    fun oversizedFileIsDeleted() =
        invalidFileIsDeleted("x".repeat(4_097))

    @Test
    fun unsupportedVersionIsDeleted() =
        invalidFileIsDeleted(
            validJson().replace(
                "\"formatVersion\":1",
                "\"formatVersion\":99",
            ),
        )

    @Test
    fun invalidReceiptUuidIsRejected() =
        rejects(authorization(receiptId = "not-a-uuid"))

    @Test
    fun invalidPayloadHashIsRejected() =
        rejects(authorization(payloadSha256 = "ABC"))

    @Test
    fun invalidUidIsRejected() =
        rejects(authorization(currentUid = " current-user "))

    @Test
    fun invalidGoogleHashIsRejected() =
        rejects(authorization(currentGoogleSubjectHash = "invalid"))

    @Test
    fun invalidProofCombinationIsRejected() =
        rejects(
            authorization(
                proofType = CloudRestoreProofType.SameGoogleIdentity,
                previousUid = "previous-user",
                previousGoogleSubjectHash = Hash,
                currentUid = "current-user",
                currentGoogleSubjectHash = OtherHash,
            ),
        )

    @Test
    fun failedAtomicReplacementThrows() = withStore(
        replace = { _, _ -> throw IOException("disk failure") },
    ) { store, directory ->
        assertThrows(IOException::class.java) {
            store.write(authorization())
        }
        assertFalse(File(directory, TempFileName).exists())
    }

    @Test
    fun clearFailureThrows() {
        val directory =
            Files.createTempDirectory("cloud-authorization-test")
                .toFile()
        try {
            val writer =
                FilePendingCloudRestoreAuthorizationStore(directory)
            writer.write(authorization())
            val failing =
                FilePendingCloudRestoreAuthorizationStore(
                    directory = directory,
                    delete = { false },
                )

            assertThrows(IOException::class.java) {
                failing.clear()
            }
            assertTrue(File(directory, FileName).exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun encodedAuthorizationContainsNoSecrets() =
        withStore { store, directory ->
            store.write(authorization())
            val json = File(directory, FileName).readText()

            listOf(
                "password",
                "rawDek",
                "accessToken",
                "email",
                "payloadJson",
                "wrappedKey",
                "envelopeBytes",
            ).forEach { forbidden ->
                assertFalse(
                    "authorization contains $forbidden",
                    json.contains(forbidden, ignoreCase = true),
                )
            }
            assertTrue(json.contains("\"payloadSha256\""))
        }

    private fun rejects(
        invalid: PendingCloudRestoreAuthorization,
    ) = withStore { store, _ ->
        assertThrows(IllegalArgumentException::class.java) {
            store.write(invalid)
        }
    }

    private fun invalidFileIsDeleted(contents: String) =
        withStore { store, directory ->
            val marker = File(directory, FileName)
            marker.writeText(contents)

            assertNull(store.read())
            assertFalse(marker.exists())
        }

    private fun validJson(): String =
        """
        {
          "formatVersion":1,
          "receiptId":"$ReceiptId",
          "payloadSha256":"$PayloadHash",
          "proofType":"exact_uid",
          "previousUid":null,
          "previousGoogleSubjectHash":null,
          "currentUid":"current-user",
          "currentGoogleSubjectHash":"$Hash",
          "authorisedAtMillis":123
        }
        """.trimIndent()

    private fun authorization(
        receiptId: String = ReceiptId,
        payloadSha256: String = PayloadHash,
        proofType: CloudRestoreProofType =
            CloudRestoreProofType.ExactUid,
        previousUid: String? = null,
        previousGoogleSubjectHash: String? = null,
        currentUid: String = "current-user",
        currentGoogleSubjectHash: String? = Hash,
        authorisedAtMillis: Long = 123L,
    ) = PendingCloudRestoreAuthorization(
        receiptId = receiptId,
        payloadSha256 = payloadSha256,
        proofType = proofType,
        previousUid = previousUid,
        previousGoogleSubjectHash = previousGoogleSubjectHash,
        currentUid = currentUid,
        currentGoogleSubjectHash = currentGoogleSubjectHash,
        authorisedAtMillis = authorisedAtMillis,
    )

    private fun withStore(
        replace: ((File, File) -> Unit)? = null,
        block: (
            FilePendingCloudRestoreAuthorizationStore,
            File,
        ) -> Unit,
    ) {
        val directory =
            Files.createTempDirectory("cloud-authorization-test")
                .toFile()
        try {
            val store =
                if (replace == null) {
                    FilePendingCloudRestoreAuthorizationStore(directory)
                } else {
                    FilePendingCloudRestoreAuthorizationStore(
                        directory = directory,
                        replace = replace,
                    )
                }
            block(store, directory)
        } finally {
            directory.deleteRecursively()
        }
    }

    private companion object {
        const val FileName =
            "pending_cloud_restore_authorization_v1.json"
        const val TempFileName =
            "pending_cloud_restore_authorization_v1.json.tmp"
        const val ReceiptId =
            "123e4567-e89b-12d3-a456-426614174000"
        const val PayloadHash =
            "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
        const val Hash =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val OtherHash =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    }
}
