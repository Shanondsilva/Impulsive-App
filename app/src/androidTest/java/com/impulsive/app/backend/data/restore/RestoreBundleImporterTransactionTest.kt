package com.impulsive.app.backend.data.restore

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.util.Base64
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.impulsive.app.backend.data.local.database.AppDatabase
import com.impulsive.app.backend.data.local.entity.BlockedDomainEntity
import com.impulsive.app.backend.data.local.entity.CloudRestoreProofType
import com.impulsive.app.backend.data.local.entity.CloudRestoreReceiptEntity
import com.impulsive.app.backend.data.local.entity.JournalChecklistItemEntity
import com.impulsive.app.backend.data.local.entity.JournalNoteEntity
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile

@RunWith(AndroidJUnit4::class)
class RestoreBundleImporterTransactionTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        restoreDirectory().deleteRecursively()
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
        restoreDirectory().deleteRecursively()
    }

    @Test
    fun importPayload_whenLateInsertFails_rollsBackAllEarlierWrites() = runBlocking {
        val seededDomainId = database.blockedDomainDao().insertForRestore(
            BlockedDomainEntity(
                domain = RollbackDomain,
                category = "default",
                isDefault = true,
                addedByUser = false,
                createdAtMillis = 1_000L,
            ),
        )
        val importer = RestoreBundleImporter(
            context,
            database,
        )

        assertFalse(importer.hasExistingUserData())

        val error = runCatching {
            importer.importPayload(validPayload())
        }.exceptionOrNull()

        assertTrue(error is SQLiteConstraintException)
        assertTrue(
            database.journalNoteDao()
                .getAllNotesForSync()
                .isEmpty(),
        )
        assertEquals(0, checklistItemCount())
        assertTrue(
            database.recoverySessionDao()
                .getAllSessions()
                .isEmpty(),
        )

        val remainingDomains = database.blockedDomainDao().getAll()
        assertEquals(1, remainingDomains.size)
        assertEquals(seededDomainId, remainingDomains.single().id)
        assertEquals(RollbackDomain, remainingDomains.single().domain)
        assertEquals("default", remainingDomains.single().category)
        assertTrue(remainingDomains.single().isDefault)
        assertFalse(remainingDomains.single().addedByUser)
    }

    @Test
    fun importPayload_whenJournalNoteCountExceedsLimit_rejectsBeforeDatabaseWrites() =
        runBlocking {
            val importer = RestoreBundleImporter(
                context,
                database,
            )
            val excessiveNotes = JSONArray().apply {
                repeat(10_001) {
                    put(JSONObject())
                }
            }
            val payload = JSONObject()
                .put("journalNotes", excessiveNotes)
                .put("checklistItems", JSONArray())
                .put("recoverySessions", JSONArray())
                .put("blockedDomains", JSONArray())

            val error = runCatching {
                importer.importPayload(payload)
            }.exceptionOrNull()

            assertTrue(error is IllegalArgumentException)
            assertEquals("Too many journal notes", error?.message)
            assertTrue(
                database.journalNoteDao()
                    .getAllNotesForSync()
                    .isEmpty(),
            )
            assertEquals(0, checklistItemCount())
            assertTrue(
                database.recoverySessionDao()
                    .getAllSessions()
                    .isEmpty(),
            )
            assertTrue(
                database.blockedDomainDao()
                    .getAll()
                    .isEmpty(),
            )
        }

    @Test
    fun importFrom_whenEnvelopeExceedsMaximumSize_rejectsWithoutImportingData() =
        runBlocking {
            val oversizedEnvelope = File.createTempFile(
                "oversized-manual-backup-",
                ".impulsivebackup",
                context.cacheDir,
            )

            try {
                RandomAccessFile(
                    oversizedEnvelope,
                    "rw",
                ).use { file ->
                    file.setLength(MaxManualEnvelopeBytesForTest + 1L)
                }

                val result = oversizedEnvelope.inputStream().use { input ->
                    ManualBackupManager(context).importFrom(
                        input,
                        "test-password".toCharArray(),
                    )
                }

                assertEquals(
                    ManualBackupManager.ImportResult.WrongPasswordOrCorrupted,
                    result,
                )
                assertTrue(
                    database.journalNoteDao()
                        .getAllNotesForSync()
                        .isEmpty(),
                )
                assertEquals(0, checklistItemCount())
                assertTrue(
                    database.recoverySessionDao()
                        .getAllSessions()
                        .isEmpty(),
                )
                assertTrue(
                    database.blockedDomainDao()
                        .getAll()
                        .none { domain ->
                            domain.addedByUser
                        },
                )
            } finally {
                oversizedEnvelope.delete()
            }
        }

    @Test
    fun importFrom_whenKdfIterationsDoNotMatchFormatV1_rejectsWithoutImportingData() =
        runBlocking {
            val envelope = JSONObject()
                .put("format", "impulsive-backup")
                .put("formatVersion", 1)
                .put("schemaVersion", 1)
                .put("kdf", "PBKDF2WithHmacSHA256")
                .put("kdfIterations", 199_999)
                .put("saltBase64", "AAAAAAAAAAAAAAAAAAAAAA==")
                .put("ivBase64", "AAAAAAAAAAAAAAAA")
                .put("cipherTextBase64", "AAAAAAAAAAAAAAAAAAAAAA==")
            val backupFile = File.createTempFile(
                "invalid-kdf-manual-backup-",
                ".impulsivebackup",
                context.cacheDir,
            )

            try {
                backupFile.writeText(
                    envelope.toString(),
                    Charsets.UTF_8,
                )

                val result = backupFile.inputStream().use { input ->
                    ManualBackupManager(context).importFrom(
                        input,
                        "test-password".toCharArray(),
                    )
                }

                assertEquals(
                    ManualBackupManager.ImportResult.WrongPasswordOrCorrupted,
                    result,
                )
                assertTrue(
                    database.journalNoteDao()
                        .getAllNotesForSync()
                        .isEmpty(),
                )
                assertEquals(0, checklistItemCount())
                assertTrue(
                    database.recoverySessionDao()
                        .getAllSessions()
                        .isEmpty(),
                )
                assertTrue(
                    database.blockedDomainDao()
                        .getAll()
                        .none { domain ->
                            domain.addedByUser
                        },
                )
            } finally {
                backupFile.delete()
            }
        }

    @Test
    fun importFrom_whenSaltLengthIsInvalid_rejectsWithoutImportingData() =
        runBlocking {
            val envelope = JSONObject()
                .put("format", "impulsive-backup")
                .put("formatVersion", 1)
                .put("schemaVersion", 1)
                .put("kdf", "PBKDF2WithHmacSHA256")
                .put("kdfIterations", 200_000)
                .put(
                    "saltBase64",
                    Base64.encodeToString(
                        ByteArray(15),
                        Base64.NO_WRAP,
                    ),
                )
                .put(
                    "ivBase64",
                    Base64.encodeToString(
                        ByteArray(12),
                        Base64.NO_WRAP,
                    ),
                )
                .put(
                    "cipherTextBase64",
                    Base64.encodeToString(
                        ByteArray(16),
                        Base64.NO_WRAP,
                    ),
                )
            val backupFile = File.createTempFile(
                "invalid-salt-manual-backup-",
                ".impulsivebackup",
                context.cacheDir,
            )

            try {
                backupFile.writeText(
                    envelope.toString(),
                    Charsets.UTF_8,
                )

                val result = backupFile.inputStream().use { input ->
                    ManualBackupManager(context).importFrom(
                        input,
                        "test-password".toCharArray(),
                    )
                }

                assertEquals(
                    ManualBackupManager.ImportResult.WrongPasswordOrCorrupted,
                    result,
                )
                assertTrue(
                    database.journalNoteDao()
                        .getAllNotesForSync()
                        .isEmpty(),
                )
                assertEquals(0, checklistItemCount())
                assertTrue(
                    database.recoverySessionDao()
                        .getAllSessions()
                        .isEmpty(),
                )
                assertTrue(
                    database.blockedDomainDao()
                        .getAll()
                        .none { domain ->
                            domain.addedByUser
                        },
                )
            } finally {
                backupFile.delete()
            }
        }

    @Test
    fun importFrom_whenIvLengthIsInvalid_rejectsWithoutImportingData() =
        runBlocking {
            val envelope = JSONObject()
                .put("format", "impulsive-backup")
                .put("formatVersion", 1)
                .put("schemaVersion", 1)
                .put("kdf", "PBKDF2WithHmacSHA256")
                .put("kdfIterations", 200_000)
                .put(
                    "saltBase64",
                    Base64.encodeToString(
                        ByteArray(16),
                        Base64.NO_WRAP,
                    ),
                )
                .put(
                    "ivBase64",
                    Base64.encodeToString(
                        ByteArray(11),
                        Base64.NO_WRAP,
                    ),
                )
                .put(
                    "cipherTextBase64",
                    Base64.encodeToString(
                        ByteArray(16),
                        Base64.NO_WRAP,
                    ),
                )
            val backupFile = File.createTempFile(
                "invalid-iv-manual-backup-",
                ".impulsivebackup",
                context.cacheDir,
            )

            try {
                backupFile.writeText(
                    envelope.toString(),
                    Charsets.UTF_8,
                )

                val result = backupFile.inputStream().use { input ->
                    ManualBackupManager(context).importFrom(
                        input,
                        "test-password".toCharArray(),
                    )
                }

                assertEquals(
                    ManualBackupManager.ImportResult.WrongPasswordOrCorrupted,
                    result,
                )
                assertTrue(
                    database.journalNoteDao()
                        .getAllNotesForSync()
                        .isEmpty(),
                )
                assertEquals(0, checklistItemCount())
                assertTrue(
                    database.recoverySessionDao()
                        .getAllSessions()
                        .isEmpty(),
                )
                assertTrue(
                    database.blockedDomainDao()
                        .getAll()
                        .none { domain ->
                            domain.addedByUser
                        },
                )
            } finally {
                backupFile.delete()
            }
        }

    @Test
    fun importFrom_whenSaltBase64IsNonCanonical_rejectsWithoutImportingData() =
        runBlocking {
            val nonCanonicalSalt =
                Base64.encodeToString(
                    ByteArray(16),
                    Base64.NO_WRAP,
                ) + "\n"
            val envelope = JSONObject()
                .put("format", "impulsive-backup")
                .put("formatVersion", 1)
                .put("schemaVersion", 1)
                .put("kdf", "PBKDF2WithHmacSHA256")
                .put("kdfIterations", 200_000)
                .put("saltBase64", nonCanonicalSalt)
                .put(
                    "ivBase64",
                    Base64.encodeToString(
                        ByteArray(12),
                        Base64.NO_WRAP,
                    ),
                )
                .put(
                    "cipherTextBase64",
                    Base64.encodeToString(
                        ByteArray(16),
                        Base64.NO_WRAP,
                    ),
                )
            val backupFile = File.createTempFile(
                "noncanonical-base64-manual-backup-",
                ".impulsivebackup",
                context.cacheDir,
            )

            try {
                backupFile.writeText(
                    envelope.toString(),
                    Charsets.UTF_8,
                )

                val result = backupFile.inputStream().use { input ->
                    ManualBackupManager(context).importFrom(
                        input,
                        "test-password".toCharArray(),
                    )
                }

                assertEquals(
                    ManualBackupManager.ImportResult.WrongPasswordOrCorrupted,
                    result,
                )
                assertTrue(
                    database.journalNoteDao()
                        .getAllNotesForSync()
                        .isEmpty(),
                )
                assertEquals(0, checklistItemCount())
                assertTrue(
                    database.recoverySessionDao()
                        .getAllSessions()
                        .isEmpty(),
                )
                assertTrue(
                    database.blockedDomainDao()
                        .getAll()
                        .none { domain ->
                            domain.addedByUser
                        },
                )
            } finally {
                backupFile.delete()
            }
        }

    @Test
    fun importPayload_whenJournalNoteBodyExceedsMaximumLength_rejectsBeforeDatabaseWrites() =
        runBlocking {
            val importer = RestoreBundleImporter(
                context,
                database,
            )
            val payload = JSONObject()
                .put(
                    "journalNotes",
                    JSONArray().put(
                        JSONObject()
                            .put("id", 201L)
                            .put("noteType", "TEXT")
                            .put("title", "Oversized body test")
                            .put("body", "a".repeat(100_001))
                            .put("createdAtMillis", 5_000L)
                            .put("updatedAtMillis", 5_100L),
                    ),
                )
                .put("checklistItems", JSONArray())
                .put("recoverySessions", JSONArray())
                .put("blockedDomains", JSONArray())

            val error = runCatching {
                importer.importPayload(payload)
            }.exceptionOrNull()

            assertTrue(error is IllegalArgumentException)
            assertEquals(
                "body exceeds maximum allowed length",
                error?.message,
            )
            assertTrue(
                database.journalNoteDao()
                    .getAllNotesForSync()
                    .isEmpty(),
            )
            assertEquals(0, checklistItemCount())
            assertTrue(
                database.recoverySessionDao()
                    .getAllSessions()
                    .isEmpty(),
            )
            assertTrue(
                database.blockedDomainDao()
                    .getAll()
                    .none { domain ->
                        domain.addedByUser
                    },
            )
        }

    @Test
    fun importPayload_whenChecklistItemReferencesMissingNote_rejectsBeforeDatabaseWrites() =
        runBlocking {
            val importer = RestoreBundleImporter(
                context,
                database,
            )
            val payload = JSONObject()
                .put(
                    "journalNotes",
                    JSONArray().put(
                        JSONObject()
                            .put("id", 100L)
                            .put("noteType", "CHECKLIST")
                            .put("title", "Checklist relationship test")
                            .put("createdAtMillis", 6_000L)
                            .put("updatedAtMillis", 6_100L),
                    ),
                )
                .put(
                    "checklistItems",
                    JSONArray().put(
                        JSONObject()
                            .put("noteId", 999L)
                            .put("text", "Orphan checklist item")
                            .put("isChecked", false)
                            .put("sortOrder", 0L)
                            .put("createdAtMillis", 6_000L)
                            .put("updatedAtMillis", 6_100L),
                    ),
                )
                .put("recoverySessions", JSONArray())
                .put("blockedDomains", JSONArray())

            val error = runCatching {
                importer.importPayload(payload)
            }.exceptionOrNull()

            assertTrue(error is IllegalArgumentException)
            assertEquals(
                "Checklist item references unknown journal note",
                error?.message,
            )
            assertTrue(
                database.journalNoteDao()
                    .getAllNotesForSync()
                    .isEmpty(),
            )
            assertEquals(0, checklistItemCount())
            assertTrue(
                database.recoverySessionDao()
                    .getAllSessions()
                    .isEmpty(),
            )
            assertTrue(
                database.blockedDomainDao()
                    .getAll()
                    .none { domain ->
                        domain.addedByUser
                    },
            )
        }

    @Test
    fun importPayload_whenJournalNotesContainDuplicateOriginalIds_rejectsBeforeDatabaseWrites() =
        runBlocking {
            val importer = RestoreBundleImporter(
                context,
                database,
            )
            val duplicateNoteId = 300L
            val payload = JSONObject()
                .put(
                    "journalNotes",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("id", duplicateNoteId)
                                .put("noteType", "TEXT")
                                .put("title", "First duplicate-ID note")
                                .put("createdAtMillis", 7_000L)
                                .put("updatedAtMillis", 7_100L),
                        )
                        .put(
                            JSONObject()
                                .put("id", duplicateNoteId)
                                .put("noteType", "TEXT")
                                .put("title", "Second duplicate-ID note")
                                .put("createdAtMillis", 8_000L)
                                .put("updatedAtMillis", 8_100L),
                        ),
                )
                .put("checklistItems", JSONArray())
                .put("recoverySessions", JSONArray())
                .put("blockedDomains", JSONArray())

            val error = runCatching {
                importer.importPayload(payload)
            }.exceptionOrNull()

            assertTrue(error is IllegalArgumentException)
            assertEquals(
                "Duplicate journal note id",
                error?.message,
            )
            assertTrue(
                database.journalNoteDao()
                    .getAllNotesForSync()
                    .isEmpty(),
            )
            assertEquals(0, checklistItemCount())
            assertTrue(
                database.recoverySessionDao()
                    .getAllSessions()
                    .isEmpty(),
            )
            assertTrue(
                database.blockedDomainDao()
                    .getAll()
                    .none { domain ->
                        domain.addedByUser
                    },
            )
        }

    @Test
    fun importPayload_whenJournalNoteFieldHasWrongType_rejectsBeforeDatabaseWrites() =
        runBlocking {
            val importer = RestoreBundleImporter(
                context,
                database,
            )
            val payload = JSONObject()
                .put(
                    "journalNotes",
                    JSONArray().put(
                        JSONObject()
                            .put("id", 400L)
                            .put("noteType", "TEXT")
                            .put("title", "Wrong field type test")
                            .put("createdAtMillis", "9_000")
                            .put("updatedAtMillis", 9_100L),
                    ),
                )
                .put("checklistItems", JSONArray())
                .put("recoverySessions", JSONArray())
                .put("blockedDomains", JSONArray())

            val error = runCatching {
                importer.importPayload(payload)
            }.exceptionOrNull()

            assertTrue(error is IllegalArgumentException)
            assertEquals(
                "createdAtMillis must be an integer",
                error?.message,
            )
            assertTrue(
                database.journalNoteDao()
                    .getAllNotesForSync()
                    .isEmpty(),
            )
            assertEquals(0, checklistItemCount())
            assertTrue(
                database.recoverySessionDao()
                    .getAllSessions()
                    .isEmpty(),
            )
            assertTrue(
                database.blockedDomainDao()
                    .getAll()
                    .none { domain ->
                        domain.addedByUser
                    },
            )
        }

    @Test
    fun importIfNeeded_whenVersionThreeAutomaticBundleOwnerMatches_restoresAndDeletesBundle() =
        runBlocking {
            writeAutomaticBundle(
                ownerUid = "user-a",
                ownerGoogleSubjectHash = ValidGoogleSubjectHash,
                payloadJson = emptyPayloadJson(),
            )
            val importer = RestoreBundleImporter(
                context,
                database,
            )

            val result = importer.importIfNeeded(
                ownerProof = AutoRestoreOwnerProof.ExactUid(
                    currentUid = "user-a",
                ),
            )

            assertEquals(AutoRestoreResult.Restored, result)
            assertFalse(restoreBundleFile().exists())
            assertDatabaseEmpty()
        }

    @Test
    fun importIfNeeded_whenVersionTwoAutomaticBundleOwnerMatches_restoresAndDeletesBundle() =
        runBlocking {
            writeAutomaticBundle(
                formatVersion = 2,
                ownerUid = "user-a",
                payloadJson = emptyPayloadJson(),
            )
            val importer = RestoreBundleImporter(
                context,
                database,
            )

            val result = importer.importIfNeeded(
                ownerProof = AutoRestoreOwnerProof.ExactUid(
                    currentUid = "user-a",
                ),
            )

            assertEquals(AutoRestoreResult.Restored, result)
            assertFalse(restoreBundleFile().exists())
            assertDatabaseEmpty()
        }

    @Test
    fun importIfNeeded_whenAutomaticBundleOwnerDiffers_preservesBundleWithoutImporting() =
        runBlocking {
            writeAutomaticBundle(
                ownerUid = "user-a",
                ownerGoogleSubjectHash = ValidGoogleSubjectHash,
                payloadJson = payloadWithOneRecoverySessionJson(),
            )
            val importer = RestoreBundleImporter(
                context,
                database,
            )

            val result = importer.importIfNeeded(
                ownerProof = AutoRestoreOwnerProof.ExactUid(
                    currentUid = "user-b",
                ),
            )

            assertEquals(AutoRestoreResult.OwnerMismatch, result)
            assertTrue(restoreBundleFile().exists())
            assertDatabaseEmpty()
        }

    @Test
    fun importIfNeeded_whenAutomaticBundleIsLegacyUnowned_preservesBundleWithoutImporting() =
        runBlocking {
            writeLegacyAutomaticBundle(
                payloadJson = payloadWithOneRecoverySessionJson(),
            )
            val importer = RestoreBundleImporter(
                context,
                database,
            )

            val result = importer.importIfNeeded(
                ownerProof = AutoRestoreOwnerProof.ExactUid(
                    currentUid = "user-a",
                ),
            )

            assertEquals(AutoRestoreResult.LegacyUnownedBundle, result)
            assertTrue(restoreBundleFile().exists())
            assertDatabaseEmpty()
        }

    @Test
    fun importIfNeeded_whenVersionThreeGoogleHashMatchesConfirmedProof_restores() =
        runBlocking {
            writeAutomaticBundle(
                ownerUid = "old-user",
                ownerGoogleSubjectHash = ValidGoogleSubjectHash,
                payloadJson = emptyPayloadJson(),
            )
            val importer = RestoreBundleImporter(
                context,
                database,
            )

            val result = importer.importIfNeeded(
                ownerProof = AutoRestoreOwnerProof.ConfirmedSameGoogleIdentity(
                    currentUid = "new-user",
                    currentGoogleSubjectHash = ValidGoogleSubjectHash,
                ),
            )

            assertEquals(AutoRestoreResult.Restored, result)
            assertFalse(restoreBundleFile().exists())
            assertDatabaseEmpty()
        }

    @Test
    fun importIfNeeded_whenVersionThreeGoogleHashDiffersFromConfirmedProof_preservesBundle() =
        runBlocking {
            writeAutomaticBundle(
                ownerUid = "old-user",
                ownerGoogleSubjectHash = OtherValidGoogleSubjectHash,
                payloadJson = payloadWithOneRecoverySessionJson(),
            )
            val importer = RestoreBundleImporter(
                context,
                database,
            )

            val result = importer.importIfNeeded(
                ownerProof = AutoRestoreOwnerProof.ConfirmedSameGoogleIdentity(
                    currentUid = "new-user",
                    currentGoogleSubjectHash = ValidGoogleSubjectHash,
                ),
            )

            assertEquals(AutoRestoreResult.OwnerMismatch, result)
            assertTrue(restoreBundleFile().exists())
            assertDatabaseEmpty()
        }

    @Test
    fun importIfNeeded_whenVersionTwoBundleUsesSameGoogleProofRequiresLegacyVerification() =
        runBlocking {
            writeAutomaticBundle(
                formatVersion = 2,
                ownerUid = "old-user",
                payloadJson = payloadWithOneRecoverySessionJson(),
            )
            val importer = RestoreBundleImporter(
                context,
                database,
            )

            val result = importer.importIfNeeded(
                ownerProof = AutoRestoreOwnerProof.ConfirmedSameGoogleIdentity(
                    currentUid = "new-user",
                    currentGoogleSubjectHash = ValidGoogleSubjectHash,
                ),
            )

            assertEquals(AutoRestoreResult.LegacyOwnerVerificationRequired, result)
            assertTrue(restoreBundleFile().exists())
            assertDatabaseEmpty()
        }

    @Test
    fun importIfNeeded_whenVersionThreeBundleHasNullGoogleHashRequiresLegacyVerification() =
        runBlocking {
            writeAutomaticBundle(
                ownerUid = "old-user",
                ownerGoogleSubjectHash = null,
                payloadJson = payloadWithOneRecoverySessionJson(),
            )
            val importer = RestoreBundleImporter(
                context,
                database,
            )

            val result = importer.importIfNeeded(
                ownerProof = AutoRestoreOwnerProof.ConfirmedSameGoogleIdentity(
                    currentUid = "new-user",
                    currentGoogleSubjectHash = ValidGoogleSubjectHash,
                ),
            )

            assertEquals(AutoRestoreResult.LegacyOwnerVerificationRequired, result)
            assertTrue(restoreBundleFile().exists())
            assertDatabaseEmpty()
        }

    @Test
    fun importIfNeeded_whenAutomaticBundleOwnerBoundChecksumIsCorrupted_deletesInvalidBundle() =
        runBlocking {
            writeAutomaticBundle(
                ownerUid = "user-a",
                ownerGoogleSubjectHash = ValidGoogleSubjectHash,
                payloadJson = payloadWithOneRecoverySessionJson(),
                checksumOverride = "0".repeat(64),
            )
            val importer = RestoreBundleImporter(
                context,
                database,
            )

            val result = importer.importIfNeeded(
                ownerProof = AutoRestoreOwnerProof.ExactUid(
                    currentUid = "user-a",
                ),
            )

            assertEquals(AutoRestoreResult.InvalidBundle, result)
            assertFalse(restoreBundleFile().exists())
            assertDatabaseEmpty()
        }

    private fun writeAutomaticBundle(
        ownerUid: String,
        payloadJson: String,
        ownerGoogleSubjectHash: String? = null,
        formatVersion: Int = RestoreBundleWriter.AutoBundleFormatVersion,
        checksumOverride: String? = null,
    ) {
        val checksumMaterial = when (formatVersion) {
            2 -> RestoreBundleWriter.automaticBundleChecksumMaterialV2(
                ownerUid = ownerUid,
                payloadJson = payloadJson,
            )

            3 -> RestoreBundleWriter.automaticBundleChecksumMaterialV3(
                ownerUid = ownerUid,
                ownerGoogleSubjectHash = ownerGoogleSubjectHash,
                payloadJson = payloadJson,
            )

            else -> error("Unsupported test bundle format")
        }
        val bundle = JSONObject()
            .put("autoBundleFormatVersion", formatVersion)
            .put("ownerUid", ownerUid)
            .put("schemaVersion", RestoreBundleWriter.SchemaVersion)
            .put("createdAtMillis", 1_700_000_000_000L)
            .put("payloadJson", payloadJson)
            .put(
                "checksumSha256",
                checksumOverride ?: RestoreBundleWriter.sha256Hex(
                    checksumMaterial,
                ),
            )
        if (formatVersion == 3) {
            bundle.put(
                "ownerGoogleSubjectHash",
                ownerGoogleSubjectHash ?: JSONObject.NULL,
            )
        }
        writeRestoreBundle(bundle)
    }

    private fun writeLegacyAutomaticBundle(payloadJson: String) {
        val bundle = JSONObject()
            .put("schemaVersion", RestoreBundleWriter.SchemaVersion)
            .put("createdAtMillis", 1_700_000_000_000L)
            .put("payloadJson", payloadJson)
            .put(
                "checksumSha256",
                RestoreBundleWriter.sha256Hex(payloadJson),
            )
        writeRestoreBundle(bundle)
    }

    private fun writeRestoreBundle(bundle: JSONObject) {
        val directory = restoreDirectory()
        directory.mkdirs()
        restoreBundleFile().writeText(
            bundle.toString(),
            Charsets.UTF_8,
        )
    }

    private fun emptyPayloadJson(): String = JSONObject()
        .put("journalNotes", JSONArray())
        .put("checklistItems", JSONArray())
        .put("recoverySessions", JSONArray())
        .put("blockedDomains", JSONArray())
        .toString()

    private fun payloadWithOneRecoverySessionJson(): String = JSONObject()
        .put("journalNotes", JSONArray())
        .put("checklistItems", JSONArray())
        .put(
            "recoverySessions",
            JSONArray().put(
                JSONObject()
                    .put("startedAt", 11_000L)
                    .put("completedAt", 101_000L)
                    .put("durationSeconds", 90),
            ),
        )
        .put("blockedDomains", JSONArray())
        .toString()

    private suspend fun assertDatabaseEmpty() {
        assertTrue(
            database.journalNoteDao()
                .getAllNotesForSync()
                .isEmpty(),
        )
        assertEquals(0, checklistItemCount())
        assertTrue(
            database.recoverySessionDao()
                .getAllSessions()
                .isEmpty(),
        )
        assertTrue(
            database.blockedDomainDao()
                .getAll()
                .none { domain -> domain.addedByUser },
        )
    }

    private fun restoreDirectory(): File = File(
        context.filesDir,
        RestoreBundleWriter.DirectoryName,
    )

    private fun restoreBundleFile(): File = File(
        restoreDirectory(),
        RestoreBundleWriter.FileName,
    )

    private fun validPayload(): JSONObject =
        JSONObject()
            .put(
                "journalNotes",
                JSONArray().put(
                    JSONObject()
                        .put("id", 101L)
                        .put("noteType", "CHECKLIST")
                        .put("title", "Rollback test note")
                        .put("createdAtMillis", 2_000L)
                        .put("updatedAtMillis", 2_100L),
                ),
            )
            .put(
                "checklistItems",
                JSONArray().put(
                    JSONObject()
                        .put("noteId", 101L)
                        .put("text", "Rollback test item")
                        .put("sortOrder", 0L)
                        .put("createdAtMillis", 2_000L)
                        .put("updatedAtMillis", 2_100L),
                ),
            )
            .put(
                "recoverySessions",
                JSONArray().put(
                    JSONObject()
                        .put("startedAt", 3_000L)
                        .put("completedAt", 93_000L)
                        .put("durationSeconds", 90),
                ),
            )
            .put(
                "blockedDomains",
                JSONArray().put(
                    JSONObject()
                        .put("domain", RollbackDomain)
                        .put("category", "restored")
                        .put("isDefault", false)
                        .put("addedByUser", true)
                        .put("createdAtMillis", 4_000L),
                ),
            )

    private fun checklistItemCount(): Int =
        database.openHelper
            .readableDatabase
            .query("SELECT COUNT(*) FROM journal_checklist_items")
            .use { cursor ->
                check(cursor.moveToFirst())
                cursor.getInt(0)
            }

    @Test
    fun cloudReceiptCommitsWithSuccessfulImport() = runBlocking {
        val receipt = cloudReceipt()

        val result =
            RestoreBundleImporter(context, database).importPayload(
                parsed = validPayload(),
                cloudRestoreReceipt = receipt,
            )

        assertEquals(
            RestoreBundleImporter.ImportOutcome.Success,
            result,
        )
        assertEquals(
            receipt,
            database.cloudRestoreReceiptDao().find(receipt.receiptId),
        )
    }

    @Test
    fun receiptInsertionFailureRollsBackAllRestoredInserts() = runBlocking {
        val receipt = cloudReceipt()
        database.cloudRestoreReceiptDao().insert(receipt)

        val error =
            runCatching {
                RestoreBundleImporter(context, database).importPayload(
                    parsed = validPayload(),
                    cloudRestoreReceipt = receipt,
                )
            }.exceptionOrNull()

        assertTrue(error is SQLiteConstraintException)
        assertTrue(
            database.journalNoteDao()
                .getAllNotesForSync()
                .isEmpty(),
        )
        assertEquals(0, checklistItemCount())
        assertTrue(
            database.recoverySessionDao()
                .getAllSessions()
                .isEmpty(),
        )
        assertTrue(database.blockedDomainDao().getAll().isEmpty())
        assertEquals(
            receipt,
            database.cloudRestoreReceiptDao().latest(),
        )
    }

    @Test
    fun invalidImportCreatesNoCloudReceipt() = runBlocking {
        val malformed =
            JSONObject()
                .put("journalNotes", "not-an-array")
                .put("checklistItems", JSONArray())
                .put("recoverySessions", JSONArray())
                .put("blockedDomains", JSONArray())

        val error =
            runCatching {
                RestoreBundleImporter(context, database).importPayload(
                    parsed = malformed,
                    cloudRestoreReceipt = cloudReceipt(),
                )
            }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertNull(database.cloudRestoreReceiptDao().latest())
    }

    @Test
    fun existingDataRejectionCreatesNoCloudReceipt() = runBlocking {
        database.journalNoteDao().insert(
            JournalNoteEntity(
                noteType = "TEXT",
                title = "Existing note",
                source = "normal_journal",
                createdAtMillis = 1L,
                updatedAtMillis = 1L,
            ),
        )

        val result =
            RestoreBundleImporter(context, database).importPayload(
                parsed = validPayload(),
                cloudRestoreReceipt = cloudReceipt(),
            )

        assertEquals(
            RestoreBundleImporter.ImportOutcome.ExistingDataPresent,
            result,
        )
        assertNull(database.cloudRestoreReceiptDao().latest())
    }

    @Test
    fun nonCloudImportsCreateNoCloudReceipt() = runBlocking {
        val result =
            RestoreBundleImporter(context, database).importPayload(
                parsed = validPayload(),
            )

        assertEquals(
            RestoreBundleImporter.ImportOutcome.Success,
            result,
        )
        assertNull(database.cloudRestoreReceiptDao().latest())
    }

    @Test
    fun receiptFieldsExactlyMatchAuthorizedProofAndPayloadHash() =
        runBlocking {
            val receipt =
                cloudReceipt().copy(
                    proofType =
                        CloudRestoreProofType
                            .SameGoogleIdentity
                            .persistedValue,
                    previousUid = "previous-user",
                    previousGoogleSubjectHash =
                        ValidGoogleSubjectHash,
                    currentGoogleSubjectHash =
                        ValidGoogleSubjectHash,
                )

            RestoreBundleImporter(context, database).importPayload(
                parsed = validPayload(),
                cloudRestoreReceipt = receipt,
            )

            assertEquals(
                receipt,
                database.cloudRestoreReceiptDao()
                    .find(receipt.receiptId),
            )
        }

    @Test
    fun clearAllTablesRemovesCloudRestoreReceipts() = runBlocking {
        database.cloudRestoreReceiptDao().insert(cloudReceipt())

        database.clearAllTables()

        assertNull(database.cloudRestoreReceiptDao().latest())
    }

    private fun cloudReceipt() =
        CloudRestoreReceiptEntity(
            receiptId =
                "123e4567-e89b-12d3-a456-426614174000",
            payloadSha256 =
                "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
            proofType =
                CloudRestoreProofType.ExactUid.persistedValue,
            previousUid = null,
            previousGoogleSubjectHash = null,
            currentUid = "current-user",
            currentGoogleSubjectHash =
                ValidGoogleSubjectHash,
            importedAtMillis = 123L,
        )

