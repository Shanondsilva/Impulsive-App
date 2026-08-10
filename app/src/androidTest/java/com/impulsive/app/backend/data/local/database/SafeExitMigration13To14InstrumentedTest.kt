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
class SafeExitMigration13To14InstrumentedTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry
                .getInstrumentation(),
            AppDatabase::class.java,
        )

    @Test
    @Throws(IOException::class)
    fun migrationCreatesEmptyLedgerAndPreservesExistingData() {
        val databaseName =
            "safe-exit-migration-13-to-14"

        helper
            .createDatabase(
                databaseName,
                13,
            )
            .use { database ->
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
                        'safe-exit-survivor.example',
                        'test',
                        0,
                        1,
                        14001
                    )
                    """.trimIndent(),
                )
            }

        helper
            .runMigrationsAndValidate(
                databaseName,
                14,
                true,
                AppDatabase.Migration13To14,
            )
            .use { database ->
                database
                    .query(
                        """
                        SELECT createdAtMillis
                        FROM blocked_domain
                        WHERE domain =
                            'safe-exit-survivor.example'
                        """.trimIndent(),
                    )
                    .use { cursor ->
                        assertTrue(
                            cursor.moveToFirst(),
                        )
                        assertEquals(
                            14001L,
                            cursor.getLong(0),
                        )
                    }

                database
                    .query(
                        """
                        SELECT COUNT(*)
                        FROM safe_exit_records
                        """.trimIndent(),
                    )
                    .use { cursor ->
                        assertTrue(
                            cursor.moveToFirst(),
                        )
                        assertEquals(
                            0,
                            cursor.getInt(0),
                        )
                    }

                database
                    .query(
                        """
                        PRAGMA table_info(
                            `safe_exit_records`
                        )
                        """.trimIndent(),
                    )
                    .use { cursor ->
                        val nameColumn =
                            cursor.getColumnIndexOrThrow(
                                "name",
                            )
                        val typeColumn =
                            cursor.getColumnIndexOrThrow(
                                "type",
                            )
                        val notNullColumn =
                            cursor.getColumnIndexOrThrow(
                                "notnull",
                            )
                        val primaryKeyColumn =
                            cursor.getColumnIndexOrThrow(
                                "pk",
                            )

                        val columns =
                            linkedMapOf<
                                String,
                                Triple<
                                    String,
                                    Int,
                                    Int,
                                >,
                            >()

                        while (
                            cursor.moveToNext()
                        ) {
                            columns[
                                cursor.getString(
                                    nameColumn,
                                ),
                            ] =
                                Triple(
                                    cursor.getString(
                                        typeColumn,
                                    ),
                                    cursor.getInt(
                                        notNullColumn,
                                    ),
                                    cursor.getInt(
                                        primaryKeyColumn,
                                    ),
                                )
                        }

                        assertEquals(
                            setOf(
                                "sourceKey",
                                "source",
                                "sourceId",
                                "completedAt",
                            ),
                            columns.keys,
                        )

                        columns.forEach {
                                (
                                    name,
                                    definition,
                                ),
                            ->
                            assertEquals(
                                "TEXT",
                                definition.first,
                            )
                            assertEquals(
                                1,
                                definition.second,
                            )
                            assertEquals(
                                if (
                                    name ==
                                    "sourceKey"
                                ) {
                                    1
                                } else {
                                    0
                                },
                                definition.third,
                            )
                        }
                    }

                database
                    .query(
                        """
                        SELECT name
                        FROM sqlite_master
                        WHERE type = 'index'
                            AND tbl_name =
                                'safe_exit_records'
                        """.trimIndent(),
                    )
                    .use { cursor ->
                        val indexes =
                            mutableSetOf<String>()

                        while (
                            cursor.moveToNext()
                        ) {
                            val value =
                                cursor.getString(0)

                            if (
                                value != null
                            ) {
                                indexes +=
                                    value
                            }
                        }

                        assertTrue(
                            indexes.contains(
                                "index_safe_exit_records_completedAt",
                            ),
                        )

                        assertTrue(
                            indexes.contains(
                                "index_safe_exit_records_source_completedAt",
                            ),
                        )
                    }

                database.execSQL(
                    """
                    INSERT INTO safe_exit_records (
                        sourceKey,
                        source,
                        sourceId,
                        completedAt
                    )
                    VALUES (
                        'reset_reading:14002',
                        'reset_reading',
                        '14002',
                        '2026-08-03T03:00'
                    )
                    """.trimIndent(),
                )

                database
                    .query(
                        """
                        SELECT
                            source,
                            sourceId,
                            completedAt
                        FROM safe_exit_records
                        WHERE sourceKey =
                            'reset_reading:14002'
                        """.trimIndent(),
                    )
                    .use { cursor ->
                        assertTrue(
                            cursor.moveToFirst(),
                        )
                        assertEquals(
                            "reset_reading",
                            cursor.getString(0),
                        )
                        assertEquals(
                            "14002",
                            cursor.getString(1),
                        )
                        assertEquals(
                            "2026-08-03T03:00",
                            cursor.getString(2),
                        )
                    }

                database
                    .query(
                        "PRAGMA user_version",
                    )
                    .use { cursor ->
                        assertTrue(
                            cursor.moveToFirst(),
                        )
                        assertEquals(
                            14,
                            cursor.getInt(0),
                        )
                    }
            }
    }
}