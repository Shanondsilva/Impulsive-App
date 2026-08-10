package com.impulsive.app.backend.data.repository.adaptive

import com.impulsive.app.backend.data.local.entity.AdaptivePreferenceEntity
import com.impulsive.app.backend.domain.model.adaptive.AdaptivePreferences
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FuturePathAlwaysOnContractTest {

    @Test
    fun `domain and entity defaults keep Future Path enabled`() {
        assertTrue(
            AdaptivePreferences()
                .pathShiftEnabled,
        )

        assertTrue(
            AdaptivePreferenceEntity()
                .pathShiftEnabled,
        )
    }

    @Test
    fun `persistence mappers repair false values`() {
        val entity =
            AdaptivePreferences(
                pathShiftEnabled =
                    false,
            ).toEntity(
                updatedAtMillis =
                    100L,
            )

        assertTrue(
            entity.pathShiftEnabled,
        )

        val domain =
            AdaptivePreferenceEntity(
                pathShiftEnabled =
                    false,
            ).toDomain()

        assertTrue(
            domain.pathShiftEnabled,
        )
    }

    @Test
    fun `production source contains no reachable Future Path off state`() {
        val production =
            File(
                "src/main/java",
            )
                .walkTopDown()
                .filter {
                    it.isFile &&
                        it.extension ==
                        "kt"
                }
                .joinToString(
                    separator =
                        "\n",
                ) {
                    it.readText()
                }

        listOf(
            "PathShiftCreateResult.Disabled",
            "PathShiftExperienceState.Disabled",
            "Future Path is off.",
            "Turn On Future Path",
            "Turn Off Future Path",
            "Turn off Future Path",
            "pathShiftEnabled = false",
        ).forEach { forbidden ->
            assertFalse(
                "Forbidden Future Path off-state remains: $forbidden",
                production.contains(
                    forbidden,
                ),
            )
        }

        val settings =
            File(
                "src/main/java/com/impulsive/app/frontend/screens/settings/" +
                    "SettingsScreen.kt",
            )
                .readText()

        assertFalse(
            settings.contains(
                "title = \"Future Path\"",
            ),
        )

        assertTrue(
            production.contains(
                "composable(AppRoutes.PathShift)",
            ),
        )
    }

    @Test
    fun `current database adds a non destructive always on migration`() {
        val database =
            File(
                "src/main/java/com/impulsive/app/backend/data/local/database/" +
                    "AppDatabase.kt",
            )
                .readText()

        assertTrue(
            database.contains(
                "version = 14",
            ),
        )

        assertTrue(
            database.contains(
                "Migration12To13",
            ),
        )

        assertTrue(
            database.contains(
                "pathShiftEnabled INTEGER NOT NULL DEFAULT 1",
            ),
        )

        assertTrue(
            database.contains(
                "adaptive_preferences_require_future_path_insert",
            ),
        )

        assertTrue(
            database.contains(
                "adaptive_preferences_require_future_path_update",
            ),
        )

        assertFalse(
            database.contains(
                "fallbackToDestructiveMigration",
            ),
        )
    }
}
