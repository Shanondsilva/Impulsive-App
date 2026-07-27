package com.impulsive.app.frontend.navigation

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ResetReadingProtectionLaunchTest {
    private val source = File(
        "src/main/java/com/impulsive/app/frontend/navigation/AppNavHost.kt",
    ).readText()

    @Test
    fun readingRequestIsConsumedAfterFallbackDestinationComposition() {
        val destination = source.substring(
            source.indexOf("composable(AppRoutes.ResetReadFallbackTask)"),
            source.indexOf("composable(AppRoutes.JournalHub)"),
        )
        val readyIndex = destination.indexOf("BlockRequestDestinationReadyEffect")
        val screenIndex = destination.indexOf("ResetReadScreen")

        assertTrue(readyIndex >= 0)
        assertTrue(screenIndex > readyIndex)
        assertTrue(destination.contains("expectedTarget = BlockLaunchTarget.ReadingReset"))
        assertTrue(destination.contains("currentRoutePattern = backStackEntry.destination.route"))
        assertTrue(destination.contains("launchMode = ResetReadLaunchMode.Fallback"))
    }
}
