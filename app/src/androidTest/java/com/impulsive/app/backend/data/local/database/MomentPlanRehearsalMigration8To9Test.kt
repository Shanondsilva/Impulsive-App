package com.impulsive.app.backend.data.local.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MomentPlanRehearsalMigration8To9Test {
    private val databaseName = "moment-plan-rehearsal-migration"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    @Throws(IOException::class)
    fun migration8To9PreservesRowsAndAddsGuardedHistoricalRehearsals() {
        val planId = UUID.randomUUID().toString()
        helper.createDatabase(databaseName, 8).use { db ->
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
                ) VALUES (100, 200, 90, 7, 3, 1, 'survivor', 'breathing')
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO moment_plans (
                    planId,
                    title,
                    momentCue,
                    actionText,
                    futureCueText,
                    actionType,
                    actionTarget,
                    enabled,
                    preferredForCue,
                    createdAtMillis,
                    updatedAtMillis,
                    rehearsedAtMillis
                ) VALUES (
                    '$planId',
                    'Plan survives',
                    'Boredom',
                    'Take a short walk',
                    'Feel clear tomorrow',
                    'TextOnly',
                    NULL,
                    1,
                    0,
                    1000,
                    1000,
                    NULL
                )
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(
            databaseName,
            9,
            true,
            AppDatabase.Migration8To9,
        ).use { db ->
            db.query(
                "SELECT triggerSource FROM recovery_sessions WHERE startedAt = 100",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("survivor", cursor.getString(0))
            }
            db.query(
                "SELECT title FROM moment_plans WHERE planId = '$planId'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Plan survives", cursor.getString(0))
            }

            val tables = db.firstColumnSet(
                "SELECT name FROM sqlite_master WHERE type = 'table'",
            )
            assertTrue("moment_plan_rehearsals" in tables)
            val indexes = db.firstColumnSet(
                """
                SELECT name
                FROM sqlite_master
                WHERE type = 'index'
                    AND tbl_name = 'moment_plan_rehearsals'
                """.trimIndent(),
            )
            assertTrue(indexes.containsAll(RequiredIndexes))
            val decisionColumns = db.firstColumnSet(
                "SELECT name FROM pragma_table_info('adaptive_decisions')",
            )
            assertTrue("momentPlanUpdatedAtMillis" in decisionColumns)
            val decisionIndexes = db.firstColumnSet(
                """
                SELECT name
                FROM sqlite_master
                WHERE type = 'index'
                    AND tbl_name = 'adaptive_decisions'
                """.trimIndent(),
            )
            assertTrue(
                "index_adaptive_decisions_momentPlanId_" +
                    "momentPlanUpdatedAtMillis_startedAtMillis" in decisionIndexes,
            )

            val columns = db.firstColumnSet(
                "SELECT name FROM pragma_table_info('moment_plan_rehearsals')",
            ).map { it.lowercase() }
            ForbiddenColumnFragments.forEach { fragment ->
                assertFalse(columns.any { fragment in it })
            }

            val rehearsalId = UUID.randomUUID().toString()
            db.execSQL(
                """
                INSERT INTO moment_plan_rehearsals (
                    rehearsalId,
                    planId,
                    planUpdatedAtMillisAtStart,
                    mode,
                    startedAtMillis,
                    completedAtMillis,
                    dismissedAtMillis
                ) VALUES (
                    '$rehearsalId',
                    '$planId',
                    1000,
                    'Guided',
                    2000,
                    2100,
                    NULL
                )
                """.trimIndent(),
            )
            db.execSQL("DELETE FROM moment_plans WHERE planId = '$planId'")
            db.query(
                """
                SELECT planId, completedAtMillis
                FROM moment_plan_rehearsals
                WHERE rehearsalId = '$rehearsalId'
                """.trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(planId, cursor.getString(0))
                assertEquals(2100L, cursor.getLong(1))
            }

            val conflictRejected = runCatching {
                db.execSQL(
                    """
                    INSERT INTO moment_plan_rehearsals (
                        rehearsalId,
                        planId,
                        planUpdatedAtMillisAtStart,
                        mode,
                        startedAtMillis,
                        completedAtMillis,
                        dismissedAtMillis
                    ) VALUES (
                        '${UUID.randomUUID()}',
                        '$planId',
                        1000,
                        'Quick',
                        3000,
                        3100,
                        3200
                    )
                    """.trimIndent(),
                )
            }.exceptionOrNull()
            assertNotNull(conflictRejected)

            db.query("PRAGMA user_version").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(9, cursor.getInt(0))
            }
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.firstColumnSet(
        query: String,
    ): Set<String> = buildSet {
        this@firstColumnSet.query(query).use { cursor ->
            while (cursor.moveToNext()) {
                add(cursor.getString(0))
            }
        }
    }

    private companion object {
        val RequiredIndexes = setOf(
            "index_moment_plan_rehearsals_planId_startedAtMillis",
            "index_moment_plan_rehearsals_planId_completedAtMillis",
            "index_moment_plan_rehearsals_completedAtMillis_" +
                "dismissedAtMillis_startedAtMillis",
            "index_moment_plan_rehearsals_completedAtMillis",
        )

        val ForbiddenColumnFragments = listOf(
            "url",
            "domain",
            "package",
            "email",
            "uid",
            "medical",
        )
    }
}
