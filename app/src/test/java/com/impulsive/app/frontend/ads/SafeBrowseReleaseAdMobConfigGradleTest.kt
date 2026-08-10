package com.impulsive.app.frontend.ads

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the Gate A9 release-config hardening in app/build.gradle.kts: a release build
 * can never produce an artifact with a missing or placeholder AdMob application ID, because
 * a dedicated validation task tied to `preReleaseBuild` fails the build first.
 */
class SafeBrowseReleaseAdMobConfigGradleTest {
    private val buildScript = File("build.gradle.kts").readText()

    @Test
    fun releaseValidationTaskExistsAndValidatesBothIds() {
        assertTrue(buildScript.contains("val validateSafeBrowseReleaseAdMobConfig by tasks.registering"))
        assertTrue(buildScript.contains("safeBrowseAdMobAppIdPattern = Regex(\"^ca-app-pub-\\\\d{16}~\\\\d{10}\$\")"))
        assertTrue(buildScript.contains("safeBrowseRewardedUnitIdPattern = Regex(\"^ca-app-pub-\\\\d{16}/\\\\d{10}\$\")"))
    }

    @Test
    fun validationTaskIsWiredToPreReleaseBuild() {
        assertTrue(buildScript.contains("tasks.named(\"preReleaseBuild\")"))
        assertTrue(buildScript.contains("dependsOn(validateSafeBrowseReleaseAdMobConfig)"))
    }

    @Test
    fun releaseManifestPlaceholderNoLongerFallsBackToAPlaceholderId() {
        // The historical bug: a hard-coded placeholder AdMob ID that let a release build
        // succeed silently with fake configuration.
        assertFalse(buildScript.contains("ca-app-pub-0000000000000000~0000000000"))
    }
}
