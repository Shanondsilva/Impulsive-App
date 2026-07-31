package com.impulsive.app.backend.data.local.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PathShiftMigration10To11InstrumentedTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    @Throws(IOException::class)
    fun migrationPreservesSchema10RowsAndCreatesPathShiftDefaults() {
        helper.createDatabase("pathshift-migration", 10).use { db ->
            db.execSQL(
                """
                INSERT INTO adaptive_preferences (
                    id, personalSuggestionsEnabled, gameSuggestionsEnabled,
                    readingSuggestionsEnabled, momentPlanSuggestionsEnabled,
                    randomisedExplorationEnabled, updatedAtMillis,
                    privateScreenProtectionEnabled, historyRetentionPolicy
                ) VALUES (1, 1, 1, 1, 1, 1, 123, 1, 'SixMonths')
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO blocked_domain (
                    domain, category, isDefault, addedByUser, createdAtMillis
                ) VALUES ('pathshift.example', 'test', 0, 1, 321)
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(
            "pathshift-migration",
            11,
            true,
            AppDatabase.Migration10To11,
        ).use { db ->
            db.query(
                "SELECT updatedAtMillis, pathShiftEnabled FROM adaptive_preferences",
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals(123L, it.getLong(0))
                assertEquals(0, it.getInt(1))
            }
            db.query(
                "SELECT createdAtMillis FROM blocked_domain WHERE domain = 'pathshift.example'",
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals(321L, it.getLong(0))
            }
            db.query("SELECT COUNT(*) FROM path_shift_cycles").use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
            }
            db.query(
                """
                SELECT COUNT(*) FROM sqlite_master
                WHERE type = 'index' AND tbl_name = 'path_shift_cycles'
                  AND sql IS NOT NULL
                """.trimIndent(),
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals(4, it.getInt(0))
            }
        }
    }

    @Test
    fun terminalAndOneActiveConstraintsRejectInvalidRows() {
        helper.createDatabase("pathshift-constraints", 10).close()
        helper.runMigrationsAndValidate(
            "pathshift-constraints",
            11,
            true,
            AppDatabase.Migration10To11,
        ).use { db ->
            db.execSQL(cycleInsert("11111111-1111-4111-8111-111111111111"))
            val duplicate = runCatching {
                db.execSQL(cycleInsert("22222222-2222-4222-8222-222222222222"))
            }
            assertTrue(duplicate.isFailure)
            val invalidTerminal = runCatching {
                db.execSQL(
                    cycleInsert(
                        "33333333-3333-4333-8333-333333333333",
                        status = "Finalised",
                    ),
                )
            }
            assertTrue(invalidTerminal.isFailure)
        }
    }

    private fun cycleInsert(id: String, status: String = "Active"): String =
        """
        INSERT INTO path_shift_cycles (
            cycleId, createdAtMillis, lookbackStartedAtMillis, lookbackEndedAtMillis,
            forecastWindowStartedAtMillis, forecastWindowEndsAtMillis,
            forecastPolicyVersion, evidenceStrength, inputProtectedMomentCount,
            inputDistinctDayCount, estimatedLowerCount, estimatedUpperCount,
            commonWindowStartMinute, commonWindowEndMinute, preparedPlanId,
            preparedPlanContentRevisionId, preparedAtMillis, reviewFinalisedAtMillis,
            observedProtectedMomentCount, preparedPlanSelectedCount,
            preparedPlanStartedCount, preparedPlanCompletedCount,
            preparedPlanDismissedCount, wrongTimingCount, repeatDetectedCount,
            status, cancelledAtMillis
        ) VALUES (
            '$id', 100, 1, 99, 200, 300, 1, 'EarlyEstimate', 7, 5, 2, 5,
            NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, 0,
            '$status', NULL
        )
        """.trimIndent()
}
