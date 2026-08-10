package com.impulsive.app.backend.data.restore

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestorePayloadSizeIntegrationSourceTest {
    @Test
    fun sharedWriterValidatesTheCompletePayloadBeforeReturning() {
        val source =
            source(
                "RestoreBundleWriter.kt",
            )

        val buildPayloadSection =
            section(
                source =
                    source,
                startToken =
                    "suspend fun buildPayloadJson(",
                endToken =
                    "companion object",
            )

        assertTrue(
            buildPayloadSection.contains(
                "RestorePayloadSizePolicy",
            ),
        )

        assertTrue(
            buildPayloadSection.contains(
                ".requireWithinLimit(",
            ),
        )

        assertOrdered(
            source =
                buildPayloadSection,
            "payload.put(",
            "CloudRecoveryOnboardingSnapshotJsonKey",
            "RestorePayloadSizePolicy",
            "payload.toString()",
        )
    }

    @Test
    fun automaticBundleValidatesBeforeChecksumAndJsonConstruction() {
        val source =
            source(
                "RestoreBundleWriter.kt",
            )

        val automaticSection =
            section(
                source =
                    source,
                startToken =
                    "internal fun buildAutomaticBundleJson(",
                endToken =
                    "internal fun automaticBundleChecksumMaterialV2(",
            )

        assertOrdered(
            source =
                automaticSection,
            "val validatedPayloadJson",
            "RestorePayloadSizePolicy",
            "val checksumMaterial",
            ".put(\"checksumSha256\"",
            "\"payloadJson\"",
            "validatedPayloadJson",
        )
    }

    @Test
    fun automaticFileWorkOccursOnlyAfterValidatedPayloadAndBundleCreation() {
        val source =
            source(
                "RestoreBundleWriter.kt",
            )

        val writeSection =
            section(
                source =
                    source,
                startToken =
                    "suspend fun writeBundle(",
                endToken =
                    "suspend fun buildPayloadJson(",
            )

        assertOrdered(
            source =
                writeSection,
            "val payloadJson = buildPayloadJson(",
            "val bundleJson = buildAutomaticBundleJson(",
            "val directory = File(",
            "temp.writeText(",
        )
    }

    @Test
    fun manualExportValidatesBeforeRandomnessEncryptionAndOutput() {
        val source =
            source(
                "ManualBackupManager.kt",
            )

        val exportSection =
            section(
                source =
                    source,
                startToken =
                    "suspend fun exportTo(",
                endToken =
                    "suspend fun importFrom(",
            )

        assertOrdered(
            source =
                exportSection,
            "val rawPayloadJson",
            ".buildPayloadJson()",
            "val payloadJson",
            "RestorePayloadSizePolicy",
            "val random = SecureRandom()",
            "cipher.doFinal(",
            "output.write(",
            "output.flush()",
        )
    }

    @Test
    fun automaticAndManualImportUseTheSharedRawPayloadLimit() {
        val importerSource =
            source(
                "RestoreBundleImporter.kt",
            )

        val manualSource =
            source(
                "ManualBackupManager.kt",
            )

        assertEquals(
            1,
            importerSource
                .windowed(
                    RestorePayloadSizePolicyReference
                        .length,
                )
                .count {
                    it ==
                        RestorePayloadSizePolicyReference
                },
        )

        assertTrue(
            manualSource.contains(
                RestorePayloadSizePolicyReference,
            ),
        )
    }

    private fun source(
        fileName: String,
    ): String {
        return File(
            "src/main/java/com/impulsive/app/" +
                "backend/data/restore/" +
                fileName,
        )
            .readText()
    }

    private fun section(
        source: String,
        startToken: String,
        endToken: String,
    ): String {
        val start =
            source.indexOf(
                startToken,
            )

        val end =
            source.indexOf(
                endToken,
                startIndex =
                    start + 1,
            )

        assertTrue(
            "Missing section start: $startToken",
            start >= 0,
        )

        assertTrue(
            "Missing section end: $endToken",
            end > start,
        )

        return source.substring(
            start,
            end,
        )
    }

    private fun assertOrdered(
        source: String,
        vararg tokens: String,
    ) {
        var previousIndex =
            -1

        tokens.forEach { token ->
            val currentIndex =
                source.indexOf(
                    token,
                    startIndex =
                        previousIndex + 1,
                )

            assertTrue(
                "Missing or out-of-order token: $token",
                currentIndex >
                    previousIndex,
            )

            previousIndex =
                currentIndex
        }
    }

    private companion object {
        const val RestorePayloadSizePolicyReference =
            "RestorePayloadSizePolicy.MaximumPayloadBytes"
    }
}