package com.impulsive.app.backend.data.restore

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class AndroidBackupCoveragePolicyTest {
    @Test
    fun android11BackupRulesContainOnlyTheIntendedLogicalPaths() {
        val includes = includePaths(
            file = File("src/main/res/xml/backup_rules.xml"),
            parentTag = "full-backup-content",
        )

        assertEquals(expectedBackupPaths, includes)
        assertNoBroadIncludes(File("src/main/res/xml/backup_rules.xml"))
        assertNoSensitivePaths(includes)
    }

    @Test
    fun android12CloudBackupRulesContainOnlyTheIntendedLogicalPaths() {
        val includes = includePaths(
            file = File("src/main/res/xml/data_extraction_rules.xml"),
            parentTag = "cloud-backup",
        )

        assertEquals(expectedBackupPaths, includes)
        assertNoBroadIncludes(File("src/main/res/xml/data_extraction_rules.xml"))
        assertNoSensitivePaths(includes)
    }

    @Test
    fun android12DeviceTransferRulesMatchCloudBackupAndAndroid11Coverage() {
        val android11 = includePaths(
            file = File("src/main/res/xml/backup_rules.xml"),
            parentTag = "full-backup-content",
        )
        val cloudBackup = includePaths(
            file = File("src/main/res/xml/data_extraction_rules.xml"),
            parentTag = "cloud-backup",
        )
        val deviceTransfer = includePaths(
            file = File("src/main/res/xml/data_extraction_rules.xml"),
            parentTag = "device-transfer",
        )

        assertEquals(expectedBackupPaths, deviceTransfer)
        assertEquals(android11, cloudBackup)
        assertEquals(cloudBackup, deviceTransfer)
        assertNoSensitivePaths(deviceTransfer)
    }

    @Test
    fun manifestKeepsExplicitBackupConfigurationAndNoRestoreAnyVersionOverride() {
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("android:allowBackup=\"true\""))
        assertTrue(manifest.contains("android:fullBackupContent=\"@xml/backup_rules\""))
        assertTrue(manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
        assertFalse(manifest.contains("android:restoreAnyVersion=\"true\""))
    }

    @Test
    fun sharedPreferencesAndSecurityStoresRemainExcludedByBackupRules() {
        val allIncludes = includePaths(
            file = File("src/main/res/xml/backup_rules.xml"),
            parentTag = "full-backup-content",
        ) + includePaths(
            file = File("src/main/res/xml/data_extraction_rules.xml"),
            parentTag = "cloud-backup",
        ) + includePaths(
            file = File("src/main/res/xml/data_extraction_rules.xml"),
            parentTag = "device-transfer",
        )

        assertNoSensitivePaths(allIncludes)
        listOf(
            "vpn_diagnostics",
            "impulsive_database_passphrase",
            "website_protection_incidents",
        ).forEach { sensitiveName ->
            assertFalse(
                "Backup rules must not include SharedPreferences store $sensitiveName",
                allIncludes.any { it.contains(sensitiveName) },
            )
        }
    }

    @Test
    fun protectionPermissionCacheIsRevalidatedFromDeviceOnInitialCompositionAndResume() {
        val source = File("src/main/java/com/impulsive/app/frontend/navigation/AppNavHost.kt").readText()
        val syncMethod = method(source, "syncProtectionSetupFromDevice")
        val lifecycleStart = source.indexOf("DisposableEffect(")
        assertTrue("AppNavHost must keep a lifecycle DisposableEffect", lifecycleStart >= 0)
        val lifecycleEnd = source.indexOf("if (state.isLoading)", lifecycleStart)
        assertTrue("Lifecycle effect must run before loading gate", lifecycleEnd > lifecycleStart)
        val lifecycleEffect = source.substring(lifecycleStart, lifecycleEnd)

        assertTrue(syncMethod.contains("usageAccessChecker.hasUsageAccess()"))
        assertTrue(syncMethod.contains("Settings.canDrawOverlays(context)"))
        assertTrue(syncMethod.contains("protectionSetupViewModel.setUsageAccessEnabled(usageAccessGranted)"))
        assertTrue(syncMethod.contains("protectionSetupViewModel.setInterruptionPermissionEnabled(overlayPermissionGranted)"))
        assertTrue(syncMethod.contains("syncBackgroundActivityPermission()"))
        assertTrue(syncMethod.contains("syncNotificationPermission()"))
        assertFalse(syncMethod.contains("clearProtectionSetup()"))
        assertFalse(syncMethod.contains("setBlockedApps(empty"))

        assertTrue(lifecycleEffect.contains("syncProtectionSetupFromDevice(recoverService = true)"))
        assertTrue(lifecycleEffect.contains("Lifecycle.Event.ON_RESUME"))
    }

    @Test
    fun roomPayloadCoverageKeepsOnlyPortableRestoreEntities() {
        val writer = File(
            "src/main/java/com/impulsive/app/backend/data/restore/RestoreBundleWriter.kt",
        ).readText()
        val blockedDomainDao = File(
            "src/main/java/com/impulsive/app/backend/data/local/dao/BlockedDomainDao.kt",
        ).readText()
        val journalDao = File(
            "src/main/java/com/impulsive/app/backend/data/local/dao/JournalNoteDao.kt",
        ).readText()

        assertTrue(writer.contains("journalNoteDao.getAllNotesForSync()"))
        assertTrue(writer.contains("journalNoteDao.getChecklistItems(note.id)"))
        assertTrue(writer.contains("database.recoverySessionDao().getAllSessions()"))
        assertTrue(writer.contains("database.blockedDomainDao().getAll()"))
        assertTrue(writer.contains("if (!domain.addedByUser) continue"))
        assertTrue(blockedDomainDao.contains("DELETE FROM blocked_domain WHERE id = :id AND addedByUser = 1"))
        assertTrue(journalDao.contains("noteType != 'FEEDBACK'"))
        assertTrue(journalDao.contains("source != 'feedback_notification'"))
        assertFalse(writer.contains("feedbackResponseDao"))
        assertFalse(writer.contains("syncTombstoneDao"))
        assertFalse(writer.contains("FeedbackResponseEntity"))
        assertFalse(writer.contains("SyncTombstoneEntity"))
    }

    @Test
    fun manualEncryptedBackupFormatStaysIndependentFromAutomaticEnvelopeVersion() {
        val source = File(
            "src/main/java/com/impulsive/app/backend/data/restore/ManualBackupManager.kt",
        ).readText()

        assertTrue(source.contains("Cipher.getInstance(Transformation)"))
        assertTrue(source.contains("GCMParameterSpec(GcmTagBits, iv)"))
        assertTrue(source.contains("private const val Transformation = \"AES/GCM/NoPadding\""))
        assertTrue(source.contains("private const val KeyBits = 256"))
        assertTrue(source.contains("private const val FormatVersion = 1"))
        assertTrue(source.contains(".put(\"formatVersion\", FormatVersion)"))
        assertTrue(source.contains(".put(\"schemaVersion\", RestoreBundleWriter.SchemaVersion)"))
        assertTrue(source.contains("if (schemaVersion != RestoreBundleWriter.SchemaVersion)"))
        assertFalse(source.contains("AutoBundleFormatVersion"))
    }

    @Test
    fun androidAppDoesNotContainBehaviouralCloudSyncWriters() {
        val productionSources = File("src/main/java")
            .walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "java") }
            .associate { it.invariantSeparatorsPath to it.readText() }

        val combined = productionSources.values.joinToString("\n")

        assertFalse(combined.contains("FirebaseFirestore"))
        assertFalse(combined.contains("JournalNoteCloudSync"))
        assertFalse(combined.contains("RecoverySessionCloudSync"))
        assertFalse(combined.contains("CloudSyncWorker"))
        assertFalse(combined.contains("protectedDomainHistory"))
        assertFalse(combined.contains("blockedDomainHistory"))
    }

    @Test
    fun firebaseOnboardingCompletionStoresOnlyCompletionMetadata() {
        val source = File("../functions/index.js").readText()
        val handler = source.substring(
            source.indexOf("async function markOnboardingCompletedForRequest"),
            source.indexOf("async function verifyPurchaseWithGoogle"),
        )

        assertTrue(handler.contains("account:"))
        assertTrue(handler.contains("onboardingCompleted: true"))
        assertTrue(handler.contains("onboardingCompletedAt:"))
        assertFalse(handler.contains("answers"))
        assertFalse(handler.contains("journalNotes"))
        assertFalse(handler.contains("recoverySessions"))
        assertFalse(handler.contains("blockedDomains"))
    }

    private fun includePaths(
        file: File,
        parentTag: String,
    ): Set<String> {
        val document = DocumentBuilderFactory
            .newInstance()
            .newDocumentBuilder()
            .parse(file)
        val parents = document.getElementsByTagName(parentTag)
        assertEquals("Expected one <$parentTag> in ${file.path}", 1, parents.length)
        val parent = parents.item(0) as Element
        val includes = parent.getElementsByTagName("include")

        return (0 until includes.length)
            .map { includes.item(it) as Element }
            .map { element ->
                val domain = element.getAttribute("domain")
                val path = element.getAttribute("path")
                "$domain:$path"
            }
            .toSet()
    }

    private fun assertNoBroadIncludes(file: File) {
        val source = file.readText()

        listOf(
            "<include domain=\"root\" path=\".\"",
            "<include domain=\"file\" path=\".\"",
            "<include domain=\"sharedpref\" path=\".\"",
        ).forEach { broadInclude ->
            assertFalse("Backup rules must not use broad include $broadInclude", source.contains(broadInclude))
        }
    }

    private fun assertNoSensitivePaths(paths: Collection<String>) {
        excludedPathFragments.forEach { excluded ->
            assertFalse(
                "Backup rules must not include sensitive/transient path matching $excluded",
                paths.any { it.contains(excluded) },
            )
        }
    }

    private fun method(source: String, name: String): String {
        val signature = Regex("suspend fun $name|fun $name|override fun $name")
        val match = signature.find(source) ?: error("Method $name not found")
        val start = match.range.first
        val bodyStart = source.indexOf('{', start)
        require(bodyStart >= 0) { "Method $name has no body" }
        var depth = 0
        for (index in bodyStart until source.length) {
            when (source[index]) {
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) return source.substring(start, index + 1)
                }
            }
        }
        error("Method $name body did not close")
    }

    private companion object {
        val expectedDataStores = setOf(
            "app_settings_prefs",
            "focus_setup",
            "game_store_prefs",
            "home_guide",
            "level_prefs",
            "onboarding_state",
            "play_store_rating_prompt",
            "protection_setup_state",
            "reflex_game_history",
            "reset_read_progress",
            "rhythm_tiles_history",
            "score_sessions",
            "served_games",
            "snake_game_history",
            "taper_preferences",
            "task_rewards",
            "theme_prefs",
            "urge_events",
            "window_outcomes",
        )

        val expectedBackupPaths = buildSet {
            add("file:restore/impulsive_restore_bundle_v1.json")
            expectedDataStores.forEach { dataStore ->
                add("file:datastore/$dataStore.preferences_pb")
            }
        }

        val excludedPathFragments = listOf(
            "impulsive.db",
            "impulsive_database_passphrase",
            "app_lock_prefs",
            "blocked_domain_defaults",
            "focus_session",
            "premium_entitlement",
            "protection_recovery_notice",
            "protection_window_notifications",
            "vpn_diagnostics",
        )
    }
}
