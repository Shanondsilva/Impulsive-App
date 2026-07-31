package com.impulsive.app.backend.data.local.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val testDbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    @Throws(IOException::class)
    fun migrate1To2CreatesJournalNotesTable() {
        helper.createDatabase(testDbName, 1).use { db ->
            db.execSQL(
                """INSERT INTO recovery_sessions
                    (startedAt, completedAt, durationSeconds, urgeBefore, urgeAfter, helped, triggerSource, recoveryType)
                    VALUES (1000, 2000, 90, 7, 3, 1, 'manual_demo', 'psychological_90_second_reset')"""
            )
        }

        helper.runMigrationsAndValidate(testDbName, 2, true, AppDatabase.Migration1To2).use { db ->
            // journal_notes table must exist and be empty after the migration
            db.query("SELECT COUNT(*) FROM journal_notes").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            // recovery_sessions data must survive unchanged
            db.query("SELECT startedAt, completedAt FROM recovery_sessions").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1000L, cursor.getLong(0))
                assertEquals(2000L, cursor.getLong(1))
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate2To3AddsColumnsAndChecklistTable() {
        helper.createDatabase(testDbName, 2).use { db ->
            db.execSQL(
                """INSERT INTO journal_notes
                    (noteType, title, body, checklist, sketch, reminderAtMillis, source, createdAtMillis, updatedAtMillis)
                    VALUES ('TEXT', 'My note', 'Hello', '', '', NULL, 'normal_journal', 100, 200)"""
            )
        }

        helper.runMigrationsAndValidate(testDbName, 3, true, AppDatabase.Migration2To3).use { db ->
            db.query("SELECT title, isPinned, category, highlightColor, sortOrder FROM journal_notes").use { cursor ->
                cursor.moveToFirst()
                assertEquals("My note", cursor.getString(0))
                // isPinned defaults to 0
                assertEquals(0, cursor.getInt(1))
                // category defaults to ''
                assertEquals("", cursor.getString(2))
                // highlightColor defaults to NULL
                assertTrue(cursor.isNull(3))
                // sortOrder defaults to NULL
                assertTrue(cursor.isNull(4))
            }
            // checklist items table must exist and be empty
            db.query("SELECT COUNT(*) FROM journal_checklist_items").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrateFullPath1To3DataSurvives() {
        helper.createDatabase(testDbName, 1).use { db ->
            db.execSQL(
                """INSERT INTO recovery_sessions
                    (startedAt, completedAt, durationSeconds, urgeBefore, urgeAfter, helped, triggerSource, recoveryType)
                    VALUES (5000, 6000, 90, 5, 2, 0, 'manual_demo', 'psychological_90_second_reset')"""
            )
        }

        helper.runMigrationsAndValidate(
            testDbName, 3, true,
            AppDatabase.Migration1To2,
            AppDatabase.Migration2To3,
        ).use { db ->
            db.query("SELECT startedAt FROM recovery_sessions").use { cursor ->
                cursor.moveToFirst()
                assertEquals(5000L, cursor.getLong(0))
            }
        }
    }

    // Pulled out so the import stays minimal — cursor.isNull is an int comparison.
    @Test
    @Throws(IOException::class)
    fun migrate3To4CreatesBlockedDomainTable() {
        helper.createDatabase(testDbName, 3).use { }

        helper.runMigrationsAndValidate(testDbName, 4, true, AppDatabase.Migration3To4).use { db ->
            db.query("SELECT COUNT(*) FROM blocked_domain").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate4To5CreatesFeedbackResponsesAndPreservesData() {
        helper.createDatabase(testDbName, 4).use { db ->
            db.execSQL(
                """
                INSERT INTO blocked_domain (
                    domain,
                    category,
                    isDefault,
                    addedByUser,
                    createdAtMillis
                )
                VALUES (
                    'example.com',
                    'test',
                    0,
                    1,
                    1234
                )
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(
            testDbName,
            5,
            true,
            AppDatabase.Migration4To5,
        ).use { db ->
            db.query(
                "SELECT COUNT(*) FROM feedback_responses",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }

            db.query(
                """
                SELECT domain, createdAtMillis
                FROM blocked_domain
                WHERE domain = 'example.com'
                """.trimIndent(),
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals("example.com", cursor.getString(0))
                assertEquals(1234L, cursor.getLong(1))
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate6To7CreatesEmptyReceiptTableAndPreservesUserData() {
        helper.createDatabase(testDbName, 6).use { db ->
            db.execSQL(
                """
                INSERT INTO recovery_sessions (
                    startedAt,
                    completedAt,
                    durationSeconds,
                    urgeBefore,
                    urgeAfter,
                    helped,
                    triggerSource,
                    recoveryType
                )
                VALUES (
                    7000,
                    8000,
                    90,
                    6,
                    2,
                    1,
                    'migration_test',
                    'breathing'
                )
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(
            testDbName,
            7,
            true,
            AppDatabase.Migration6To7,
        ).use { db ->
            db.query(
                "SELECT COUNT(*) FROM cloud_restore_receipts",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            db.query(
                """
                SELECT startedAt, recoveryType
                FROM recovery_sessions
                """.trimIndent(),
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(7000L, cursor.getLong(0))
                assertEquals("breathing", cursor.getString(1))
            }
            db.query("PRAGMA user_version").use { cursor ->
                cursor.moveToFirst()
                assertEquals(7, cursor.getInt(0))
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun completeExportedMigrationPathReachesVersion7() {
        helper.createDatabase(testDbName, 3).use { db ->
            db.execSQL(
                """
                INSERT INTO journal_notes (
                    noteType,
                    title,
                    body,
                    checklist,
                    sketch,
                    reminderAtMillis,
                    source,
                    createdAtMillis,
                    updatedAtMillis,
                    isPinned,
                    category,
                    highlightColor,
                    sortOrder
                )
                VALUES (
                    'TEXT',
                    'Survives',
                    'Migration content',
                    '',
                    '',
                    NULL,
                    'normal_journal',
                    100,
                    200,
                    0,
                    '',
                    NULL,
                    NULL
                )
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(
            testDbName,
            7,
            true,
            AppDatabase.Migration3To4,
            AppDatabase.Migration4To5,
            AppDatabase.Migration5To6,
            AppDatabase.Migration6To7,
        ).use { db ->
            db.query(
                "SELECT title, body FROM journal_notes",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals("Survives", cursor.getString(0))
                assertEquals("Migration content", cursor.getString(1))
            }
            db.query(
                "SELECT COUNT(*) FROM cloud_restore_receipts",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            db.query("PRAGMA user_version").use { cursor ->
                cursor.moveToFirst()
                assertEquals(7, cursor.getInt(0))
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate7To8PreservesDataAndCreatesAdaptiveSchema() {
        helper.createDatabase(testDbName, 7).use { db ->
            db.execSQL(
                """
                INSERT INTO recovery_sessions (
                    startedAt,
                    completedAt,
                    durationSeconds,
                    urgeBefore,
                    urgeAfter,
                    helped,
                    triggerSource,
                    recoveryType
                )
                VALUES (
                    9000,
                    10000,
                    90,
                    7,
                    2,
                    1,
                    'schema_7_survivor',
                    'breathing'
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO cloud_restore_receipts (
                    receiptId,
                    payloadSha256,
                    proofType,
                    previousUid,
                    previousGoogleSubjectHash,
                    currentUid,
                    currentGoogleSubjectHash,
                    importedAtMillis
                )
                VALUES (
                    'receipt-7',
                    'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                    'EXACT_UID',
                    NULL,
                    NULL,
                    'uid-7',
                    NULL,
                    11000
                )
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(
            testDbName,
            8,
            true,
            AppDatabase.Migration7To8,
        ).use { db ->
            db.query(
                """
                SELECT triggerSource, recoveryType
                FROM recovery_sessions
                WHERE startedAt = 9000
                """.trimIndent(),
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals("schema_7_survivor", cursor.getString(0))
                assertEquals("breathing", cursor.getString(1))
            }
            db.query(
                """
                SELECT currentUid, importedAtMillis
                FROM cloud_restore_receipts
                WHERE receiptId = 'receipt-7'
                """.trimIndent(),
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals("uid-7", cursor.getString(0))
                assertEquals(11000L, cursor.getLong(1))
            }

            val tables = db.stringSet(
                """
                SELECT name
                FROM sqlite_master
                WHERE type = 'table'
                """.trimIndent(),
            )
            assertTrue("adaptive_decisions" in tables)
            assertTrue("moment_plans" in tables)
            assertTrue("adaptive_preferences" in tables)

            db.query(
                """
                SELECT
                    id,
                    personalSuggestionsEnabled,
                    gameSuggestionsEnabled,
                    readingSuggestionsEnabled,
                    momentPlanSuggestionsEnabled,
                    randomisedExplorationEnabled,
                    updatedAtMillis
                FROM adaptive_preferences
                """.trimIndent(),
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
                assertEquals(1, cursor.getInt(1))
                assertEquals(1, cursor.getInt(2))
                assertEquals(1, cursor.getInt(3))
                assertEquals(1, cursor.getInt(4))
                assertEquals(1, cursor.getInt(5))
                assertEquals(0L, cursor.getLong(6))
            }

            val indexes = db.stringSet(
                """
                SELECT name
                FROM sqlite_master
                WHERE type = 'index'
                    AND (
                        tbl_name = 'adaptive_decisions'
                        OR tbl_name = 'moment_plans'
                    )
                """.trimIndent(),
            )
            val requiredIndexes = setOf(
                "index_adaptive_decisions_protectionIncidentToken",
                "index_adaptive_decisions_createdAtMillis",
                "index_adaptive_decisions_observationFinalisedAtMillis_" +
                    "observationDeadlineAtMillis",
                "index_adaptive_decisions_actualIntervention_" +
                    "observationFinalisedAtMillis_createdAtMillis",
                "index_adaptive_decisions_momentCue_" +
                    "observationFinalisedAtMillis_createdAtMillis",
                "index_adaptive_decisions_momentPlanId",
                "index_moment_plans_enabled_updatedAtMillis",
                "index_moment_plans_momentCue_enabled_preferredForCue",
            )
            assertTrue(indexes.containsAll(requiredIndexes))

            val adaptiveColumns = buildSet {
                addAll(db.tableColumns("adaptive_decisions"))
                addAll(db.tableColumns("moment_plans"))
                addAll(db.tableColumns("adaptive_preferences"))
            }.map { it.lowercase() }
            listOf(
                "url",
                "domain",
                "search",
                "pagetitle",
                "pagecontent",
                "notification",
                "email",
                "firebaseuid",
                "medical",
            ).forEach { forbidden ->
                assertFalse(
                    "Forbidden adaptive column fragment: $forbidden",
                    adaptiveColumns.any { forbidden in it },
                )
            }

            db.query("PRAGMA user_version").use { cursor ->
                cursor.moveToFirst()
                assertEquals(8, cursor.getInt(0))
            }
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.stringSet(
        query: String,
    ): Set<String> = buildSet {
        this@stringSet.query(query).use { cursor ->
            while (cursor.moveToNext()) {
                add(cursor.getString(0))
            }
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.tableColumns(
        tableName: String,
    ): Set<String> = buildSet {
        query("PRAGMA table_info(`$tableName`)").use { cursor ->
            while (cursor.moveToNext()) {
                add(cursor.getString(1))
            }
        }
    }

    private fun assertTrue(value: Boolean) = assertEquals(true, value)
}
