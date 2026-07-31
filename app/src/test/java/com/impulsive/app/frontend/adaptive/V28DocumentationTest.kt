package com.impulsive.app.frontend.adaptive

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V28DocumentationTest {
    @Test
    fun architectureDocumentsEveryPrivateMomentLoopStageAndBoundary() {
        val text = document("docs/innovation/V28_PRIVATE_MOMENT_LOOP_ARCHITECTURE.md")

        listOf(
            "Prepare",
            "Notice",
            "Pivot",
            "Act",
            "Learn",
            "Understand and Control",
            "Encryption boundary",
            "Protection boundary",
            "No-cloud behavioural-data boundary",
            "Failure fallback",
            "Process-death recovery",
            "Test architecture",
        ).forEach { required -> assertTrue(required, text.contains(required)) }
        assertTrue(text.contains("```mermaid"))
    }

    @Test
    fun manualPlanHasHistoricalTestsAndFinalUiRepairRetestItemsUnpassed() {
        val text = document("docs/testing/V28_CONNECTED_INNOVATION_MANUAL_TESTS.md")
        val headings = Regex("(?m)^## (\\d+)\\. ").findAll(text).toList()

        assertEquals((1..121).toList(), headings.map { it.groupValues[1].toInt() })
        assertEquals(121, Regex("(?m)^- PASS/FAIL:$").findAll(text).count())
        assertEquals(105, Regex("(?m)^- Evidence filename:").findAll(text).count())
        assertTrue(text.contains("Final UI and Accessibility Repair Retest"))
        assertFalse(text.contains("- PASS/FAIL: PASS"))
    }

    @Test
    fun roadmapMarksResearchConceptsAsNotImplemented() {
        val text = document("docs/innovation/V28_FUTURE_PLATFORM_ROADMAP.md")

        assertTrue(text.contains("Private Learning Network"))
        assertTrue(text.contains("Moment Protocol Studio"))
        assertTrue(text.contains("It is not implemented."))
        assertTrue(text.contains("No partnership is claimed"))
        assertFalse(text.contains("medically proven", ignoreCase = true))
    }

    @Test
    fun evidenceAndDefensibilityDocsRejectUnsupportedClaims() {
        val evidence = document("docs/innovation/V28_ENDORSEMENT_EVIDENCE_MATRIX.md")
        val defensibility = document("docs/innovation/V28_DEFENSIBILITY_MAP.md")

        assertTrue(evidence.contains("Remaining limitation"))
        assertTrue(evidence.contains("Prohibited overclaim"))
        assertTrue(evidence.contains("There is no evidence"))
        assertTrue(defensibility.contains("does not claim monopoly"))
        assertTrue(defensibility.contains("no legal conclusion about patentability"))
    }

    private fun document(path: String): String {
        val file = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .map { File(it, path) }
            .firstOrNull(File::isFile)
        return requireNotNull(file) { "Could not find $path" }.readText()
    }
}
