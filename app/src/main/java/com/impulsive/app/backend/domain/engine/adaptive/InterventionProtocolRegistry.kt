package com.impulsive.app.backend.domain.engine.adaptive

import com.impulsive.app.backend.domain.model.adaptive.ImpulsiveDestination
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentPlan
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanActionType

@JvmInline
value class InterventionProtocolId(val value: String) {
    init {
        require(value.matches(Regex("[a-z][a-z0-9_]{2,63}"))) {
            "Protocol ID must be a stable lowercase identifier."
        }
    }
}

@JvmInline
value class InterventionProtocolVersion(val value: Int) {
    init {
        require(value > 0) { "Protocol version must be positive." }
    }
}

enum class InterventionEligibilityCategory {
    FirstMoment,
    RepeatedMoment,
    PersonalPlan,
}

enum class InterventionRouteFamily {
    InAppPause,
    InAppGame,
    InAppReading,
    MomentPlanText,
    ExternalApplication,
    InAppFocus,
    InAppJournal,
    MomentPlanGame,
    MomentPlanReading,
}

enum class InterventionStartRule {
    PauseTimerStarted,
    GenuineGameSessionStarted,
    ReadingSessionStarted,
    PlanActionConfirmed,
    DestinationActivityStarted,
}

enum class InterventionCompletionRule {
    PauseDurationElapsed,
    GenuineGameCompletion,
    ReadingMinimumAndArticleEnd,
    ExplicitManualConfirmation,
}

enum class InterventionDismissalRule {
    ExitAfterPresentationOrStartWithoutCompletion,
}

enum class InterventionNetworkRequirement {
    None,
    ExternalDestinationControlled,
}

enum class InterventionStoredField {
    DecisionId,
    ProtocolIdentity,
    RecommendationPolicyVersion,
    EligibilitySnapshot,
    LifecycleTimestamps,
    FeedbackCode,
    RepeatObservation,
    PlanId,
    PlanContentRevisionId,
}

enum class InterventionProhibitedField {
    ProtectedSourceIdentity,
    ProtectedPackage,
    Url,
    Domain,
    PageTitle,
    SearchText,
    NotificationContent,
    JournalContent,
    AccountEmail,
    AccountUid,
    SelectedApplicationLabel,
}

data class InterventionDataPolicy(
    val permittedStoredFields: Set<InterventionStoredField>,
    val prohibitedStoredFields: Set<InterventionProhibitedField>,
)

data class InterventionAccessibilityPolicy(
    val supportsTalkBack: Boolean,
    val supportsLargeText: Boolean,
    val hasNonAudioPath: Boolean,
    val avoidsColourOnlyState: Boolean,
)

sealed interface InterventionSafeFallback {
    data class Protocol(
        val protocolId: InterventionProtocolId,
        val version: InterventionProtocolVersion,
    ) : InterventionSafeFallback

    data object PreserveProtectionAndOfferGenericSupport :
        InterventionSafeFallback
}

data class InterventionProtocolContract(
    val protocolId: InterventionProtocolId,
    val version: InterventionProtocolVersion,
    val family: InterventionFamily,
    val consumerDisplayName: String,
    val eligibilityCategory: InterventionEligibilityCategory,
    val routeFamily: InterventionRouteFamily,
    val startRule: InterventionStartRule,
    val completionRule: InterventionCompletionRule,
    val dismissalRule: InterventionDismissalRule,
    val supportsFeedback: Boolean,
    val supportsRepeatObservation: Boolean,
    val requiresAppLock: Boolean,
    val networkRequirement: InterventionNetworkRequirement,
    val manualCompletionAvailable: Boolean,
    val dataPolicy: InterventionDataPolicy,
    val accessibilityPolicy: InterventionAccessibilityPolicy,
    val safeFallback: InterventionSafeFallback,
)

data class HistoricalInterventionProtocol(
    val protocolId: InterventionProtocolId,
    val recordedVersion: InterventionProtocolVersion,
    val family: InterventionFamily,
    val consumerDisplayName: String,
    val executableContract: InterventionProtocolContract?,
)

data class InterventionProtocolValidationIssue(
    val protocolId: String,
    val message: String,
)

object InterventionProtocolValidator {
    fun validate(
        contracts: List<InterventionProtocolContract>,
    ): List<InterventionProtocolValidationIssue> = buildList {
        contracts
            .groupingBy { it.protocolId to it.version }
            .eachCount()
            .filterValues { it != 1 }
            .keys
            .forEach { (id, _) ->
                add(issue(id, "Protocol ID and version pair must be unique."))
            }

        contracts.forEach { contract ->
            val id = contract.protocolId
            if (contract.consumerDisplayName.isBlank()) {
                add(issue(id, "Consumer display name is required."))
            }
            if (
                contract.completionRule ==
                InterventionCompletionRule.ExplicitManualConfirmation &&
                !contract.manualCompletionAvailable
            ) {
                add(issue(id, "Manual completion rule requires manual confirmation."))
            }
            if (
                contract.dataPolicy.prohibitedStoredFields
                    .containsAll(RequiredProhibitedFields)
                    .not()
            ) {
                add(issue(id, "Private protected-source fields must be prohibited."))
            }
            val accessibility = contract.accessibilityPolicy
            if (
                !accessibility.supportsTalkBack ||
                !accessibility.supportsLargeText ||
                !accessibility.hasNonAudioPath ||
                !accessibility.avoidsColourOnlyState
            ) {
                add(issue(id, "Required accessibility capabilities are missing."))
            }
            if (
                contract.safeFallback is InterventionSafeFallback.Protocol &&
                contract.safeFallback.protocolId == contract.protocolId &&
                contract.safeFallback.version == contract.version
            ) {
                add(issue(id, "A protocol cannot use itself as its only fallback."))
            }
        }
    }

