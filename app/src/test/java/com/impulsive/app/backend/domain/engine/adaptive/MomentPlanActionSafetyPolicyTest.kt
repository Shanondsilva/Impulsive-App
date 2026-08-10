package com.impulsive.app.backend.domain.engine.adaptive

import com.impulsive.app.backend.domain.model.adaptive.ImpulsiveDestination
import com.impulsive.app.backend.domain.model.adaptive.MomentPlan
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanActionType
import org.junit.Assert.assertEquals
import org.junit.Test

class MomentPlanActionSafetyPolicyTest {
    @Test
    fun executableAppPreservesPlanAndRevisionIdentity() {
        val result = evaluate(appPlan()) as MomentPlanActionSafetyResult.Available
        assertEquals("plan", result.planId)
        assertEquals("revision", result.contentRevisionId)
        assertEquals("com.example.safe", result.actionTarget)
    }

    @Test
    fun safeExternalDestination_remainsExecutable() {
        assertEquals(
            MomentPlanActionSafetyResult.Available(
                "plan",
                "revision",
                MomentPlanActionType.LaunchSelectedApp,
                "com.example.safe",
            ),
            evaluate(appPlan()),
        )
    }

    @Test
    fun protectedDestination_remainsRejected() {
        assertReason(
            appPlan(),
            MomentPlanActionUnavailableReason.ProtectedApplication,
            context().copy(protectedPackageNames = setOf("com.example.safe")),
        )
    }

    @Test
    fun staleRevision_remainsRejected() {
        assertReason(
            appPlan(),
            MomentPlanActionUnavailableReason.StaleContentRevision,
            context().copy(expectedContentRevisionId = "old-revision"),
        )
    }

    @Test
    fun rejectsEveryUnavailableAppBoundary() {
        assertReason(appPlan(target = ""), MomentPlanActionUnavailableReason.BlankDestination)
        assertReason(appPlan(target = "not-a-package"), MomentPlanActionUnavailableReason.MalformedPackageName)
        assertReason(appPlan(target = "com.example.missing"), MomentPlanActionUnavailableReason.MissingPackage)
        assertReason(
            appPlan(),
            MomentPlanActionUnavailableReason.TriggeringApplication,
            context().copy(triggeringPackageName = "com.example.safe"),
        )
        assertReason(
            appPlan(),
            MomentPlanActionUnavailableReason.ProtectedApplication,
            context().copy(protectedPackageNames = setOf("com.example.safe")),
        )
    }

    @Test
    fun rejectsDisabledStaleTextAndUnsupportedInternalPlans() {
        assertReason(appPlan().copy(enabled = false), MomentPlanActionUnavailableReason.DisabledPlan)
        assertReason(
            appPlan(),
            MomentPlanActionUnavailableReason.StaleContentRevision,
            context().copy(expectedContentRevisionId = "old-revision"),
        )
        assertReason(
            appPlan().copy(actionType = MomentPlanActionType.TextOnly, actionTarget = null),
            MomentPlanActionUnavailableReason.TextOnlyNotExecutable,
        )
        assertReason(
            appPlan().copy(
                actionType = MomentPlanActionType.OpenImpulsiveDestination,
                actionTarget = "invented",
            ),
            MomentPlanActionUnavailableReason.UnsupportedInternalDestination,
        )
        assertReason(
            appPlan().copy(
                actionType = MomentPlanActionType.OpenImpulsiveDestination,
                actionTarget = ImpulsiveDestination.Focus.storageValue,
            ),
            MomentPlanActionUnavailableReason.DestinationUnavailable,
            context().copy(availableInternalDestinations = emptySet()),
        )
    }

    private fun assertReason(
        plan: MomentPlan,
        reason: MomentPlanActionUnavailableReason,
        context: MomentPlanActionSafetyContext = context(),
    ) = assertEquals(
        MomentPlanActionSafetyResult.Unavailable(reason),
        evaluate(plan, context),
    )

    private fun evaluate(
        plan: MomentPlan,
        context: MomentPlanActionSafetyContext = context(),
    ) = MomentPlanActionSafetyPolicy.evaluate(plan, context)

    private fun context() = MomentPlanActionSafetyContext(
        expectedContentRevisionId = "revision",
        availablePackageNames = setOf("com.example.safe"),
        protectedPackageNames = emptySet(),
    )

    private fun appPlan(target: String? = "com.example.safe") = MomentPlan(
        planId = "plan",
        title = "Plan",
        momentCue = null,
        actionText = "Open the safe app",
        futureCueText = "Use this next time",
        actionType = MomentPlanActionType.LaunchSelectedApp,
        actionTarget = target,
        enabled = true,
        preferredForCue = false,
        createdAtMillis = 1L,
        updatedAtMillis = 2L,
        contentRevisionId = "revision",
    )
}
