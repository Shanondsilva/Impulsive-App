package com.impulsive.app.backend.data.restore

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreBundleWriterAutoBundleTest {
    @Test
    fun automaticBundleSourceContainsFormatOwnerAndPayloadSchemaVersions() {
        val source = File(
            "src/main/java/com/impulsive/app/backend/data/restore/RestoreBundleWriter.kt",
        ).readText()

        assertEquals(2, RestoreBundleWriter.AutoBundleFormatVersion)
        assertEquals(1, RestoreBundleWriter.SchemaVersion)
        assertTrue(source.contains(".put(\"autoBundleFormatVersion\", AutoBundleFormatVersion)"))
        assertTrue(source.contains(".put(\"ownerUid\", normalizedOwnerUid)"))
        assertTrue(source.contains(".put(\"schemaVersion\", SchemaVersion)"))
    }

    @Test
    fun automaticBundleChecksumChangesWhenOwnerUidChanges() {
        val payloadJson = emptyPayloadJson()
        val userA = RestoreBundleWriter.sha256Hex(
            RestoreBundleWriter.automaticBundleChecksumMaterial(
                ownerUid = "user-a",
                payloadJson = payloadJson,
            ),
        )
        val userB = RestoreBundleWriter.sha256Hex(
            RestoreBundleWriter.automaticBundleChecksumMaterial(
                ownerUid = "user-b",
                payloadJson = payloadJson,
            ),
        )

        assertNotEquals(userA, userB)
    }

    @Test
    fun automaticBundleChecksumMaterialIncludesOwnerUidAndPayloadJson() {
        val payloadJson = emptyPayloadJson()

        assertEquals(
            "user-a\n$payloadJson",
            RestoreBundleWriter.automaticBundleChecksumMaterial(
                ownerUid = "user-a",
                payloadJson = payloadJson,
            ),
        )
    }

    @Test
    fun manualEncryptedBackupPayloadSchemaVersionRemainsOne() {
        assertEquals(1, RestoreBundleWriter.SchemaVersion)
    }

    private fun emptyPayloadJson(): String =
        "{\"journalNotes\":[],\"checklistItems\":[],\"recoverySessions\":[],\"blockedDomains\":[]}"
}