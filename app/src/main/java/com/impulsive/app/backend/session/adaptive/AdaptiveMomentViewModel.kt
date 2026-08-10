package com.impulsive.app.backend.session.adaptive

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveDecision
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveReasonCode
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentCue
import com.impulsive.app.backend.domain.model.adaptive.MomentIntensity
import com.impulsive.app.backend.domain.model.adaptive.MomentPlan
import com.impulsive.app.backend.session.progress.SafeExitRecordingCoordinator
import com.impulsive.app.backend.data.local.device.InstalledAppScanner
import com.impulsive.app.backend.data.repository.ProtectionSetupRepository
import com.impulsive.app.backend.domain.engine.adaptive.MomentPlanActionSafetyContext
import com.impulsive.app.backend.domain.engine.adaptive.MomentPlanActionSafetyPolicy
import com.impulsive.app.backend.domain.engine.adaptive.MomentPlanActionSafetyResult
import com.impulsive.app.backend.session.progress.SafeExitRecordingResult
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class AdaptiveMomentUiMode {
    Loading,
    FirstAttemptPause,
    RepeatedChoice,
    PauseRunning,
    MomentPlan,
    UnavailablePlan,
    SafeFallback,
    GenericFailure,
}

enum class OptionalPromptUiState {
    Unanswered,
    Selected,
    Skipped,
}

data class AdaptiveMomentUiState(
    val mode: AdaptiveMomentUiMode = AdaptiveMomentUiMode.Loading,
    val decision: AdaptiveDecision? = null,
    val plans: List<MomentPlan> = emptyList(),
    val selectedCue: MomentCue? = null,
    val urgeRating: Int? = null,
    val cuePromptState: OptionalPromptUiState = OptionalPromptUiState.Unanswered,
    val urgePromptState: OptionalPromptUiState = OptionalPromptUiState.Unanswered,
    val explanationVisible: Boolean = false,
    val savingChoice: Boolean = false,
    val savingOutcome: Boolean = false,
    val routing: Boolean = false,
    val message: String? = null,
    val routeRequest: AdaptiveRouteRequest? = null,
    val momentPlanSafeExitRequestStatus:
        MomentPlanSafeExitRequestStatus =
        MomentPlanSafeExitRequestStatus.Idle,
    val familiarStepState: FamiliarStepSessionState =
        FamiliarStepSessionState.Unavailable(
            com.impulsive.app.backend.domain.model.adaptive.FamiliarStepNoMatchReason.FirstAttempt,
        ),
) {
    val assignedIntervention: InterventionFamily?
        get() = decision?.assignment?.assignedSuggestion

    val selectedPlan: MomentPlan?
        get() {
            val assignedId = decision?.assignment?.momentPlanId
            return assignedId?.let { id ->
                plans.firstOrNull { it.planId == id && it.enabled }
            } ?: plans.firstOrNull { it.enabled }
        }

    val whyThisText: String
        get() = AdaptiveWhyThisCopy.forReason(
            decision?.assignment?.reasonCode ?: AdaptiveReasonCode.StableFallback,
        )
}

class AdaptiveMomentViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val decisionId = savedStateHandle.get<String>("decisionId").orEmpty()
    private val triggeringPackageName = savedStateHandle
        .get<String>("triggeringPackageName")
        ?.takeIf(String::isNotBlank)
    private val decisions = AdaptivePhase4Dependencies.decisions(application)
    private val plans = AdaptivePhase4Dependencies.momentPlans(application)
    private val chooserRefresh = AdaptiveChooserRefresh(decisions, plans)
    private val lifecycle = AdaptivePhase4Dependencies.lifecycle(application)
    private val followUpSupport = AdaptivePhase4Dependencies.followUpSupport(application)
    private val outcomeCoordinator =
        AdaptivePhase4Dependencies.outcomeCoordinator(application)
    private val familiarSteps = AdaptivePhase4Dependencies.familiarSteps(application)
    private val familiarStepControls =
        AdaptivePhase4Dependencies.familiarStepControls(application)
    private val protectionSetup = ProtectionSetupRepository(application)
    private val installedAppScanner = InstalledAppScanner(application)
    private val momentPlanSafeExitRecorder =
        MomentPlanSafeExitRecorder(
            SafeExitRecordingCoordinator(
                application,
            ),
            ZoneId.systemDefault(),
        )
    private val momentPlanSafeExitReconciliationScheduler =
        WorkManagerMomentPlanSafeExitReconciliationScheduler(
            application,
        )
    private val promptStateStore = AdaptiveOptionalPromptStateStore(savedStateHandle)
    private val choiceOperationGuard = AdaptiveChoiceOperationGuard()
    private val refreshInFlight = AtomicBoolean(false)
    private val _state = MutableStateFlow(AdaptiveMomentUiState())
    val state: StateFlow<AdaptiveMomentUiState> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload(momentPlanDelivery: Boolean = false) {
        viewModelScope.launch {
            try {
                if (decisionId.isBlank()) {
                    _state.value = AdaptiveMomentUiState(
                        mode = AdaptiveMomentUiMode.SafeFallback,
                    )
                    return@launch
                }
                val loaded = chooserRefresh.load(decisionId)
                if (loaded == null) {
                    _state.value = AdaptiveMomentUiState(
                        mode = AdaptiveMomentUiMode.SafeFallback,
                    )
                    return@launch
                }
                val decision = loaded.decision
                val enabledPlans = loaded.availablePlans
                val familiarStepState = if (
                    decision.assignment.momentIntensity == MomentIntensity.FirstAttempt
                ) {
                    FamiliarStepSessionState.Unavailable(
                        com.impulsive.app.backend.domain.model.adaptive
                            .FamiliarStepNoMatchReason.FirstAttempt,
                    )
                } else {
                    familiarSteps.state(decision.decisionId)
                }
                val mode = if (momentPlanDelivery) {
                    val selectedId = decision.assignment.momentPlanId
                    if (
                        selectedId == null ||
                        enabledPlans.none { it.planId == selectedId && it.enabled }
                    ) {
                        AdaptiveMomentUiMode.UnavailablePlan
                    } else {
                        AdaptiveMomentUiMode.MomentPlan
                    }
                } else if (decision.assignment.momentIntensity == MomentIntensity.FirstAttempt) {
                    if (decision.startedAtMillis != null &&
                        decision.assignment.actualIntervention == InterventionFamily.ShortPause
                    ) {
                        AdaptiveMomentUiMode.PauseRunning
                    } else {
                        AdaptiveMomentUiMode.FirstAttemptPause
                    }
                } else {
                    AdaptiveMomentUiMode.RepeatedChoice
                }
                _state.value = AdaptiveMomentUiState(
                    mode = mode,
                    decision = decision,
                    plans = enabledPlans,
                    selectedCue = promptStateStore.selectedCue(decision.momentCue),
                    urgeRating = promptStateStore.urgeRating(decision.baselineUrgeRating),
                    cuePromptState = promptStateStore.cuePromptState(decision.momentCue),
                    urgePromptState = promptStateStore.urgePromptState(
                        decision.baselineUrgeRating,
                    ),
                    familiarStepState = familiarStepState,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                _state.value = AdaptiveMomentUiState(
                    mode = AdaptiveMomentUiMode.SafeFallback,
                )
            }
        }
    }

    fun onPresented() {
        val decision = _state.value.decision ?: return
        if (decision.presentedAtMillis != null) return
        viewModelScope.launch {
            lifecycle.markPresented(decision.decisionId, System.currentTimeMillis())
            refreshDecision()
        }
    }

    fun showOtherOptions() {
        _state.update {
            it.copy(
                mode = AdaptiveMomentUiMode.RepeatedChoice,
                explanationVisible = false,
            )
        }
    }

    fun selectCue(cue: MomentCue) {
        promptStateStore.selectCue(cue)
        _state.update {
            it.copy(
                selectedCue = cue,
                cuePromptState = OptionalPromptUiState.Selected,
            )
        }
    }

    fun skipCue() {
        promptStateStore.skipCue()
        _state.update {
            it.copy(
                selectedCue = null,
                cuePromptState = OptionalPromptUiState.Skipped,
            )
        }
    }

    fun reopenCue() {
        promptStateStore.reopenCue()
        _state.update {
            it.copy(
                selectedCue = null,
                cuePromptState = OptionalPromptUiState.Unanswered,
            )
        }
    }

    fun selectUrge(rating: Int) {
        if (!promptStateStore.selectUrge(rating)) {
            _state.update {
                it.copy(message = "Choose a number from 0 to 10, or skip.")
            }
            return
        }
        _state.update {
            it.copy(
                urgeRating = rating,
                urgePromptState = OptionalPromptUiState.Selected,
            )
        }
    }

    fun skipUrge() {
        promptStateStore.skipUrge()
        _state.update {
            it.copy(
                urgeRating = null,
                urgePromptState = OptionalPromptUiState.Skipped,
            )
        }
    }

    fun reopenUrge() {
        promptStateStore.reopenUrge()
        _state.update {
            it.copy(
                urgeRating = null,
                urgePromptState = OptionalPromptUiState.Unanswered,
            )
        }
    }

    fun toggleExplanation() {
        _state.update { it.copy(explanationVisible = !it.explanationVisible) }
    }

    fun startFamiliarStep() {
        val available = _state.value.familiarStepState as?
            FamiliarStepSessionState.FamiliarStepAvailable ?: return
        if (!choiceOperationGuard.tryStart()) return
        _state.update { it.copy(savingChoice = true, message = null) }
        viewModelScope.launch {
            try {
                when (
                    val result = familiarSteps.start(decisionId, available.routeIdentity)
                ) {
                    is FamiliarStepStartResult.Ready -> {
                        refreshDecision(
                            if (result.routeRequest == null) {
                                AdaptiveMomentUiMode.PauseRunning
                            } else {
                                null
                            },
                        )
                        _state.update {
                            it.copy(
                                savingChoice = false,
                                routing = result.routeRequest != null,
                                routeRequest = result.routeRequest,
                            )
                        }
                    }
                    is FamiliarStepStartResult.ResumeExistingCycle -> {
                        _state.update {
                            it.copy(
                                savingChoice = false,
                                routing = true,
                                routeRequest = result.routeRequest,
                                message = null,
                            )
                        }
                    }
                    is FamiliarStepStartResult.Unavailable -> _state.update {
                        it.copy(
                            savingChoice = false,
                            familiarStepState = FamiliarStepSessionState.Unavailable(
                                result.reason,
                            ),
                            message = "That familiar step is no longer available.",
                        )
                    }
                    is FamiliarStepStartResult.LifecycleRejected,
                    FamiliarStepStartResult.SupportCycleUnavailable -> _state.update {
                        it.copy(
                            savingChoice = false,
                            message = "That support option could not be started. Please try again.",
                        )
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                _state.update {
                    it.copy(
                        savingChoice = false,
                        message = "That support option could not be started. Please try again.",
                    )
                }
            } finally {
                choiceOperationGuard.clear()
            }
        }
    }

    fun chooseAnotherSupportFromFamiliarStep() = showOtherOptions()

    fun leaveFamiliarStepMoment() = dismissCurrentIntervention()

    fun clearFamiliarStepHistory() {
        viewModelScope.launch {
            if (familiarStepControls.clearAdaptiveHistory() == AdaptiveLifecycleResult.Applied) {
                _state.update {
                    it.copy(
                        familiarStepState = FamiliarStepSessionState.Unavailable(
                            com.impulsive.app.backend.domain.model.adaptive
                                .FamiliarStepNoMatchReason.InsufficientEvidence,
                        ),
                    )
                }
            }
        }
    }

    fun setPersonalSuggestionsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            familiarStepControls.setPersonalSuggestionsEnabled(enabled)
            val nextState = if (!enabled) {
                FamiliarStepSessionState.Unavailable(
                    com.impulsive.app.backend.domain.model.adaptive
                        .FamiliarStepNoMatchReason.PersonalSuggestionsDisabled,
                )
            } else {
                familiarSteps.state(decisionId)
            }
            _state.update { it.copy(familiarStepState = nextState) }
        }
    }

    fun refreshAfterReturn() {
        if (!refreshInFlight.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                val loaded = chooserRefresh.load(decisionId)
                    ?: run {
                        _state.update {
                            it.copy(
                                savingChoice = false,
                                routing = false,
                                routeRequest = null,
                            )
                        }
                        choiceOperationGuard.clear()
                        return@launch
                    }
                _state.update {
                    it.copy(
                        decision = loaded.decision,
                        plans = loaded.availablePlans,
                        savingChoice = false,
                        routing = false,
                        routeRequest = null,
                        message = null,
                    )
                }
                choiceOperationGuard.clear()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                _state.update {
                    it.copy(
                        savingChoice = false,
                        routing = false,
                        routeRequest = null,
                    )
                }
                choiceOperationGuard.clear()
            } finally {
                refreshInFlight.set(false)
            }
        }
    }

    fun choose(intervention: InterventionFamily) {
        val snapshot = _state.value
        val cachedDecision = snapshot.decision ?: return
        if (snapshot.savingChoice || snapshot.routing) return
        if (intervention !in cachedDecision.assignment.eligibleInterventions) return
        val planId = if (intervention == InterventionFamily.MomentPlan) {
            snapshot.selectedPlan?.planId ?: run {
                _state.update {
                    it.copy(
                        mode = AdaptiveMomentUiMode.UnavailablePlan,
                        message = "That Moment Plan is unavailable. Choose another option.",
                    )
                }
                return
            }
        } else {
            null
        }
        if (!choiceOperationGuard.tryStart()) return
        _state.update { it.copy(savingChoice = true, message = null) }
        viewModelScope.launch {
            try {
                val decision = decisions.getById(cachedDecision.decisionId)
                    ?: run {
                        _state.update {
                            it.copy(
                                savingChoice = false,
                                message = "That choice could not be saved. Please try again.",
                            )
                        }
                        return@launch
                    }
                if (intervention !in decision.assignment.eligibleInterventions) {
                    _state.update {
                        it.copy(
                            savingChoice = false,
                            message = "That support option is no longer available.",
                        )
                    }
                    return@launch
                }
                _state.update { it.copy(decision = decision) }
                val existingChoice = decision.assignment.actualIntervention
                val sameExistingChoice =
                    existingChoice == intervention &&
                        (
                            intervention != InterventionFamily.MomentPlan ||
                                decision.assignment.momentPlanId == planId
                            )
                if (
                    decision.startedAtMillis != null &&
                    existingChoice != null &&
                    !sameExistingChoice
                ) {
                    when (
                        val followUp = followUpSupport.chooseAnother(
                            AdaptiveFollowUpRequest(
                                previousDecisionId = decision.decisionId,
                                intervention = intervention,
                                momentPlanId = planId,
                                selectedCue = snapshot.selectedCue.takeIf {
                                    snapshot.cuePromptState ==
                                        OptionalPromptUiState.Selected
                                },
                                urgeRating = snapshot.urgeRating.takeIf {
                                    snapshot.urgePromptState ==
                                        OptionalPromptUiState.Selected
                                },
                            ),
                        )
                    ) {
                        is AdaptiveFollowUpResult.Ready -> {
                            _state.update {
                                it.copy(
                                    savingChoice = false,
                                    routing = followUp.routeRequest != null,
                                    routeRequest = followUp.routeRequest,
                                    message = null,
                                )
                            }
                        }
                        AdaptiveFollowUpResult.InvalidMomentPlan -> {
                            _state.update {
                                it.copy(
                                    savingChoice = false,
                                    mode = AdaptiveMomentUiMode.UnavailablePlan,
                                    message =
                                        "That Moment Plan is unavailable. Choose another option.",
                                )
                            }
                        }
                        else -> {
                            _state.update {
                                it.copy(
                                    savingChoice = false,
                                    message =
                                        "That choice could not be saved. Please try again.",
                                )
                            }
                        }
                    }
                    return@launch
                }
                if (decision.presentedAtMillis == null) {
                    val presented = lifecycle.markPresented(
                        decision.decisionId,
                        System.currentTimeMillis(),
                    )
                    if (
                        presented != AdaptiveLifecycleResult.Applied &&
                        presented != AdaptiveLifecycleResult.Idempotent &&
                        presented != AdaptiveLifecycleResult.SchedulingFailure
                    ) {
                        val latest = decisions.getById(decision.decisionId)
                        if (latest?.presentedAtMillis == null) {
                            _state.update {
                                it.copy(
                                    savingChoice = false,
                                    message = "That choice could not be saved. Please try again.",
                                )
                            }
                            return@launch
                        }
                    }
                }
                if (
                    snapshot.cuePromptState == OptionalPromptUiState.Selected ||
                    snapshot.urgePromptState == OptionalPromptUiState.Selected
                ) {
                    decisions.recordMomentContextOnce(
                        decisionId = decision.decisionId,
                        cue = snapshot.selectedCue.takeIf {
                            snapshot.cuePromptState == OptionalPromptUiState.Selected
                        },
                        urgeRating = snapshot.urgeRating.takeIf {
                            snapshot.urgePromptState == OptionalPromptUiState.Selected
                        },
                    )
                }
                val choiceResult = when {
                    existingChoice == null -> lifecycle.recordActualChoice(
                        decisionId = decision.decisionId,
                        intervention = intervention,
                        momentPlanId = planId,
                    )
                    decision.startedAtMillis == null ->
                        lifecycle.replacePendingActualChoice(
                            decisionId = decision.decisionId,
                            intervention = intervention,
                            momentPlanId = planId,
                        )
                    sameExistingChoice -> AdaptiveLifecycleResult.Idempotent
                    else -> AdaptiveLifecycleResult.ConflictingChoice
                }
                when (choiceResult) {
                    AdaptiveLifecycleResult.Applied,
                    AdaptiveLifecycleResult.Idempotent -> Unit
                    else -> {
                        _state.update {
                            it.copy(
                                savingChoice = false,
                                message = "That choice could not be saved. Please try again.",
                            )
                        }
                        return@launch
                    }
                }
                if (intervention == InterventionFamily.ShortPause) {
                    val started = lifecycle.markStarted(
                        decision.decisionId,
                        System.currentTimeMillis(),
                    )
                    if (
                        started != AdaptiveLifecycleResult.Applied &&
                        started != AdaptiveLifecycleResult.Idempotent
                    ) {
                        _state.update {
                            it.copy(
                                savingChoice = false,
                                message = "The pause could not be started. Please try again.",
                            )
                        }
                        return@launch
                    }
                    refreshDecision(AdaptiveMomentUiMode.PauseRunning)
                    return@launch
                }
                val route = AdaptiveMomentRoutingPolicy.forChoice(
                    decision.decisionId,
                    intervention,
                )
                _state.update {
                    it.copy(
                        savingChoice = false,
                        routing = route != null,
                        routeRequest = route,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                _state.update {
                    it.copy(
                        savingChoice = false,
                        routing = false,
                        message = "That choice could not be saved. Please try again.",
                    )
                }
            } finally {
                choiceOperationGuard.clear()
            }
        }
    }

    fun startMomentPlan() {
        val snapshot = _state.value
        val decision = snapshot.decision ?: return
        val plan = snapshot.selectedPlan ?: run {
            _state.update {
                it.copy(
                    mode = AdaptiveMomentUiMode.UnavailablePlan,
                    message = "That Moment Plan is unavailable. Choose another option.",
                )
            }
            return
        }
        if (snapshot.routing) return
        viewModelScope.launch {
            val protection = protectionSetup.state.first()
            val expectedRevision = decision.assignment.actualPlanContentRevisionId
                ?: decision.assignment.assignedPlanContentRevisionId
                ?: plan.contentRevisionId
            val safety = MomentPlanActionSafetyPolicy.evaluate(
                plan,
                MomentPlanActionSafetyContext(
                    expectedContentRevisionId = expectedRevision,
                    availablePackageNames = installedAppScanner.getLaunchableAppCandidates()
                        .mapTo(mutableSetOf()) { it.packageName },
                    protectedPackageNames = protection.selectedBlockedAppPackageNames +
                        protection.websiteProtectedAppPackageNames,
                    triggeringPackageName = triggeringPackageName,
                ),
            )
            if (safety !is MomentPlanActionSafetyResult.Available) {
                _state.update {
                    it.copy(message = "That plan action is unavailable. Choose another option.")
                }
                return@launch
            }
            val route = AdaptiveMomentRoutingPolicy.forPlanAction(decision.decisionId, plan)
            if (route == null) {
                _state.update {
                    it.copy(message = "That plan action is unavailable. Choose another option.")
                }
                return@launch
            }
            _state.update { it.copy(routing = true, routeRequest = route) }
        }
    }

    fun completeCurrentIntervention() {
        finishCurrentIntervention(completed = true)
    }

    fun requestCompletedMomentPlanWalkAway() {
        val decision =
            _state.value.decision
                ?: return

        if (
            _state.value
                .momentPlanSafeExitRequestStatus ==
                MomentPlanSafeExitRequestStatus
                    .Recording ||
            _state.value
                .momentPlanSafeExitRequestStatus ==
                MomentPlanSafeExitRequestStatus
                    .Durable
        ) {
            return
        }

        val candidate =
            MomentPlanSafeExitCandidateFactory
                .createOrNull(
                    decision =
                        decision,
                    zoneId =
                        ZoneId.systemDefault(),
                )

        if (
            candidate == null
        ) {
            _state.update {
                it.copy(
                    momentPlanSafeExitRequestStatus =
                        MomentPlanSafeExitRequestStatus
                            .Failed,
                )
            }

            return
        }

        /*
         * Persist the durable request synchronously before entering
         * viewModelScope. The later UI may navigate away immediately after
         * invoking this method.
         */
        val enqueueReceipt =
            momentPlanSafeExitReconciliationScheduler
                .request(
                    decision.decisionId,
                )

        _state.update {
            it.copy(
                momentPlanSafeExitRequestStatus =
                    MomentPlanSafeExitRequestStatus
                        .Recording,
            )
        }

        viewModelScope.launch {
            try {
                val latestDecision =
                    decisions.getById(
                        decision.decisionId,
                    )

                if (
                    latestDecision == null
                ) {
                    _state.update {
                        it.copy(
                            momentPlanSafeExitRequestStatus =
                                MomentPlanSafeExitRequestStatus
                                    .Failed,
                        )
                    }

                    return@launch
                }

                val enqueueAccepted =
                    enqueueReceipt
                        ?.awaitAccepted()
                        ?: false

                val immediateResult =
                    momentPlanSafeExitRecorder
                        .recordExplicitWalkAway(
                            latestDecision,
                        )

                val finalStatus =
                    when (immediateResult) {
                        is SafeExitRecordingResult.Recorded,
                        is SafeExitRecordingResult.Duplicate -> MomentPlanSafeExitRequestStatus.Durable

                        is SafeExitRecordingResult.Rejected,
                        null ->
                            MomentPlanSafeExitRequestStatus.Failed

                        SafeExitRecordingResult.RetryableFailure ->
                            if (
                                enqueueAccepted
                            ) {
                                MomentPlanSafeExitRequestStatus.Durable
                            } else {
                                MomentPlanSafeExitRequestStatus.Failed
                            }
                    }

                _state.update {
                    it.copy(
                        momentPlanSafeExitRequestStatus =
                            finalStatus,
                    )
                }
            } catch (
                cancellation:
                    CancellationException,
            ) {
                throw cancellation
            } catch (
                _: Exception,
            ) {
                _state.update {
                    it.copy(
                        momentPlanSafeExitRequestStatus =
                            MomentPlanSafeExitRequestStatus
                                .Failed,
                    )
                }
            }
        }
    }
    fun dismissCurrentIntervention() {
        finishCurrentIntervention(completed = false)
    }

    private fun finishCurrentIntervention(completed: Boolean) {
        val decision = _state.value.decision ?: return
        if (_state.value.savingOutcome) return
        _state.update { it.copy(savingOutcome = true) }
        viewModelScope.launch {
            try {
                val result = if (completed) {
                    outcomeCoordinator.complete(decision.decisionId)
                } else {
                    outcomeCoordinator.dismiss(decision.decisionId)
                }
                if (
                    result == AdaptiveOutcomeResult.Applied ||
                    result == AdaptiveOutcomeResult.Idempotent
                ) {
                    refreshDecision()
                    _state.update {
                        it.copy(
                            savingOutcome = false,
                            routing = true,
                            routeRequest = AdaptiveRouteRequest(
                                decisionId = decision.decisionId,
                                kind = AdaptiveRouteKind.Feedback,
                            ),
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            savingOutcome = false,
                            message = "That update could not be saved. Please try again.",
                        )
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                _state.update {
                    it.copy(
                        savingOutcome = false,
                        message = "That update could not be saved. Please try again.",
                    )
                }
            }
        }
    }

    fun markStartedAfterSuccessfulEntry() {
        val decision = _state.value.decision ?: return
        viewModelScope.launch {
            val latest = decisions.getById(decision.decisionId)
            if (latest?.startedAtMillis != null) {
                refreshDecision()
                return@launch
            }
            val result = lifecycle.markStarted(
                decision.decisionId,
                System.currentTimeMillis(),
            )
            if (
                result == AdaptiveLifecycleResult.Applied ||
                result == AdaptiveLifecycleResult.Idempotent
            ) {
                refreshDecision()
            } else {
                _state.update {
                    it.copy(
                        routing = false,
                        routeRequest = null,
                        message = "That support option could not be opened. Please try again.",
                    )
                }
            }
        }
    }

    fun routeFailed() {
        _state.update {
            it.copy(
                routing = false,
                routeRequest = null,
                message = "That support option could not be opened. Please try again.",
            )
        }
    }

    fun consumeRoute() {
        _state.update { it.copy(routing = false, routeRequest = null) }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    private suspend fun refreshDecision(mode: AdaptiveMomentUiMode? = null) {
        val latest = decisions.getById(decisionId)
        _state.update {
            it.copy(
                mode = mode ?: it.mode,
                decision = latest ?: it.decision,
                savingChoice = false,
            )
        }
    }

}
