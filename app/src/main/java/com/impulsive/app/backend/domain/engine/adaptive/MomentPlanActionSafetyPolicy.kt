package com.impulsive.app.backend.domain.engine.adaptive

import com.impulsive.app.backend.domain.model.adaptive.ImpulsiveDestination
import com.impulsive.app.backend.domain.model.adaptive.MomentPlan
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanActionType

enum class MomentPlanActionUnavailableReason {
    BlankDestination,
    MalformedPackageName,
    MissingPackage,
    TriggeringApplication,
    ProtectedApplication,
    DisabledPlan,
    StaleContentRevision,
    UnsupportedInternalDestination,
    TextOnlyNotExecutable,
    DestinationUnavailable,
}

data class MomentPlanActionSafetyContext(
    val expectedContentRevisionId: String,
    val availablePackageNames: Set<String>,
    val protectedPackageNames: Set<String>,
    val triggeringPackageName: String? = null,
    val availableInternalDestinations: Set<ImpulsiveDestination> = ImpulsiveDestination.entries.toSet(),
)

sealed interface MomentPlanActionSafetyResult {
    data class Available(
        val planId: String,
        val contentRevisionId: String,
        val actionType: MomentPlanActionType,
        val actionTarget: String,
    ) : MomentPlanActionSafetyResult

    data class Unavailable(val reason: MomentPlanActionUnavailableReason) :
        MomentPlanActionSafetyResult
}

object MomentPlanActionSafetyPolicy {
    fun evaluate(
        plan: MomentPlan,
        context: MomentPlanActionSafetyContext,
    ): MomentPlanActionSafetyResult {
        if (!plan.enabled) return unavailable(MomentPlanActionUnavailableReason.DisabledPlan)
        if (plan.contentRevisionId != context.expectedContentRevisionId) {
            return unavailable(MomentPlanActionUnavailableReason.StaleContentRevision)
        }
        if (plan.actionType == MomentPlanActionType.TextOnly) {
            return unavailable(MomentPlanActionUnavailableReason.TextOnlyNotExecutable)
        }
        val target = plan.actionTarget?.trim().orEmpty()
        if (target.isBlank()) return unavailable(MomentPlanActionUnavailableReason.BlankDestination)

        when (plan.actionType) {
            MomentPlanActionType.TextOnly -> error("Handled before target validation")
            MomentPlanActionType.OpenImpulsiveDestination -> {
                val destination = ImpulsiveDestination.entries
                    .firstOrNull { it.storageValue == target }
                    ?: return unavailable(MomentPlanActionUnavailableReason.UnsupportedInternalDestination)
                if (destination !in context.availableInternalDestinations) {
                    return unavailable(MomentPlanActionUnavailableReason.DestinationUnavailable)
                }
            }
            MomentPlanActionType.LaunchSelectedApp -> {
                if (!isValidAndroidPackage(target)) {
                    return unavailable(MomentPlanActionUnavailableReason.MalformedPackageName)
                }
                if (target == context.triggeringPackageName) {
                    return unavailable(MomentPlanActionUnavailableReason.TriggeringApplication)
                }
                if (target in context.protectedPackageNames) {
                    return unavailable(MomentPlanActionUnavailableReason.ProtectedApplication)
                }
                if (target !in context.availablePackageNames) {
                    return unavailable(MomentPlanActionUnavailableReason.MissingPackage)
                }
            }
        }
        return MomentPlanActionSafetyResult.Available(
            planId = plan.planId,
            contentRevisionId = plan.contentRevisionId,
            actionType = plan.actionType,
            actionTarget = target,
        )
    }

    private fun unavailable(reason: MomentPlanActionUnavailableReason) =
        MomentPlanActionSafetyResult.Unavailable(reason)

    private fun isValidAndroidPackage(value: String): Boolean = value.matches(
        Regex("""^[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+$"""),
    )
}
