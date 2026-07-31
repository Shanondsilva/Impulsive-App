package com.impulsive.app.frontend

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V28FinalRepairSourceSafetyTest {

    private val projectRoot = File(System.getProperty("user.dir"))
    private val appRoot =
        if (projectRoot.name == "app") projectRoot else File(projectRoot, "app")

    private val appMonitorServiceSource = File(
        appRoot,
        "src/main/java/com/impulsive/app/backend/service/protection/AppMonitorService.kt",
    ).readText().replace("\r\n", "\n")

    private val progressDashboardScreenSource = File(
        appRoot,
        "src/main/java/com/impulsive/app/frontend/screens/progress/ProgressDashboardScreen.kt",
    ).readText().replace("\r\n", "\n")

    // The approved front/back labels live in the pure content model extracted
    // during the Reset Reading flip-card repair, not inline in the screen file.
    private val resetReadingFlipContentSource = File(
        appRoot,
        "src/main/java/com/impulsive/app/frontend/screens/progress/ResetReadingFlipContent.kt",
    ).readText().replace("\r\n", "\n")

    @Test
    fun replaceForegroundWithGenericMonitoringNotification_doesNotResetDismissal() {
        val methodStart = appMonitorServiceSource.indexOf(
            "private fun replaceForegroundWithGenericMonitoringNotification()",
        )
        assertTrue(
            "replaceForegroundWithGenericMonitoringNotification method not found",
            methodStart >= 0,
        )

        val methodEnd = appMonitorServiceSource.indexOf(
            "\n    }\n",
            methodStart,
        )
        val methodBody = appMonitorServiceSource.substring(
            methodStart,
            methodEnd,
        )

        assertFalse(
            "Focus reconciliation must not reset monitoringNotificationDismissed",
            methodBody.contains("monitoringNotificationDismissed = false"),
        )
    }

    @Test
    fun replaceForegroundWithGenericMonitoringNotification_doesNotCancelForegroundNotification() {
        val methodStart = appMonitorServiceSource.indexOf(
            "private fun replaceForegroundWithGenericMonitoringNotification()",
        )
        assertTrue(
            "replaceForegroundWithGenericMonitoringNotification method not found",
            methodStart >= 0,
        )

        val methodEnd = appMonitorServiceSource.indexOf(
            "\n    }\n",
            methodStart,
        )
        val methodBody = appMonitorServiceSource.substring(
            methodStart,
            methodEnd,
        )

        assertFalse(
            "Dismissed foreground notification must be preserved by suppressing reposts, not NotificationManager.cancel",
            methodBody.contains("NotificationManagerCompat.from(this).cancel"),
        )

        assertTrue(
            "Focus reconciliation must use the shared publication policy",
            methodBody.contains(
                "shouldPublishMonitoringNotificationUpdate()",
            ),
        )
    }

    @Test
    fun runningAndPausedFocus_updatesRespectDismissalPolicy() {
        val observerStart = appMonitorServiceSource.indexOf(
            "private fun startForegroundNotificationObserver()",
        )
        assertTrue(
            "startForegroundNotificationObserver method not found",
            observerStart >= 0,
        )

        val observerEnd = appMonitorServiceSource.indexOf(
            "\n    private fun scheduleFocusCompletion",
            observerStart,
        )
        assertTrue(
            "startForegroundNotificationObserver end not found",
            observerEnd > observerStart,
        )

        val observerBody = appMonitorServiceSource.substring(
            observerStart,
            observerEnd,
        )

        val policyGuardCount =
            Regex(
                "if \\(shouldPublishMonitoringNotificationUpdate\\(\\)\\)",
            ).findAll(observerBody).count()

        assertTrue(
            "Running and Paused Focus branches must both gate notification reposting",
            policyGuardCount >= 2,
        )
    }

    @Test
    fun progressResetReadingRendering_omitsRemovedContent() {
        val cardStart = progressDashboardScreenSource.indexOf(
            "private fun ResetReadingProgressCard(",
        )
        assertTrue("ResetReadingProgressCard not found", cardStart >= 0)
        val metricStart = progressDashboardScreenSource.indexOf(
            "private fun ResetReadingCompactMetric(",
            cardStart,
        )
        assertTrue("ResetReadingCompactMetric not found", metricStart >= 0)
        val metricEnd = progressDashboardScreenSource.indexOf("\n}\n", metricStart)
        val cardRenderingSource = progressDashboardScreenSource.substring(cardStart, metricEnd)

        assertFalse(
            cardRenderingSource.contains("safe reading"),
        )
        assertFalse(
            cardRenderingSource.contains("start reset"),
        )
        assertFalse(
            cardRenderingSource.contains("secondaryMetric == \"Open\""),
        )
    }

    @Test
    fun progressResetReadingRendering_containsApprovedLabels() {
        val combinedSource = progressDashboardScreenSource + resetReadingFlipContentSource
        assertTrue(combinedSource.contains("\"Last completed\""))
        assertTrue(combinedSource.contains("\"Helpful\""))
        assertTrue(combinedSource.contains("\"Completed\""))
        assertTrue(combinedSource.contains("\"Abandoned\""))
    }
}
