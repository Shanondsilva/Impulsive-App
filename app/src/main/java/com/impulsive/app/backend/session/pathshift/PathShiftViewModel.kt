package com.impulsive.app.backend.session.pathshift

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.impulsive.app.backend.data.local.database.AppDatabase
import com.impulsive.app.backend.data.repository.adaptive.RoomAdaptiveDecisionRepository
import com.impulsive.app.backend.data.repository.adaptive.RoomMomentPlanRepository
import com.impulsive.app.backend.data.repository.pathshift.RoomPathShiftCycleRepository
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSourceKind
import com.impulsive.app.backend.domain.model.adaptive.MomentPlan
import com.impulsive.app.backend.domain.pathshift.PathShiftCycle
import com.impulsive.app.backend.domain.pathshift.PathShiftForecastInput
import com.impulsive.app.backend.domain.pathshift.PathShiftForecastPolicy
import com.impulsive.app.backend.domain.pathshift.PathShiftForecastResult
import com.impulsive.app.backend.domain.pathshift.PathShiftProtectedMoment
import com.impulsive.app.frontend.pathshift.PathShiftExperienceState
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PathShiftUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val experience: PathShiftExperienceState = PathShiftExperienceState.Unavailable,
    val preview: PathShiftForecastResult.Available? = null,
    val cycle: PathShiftCycle? = null,
    val enabledPlans: List<MomentPlan> = emptyList(),
    val preparedPlan: MomentPlan? = null,
    val preparedPlanRevisionMismatch: Boolean = false,
    val message: String? = null,
) {
    val homeTitle: String
        get() = if (experience == PathShiftExperienceState.AwaitingReview) {
            "PATH REVIEW READY"
        } else {
            "YOUR CURRENT PATH"
        }

    val homeSummary: String
        get() = when (experience) {
            PathShiftExperienceState.InsufficientHistory ->
                "Impulsive is still gathering enough private history for a cautious estimate."
            PathShiftExperienceState.ForecastReady ->
                "A private seven-day estimate is ready."
            PathShiftExperienceState.Active -> cycle?.let {
                "${it.estimatedLowerCount} to ${it.estimatedUpperCount} protected moments " +
                    "estimated for this seven-day period."
            } ?: "Your private seven-day estimate is active."
            PathShiftExperienceState.AwaitingReview ->
                "See what was estimated and what was recorded."
            PathShiftExperienceState.FinalisedReview ->
                "Your latest private Path Review is ready."
            PathShiftExperienceState.Unavailable ->
                "Your private estimate is temporarily unavailable."
        }
}

class PathShiftViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val cycles = RoomPathShiftCycleRepository(database.pathShiftCycleDao())
    private val decisions = RoomAdaptiveDecisionRepository(database.adaptiveDecisionDao())
    private val plans = RoomMomentPlanRepository(database.momentPlanDao())
    private val coordinator = PathShiftDependencies.coordinator(application)
    private val policy = PathShiftForecastPolicy()
    private val _state = MutableStateFlow(PathShiftUiState())
    val state: StateFlow<PathShiftUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    fun createCycle() = mutate {
        when (val result = coordinator.createCycle()) {
            is PathShiftCreateResult.Created,
            is PathShiftCreateResult.Existing,
            -> null
            is PathShiftCreateResult.Unavailable -> "Not enough history yet."
            is PathShiftCreateResult.SchedulingFailure ->
                "Your PathShift was saved and will be recovered automatically."
            PathShiftCreateResult.PersistenceFailure ->
                "Your PathShift could not be created. Please try again."
        }
    }

    fun preparePlan(planId: String) = mutate {
        val cycleId = _state.value.cycle?.cycleId
            ?: return@mutate "Create a PathShift before preparing a plan."
        when (coordinator.preparePlan(cycleId, planId)) {
            PathShiftMutationResult.Applied -> "Your plan is ready for this PathShift."
            PathShiftMutationResult.InvalidPlan ->
                "That plan is not currently available for protected moments."
            else -> "That plan could not be prepared. Please try again."
        }
    }

    fun useNewPlanRevision() = mutate {
        val cycleId = _state.value.cycle?.cycleId ?: return@mutate null
        when (coordinator.useNewPlanRevision(cycleId)) {
            PathShiftMutationResult.Applied -> "The new plan version is ready."
            else -> "The new plan version could not be prepared."
        }
    }

    fun removePreparedPlan() = mutate {
        val cycleId = _state.value.cycle?.cycleId ?: return@mutate null
        when (coordinator.removePreparedPlan(cycleId)) {
            PathShiftMutationResult.Applied,
            PathShiftMutationResult.Idempotent,
            -> "This PathShift no longer has a prepared plan."
            else -> "The prepared plan could not be removed."
        }
    }

    fun cancelActive() = mutate {
        when (coordinator.cancelActive()) {
            PathShiftMutationResult.Applied,
            PathShiftMutationResult.Idempotent,
            -> "The current PathShift was stopped. Your Moment history remains."
            else -> "The current PathShift could not be stopped."
        }
    }

    fun reportEstimateUnhelpful() {
        _state.update {
            it.copy(
                message =
                    "Thanks. This stays on this device and is not treated as plan feedback.",
            )
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun mutate(operation: suspend () -> String?) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val message = try {
                operation()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                "That change could not be completed. Please try again."
            }
            load(message)
        }
    }

    private suspend fun load(message: String? = _state.value.message) {
        try {
            val active = cycles.getActive()
            val latestFinalised = cycles.observeLatestFinalised(1).first().firstOrNull()
            val enabledPlans = plans.observeEnabled().first()
            val currentCycle = active ?: latestFinalised
            val prepared = currentCycle?.preparedPlanId?.let { plans.getById(it) }
            val mismatch = currentCycle?.let { cycle ->
                cycle.preparedPlanContentRevisionId != null &&
                    prepared?.contentRevisionId != cycle.preparedPlanContentRevisionId
            } == true
            val now = System.currentTimeMillis()

            if (active != null) {
                _state.value = PathShiftUiState(
                    loading = false,
                    experience = if (now >= active.forecastWindowEndsAtMillis) {
                        PathShiftExperienceState.AwaitingReview
                    } else {
                        PathShiftExperienceState.Active
                    },
                    cycle = active,
                    enabledPlans = enabledPlans,
                    preparedPlan = prepared,
                    preparedPlanRevisionMismatch = mismatch,
                    message = message,
                )
                return
            }
            if (latestFinalised != null) {
                _state.value = PathShiftUiState(
                    loading = false,
                    experience = PathShiftExperienceState.FinalisedReview,
                    cycle = latestFinalised,
                    enabledPlans = enabledPlans,
                    preparedPlan = prepared,
                    preparedPlanRevisionMismatch = mismatch,
                    message = message,
                )
                return
            }

            val history = decisions.getBetween(0L, Long.MAX_VALUE)
            val preview = policy.calculate(
                PathShiftForecastInput(
                    protectedMoments = history.map {
                        PathShiftProtectedMoment(
                            incidentToken = it.protectionIncidentToken,
                            occurredAtMillis = it.createdAtMillis,
                            sourceKind = if (
                                it.sourceKind == AdaptiveSourceKind.ExplicitUserSupport
                            ) {
                                AdaptiveSourceKind.ExplicitUserSupport
                            } else {
                                it.sourceKind
                            },
                        )
                    },
                    generatedAtMillis = now,
                    zoneId = ZoneId.systemDefault(),
                ),
            )
            _state.value = PathShiftUiState(
                loading = false,
                experience = if (preview is PathShiftForecastResult.Available) {
                    PathShiftExperienceState.ForecastReady
                } else {
                    PathShiftExperienceState.InsufficientHistory
                },
                preview = preview as? PathShiftForecastResult.Available,
                enabledPlans = enabledPlans,
                message = message,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            _state.value = PathShiftUiState(
                loading = false,
                experience = PathShiftExperienceState.Unavailable,
                message = message ?: "PathShift could not be loaded. Please try again.",
            )
        }
    }
}
