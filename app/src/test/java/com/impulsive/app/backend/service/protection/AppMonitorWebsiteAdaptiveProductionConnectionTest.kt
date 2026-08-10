package com.impulsive.app.backend.service.protection

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppMonitorWebsiteAdaptiveProductionConnectionTest {
    private val source = File(
        "src/main/java/com/impulsive/app/" +
            "backend/service/protection/" +
            "AppMonitorService.kt",
    ).readText()

    private val websitePath = source
        .substringAfter(
            "private fun " +
                "launchWebsiteProtectionIncidentSurface",
        )
        .substringBefore(
            "private fun " +
                "handleFocusInterruption",
        )

    @Test
    fun serviceUsesRestartSafeWebsiteAdmissionResolver() {
        assertTrue(source.contains("WebsiteAdaptiveIncidentAdmissionResolver"))

        assertTrue(websitePath.contains("websiteAdaptiveIncidentAdmissionResolver"))

        assertTrue(websitePath.contains(".resolve("))

        assertTrue(websitePath.contains("lastAdmittedIncidentToken"))

        assertTrue(websitePath.contains("activeCycleIncidentToken"))

        assertTrue(websitePath.contains("WebsiteAdaptiveIncidentAdmissionResolution"))

        assertFalse(
            websitePath.contains(
                "adaptiveDecisionRepository" +
                    ".getByIncidentToken",
            ),
        )
    }

    @Test
    fun serviceUpdatesMemoryHintOnlyAfterPersistedRecognition() {
        val compact = websitePath.replace(Regex("\\s+"), " ")

        assertTrue(
            compact.contains("recognised .decisionId != null") ||
                compact.contains("recognised.decisionId != null"),
        )

        assertTrue(
            compact.contains("!recognised .fallbackRequired") ||
                compact.contains("!recognised.fallbackRequired"),
        )

        assertTrue(websitePath.contains("lastWebsiteAdaptiveIncidentToken"))
    }

    @Test
    fun servicePreservesActiveCycleAndBlockedSiteRouting() {
        assertTrue(
            websitePath.contains("adaptiveSupportCycleCoordinator" + ".recover()") ||
                websitePath.contains("adaptiveSupportCycleCoordinator" + "\n"),
        )

        assertTrue(
            websitePath.contains(
                "WebsiteAdaptiveIncidentAdmissionResolution" +
                    ".ActiveCycle",
            ) ||
                websitePath.contains(
                    "WebsiteAdaptiveIncidentAdmissionResolution" +
                        "\n",
                ),
        )

        assertTrue(websitePath.contains("BlockedSiteInterruptionState"))

        assertTrue(
            websitePath.contains(
                "BlockedSitePrimaryAction" +
                    ".OpenCoordinatorRecommendation",
            ),
        )

        assertTrue(
            websitePath.contains(
                "BlockedSiteQuietFallback" +
                    ".DismissInterruption",
            ),
        )
    }
}
