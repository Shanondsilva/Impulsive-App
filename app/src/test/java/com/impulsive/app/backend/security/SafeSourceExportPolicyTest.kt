package com.impulsive.app.backend.security

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AUD-004: the safe source export must never carry diagnostic logs, secrets or
 * build artifacts.
 *
 * The original defect was structural — collection and post-archive validation
 * enforced different policies — so these tests lock the single-policy design as
 * well as the specific filename gap that leaked crash logs.
 */
class SafeSourceExportPolicyTest {

    private val exporterSource =
        File("../scripts/export-source-safe.ps1").readText()

    @Test
    fun `safe source export excludes diagnostic text logs`() {
        // Plain .log files were already covered; these .txt dumps were not.
        assertTrue(exporterSource.contains("'*.log'"))
        assertTrue(exporterSource.contains("'*-log.txt'"))
        assertTrue(exporterSource.contains("'*_log.txt'"))
        assertTrue(exporterSource.contains("'*logcat*.txt'"))
    }

    @Test
    fun `safe export uses one canonical forbidden entry policy`() {
        assertTrue(exporterSource.contains("function Test-IsForbiddenExportEntry"))
        assertTrue(exporterSource.contains("Test-IsExcludedDirectoryEntry"))
        assertTrue(exporterSource.contains("Test-IsExcludedNameEntry"))
        assertTrue(exporterSource.contains("Test-IsSecretEntry"))
        // Filename matching must be explicitly case-insensitive.
        assertTrue(exporterSource.contains("-ilike \$pattern"))
    }

    @Test
    fun `pre archive collection uses the canonical policy`() {
        val collectionBlock = exporterSource
            .substringAfter("\$files =")
            .substringBefore("Add-Type -AssemblyName System.IO.Compression")

        assertTrue(collectionBlock.contains("Test-IsForbiddenExportEntry"))

        /*
         * Collection must not carry its own duplicate exclusion loop, which is
         * how the two policies drifted apart originally.
         */
        assertFalse(collectionBlock.contains("foreach (\$pat in \$excludeNamePatterns)"))
        assertFalse(collectionBlock.contains("\$excludeDirs -contains"))
    }

    @Test
    fun `finished archive is validated with the complete policy`() {
        val validationBlock = exporterSource
            .substringAfter("\$zipRead =")
            .substringBefore("if (\$violations.Count -gt 0)")

        assertTrue(validationBlock.contains("Test-IsForbiddenExportEntry"))

        /*
         * The final loop must never regress to secret-only validation.
         * Test-IsSecretEntry may still run indirectly via the canonical
         * function, but not as the predicate here.
         */
        val secretOnlyPredicate = "if\\s*\\(\\s*Test-IsSecretEntry\\s+\\\$e\\.FullName"

        assertFalse(
            Regex(secretOnlyPredicate).containsMatchIn(validationBlock),
        )
    }

    @Test
    fun `unsafe finished archive is deleted and fails closed`() {
        assertTrue(exporterSource.contains("Remove-Item \$outputPath -Force"))
        assertTrue(exporterSource.contains("SAFE EXPORT POLICY SCAN FAILED"))
        assertTrue(exporterSource.contains("exit 1"))
    }

    @Test
    fun `successful export reports complete safe export policy scan`() {
        assertTrue(exporterSource.contains("SAFE EXPORT POLICY SCAN: CLEAN"))
        // The old wording understated what is actually enforced.
        assertFalse(exporterSource.contains("SECRET SCAN: CLEAN"))
    }

    @Test
    fun `established secret and artifact exclusions are preserved`() {
        listOf(
            "'*.jks'",
            "'*.keystore'",
            "'*.p12'",
            "'*.pfx'",
            "'*.pem'",
            "'*.key'",
            "'*.zip'",
            "'*.apk'",
            "'*.aab'",
            "'local.properties'",
            "'secrets.properties*'",
            "'changed-*.txt'",
        ).forEach { pattern ->
            assertTrue("missing exclusion $pattern", exporterSource.contains(pattern))
        }

        listOf("'.git'", "'build'", "'node_modules'", "'release-keystore'").forEach { dir ->
            assertTrue("missing excluded directory $dir", exporterSource.contains(dir))
        }
    }
}
