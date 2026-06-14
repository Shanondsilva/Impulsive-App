package com.impulsive.app.backend.data.local.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
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

    private fun assertTrue(value: Boolean) = assertEquals(true, value)
}