@Test
fun replacementPreservesFeedbackNoteAndItsChecklistItems() =
    runBlocking {
        val journalDao =
            database.journalNoteDao()

        val feedbackNoteId =
            journalDao.insert(
                JournalNoteEntity(
                    noteType =
                        "FEEDBACK",

                    title =
                        "Preserved feedback",

                    body =
                        "Not part of RestoreBundle",

                    source =
                        "feedback_notification",

                    createdAtMillis =
                        1_000L,

                    updatedAtMillis =
                        1_000L,
                ),
            )

        journalDao.insertChecklistItems(
            listOf(
                JournalChecklistItemEntity(
                    noteId =
                        feedbackNoteId,

                    text =
                        "Preserved feedback checklist",

                    sortOrder =
                        0L,

                    createdAtMillis =
                        1_000L,

                    updatedAtMillis =
                        1_000L,
                ),
            ),
        )

        val ordinaryNoteId =
            journalDao.insert(
                JournalNoteEntity(
                    noteType =
                        "CHECKLIST",

                    title =
                        "Replace me",

                    source =
                        "normal_journal",

                    createdAtMillis =
                        2_000L,

                    updatedAtMillis =
                        2_000L,
                ),
            )

        journalDao.insertChecklistItems(
            listOf(
                JournalChecklistItemEntity(
                    noteId =
                        ordinaryNoteId,

                    text =
                        "Replace this checklist item",

                    sortOrder =
                        0L,

                    createdAtMillis =
                        2_000L,

                    updatedAtMillis =
                        2_000L,
                ),
            ),
        )

        val result =
            RestoreBundleImporter(
                context,
                database,
            ).importPayload(
                parsed =
                    JSONObject(
                        emptyPayloadJson(),
                    ),

                mode =
                    RestoreBundleImporter
                        .ImportMode
                        .ReplaceRestoreBundleData,
            )

        assertEquals(
            RestoreBundleImporter
                .ImportOutcome
                .Success,
            result,
        )

        assertTrue(
            journalDao
                .getAllNotesForSync()
                .isEmpty(),
        )

        val preservedFeedback =
            journalDao
                .getObsoleteFeedbackNotes()

        assertEquals(
            1,
            preservedFeedback.size,
        )

        assertEquals(
            feedbackNoteId,
            preservedFeedback
                .single()
                .id,
        )

        val preservedChecklist =
            journalDao
                .getChecklistItems(
                    feedbackNoteId,
                )

        assertEquals(
            1,
            preservedChecklist.size,
        )

        assertEquals(
            "Preserved feedback checklist",
            preservedChecklist
                .single()
                .text,
        )

        assertEquals(
            1,
            checklistItemCount(),
        )
    }

    private companion object {
        const val RollbackDomain = "rollback-test.example"
        const val MaxManualEnvelopeBytesForTest = 12 * 1024 * 1024
        const val ValidGoogleSubjectHash =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val OtherValidGoogleSubjectHash =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    }
}