    private fun issue(
        id: InterventionProtocolId,
        message: String,
    ) = InterventionProtocolValidationIssue(id.value, message)

    private val RequiredProhibitedFields = setOf(
        InterventionProhibitedField.ProtectedSourceIdentity,
        InterventionProhibitedField.ProtectedPackage,
        InterventionProhibitedField.Url,
        InterventionProhibitedField.Domain,
        InterventionProhibitedField.JournalContent,
    )
}

object InterventionProtocolRegistry {
    val CurrentVersion = InterventionProtocolVersion(1)

    val contracts: List<InterventionProtocolContract> = listOf(
        contract(
            id = "short_pause",
            family = InterventionFamily.ShortPause,
            displayName = "Short Pause",
            eligibility = InterventionEligibilityCategory.FirstMoment,
            route = InterventionRouteFamily.InAppPause,
            start = InterventionStartRule.PauseTimerStarted,
            completion = InterventionCompletionRule.PauseDurationElapsed,
            appLock = false,
        ),
        contract(
            id = "pivot_game",
            family = InterventionFamily.PivotGame,
            displayName = "Pivot Game",
            eligibility = InterventionEligibilityCategory.RepeatedMoment,
            route = InterventionRouteFamily.InAppGame,
            start = InterventionStartRule.GenuineGameSessionStarted,
            completion = InterventionCompletionRule.GenuineGameCompletion,
            appLock = false,
        ),
        contract(
            id = "reset_reading",
            family = InterventionFamily.PivotReading,
            displayName = "Reset Reading",
            eligibility = InterventionEligibilityCategory.RepeatedMoment,
            route = InterventionRouteFamily.InAppReading,
            start = InterventionStartRule.ReadingSessionStarted,
            completion = InterventionCompletionRule.ReadingMinimumAndArticleEnd,
            appLock = false,
        ),
        planContract(
            id = "moment_plan_text",
            displayName = "Moment Plan",
            route = InterventionRouteFamily.MomentPlanText,
            start = InterventionStartRule.PlanActionConfirmed,
            completion = InterventionCompletionRule.ExplicitManualConfirmation,
            manualCompletion = true,
        ),
        planContract(
            id = "moment_plan_external_app",
            displayName = "Moment Plan",
            route = InterventionRouteFamily.ExternalApplication,
            start = InterventionStartRule.DestinationActivityStarted,
            completion = InterventionCompletionRule.ExplicitManualConfirmation,
            manualCompletion = true,
            network = InterventionNetworkRequirement.ExternalDestinationControlled,
        ),
        planContract(
            id = "moment_plan_focus",
            displayName = "Moment Plan",
            route = InterventionRouteFamily.InAppFocus,
            start = InterventionStartRule.DestinationActivityStarted,
            completion = InterventionCompletionRule.ExplicitManualConfirmation,
            manualCompletion = true,
        ),
        planContract(
            id = "moment_plan_journal",
            displayName = "Moment Plan",
            route = InterventionRouteFamily.InAppJournal,
            start = InterventionStartRule.DestinationActivityStarted,
            completion = InterventionCompletionRule.ExplicitManualConfirmation,
            manualCompletion = true,
        ),
        planContract(
            id = "moment_plan_pivot_game",
            displayName = "Moment Plan",
            route = InterventionRouteFamily.MomentPlanGame,
            start = InterventionStartRule.GenuineGameSessionStarted,
            completion = InterventionCompletionRule.GenuineGameCompletion,
            manualCompletion = false,
        ),
        planContract(
            id = "moment_plan_reset_reading",
            displayName = "Moment Plan",
            route = InterventionRouteFamily.MomentPlanReading,
            start = InterventionStartRule.ReadingSessionStarted,
            completion = InterventionCompletionRule.ReadingMinimumAndArticleEnd,
            manualCompletion = false,
        ),
    ).also { registered ->
        check(InterventionProtocolValidator.validate(registered).isEmpty()) {
            "Built-in intervention protocols must satisfy every invariant."
        }
    }

    fun resolveExecutable(
        protocolId: InterventionProtocolId,
        version: InterventionProtocolVersion,
    ): InterventionProtocolContract? =
        contracts.firstOrNull {
            it.protocolId == protocolId && it.version == version
        }

