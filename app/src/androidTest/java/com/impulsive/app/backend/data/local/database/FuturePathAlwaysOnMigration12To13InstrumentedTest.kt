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
class FuturePathAlwaysOnMigration12To13InstrumentedTest {

    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry
                .getInstrumentation(),
            AppDatabase::class.java,
        )

    @Test
    @Throws(IOException::class)
    fun migrationRepairsFalsePreferenceAndInstallsAlwaysOnInvariant() {
        val databaseName =
            "future-path-always-on-migration"

        helper
            .createDatabase(
                databaseName,
                12,
            )
            .use { database ->
                database.execSQL(
                    """
                    INSERT INTO adaptive_preferences (
                        id,
                        personalSuggestionsEnabled,
                        gameSuggestionsEnabled,
                        readingSuggestionsEnabled,
                        momentPlanSuggestionsEnabled,
                        randomisedExplorationEnabled,
                        updatedAtMillis,
                        privateScreenProtectionEnabled,
                        historyRetentionPolicy,
                        pathShiftEnabled
                    )
                    VALUES (
                        1,
                        0,
                        1,
                        0,
                        1,
                        0,
                        123,
                        0,
                        'OneYear',
                        0
                    )
                    """.trimIndent(),
                )

                database.execSQL(
                    """
                    INSERT INTO blocked_domain (
                        domain,
                        category,
                        isDefault,
                        addedByUser,
                        createdAtMillis
                    )
                    VALUES (
                        'future-path-survivor.example',
                        'test',
                        0,
                        1,
                        321
                    )
                    """.trimIndent(),
                )
            }

        helper
            .runMigrationsAndValidate(
                databaseName,
                13,
                true,
                AppDatabase.Migration12To13,
            )
            .use { database ->
                database
                    .query(
                        """
                        SELECT
                            personalSuggestionsEnabled,
                            gameSuggestionsEnabled,
                            readingSuggestionsEnabled,
                            momentPlanSuggestionsEnabled,
                            randomisedExplorationEnabled,
                            updatedAtMillis,
                            privateScreenProtectionEnabled,
                            historyRetentionPolicy,
                            pathShiftEnabled
                        FROM adaptive_preferences
                        WHERE id = 1
                        """.trimIndent(),
                    )
                    .use { cursor ->
                        assertTrue(
                            cursor.moveToFirst(),
                        )
                        assertEquals(
                            0,
                            cursor.getInt(
                                0,
                            ),
                        )
                        assertEquals(
                            1,
                            cursor.getInt(
                                1,
                            ),
                        )
                        assertEquals(
                            0,
                            cursor.getInt(
                                2,
                            ),
                        )
                        assertEquals(
                            1,
                            cursor.getInt(
                                3,
                            ),
                        )
                        assertEquals(
                            0,
                            cursor.getInt(
                                4,
                            ),
                        )
                        assertEquals(
                            123L,
                            cursor.getLong(
                                5,
                            ),
                        )
                        assertEquals(
                            0,
                            cursor.getInt(
                                6,
                            ),
                        )
                        assertEquals(
                            "OneYear",
                            cursor.getString(
                                7,
                            ),
                        )
                        assertEquals(
                            1,
                            cursor.getInt(
                                8,
                            ),
                        )
                    }

                database
                    .query(
                        """
                        SELECT createdAtMillis
                        FROM blocked_domain
                        WHERE domain =
                            'future-path-survivor.example'
                        """.trimIndent(),
                    )
                    .use { cursor ->
                        assertTrue(
                            cursor.moveToFirst(),
                        )
                        assertEquals(
                            321L,
                            cursor.getLong(
                                0,
                            ),
                        )
                    }

                database
                    .query(
                        """
                        PRAGMA table_info(
                            `adaptive_preferences`
                        )
                        """.trimIndent(),
                    )
                    .use { cursor ->
                        val nameColumn =
                            cursor.getColumnIndexOrThrow(
                                "name",
                            )
                        val defaultColumn =
                            cursor.getColumnIndexOrThrow(
                                "dflt_value",
                            )

                        var defaultValue:
                            String? =
                            null

                        while (
                            cursor.moveToNext()
                        ) {
                            if (
                                cursor.getString(
                                    nameColumn,
                                ) ==
                                "pathShiftEnabled"
                            ) {
                                defaultValue =
                                    cursor.getString(
                                        defaultColumn,
                                    )
                            }
                        }

                        assertEquals(
                            "1",
                            defaultValue,
                        )
                    }

                assertTrue(
                    runCatching {
                        database.execSQL(
                            """
                            UPDATE adaptive_preferences
                            SET pathShiftEnabled = 0
                            WHERE id = 1
                            """.trimIndent(),
                        )
                    }.isFailure,
                )

                database.execSQL(
                    """
                    DELETE FROM adaptive_preferences
                    """.trimIndent(),
                )

                assertTrue(
                    runCatching {
                        database.execSQL(
                            """
                            INSERT INTO adaptive_preferences (
                                id,
                                pathShiftEnabled
                            )
                            VALUES (
                                1,
                                0
                            )
                            """.trimIndent(),
                        )
                    }.isFailure,
                )

                database.execSQL(
                    """
                    INSERT INTO adaptive_preferences (
                        id
                    )
                    VALUES (
                        1
                    )
                    """.trimIndent(),
                )

                database
                    .query(
                        """
                        SELECT pathShiftEnabled
                        FROM adaptive_preferences
                        WHERE id = 1
                        """.trimIndent(),
                    )
                    .use { cursor ->
                        assertTrue(
                            cursor.moveToFirst(),
                        )
                        assertEquals(
                            1,
                            cursor.getInt(
                                0,
                            ),
                        )
                    }
            }
    }
}
