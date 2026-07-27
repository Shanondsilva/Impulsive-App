package com.impulsive.app.backend.data.restore

import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingRestoredOwnershipClaimStoreTest {
    @Test
    fun validClaimRoundTrips() = withStore { store, _ ->
        val claim = claim()

        store.write(claim)

        assertEquals(claim, store.read())
    }

    @Test
    fun timestampRoundTrips() = withStore { store, _ ->
        store.write(claim(createdAtMillis = 9_876_543_210L))

        assertEquals(9_876_543_210L, store.read()?.createdAtMillis)
    }

    @Test
    fun matchingIdentityIgnoresTimestamp() {
        assertTrue(
            claim(createdAtMillis = 1L).hasSameIdentityAs(
                claim(createdAtMillis = 2L),
            ),
        )
    }

    @Test
    fun differentUidOrGoogleHashDoesNotMatch() {
        val original = claim()

        assertFalse(original.hasSameIdentityAs(claim(currentUid = "other-current")))
        assertFalse(
            original.hasSameIdentityAs(
                claim(
                    previousGoogleSubjectHash = OtherHash,
                    currentGoogleSubjectHash = OtherHash,
                ),
            ),
        )
    }

    @Test
    fun blankPreviousUidIsRejected() = rejects(claim(previousOwnerUid = " "))

    @Test
    fun blankCurrentUidIsRejected() = rejects(claim(currentUid = ""))

    @Test
    fun uidOver128CharactersIsRejected() = rejects(claim(currentUid = "x".repeat(129)))

    @Test
    fun equalPreviousAndCurrentUidIsRejected() =
        rejects(claim(previousOwnerUid = "same", currentUid = "same"))

    @Test
    fun invalidPreviousHashIsRejected() =
        rejects(claim(previousGoogleSubjectHash = "invalid"))

    @Test
    fun invalidCurrentHashIsRejected() =
        rejects(claim(currentGoogleSubjectHash = "invalid"))

    @Test
    fun unequalValidHashesAreRejected() =
        rejects(claim(currentGoogleSubjectHash = OtherHash))

    @Test
    fun malformedJsonIsDeleted() = invalidMarkerIsDeleted("{not-json")

    @Test
    fun unsupportedFormatIsDeleted() =
        invalidMarkerIsDeleted(
            validJson().replace("\"formatVersion\":1", "\"formatVersion\":99"),
        )

    @Test
    fun wrongProofTypeIsDeleted() =
        invalidMarkerIsDeleted(
            validJson().replace(
                "\"proofType\":\"same_google_identity\"",
                "\"proofType\":\"legacy\"",
            ),
        )

    @Test
    fun oversizedMarkerIsDeleted() =
        invalidMarkerIsDeleted("x".repeat(4_097))

    @Test
    fun failedAtomicReplacementThrows() = withStore(
        replace = { _, _ -> throw IOException("disk failure") },
    ) { store, directory ->
        assertThrows(IOException::class.java) {
            store.write(claim())
        }
        assertFalse(File(directory, TempFileName).exists())
    }

    @Test
    fun clearRemovesMarker() = withStore { store, directory ->
        store.write(claim())

        store.clear()

        assertFalse(File(directory, FileName).exists())
        assertNull(store.read())
    }

    @Test
    fun failedDeletionThrows() = withStore { store, directory ->
        val markerDirectory = File(directory, FileName)
        assertTrue(markerDirectory.mkdirs())
        File(markerDirectory, "child").writeText("keeps directory non-empty")

        assertThrows(IOException::class.java) {
            store.clear()
        }
    }

    @Test
    fun jsonContainsNoRecoverySecrets() = withStore { store, directory ->
        store.write(claim())
        val json = File(directory, FileName).readText().lowercase()

        listOf(
            "email",
            "password",
            "token",
            "access token",
            "dek",
            "payload",
            "envelope",
            "recovery secret",
        ).forEach { forbidden ->
            assertFalse("marker contains $forbidden", json.contains(forbidden))
        }
    }

    private fun rejects(invalidClaim: PendingRestoredOwnershipClaim) =
        withStore { store, _ ->
            assertThrows(IllegalArgumentException::class.java) {
                store.write(invalidClaim)
            }
        }

    private fun invalidMarkerIsDeleted(contents: String) =
        withStore { store, directory ->
            val marker = File(directory, FileName)
            marker.writeText(contents)

            assertNull(store.read())
            assertFalse(marker.exists())
        }

    private fun validJson(): String =
        """{"formatVersion":1,"proofType":"same_google_identity","previousOwnerUid":"previous-owner","previousGoogleSubjectHash":"$Hash","currentUid":"current-owner","currentGoogleSubjectHash":"$Hash","createdAtMillis":123}"""

    private fun claim(
        previousOwnerUid: String = "previous-owner",
        previousGoogleSubjectHash: String = Hash,
        currentUid: String = "current-owner",
        currentGoogleSubjectHash: String = Hash,
        createdAtMillis: Long = 123L,
    ) = PendingRestoredOwnershipClaim(
        previousOwnerUid = previousOwnerUid,
        previousGoogleSubjectHash = previousGoogleSubjectHash,
        currentUid = currentUid,
        currentGoogleSubjectHash = currentGoogleSubjectHash,
        createdAtMillis = createdAtMillis,
    )

    private fun withStore(
        replace: ((File, File) -> Unit)? = null,
        block: (FilePendingRestoredOwnershipClaimStore, File) -> Unit,
    ) {
        val directory = Files.createTempDirectory("pending-claim-test").toFile()
        try {
            val store = if (replace == null) {
                FilePendingRestoredOwnershipClaimStore(directory)
            } else {
                FilePendingRestoredOwnershipClaimStore(directory, replace)
            }
            block(store, directory)
        } finally {
            directory.deleteRecursively()
        }
    }

    private companion object {
        const val FileName = "pending_restored_ownership_claim_v1.json"
        const val TempFileName = "pending_restored_ownership_claim_v1.json.tmp"
        const val Hash = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val OtherHash = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    }
}
