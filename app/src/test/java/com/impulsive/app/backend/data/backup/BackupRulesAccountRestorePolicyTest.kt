package com.impulsive.app.backend.data.backup

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRulesAccountRestorePolicyTest {
    @Test
    fun backupRulesKeepAccountBoundRestoreIncludesAndSensitiveExcludes() {
        val backupRules = File("src/main/res/xml/backup_rules.xml").readText()
        val dataExtractionRules = File("src/main/res/xml/data_extraction_rules.xml").readText()
        val combined = backupRules + "\n" + dataExtractionRules

        listOf(
            "restore/impulsive_restore_bundle_v1.json",
            "datastore/onboarding_state.preferences_pb",
            "datastore/app_settings_prefs.preferences_pb",
            "datastore/focus_setup.preferences_pb",
            "datastore/game_store_prefs.preferences_pb",
            "datastore/home_guide.preferences_pb",
            "datastore/level_prefs.preferences_pb",
            "datastore/play_store_rating_prompt.preferences_pb",
            "datastore/protection_setup_state.preferences_pb",
            "datastore/reflex_game_history.preferences_pb",
            "datastore/reset_read_progress.preferences_pb",
            "datastore/rhythm_tiles_history.preferences_pb",
            "datastore/score_sessions.preferences_pb",
            "datastore/served_games.preferences_pb",
            "datastore/taper_preferences.preferences_pb",
            "datastore/task_rewards.preferences_pb",
            "datastore/theme_prefs.preferences_pb",
            "datastore/urge_events.preferences_pb",
            "datastore/window_outcomes.preferences_pb",
        ).forEach { path ->
            assertTrue("Expected backup include for $path", combined.contains(path))
        }

        listOf(
            "impulsive.db",
            "impulsive_database_passphrase",
            "DatabasePassphraseStore",
            "app_lock_prefs.preferences_pb",
            "premium_entitlement.preferences_pb",
            "focus_session.preferences_pb",
            "one_minute_access.preferences_pb",
            "protection_window_notifications.preferences_pb",
        ).forEach { path ->
            assertFalse("Sensitive/transient data must not be included: $path", combined.contains("path=\"$path\""))
        }
    }
}