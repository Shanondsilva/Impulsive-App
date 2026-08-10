package com.impulsive.app.backend.data.restore

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.impulsive.app.backend.data.local.database.AppDatabase
import com.impulsive.app.backend.data.local.entity.SafeExitEntity
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SafeExitBackupRestoreInstrumentedTest {
    private lateinit var context:
        Context

    private lateinit var database:
        AppDatabase

    private lateinit var importer:
        RestoreBundleImporter

    @Before
    fun setUp() {
        context =
            ApplicationProvider
                .getApplicationContext()

        database =
            Room
                .inMemoryDatabaseBuilder(
                    context,
                    AppDatabase::class.java,
                )
                .build()

        importer =
            RestoreBundleImporter(
                context,
                database,
                recoverAdaptiveObservations = {},
                recoverPathShift = {},
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun safeExitExtensionRestoresCanonicalRecords() =
        runBlocking {
            val older =
                record(
                    sourceKey =
                        "reset_reading:2001",
                    source =
                        "reset_reading",
                    sourceId =
                        "2001",
                    completedAt =
                        "2026-08-02T11:00",
                )

            val newer =
                record(
                    sourceKey =
                        "moment_plan:decision-2002",
                    source =
                        "moment_plan",
                    sourceId =
                        "decision-2002",
                    completedAt =
                        "2026-08-03T11:00",
                )

            val result =
                importer.importPayload(
                    basePayload()
                        .put(
                            SafeExitRestorePayloadCodec
                                .JsonKey,
                            SafeExitRestorePayloadCodec
                                .encode(
                                    listOf(
                                        older,
                                        newer,
                                    ),
                                ),
                        ),
                )

            assertEquals(
                RestoreBundleImporter
                    .ImportOutcome
                    .Success,
                result,
            )

            assertEquals(
                listOf(
                    newer,
                    older,
                ),
                database
                    .safeExitDao()
                    .getAllForBackup(),
            )
        }

    @Test
    fun explicitEmptySnapshotClearsLedgerInReplaceMode() =
        runBlocking {
            database
                .safeExitDao()
                .insertForRestore(
                    listOf(
                        record(
                            sourceKey =
                                "reset_reading:2003",
                            source =
                                "reset_reading",
                            sourceId =
                                "2003",
                            completedAt =
                                "2026-08-03T11:05",
                        ),
                    ),
                )

            val result =
                importer.importPayload(
                    parsed =
                        basePayload()
                            .put(
                                SafeExitRestorePayloadCodec
                                    .JsonKey,
                                SafeExitRestorePayloadCodec
                                    .encode(
                                        emptyList(),
                                    ),
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

            assertEquals(
                0,
                database
                    .safeExitDao()
                    .count(),
            )
        }

    @Test
    fun historicalPayloadWithoutExtensionPreservesLedgerInReplaceMode() =
        runBlocking {
            val existing =
                record(
                    sourceKey =
                        "moment_plan:decision-2004",
                    source =
                        "moment_plan",
                    sourceId =
                        "decision-2004",
                    completedAt =
                        "2026-08-03T11:10",
                )

            database
                .safeExitDao()
                .insertForRestore(
                    listOf(
                        existing,
                    ),
                )

            val result =
                importer.importPayload(
                    parsed =
                        basePayload(),
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

            assertEquals(
                listOf(
                    existing,
                ),
                database
                    .safeExitDao()
                    .getAllForBackup(),
            )
        }

    @Test
    fun safeExitOnlyDatabaseCountsAsExistingUserData() =
        runBlocking {
            database
                .safeExitDao()
                .insertForRestore(
                    listOf(
                        record(
                            sourceKey =
                                "pivot_game:REFLEX_OVERRIDE:2005",
                            source =
                                "pivot_game",
                            sourceId =
                                "REFLEX_OVERRIDE:2005",
                            completedAt =
                                "2026-08-03T11:15",
                        ),
                    ),
                )

            assertTrue(
                importer
                    .hasExistingUserData(),
            )

            val result =
                importer.importPayload(
                    basePayload(),
                )

            assertEquals(
                RestoreBundleImporter
                    .ImportOutcome
                    .ExistingDataPresent,
                result,
            )

            assertEquals(
                1,
                database
                    .safeExitDao()
                    .count(),
            )
        }

    @Test
    fun invalidSafeExitExtensionIsRejectedBeforeAnyDatabaseWrite() =
        runBlocking {
            val payload =
                basePayload()
                    .put(
                        "journalNotes",
                        JSONArray()
                            .put(
                                JSONObject()
                                    .put(
                                        "id",
                                        1L,
                                    )
                                    .put(
                                        "noteType",
                                        "TEXT",
                                    )
                                    .put(
                                        "title",
                                        "Must not import",
                                    )
                                    .put(
                                        "createdAtMillis",
                                        1_000L,
                                    )
                                    .put(
                                        "updatedAtMillis",
                                        1_000L,
                                    ),
                            ),
                    )
                    .put(
                        SafeExitRestorePayloadCodec
                            .JsonKey,
                        JSONObject()
                            .put(
                                "formatVersion",
                                1,
                            )
                            .put(
                                "records",
                                JSONArray()
                                    .put(
                                        JSONArray()
                                            .put(
                                                "unknown:2006",
                                            )
                                            .put(
                                                "2026-08-03T11:20",
                                            ),
                                    ),
                            ),
                    )

            val error =
                runCatching {
                    importer
                        .importPayload(
                            payload,
                        )
                }.exceptionOrNull()

            assertTrue(
                error is
                    IllegalArgumentException,
            )

            assertTrue(
                database
                    .journalNoteDao()
                    .getAllNotesForSync()
                    .isEmpty(),
            )

            assertEquals(
                0,
                database
                    .safeExitDao()
                    .count(),
            )
        }

    @Test
    fun duplicateSourceKeysAreRejectedBeforeAnyDatabaseWrite() =
        runBlocking {
            val row =
                JSONArray()
                    .put(
                        "reset_reading:2007",
                    )
                    .put(
                        "2026-08-03T11:25",
                    )

            val payload =
                basePayload()
                    .put(
                        SafeExitRestorePayloadCodec
                            .JsonKey,
                        JSONObject()
                            .put(
                                "formatVersion",
                                1,
                            )
                            .put(
                                "records",
                                JSONArray()
                                    .put(
                                        row,
                                    )
                                    .put(
                                        JSONArray(
                                            row.toString(),
                                        ),
                                    ),
                            ),
                    )

            val error =
                runCatching {
                    importer
                        .importPayload(
                            payload,
                        )
                }.exceptionOrNull()

            assertTrue(
                error is
                    IllegalArgumentException,
            )

            assertEquals(
                0,
                database
                    .safeExitDao()
                    .count(),
            )
        }

    @Test
    fun historicalPayloadWithoutExtensionImportsIntoEmptyDatabase() =
        runBlocking {
            assertFalse(
                importer
                    .hasExistingUserData(),
            )

            val result =
                importer
                    .importPayload(
                        basePayload(),
                    )

            assertEquals(
                RestoreBundleImporter
                    .ImportOutcome
                    .Success,
                result,
            )

            assertEquals(
                0,
                database
                    .safeExitDao()
                    .count(),
            )
        }

    private fun basePayload():
        JSONObject {
        return JSONObject()
            .put(
                "journalNotes",
                JSONArray(),
            )
            .put(
                "checklistItems",
                JSONArray(),
            )
            .put(
                "recoverySessions",
                JSONArray(),
            )
            .put(
                "blockedDomains",
                JSONArray(),
            )
    }

    private fun record(
        sourceKey: String,
        source: String,
        sourceId: String,
        completedAt: String,
    ): SafeExitEntity {
        return SafeExitEntity(
            sourceKey =
                sourceKey,
            source =
                source,
            sourceId =
                sourceId,
            completedAt =
                completedAt,
        )
    }
}
