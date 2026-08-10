package com.impulsive.app.backend.domain.engine.adaptive

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentPlanActionSafetyProductionConnectionTest {
    @Test
    fun triggeringPackage_isRejectedWithoutPersistence() {
        val viewModel = source(
            "backend/session/adaptive/AdaptiveMomentViewModel.kt",
        )
        val navigation = source("frontend/navigation/AppNavHost.kt")
        assertTrue(viewModel.contains("triggeringPackageName = triggeringPackageName"))
        assertTrue(navigation.contains("triggeringPackageName = request.sourcePackageName"))
        assertFalse(viewModel.contains("decisions.update(triggeringPackageName"))
    }

    @Test
    fun textOnlyStart_doesNotRecordStarted() {
        val source = source("backend/session/adaptive/AdaptiveMomentViewModel.kt")
        val method = source.substringAfter("fun startMomentPlan()")
            .substringBefore("fun completeCurrentIntervention()")
        assertTrue(method.contains("MomentPlanActionSafetyPolicy.evaluate"))
        assertFalse(method.contains("markStartedAfterSuccessfulEntry"))
        assertFalse(method.contains("MomentPlanActionType.TextOnly"))
    }

    @Test
    fun textOnlyStart_doesNotNavigate() {
        val source = source("backend/session/adaptive/AdaptiveMomentViewModel.kt")
        val method = source.substringAfter("fun startMomentPlan()")
            .substringBefore("fun completeCurrentIntervention()")
        assertTrue(method.indexOf("MomentPlanActionSafetyPolicy.evaluate") < method.indexOf("routeRequest = route"))
    }

    private fun source(relative: String) = File(
        "src/main/java/com/impulsive/app/$relative",
    ).readText()
}
