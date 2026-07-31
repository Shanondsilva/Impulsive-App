package com.impulsive.app.frontend.adaptive

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveRehearsalSourceTest {
    private val navigation = source(
        "frontend/navigation/AppNavHost.kt",
    )
    private val screens = source(
        "frontend/screens/adaptive/MomentPlanScreens.kt",
    )
    private val viewModels = source(
        "backend/session/adaptive/MomentPlanViewModels.kt",
    )
    private val rehearsalViewModels = source(
        "backend/session/adaptive/MomentPlanRehearsalViewModels.kt",
    )
    private val mainActivity = source("MainActivity.kt")
    private val notifications = source(
        "backend/service/protection/ProtectionNotificationHelper.kt",
    )

    @Test
    fun routeContainsOnlyOpaqueRehearsalIdentifier() {
        assertTrue(
            navigation.contains(
                "const val MomentPlanRehearsal = " +
                    "\"moment_plan_rehearsal/{rehearsalId}\"",
            ),
        )
        val routeSection = navigation.substring(
            navigation.indexOf("fun momentPlanRehearsal("),
            navigation.indexOf("fun impulsiveBlock("),
        )
        assertFalse(routeSection.contains("actionText"))
        assertFalse(routeSection.contains("futureCueText"))
        assertFalse(routeSection.contains("momentCue"))
        assertFalse(routeSection.contains("actionTarget"))
    }

    @Test
    fun backOffersContinueOrRecordedLeaveWithoutFabricatingCompletion() {
        assertTrue(screens.contains("\"Continue Practice\""))
        assertTrue(screens.contains("\"Leave Practice\""))
        assertTrue(screens.contains("viewModel.leave()"))
        assertFalse(screens.contains("onBack = viewModel::finish"))
    }

    @Test
    fun oldPreviewPathsCannotWritePracticeTimestamp() {
        assertFalse(
            viewModels.contains("rehearsedAtMillis = System.currentTimeMillis()"),
        )
        assertTrue(rehearsalViewModels.contains("coordinator.complete(rehearsalId)"))
    }

    @Test
    fun appLockRemainsGlobalAndNotificationsContainNoPlanContent() {
        assertTrue(mainActivity.contains("if (locked)"))
        assertTrue(mainActivity.contains("AppLockGateScreen("))
        assertFalse(notifications.contains("actionText"))
        assertFalse(notifications.contains("futureCueText"))
        assertFalse(notifications.contains("rehearsalId"))
    }

    private fun source(path: String): String =
        File("src/main/java/com/impulsive/app/$path").readText()
}
