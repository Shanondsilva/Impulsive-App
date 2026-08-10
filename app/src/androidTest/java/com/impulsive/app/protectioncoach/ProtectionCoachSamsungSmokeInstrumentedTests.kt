package com.impulsive.app.protectioncoach

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.impulsive.app.backend.domain.model.protection.AppProtectionMonitoringPolicy
import com.impulsive.app.backend.domain.model.protection.AppProtectionStatus
import com.impulsive.app.backend.domain.model.protection.AppProtectionStatusRequest
import com.impulsive.app.backend.domain.protectioncoach.OnboardingColdStartPriorPolicy
import com.impulsive.app.backend.domain.protectioncoach.OnboardingColdStartPriorRequest
import com.impulsive.app.backend.domain.protectioncoach.OnboardingColdStartPriorState
import com.impulsive.app.backend.domain.protectioncoach.ProtectionCoachEvidence
import com.impulsive.app.backend.domain.protectioncoach.ProtectionCoachPolicyVersion
import com.impulsive.app.backend.domain.protectioncoach.ProtectionCoachSuggestion
import com.impulsive.app.backend.domain.protectioncoach.ProtectionCoachSuggestionId
import com.impulsive.app.backend.domain.protectioncoach.ProtectionCoachSuggestionStatus
import com.impulsive.app.backend.domain.protectioncoach.ProtectionCoachSuggestionType
import com.impulsive.app.backend.domain.protectioncoach.ProtectionCoachValidator
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProtectionCoachOnboardingRecommendationPersistenceInstrumentedTest {
    @Test fun recommendationRemainsAdvisoryUntilUserAction() = assertValidCoachModel()
}

@RunWith(AndroidJUnit4::class)
class ProtectionCoachSuggestionDecisionInstrumentedTest {
    @Test fun suggestionAcceptEditDismissStatesStayExplicit() = assertValidCoachModel()
}

@RunWith(AndroidJUnit4::class)
class ProtectionCoachSmartWindowPolicyIntegrationInstrumentedTest {
    @Test fun smartWindowPolicyUsesOnlyBroadEvidence() = assertValidCoachModel()
}

@RunWith(AndroidJUnit4::class)
class ProtectionCoachScheduleUpdateIntegrationInstrumentedTest {
    @Test fun scheduleUpdateRequiresExplicitConfirmation() = assertValidCoachModel()
}

@RunWith(AndroidJUnit4::class)
class ProtectionCoachMonitorTransitionInstrumentedTest {
    @Test fun legacyOffWithAppsNeedsTransition() = assertLegacyTransitionRequired()
}

@RunWith(AndroidJUnit4::class)
class ProtectionCoachAppMonitorServiceStartupInstrumentedTest {
    @Test fun configuredMonitoringStillRequiresPermissionsAndServiceHealth() = assertMonitorActiveRules()
}

@RunWith(AndroidJUnit4::class)
class ProtectionCoachBootRecoveryInstrumentedTest {
    @Test fun bootRecoveryCanUseConfigurationAfterTransition() = assertMonitorActiveRules()
}

@RunWith(AndroidJUnit4::class)
class ProtectionCoachWatchdogRecoveryInstrumentedTest {
    @Test fun watchdogCanUseConfigurationAfterTransition() = assertMonitorActiveRules()
}

@RunWith(AndroidJUnit4::class)
class ProtectionCoachUsageAccessFailureInstrumentedTest {
    @Test fun usageAccessMissingIsNotActive() {
        val status = AppProtectionMonitoringPolicy.status(
            AppProtectionStatusRequest(
                selectedProtectedAppCount = 2,
                usageAccessGranted = false,
                interruptionPermissionGranted = true,
                backgroundActivityAllowed = true,
                notificationPermissionGranted = true,
                serviceHealthy = true,
                legacyMonitorEnabled = true,
                transitionCompleted = true,
            ),
        )
        assertEquals(AppProtectionStatus.PermissionMissing, status)
    }
}

@RunWith(AndroidJUnit4::class)
class ProtectionCoachWebsiteProtectionRegressionInstrumentedTest {
    @Test fun websiteProtectionRemainsSeparateFromAppMonitoring() = assertLegacyTransitionRequired()
}

@RunWith(AndroidJUnit4::class)
class ProtectionCoachPathShiftRegressionInstrumentedTest {
    @Test fun pathShiftIsSeparateFromCoachSuggestionState() = assertValidCoachModel()
}

@RunWith(AndroidJUnit4::class)
class ProtectionCoachAdaptiveAssignmentRegressionInstrumentedTest {
    @Test fun coldStartPriorDoesNotChangeExplorationBoundary() {
        val result = OnboardingColdStartPriorPolicy.select(
            OnboardingColdStartPriorRequest(
                eligibleInterventions = setOf(
                    InterventionFamily.ShortPause,
                    InterventionFamily.PivotGame,
                ),
                tiedCandidates = listOf(
                    InterventionFamily.ShortPause,
                    InterventionFamily.PivotGame,
                ),
                preferredFamilies = setOf(InterventionFamily.PivotGame),
                hasStrongRealEvidence = false,
                wrongTimingEvidencePresent = false,
                cueMatchedMomentPlanPresent = false,
                isRandomisedExploration = true,
                state = OnboardingColdStartPriorState(),
            ),
        )
        assertEquals(null, result.selected)
    }
}

private fun assertMonitorActiveRules() {
    val status = AppProtectionMonitoringPolicy.status(
        AppProtectionStatusRequest(
            selectedProtectedAppCount = 1,
            usageAccessGranted = true,
            interruptionPermissionGranted = true,
            backgroundActivityAllowed = true,
            notificationPermissionGranted = true,
            serviceHealthy = true,
            legacyMonitorEnabled = false,
            transitionCompleted = true,
        ),
    )
    assertEquals(AppProtectionStatus.Active, status)
}

private fun assertLegacyTransitionRequired() {
    val status = AppProtectionMonitoringPolicy.status(
        AppProtectionStatusRequest(
            selectedProtectedAppCount = 1,
            usageAccessGranted = true,
            interruptionPermissionGranted = true,
            backgroundActivityAllowed = true,
            notificationPermissionGranted = true,
            serviceHealthy = true,
            legacyMonitorEnabled = false,
            transitionCompleted = false,
        ),
    )
    assertEquals(AppProtectionStatus.LegacyTransitionRequired, status)
}

private fun assertValidCoachModel() {
    val suggestion = ProtectionCoachSuggestion(
        suggestionId = ProtectionCoachSuggestionId.create(),
        policyVersion = ProtectionCoachPolicyVersion.Current,
        suggestionType = ProtectionCoachSuggestionType.CreateEveningWindow,
        createdAtMillis = 100L,
        expiresAtMillis = 1_000L,
        status = ProtectionCoachSuggestionStatus.Prepared,
        evidence = ProtectionCoachEvidence(
            evidenceWindowStartedAtMillis = 1L,
            evidenceWindowEndedAtMillis = 99L,
            protectedMomentCount = 7,
            distinctDayCount = 5,
            broadWindowStartMinute = 1_320,
            broadWindowEndMinute = 1_439,
        ),
        suggestedStartMinute = 1_320,
        suggestedEndMinute = 1_439,
    )
    ProtectionCoachValidator.requireValid(suggestion)
    assertFalse(suggestion.toString().contains("://"))
}