    fun resolveForFamily(
        family: InterventionFamily,
    ): InterventionProtocolContract? = when (family) {
        InterventionFamily.ShortPause -> resolveCurrent("short_pause")
        InterventionFamily.PivotGame -> resolveCurrent("pivot_game")
        InterventionFamily.PivotReading -> resolveCurrent("reset_reading")
        InterventionFamily.MomentPlan -> null
    }

    fun resolveForPlan(
        plan: MomentPlan,
    ): InterventionProtocolContract? {
        val id = when (plan.actionType) {
            MomentPlanActionType.TextOnly -> "moment_plan_text"
            MomentPlanActionType.LaunchSelectedApp -> "moment_plan_external_app"
            MomentPlanActionType.OpenImpulsiveDestination -> when (
                plan.actionTarget
            ) {
                ImpulsiveDestination.Focus.storageValue -> "moment_plan_focus"
                ImpulsiveDestination.Journal.storageValue -> "moment_plan_journal"
                ImpulsiveDestination.PivotGames.storageValue ->
                    "moment_plan_pivot_game"
                ImpulsiveDestination.ResetReading.storageValue ->
                    "moment_plan_reset_reading"
                else -> return null
            }
        }
        return resolveCurrent(id)
    }

    fun historical(
        protocolId: InterventionProtocolId,
        version: InterventionProtocolVersion,
    ): HistoricalInterventionProtocol? {
        val currentForId = contracts.firstOrNull { it.protocolId == protocolId }
            ?: return null
        return HistoricalInterventionProtocol(
            protocolId = protocolId,
            recordedVersion = version,
            family = currentForId.family,
            consumerDisplayName = currentForId.consumerDisplayName,
            executableContract = resolveExecutable(protocolId, version),
        )
    }

    fun supportsFeedback(family: InterventionFamily): Boolean =
        contracts.any { it.family == family && it.supportsFeedback }

    fun displayName(family: InterventionFamily): String =
        resolveForFamily(family)?.consumerDisplayName
            ?: if (family == InterventionFamily.MomentPlan) {
                "Moment Plan"
            } else {
                "Personal support"
            }

    private fun resolveCurrent(id: String): InterventionProtocolContract? =
        resolveExecutable(InterventionProtocolId(id), CurrentVersion)

    private fun contract(
        id: String,
        family: InterventionFamily,
        displayName: String,
        eligibility: InterventionEligibilityCategory,
        route: InterventionRouteFamily,
        start: InterventionStartRule,
        completion: InterventionCompletionRule,
        appLock: Boolean,
    ) = InterventionProtocolContract(
        protocolId = InterventionProtocolId(id),
        version = CurrentVersion,
        family = family,
        consumerDisplayName = displayName,
        eligibilityCategory = eligibility,
        routeFamily = route,
        startRule = start,
        completionRule = completion,
        dismissalRule =
            InterventionDismissalRule.ExitAfterPresentationOrStartWithoutCompletion,
        supportsFeedback = true,
        supportsRepeatObservation = true,
        requiresAppLock = appLock,
        networkRequirement = InterventionNetworkRequirement.None,
        manualCompletionAvailable =
            completion == InterventionCompletionRule.ExplicitManualConfirmation,
        dataPolicy = defaultDataPolicy(),
        accessibilityPolicy = requiredAccessibility(),
        safeFallback = if (id == "short_pause") {
            InterventionSafeFallback.PreserveProtectionAndOfferGenericSupport
        } else {
            InterventionSafeFallback.Protocol(
                InterventionProtocolId("short_pause"),
                CurrentVersion,
            )
        },
    )

    private fun planContract(
        id: String,
        displayName: String,
        route: InterventionRouteFamily,
        start: InterventionStartRule,
        completion: InterventionCompletionRule,
        manualCompletion: Boolean,
        network: InterventionNetworkRequirement =
            InterventionNetworkRequirement.None,
    ) = InterventionProtocolContract(
        protocolId = InterventionProtocolId(id),
        version = CurrentVersion,
        family = InterventionFamily.MomentPlan,
        consumerDisplayName = displayName,
        eligibilityCategory = InterventionEligibilityCategory.PersonalPlan,
        routeFamily = route,
        startRule = start,
        completionRule = completion,
        dismissalRule =
            InterventionDismissalRule.ExitAfterPresentationOrStartWithoutCompletion,
        supportsFeedback = true,
        supportsRepeatObservation = true,
        requiresAppLock = true,
        networkRequirement = network,
        manualCompletionAvailable = manualCompletion,
        dataPolicy = defaultDataPolicy(),
        accessibilityPolicy = requiredAccessibility(),
        safeFallback = InterventionSafeFallback.Protocol(
            InterventionProtocolId("short_pause"),
            CurrentVersion,
        ),
    )

    private fun defaultDataPolicy() = InterventionDataPolicy(
        permittedStoredFields = InterventionStoredField.entries.toSet(),
        prohibitedStoredFields = InterventionProhibitedField.entries.toSet(),
    )

    private fun requiredAccessibility() = InterventionAccessibilityPolicy(
        supportsTalkBack = true,
        supportsLargeText = true,
        hasNonAudioPath = true,
        avoidsColourOnlyState = true,
    )
}
