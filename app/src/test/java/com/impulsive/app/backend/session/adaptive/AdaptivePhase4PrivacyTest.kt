package com.impulsive.app.backend.session.adaptive

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptivePhase4PrivacyTest {
    private val sessionSource = sourceDirectory(
        "src/main/java/com/impulsive/app/backend/session/adaptive",
    )
    private val worker = source(
        "src/main/java/com/impulsive/app/backend/session/adaptive/AdaptiveObservation.kt",
    )
    private val entity = source(
        "src/main/java/com/impulsive/app/backend/data/local/entity/AdaptiveDecisionEntity.kt",
    )
    private val application = source("src/main/java/com/impulsive/app/ImpulsiveApplication.kt")
    private val gradle = source("build.gradle.kts")

    @Test
    fun workerInputContainsOnlyDecisionId() {
        assertTrue(worker.contains("putString(AdaptiveObservationFinalizerWorker.InputDecisionId, decisionId)"))
        assertFalse(worker.contains("putString(\"url\""))
        assertFalse(worker.contains("putString(\"domain\""))
        assertFalse(worker.contains("putString(\"package\""))
    }

    @Test
    fun uniqueWorkNameUsesRequiredFormat() {
        assertTrue(worker.contains("\"adaptive-observation-${'$'}decisionId\""))
    }

    @Test
    fun uniqueWorkUsesKeepPolicy() {
        assertTrue(worker.contains("ExistingWorkPolicy.KEEP"))
    }

    @Test
    fun workerRequiresNoNetworkConstraint() {
        assertFalse(worker.contains("NetworkType"))
        assertFalse(worker.contains("setRequiredNetworkType"))
    }

    @Test
    fun presentationContainsNoUrlOrDomain() {
        val models = source(
            "src/main/java/com/impulsive/app/backend/session/adaptive/AdaptiveLifecycleModels.kt",
        )
        val presentation = models.substring(
            models.indexOf("data class AdaptiveMomentPresentation"),
            models.indexOf("data class AdaptiveMomentCoordinationResult"),
        ).lowercase()
        assertFalse(presentation.contains("url"))
        assertFalse(presentation.contains("domain"))
        assertFalse(presentation.contains("actiontext"))
        assertFalse(presentation.contains("futurecue"))
        assertFalse(presentation.contains("packagename"))
    }

    @Test
    fun logsContainNoPlanText() {
        assertFalse(sessionSource.contains("Log.w(\"AdaptiveMoment\", actionText"))
        assertFalse(sessionSource.contains("Log.w(\"AdaptiveMoment\", futureCueText"))
        assertTrue(sessionSource.contains("error.javaClass.simpleName"))
    }

    @Test
    fun noFirebaseDependencyIsIntroducedInPhase4Package() {
        assertFalse(sessionSource.contains("com.google.firebase"))
        assertFalse(worker.contains("Firebase"))
    }

    @Test
    fun noUnencryptedDataStoreIsIntroduced() {
        assertFalse(sessionSource.contains("DataStore"))
        assertFalse(sessionSource.contains("preferencesDataStore"))
    }

    @Test
    fun noRawSourceFieldWasAddedToAdaptiveEntity() {
        assertFalse(entity.contains("rawUrl"))
        assertFalse(entity.contains("domain"))
        assertFalse(entity.contains("sourcePackage"))
        assertFalse(entity.contains("pageTitle"))
    }

    @Test
    fun windowClassificationRequiresNoProtectedPackageOrDomain() {
        val coordinator = source(
            "src/main/java/com/impulsive/app/backend/session/adaptive/AdaptiveMomentCoordinator.kt",
        )
        assertFalse(coordinator.contains("sourcePackage"))
        assertFalse(coordinator.contains("val domain"))
        assertFalse(coordinator.contains("rawUrl"))
    }

    @Test
    fun phase4DoesNotIntegrateProtectionServices() {
        assertFalse(sessionSource.contains("AppMonitorService"))
        assertFalse(sessionSource.contains("ImpulsiveVpnService"))
        assertFalse(sessionSource.contains("ProtectionInterruptionOverlay"))
    }

    @Test
    fun phase4DoesNotIntegrateGamesOrReading() {
        assertFalse(sessionSource.contains("RecoveryGamesScreen"))
        assertFalse(sessionSource.contains("ResetReadScreen"))
        assertFalse(sessionSource.contains("startActivity("))
    }

    @Test
    fun startupRecoveryCreatesNoDecision() {
        assertTrue(application.contains("AdaptivePhase4Dependencies.recovery(applicationContext).recover()"))
        assertFalse(application.contains("AdaptiveMomentCoordinator"))
    }

    @Test
    fun workManagerDependencyWasAlreadyPresent() {
        assertTrue(gradle.contains("implementation(libs.androidx.work.runtime.ktx)"))
    }

    private fun source(path: String): String = File(path).readText()

    private fun sourceDirectory(path: String): String =
        File(path).walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }
}
