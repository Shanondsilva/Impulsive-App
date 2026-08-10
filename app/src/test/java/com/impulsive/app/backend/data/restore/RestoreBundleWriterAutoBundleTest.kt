package com.impulsive.app.backend.data.restore

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreBundleWriterAutoBundleTest {
    @Test
    fun automaticBundleSourceContainsFormatOwnerHashAndPayloadSchemaVersions() {
        val source = writerSource()

        assertEquals(3, RestoreBundleWriter.AutoBundleFormatVersion)
        assertEquals(1, RestoreBundleWriter.SchemaVersion)
        assertTrue(source.contains(".put(\"autoBundleFormatVersion\", AutoBundleFormatVersion)"))
        assertTrue(source.contains(".put(\"ownerUid\", normalizedOwnerUid)"))
        assertTrue(source.contains("\"ownerGoogleSubjectHash\""))
        assertTrue(source.contains(".put(\"schemaVersion\", SchemaVersion)"))
    }

    @Test
    fun versionThreeBundleRepresentsNullGoogleSubjectHashSafelyInSource() {
        val source = writerSource()

        assertTrue(source.contains("normalizedGoogleSubjectHash ?: JSONObject.NULL"))
    }

    @Test
    fun versionThreeBundleValidatesAndWritesValidGoogleSubjectHashInSource() {
        val source = writerSource()

        assertTrue(source.contains("require(isValidGoogleSubjectHash(hash))"))
        assertTrue(source.contains("normalizedGoogleSubjectHash"))
        assertFalse(source.contains("ownerGoogleSubjectHash ?: \"\""))
    }

    @Test
    fun versionThreeBundleRejectsInvalidGoogleSubjectHashBeforeJsonWrite() {
        val error = runCatching {
            RestoreBundleWriter.buildAutomaticBundleJson(
                ownerUid = "user-a",
                ownerGoogleSubjectHash = "not-a-valid-hash",
                payloadJson = emptyPayloadJson(),
                createdAtMillis = 1_700_000_000_000L,
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun automaticBundleChecksumChangesWhenOwnerUidChanges() {
        val payloadJson = emptyPayloadJson()
        val userA = RestoreBundleWriter.sha256Hex(
            RestoreBundleWriter.automaticBundleChecksumMaterialV3(
                ownerUid = "user-a",
                ownerGoogleSubjectHash = ValidGoogleSubjectHash,
                payloadJson = payloadJson,
            ),
        )
        val userB = RestoreBundleWriter.sha256Hex(
            RestoreBundleWriter.automaticBundleChecksumMaterialV3(
                ownerUid = "user-b",
                ownerGoogleSubjectHash = ValidGoogleSubjectHash,
                payloadJson = payloadJson,
            ),
        )

        assertNotEquals(userA, userB)
    }

    @Test
    fun automaticBundleChecksumChangesWhenGoogleSubjectHashChanges() {
        val payloadJson = emptyPayloadJson()
        val hashA = RestoreBundleWriter.sha256Hex(
            RestoreBundleWriter.automaticBundleChecksumMaterialV3(
                ownerUid = "user-a",
                ownerGoogleSubjectHash = ValidGoogleSubjectHash,
                payloadJson = payloadJson,
            ),
        )
        val hashB = RestoreBundleWriter.sha256Hex(
            RestoreBundleWriter.automaticBundleChecksumMaterialV3(
                ownerUid = "user-a",
                ownerGoogleSubjectHash = OtherValidGoogleSubjectHash,
                payloadJson = payloadJson,
            ),
        )

        assertNotEquals(hashA, hashB)
    }

    @Test
    fun automaticBundleChecksumDistinguishesNullFromRealGoogleSubjectHash() {
        val payloadJson = emptyPayloadJson()
        val nullHash = RestoreBundleWriter.sha256Hex(
            RestoreBundleWriter.automaticBundleChecksumMaterialV3(
                ownerUid = "user-a",
                ownerGoogleSubjectHash = null,
                payloadJson = payloadJson,
            ),
        )
        val realHash = RestoreBundleWriter.sha256Hex(
            RestoreBundleWriter.automaticBundleChecksumMaterialV3(
                ownerUid = "user-a",
                ownerGoogleSubjectHash = ValidGoogleSubjectHash,
                payloadJson = payloadJson,
            ),
        )

        assertNotEquals(nullHash, realHash)
    }

    @Test
    fun versionTwoChecksumMaterialRemainsOwnerUidNewlinePayloadJson() {
        val payloadJson = emptyPayloadJson()

        assertEquals(
            "user-a\n$payloadJson",
            RestoreBundleWriter.automaticBundleChecksumMaterialV2(
                ownerUid = "user-a",
                payloadJson = payloadJson,
            ),
        )
    }

    @Test
    fun manualEncryptedBackupPayloadSchemaVersionRemainsOne() {
        assertEquals(1, RestoreBundleWriter.SchemaVersion)
    }

    @Test
    fun sharedPayloadWriterIncludesVersionedSafeExitExtension() {
        val source =
            writerSource()

        assertEquals(
            1,
            RestoreBundleWriter
                .SchemaVersion,
        )

        assertTrue(
            source.contains(
                "SafeExitRestorePayloadCodec.JsonKey",
            ),
        )

        assertTrue(
            source.contains(
                ".safeExitDao()",
            ),
        )

        assertTrue(
            source.contains(
                ".getAllForBackup()",
            ),
        )
    }

    @Test
    fun automaticBundleBuilderAcceptsPayloadAtSharedUtf8Limit() {
        val payloadJson =
            "a".repeat(
                RestorePayloadSizePolicy
                    .MaximumPayloadBytes,
            )

        val bundle =
            RestoreBundleWriter
                .buildAutomaticBundleJson(
                    ownerUid =
                        "user-a",
                    ownerGoogleSubjectHash =
                        ValidGoogleSubjectHash,
                    payloadJson =
                        payloadJson,
                    createdAtMillis =
                        1_700_000_000_000L,
                )

        assertTrue(
            bundle.isNotBlank(),
        )
    }

    @Test
    fun automaticBundleBuilderRejectsPayloadOneUtf8ByteOverLimit() {
        val payloadJson =
            "a".repeat(
                RestorePayloadSizePolicy
                    .MaximumPayloadBytes +
                    1,
            )

        val error =
            assertThrows(
                IllegalArgumentException::class.java,
            ) {
                RestoreBundleWriter
                    .buildAutomaticBundleJson(
                        ownerUid =
                            "user-a",
                        ownerGoogleSubjectHash =
                            ValidGoogleSubjectHash,
                        payloadJson =
                            payloadJson,
                        createdAtMillis =
                            1_700_000_000_000L,
                    )
            }

        assertEquals(
            RestorePayloadSizePolicy
                .OversizedPayloadMessage,
            error.message,
        )
    }
    private fun writerSource(): String = File(
        "src/main/java/com/impulsive/app/backend/data/restore/RestoreBundleWriter.kt",
    ).readText()

    private fun emptyPayloadJson(): String =
        "{\"journalNotes\":[],\"checklistItems\":[],\"recoverySessions\":[],\"blockedDomains\":[]}"

    private companion object {
        const val ValidGoogleSubjectHash =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val OtherValidGoogleSubjectHash =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    }
}