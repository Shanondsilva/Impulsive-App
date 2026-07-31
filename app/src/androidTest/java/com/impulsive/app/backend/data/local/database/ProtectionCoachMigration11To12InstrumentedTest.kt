package com.impulsive.app.backend.data.local.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProtectionCoachMigration11To12InstrumentedTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    @Throws(IOException::class)
    fun migration11To12PreservesRowsAndCreatesConstrainedCoachLedger() {
        helper.createDatabase("protection-coach-migration", 11).use { db ->
            db.execSQL(
                """
                INSERT INTO adaptive_preferences (
                    id, personalSuggestionsEnabled, gameSuggestionsEnabled,
                    readingSuggestionsEnabled, momentPlanSuggestionsEnabled,
                    randomisedExplorationEnabled, updatedAtMillis,
                    privateScreenProtectionEnabled, historyRetentionPolicy,
                    pathShiftEnabled
                ) VALUES (1, 1, 1, 1, 1, 1, 123, 1, 'SixMonths', 0)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO blocked_domain (
                    domain, category, isDefault, addedByUser, createdAtMillis
                ) VALUES ('coach-survivor.example', 'test', 0, 1, 321)
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(
            "protection-coach-migration",
            12,
            true,
            AppDatabase.Migration11To12,
        ).use { db ->
            db.query(
                "SELECT updatedAtMillis FROM adaptive_preferences WHERE id = 1",
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals(123L, it.getLong(0))
            }
            db.query(
                "SELECT createdAtMillis FROM blocked_domain WHERE domain = 'coach-survivor.example'",
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals(321L, it.getLong(0))
            }
            db.query(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'protection_coach_suggestions'",
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals(1, it.getInt(0))
            }

            val indexes = db.stringSet(
                """
                SELECT name
                FROM sqlite_master
                WHERE type = 'index'
                    AND tbl_name = 'protection_coach_suggestions'
                    AND sql IS NOT NULL
                """.trimIndent(),
            )
            assertTrue(indexes.containsAll(RequiredIndexes))

            db.execSQL(validCoachInsert("11111111-1111-4111-8111-111111111111"))
            assertTrue(runCatching {
                db.execSQL(
                    validCoachInsert(
                        "22222222-2222-4222-8222-222222222222",
                        status = "AutoApplied",
                    ),
                )
            }.isFailure)
            assertTrue(runCatching {
                db.execSQL(
                    validCoachInsert(
                        "33333333-3333-4333-8333-333333333333",
                        broadWindowStartMinute = 1_500,
                    ),
                )
            }.isFailure)

            val columns = db.tableColumns("protection_coach_suggestions")
                .map { it.lowercase() }
            ForbiddenColumnFragments.forEach { forbidden ->
                assertFalse(columns.any { forbidden in it })
            }
        }
    }

    private fun validCoachInsert(
        id: String,
        status: String = "Prepared",
        broadWindowStartMinute: Int? = 1_320,
    ): String =
        """
        INSERT INTO protection_coach_suggestions (
            suggestionId, policyVersion, suggestionType, createdAtMillis,
            expiresAtMillis, status, presentedAtMillis, acceptedAtMillis,
            dismissedAtMillis, suppressedAtMillis, evidenceWindowStartedAtMillis,
            evidenceWindowEndedAtMillis, evidenceProtectedMomentCount,
            evidenceDistinctDayCount, broadWindowStartMinute, broadWindowEndMinute,
            suggestedStartMinute, suggestedEndMinute, acceptedStartMinute,
            acceptedEndMinute, onboardingReasonCode, relatedMomentPlanId,
            relatedMomentPlanContentRevisionId
        ) VALUES (
            '$id', 1, 'CreateEveningWindow', 100, 1000, '$status',
            NULL, NULL, NULL, NULL, 1, 99, 7, 5,
            ${broadWindowStartMinute ?: "NULL"}, 1439, 1320, 1439,
            NULL, NULL, NULL, NULL, NULL
        )
        """.trimIndent()

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

    private companion object {
        val RequiredIndexes = setOf(
            "index_protection_coach_suggestions_status_expiresAtMillis",
            "index_protection_coach_suggestions_type_status_broadWindow",
            "index_protection_coach_suggestions_createdAtMillis",
            "index_protection_coach_suggestions_relatedMomentPlan",
        )

        val ForbiddenColumnFragments = listOf(
            "package",
            "url",
            "domain",
            "browser",
            "email",
            "uid",
            "journal",
            "trigger",
        )
    }
}
