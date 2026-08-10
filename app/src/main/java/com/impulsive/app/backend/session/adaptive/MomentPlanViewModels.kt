package com.impulsive.app.backend.session.adaptive

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.impulsive.app.backend.data.local.database.AppDatabase
import com.impulsive.app.backend.data.local.device.InstalledAppScanner
import com.impulsive.app.backend.data.repository.adaptive.RoomAdaptivePreferenceRepository
import com.impulsive.app.backend.data.repository.adaptive.RoomMomentPlanRepository
import com.impulsive.app.backend.domain.engine.adaptive.MomentPlanContentRevisionPolicy
import com.impulsive.app.backend.domain.engine.adaptive.RandomMomentPlanContentRevisionIdSource
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveMomentLimits
import com.impulsive.app.backend.domain.model.adaptive.AdaptivePreferences
import com.impulsive.app.backend.domain.model.adaptive.ImpulsiveDestination
import com.impulsive.app.backend.domain.model.adaptive.MomentCue
import com.impulsive.app.backend.domain.model.adaptive.MomentPlan
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanActionType
import com.impulsive.app.backend.domain.repository.adaptive.AdaptivePreferenceRepository
import com.impulsive.app.backend.domain.repository.adaptive.MomentPlanRepository
import com.impulsive.app.backend.domain.repository.adaptive.MomentPlanSaveResult
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MomentPlanListUiState(
    val loading: Boolean = true,
    val plans: List<MomentPlan> = emptyList(),
    val message: String? = null,
    val deletingPlanId: String? = null,
    val practisedPlan: MomentPlan? = null,
) {
    val isEmpty: Boolean get() = !loading && plans.isEmpty()
}

data class MomentPlanHomeUiState(
    val loading: Boolean = true,
    val activePlan: MomentPlan? = null,
)

data class LaunchableAppUiModel(
    val packageName: String,
    val label: String,
)

data class MomentPlanEditorUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val planId: String = "",
    val editing: Boolean = false,
    val step: Int = 1,
    val title: String = "",
    val futureCueText: String = "",
    val momentCue: MomentCue? = null,
    val actionType: MomentPlanActionType = MomentPlanActionType.TextOnly,
    val actionText: String = "",
    val actionTarget: String? = null,
    val selectedAppLabel: String? = null,
    val enabled: Boolean = true,
    val saving: Boolean = false,
    val savedPlanId: String? = null,
    val validationMessage: String? = null,
    val practicePreviewVisible: Boolean = false,
    val practiceComplete: Boolean = false,
    val apps: List<LaunchableAppUiModel> = emptyList(),
)

data class MomentPlanDetailUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val plan: MomentPlan? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val practicePreviewVisible: Boolean = false,
    val practiceComplete: Boolean = false,
    val deleted: Boolean = false,
)

data class AdaptivePreferencesUiState(
    val loading: Boolean = true,
    val preferences: AdaptivePreferences = AdaptivePreferences(),
    val saving: Boolean = false,
    val message: String? = null,
)

private fun Application.momentPlanRepository(): MomentPlanRepository {
    val database = AppDatabase.getInstance(this)
    return RoomMomentPlanRepository(database.momentPlanDao())
}

private fun Application.adaptivePreferenceRepository(): AdaptivePreferenceRepository {
    val database = AppDatabase.getInstance(this)
    return RoomAdaptivePreferenceRepository(database.adaptivePreferenceDao())
}

class MomentPlanHomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = application.momentPlanRepository()
    private val _state = MutableStateFlow(MomentPlanHomeUiState())
    val state: StateFlow<MomentPlanHomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeEnabled()
                .catch {
                    logSafe("load home plan", it)
                    _state.value = MomentPlanHomeUiState(loading = false)
                }
                .collect { plans ->
                    _state.value = MomentPlanHomeUiState(
                        loading = false,
                        activePlan = MomentPlanPresentation.sorted(plans).firstOrNull(),
                    )
                }
        }
    }
}

class MomentPlanListViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = application.momentPlanRepository()
    private val _state = MutableStateFlow(MomentPlanListUiState())
    val state: StateFlow<MomentPlanListUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeAll()
                .catch {
                    logSafe("load plan list", it)
                    _state.value = MomentPlanListUiState(
                        loading = false,
                        message = "Moment Plans could not be loaded. Please try again.",
                    )
                }
                .collect { plans ->
                    _state.update {
                        it.copy(
                            loading = false,
                            plans = MomentPlanPresentation.sorted(plans),
                        )
                    }
                }
        }
    }

    fun setEnabled(plan: MomentPlan, enabled: Boolean) = runPlanOperation("change plan state") {
        val result = repository.update(
            plan.copy(
                enabled = enabled,
                preferredForCue = plan.preferredForCue && enabled,
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
        showResult(result)
    }

    fun makePreferred(plan: MomentPlan) = runPlanOperation("make plan preferred") {
        showResult(repository.setPreferred(plan.planId, System.currentTimeMillis()))
    }

    fun requestDelete(planId: String) {
        _state.update { it.copy(deletingPlanId = planId) }
    }

    fun cancelDelete() {
        _state.update { it.copy(deletingPlanId = null) }
    }

    fun confirmDelete() = runPlanOperation("delete plan") {
        val planId = _state.value.deletingPlanId ?: return@runPlanOperation
        showResult(repository.delete(planId))
        _state.update { it.copy(deletingPlanId = null) }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun showResult(
        result: MomentPlanSaveResult,
        successMessage: String? = null,
    ) {
        _state.update {
            it.copy(message = result.userMessage(successMessage))
        }
    }

    private fun runPlanOperation(
        context: String,
        block: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                logSafe(context, error)
                _state.update {
                    it.copy(message = "That change could not be saved. Please try again.")
                }
            }
        }
    }
}

class MomentPlanEditorViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val repository = application.momentPlanRepository()
    private val scanner = InstalledAppScanner(application)
    private val requestedPlanId = savedStateHandle.get<String>("planId")?.takeIf { it.isNotBlank() }
    private val stableNewId = savedStateHandle.get<String>(NEW_PLAN_ID_KEY)
        ?: UUID.randomUUID().toString().also { savedStateHandle[NEW_PLAN_ID_KEY] = it }
    private val stableNewContentRevisionId =
        savedStateHandle.get<String>(NEW_CONTENT_REVISION_ID_KEY)
            ?: UUID.randomUUID().toString().also {
                savedStateHandle[NEW_CONTENT_REVISION_ID_KEY] = it
            }
    private val contentRevisionPolicy =
        MomentPlanContentRevisionPolicy(RandomMomentPlanContentRevisionIdSource)
    private var original: MomentPlan? = null
    private var persisted = requestedPlanId != null
    private val _state = MutableStateFlow(
        MomentPlanEditorUiState(
            planId = requestedPlanId ?: stableNewId,
            editing = requestedPlanId != null,
        ),
    )
    val state: StateFlow<MomentPlanEditorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val apps = loadApps()
            if (requestedPlanId == null) {
                _state.update { it.copy(loading = false, apps = apps) }
                return@launch
            }
            try {
                val plan = repository.getById(requestedPlanId)
                if (plan == null) {
                    _state.update { it.copy(loading = false, missing = true, apps = apps) }
                } else {
                    original = plan
                    val appLabel = if (plan.actionType == MomentPlanActionType.LaunchSelectedApp) {
                        apps.firstOrNull { it.packageName == plan.actionTarget }?.label
                    } else {
                        null
                    }
                    _state.value = MomentPlanEditorUiState(
                        loading = false,
                        planId = plan.planId,
                        editing = true,
                        title = plan.title,
                        futureCueText = plan.futureCueText,
                        momentCue = plan.momentCue,
                        actionType = plan.actionType,
                        actionText = plan.actionText,
                        actionTarget = plan.actionTarget,
                        selectedAppLabel = appLabel,
                        enabled = plan.enabled,
                        apps = apps,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                logSafe("load plan editor", error)
                _state.update {
                    it.copy(
                        loading = false,
                        missing = true,
                        apps = apps,
                        validationMessage = "This Moment Plan could not be loaded.",
                    )
                }
            }
        }
    }

    fun setStep(step: Int) {
        _state.update { it.copy(step = step.coerceIn(1, 4), validationMessage = null) }
    }

    fun updateTitle(value: String) =
        updateEditor { copy(title = value.take(AdaptiveMomentLimits.PlanTitleCharacters)) }

    fun updateFutureCue(value: String) =
        updateEditor { copy(futureCueText = value.take(AdaptiveMomentLimits.PlanFutureCueCharacters)) }

    fun selectCue(value: MomentCue) = updateEditor { copy(momentCue = value) }

    fun selectActionType(value: MomentPlanActionType) = updateEditor {
        copy(
            actionType = value,
            actionText = "",
            actionTarget = null,
            selectedAppLabel = null,
        )
    }

    fun updateActionText(value: String) =
        updateEditor { copy(actionText = value.take(AdaptiveMomentLimits.PlanActionCharacters)) }

    fun selectDestination(destination: ImpulsiveDestination) = updateEditor {
        copy(
            actionType = MomentPlanActionType.OpenImpulsiveDestination,
            actionTarget = destination.storageValue,
            actionText = "Open ${MomentPlanPresentation.destinationLabel(destination.storageValue)}",
        )
    }

    fun selectApp(app: LaunchableAppUiModel) {
        if (_state.value.apps.none { it.packageName == app.packageName }) return
        updateEditor {
            copy(
                actionType = MomentPlanActionType.LaunchSelectedApp,
                actionTarget = app.packageName,
                selectedAppLabel = app.label,
                actionText = "Open ${app.label}".take(AdaptiveMomentLimits.PlanActionCharacters),
            )
        }
    }

    fun nextStep() {
        val current = _state.value
        val message = stepValidationMessage(current)
        if (message != null) {
            _state.update { it.copy(validationMessage = message) }
        } else {
            setStep(current.step + 1)
        }
    }

    fun save(onSaved: (String) -> Unit) = persist(onSaved = onSaved)

    fun saveAndPractise(onPractise: (String) -> Unit) =
        persist(onSaved = onPractise)

    private fun persist(
        onSaved: (String) -> Unit = {},
    ) {
        val snapshot = _state.value
        if (snapshot.saving) return
        val now = System.currentTimeMillis()
        val base = original
        val draft = MomentPlan(
            planId = snapshot.planId,
            title = snapshot.title.trim(),
            momentCue = snapshot.momentCue,
            actionText = snapshot.actionText.trim(),
            futureCueText = snapshot.futureCueText.trim(),
            actionType = snapshot.actionType,
            actionTarget = snapshot.actionTarget,
            enabled = snapshot.enabled,
            preferredForCue = base?.preferredForCue == true && snapshot.enabled,
            createdAtMillis = base?.createdAtMillis ?: now,
            updatedAtMillis = now,
            rehearsedAtMillis = base?.rehearsedAtMillis,
            contentRevisionId = base?.contentRevisionId ?: stableNewContentRevisionId,
        )
        val plan = draft.copy(
            contentRevisionId = base?.let {
                contentRevisionPolicy.revisionForEdit(it, draft)
            } ?: stableNewContentRevisionId,
        )
        val validation = MomentPlanPresentation.validationMessage(plan)
            ?: selectedTargetValidationMessage(snapshot)
        if (validation != null) {
            _state.update { it.copy(validationMessage = validation) }
            return
        }
        _state.update { it.copy(saving = true, validationMessage = null) }
        viewModelScope.launch {
            try {
                val result = if (persisted) repository.update(plan) else repository.create(plan)
                if (result == MomentPlanSaveResult.Applied) {
                    persisted = true
                    original = plan
                    _state.update {
                        it.copy(
                            saving = false,
                            savedPlanId = plan.planId,
                        )
                    }
                    onSaved(plan.planId)
                } else {
                    _state.update {
                        it.copy(saving = false, validationMessage = result.userMessage())
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                logSafe("save plan editor", error)
                _state.update {
                    it.copy(
                        saving = false,
                        validationMessage = "This Moment Plan could not be saved. Please try again.",
                    )
                }
            }
        }
    }

    private fun selectedTargetValidationMessage(state: MomentPlanEditorUiState): String? =
        when (state.actionType) {
            MomentPlanActionType.TextOnly -> null
            MomentPlanActionType.OpenImpulsiveDestination ->
                if (ImpulsiveDestination.entries.none { it.storageValue == state.actionTarget }) {
                    "Choose an Impulsive destination."
                } else {
                    null
                }
            MomentPlanActionType.LaunchSelectedApp ->
                if (state.apps.none { it.packageName == state.actionTarget }) {
                    "Choose an app that is currently available."
                } else {
                    null
                }
        }

    private fun stepValidationMessage(state: MomentPlanEditorUiState): String? = when (state.step) {
        1 -> when {
            state.title.isBlank() -> "Add a name for this plan."
            !MomentPlanPresentation.hasMeaningfulTitle(state.title) ->
                "Use at least two letters or numbers in the plan name."
            state.futureCueText.isBlank() -> "Add how you would like the next day to feel."
            !MomentPlanPresentation.hasMeaningfulFutureCue(state.futureCueText) ->
                "Add a little more detail about how you want the next day to feel."
            else -> null
        }
        2 -> if (state.momentCue == null) "Choose when this plan belongs." else null
        3 -> when (state.actionType) {
            MomentPlanActionType.TextOnly -> when {
                state.actionText.isBlank() ->
                    "Choose or write an action."

                !MomentPlanPresentation.hasMeaningfulAction(state.actionText) ->
                    "Use at least three letters or numbers for the action."

                else ->
                    null
            }

            MomentPlanActionType.OpenImpulsiveDestination,
            MomentPlanActionType.LaunchSelectedApp ->
                selectedTargetValidationMessage(state)
        }
        else -> null
    }

    private fun updateEditor(transform: MomentPlanEditorUiState.() -> MomentPlanEditorUiState) {
        _state.update { it.transform().copy(validationMessage = null) }
    }

    private fun loadApps(): List<LaunchableAppUiModel> = try {
        scanner.getLaunchableAppCandidates().map {
            LaunchableAppUiModel(packageName = it.packageName, label = it.appLabel)
        }.sortedBy { it.label.lowercase() }
    } catch (error: Throwable) {
        logSafe("load launcher apps", error)
        emptyList()
    }

    private companion object {
        const val NEW_PLAN_ID_KEY = "momentPlanNewStableId"
        const val NEW_CONTENT_REVISION_ID_KEY = "momentPlanNewStableContentRevisionId"
    }
}

class MomentPlanDetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val repository = application.momentPlanRepository()
    private val planId = savedStateHandle.get<String>("planId").orEmpty()
    private val _state = MutableStateFlow(MomentPlanDetailUiState())
    val state: StateFlow<MomentPlanDetailUiState> = _state.asStateFlow()

    init {
        observeCurrentPlan()
    }

    private fun observeCurrentPlan() {
        viewModelScope.launch {
            repository.observeAll()
                .catch { error ->
                    logSafe("observe plan detail", error)
                    _state.value = MomentPlanDetailUiState(
                        loading = false,
                        missing = true,
                        message = "This Moment Plan could not be loaded.",
                    )
                }
                .collect { plans ->
                    val plan = plans.firstOrNull { it.planId == planId }
                    _state.update { current ->
                        when {
                            plan != null -> current.copy(
                                loading = false,
                                missing = false,
                                plan = plan,
                                busy = false,
                                message = null,
                            )
                            current.deleted -> current.copy(
                                loading = false,
                                missing = false,
                                plan = null,
                                busy = false,
                            )
                            else -> MomentPlanDetailUiState(
                                loading = false,
                                missing = true,
                            )
                        }
                    }
                }
        }
    }

    fun reload() {
        viewModelScope.launch {
            try {
                val plan = planId.takeIf { it.isNotBlank() }?.let { repository.getById(it) }
                _state.value = if (plan == null) {
                    MomentPlanDetailUiState(loading = false, missing = true)
                } else {
                    MomentPlanDetailUiState(loading = false, plan = plan)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                logSafe("load plan detail", error)
                _state.value = MomentPlanDetailUiState(
                    loading = false,
                    missing = true,
                    message = "This Moment Plan could not be loaded.",
                )
            }
        }
    }

    fun setEnabled(enabled: Boolean) = mutate("change detail plan state") { plan ->
        repository.update(
            plan.copy(
                enabled = enabled,
                preferredForCue = plan.preferredForCue && enabled,
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    fun makePreferred() = mutate("make detail plan preferred") { plan ->
        repository.setPreferred(plan.planId, System.currentTimeMillis())
    }

    fun delete() = mutate("delete detail plan", success = {
        _state.update { it.copy(deleted = true) }
    }) { plan ->
        repository.delete(plan.planId)
    }

    private fun mutate(
        context: String,
        success: () -> Unit = {},
        operation: suspend (MomentPlan) -> MomentPlanSaveResult,
    ) {
        val plan = _state.value.plan ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                val result = operation(plan)
                if (result == MomentPlanSaveResult.Applied) {
                    _state.update { it.copy(busy = false) }
                    success()
                } else {
                    _state.update { it.copy(busy = false, message = result.userMessage()) }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                logSafe(context, error)
                _state.update {
                    it.copy(
                        busy = false,
                        message = "That change could not be saved. Please try again.",
                    )
                }
            }
        }
    }
}

class AdaptivePreferencesViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = application.adaptivePreferenceRepository()
    private val _state = MutableStateFlow(AdaptivePreferencesUiState())
    val state: StateFlow<AdaptivePreferencesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.insertDefaults(System.currentTimeMillis())
            repository.observe()
                .catch {
                    logSafe("load adaptive preferences", it)
                    _state.update {
                        it.copy(
                            loading = false,
                            message = "Personal support settings could not be loaded.",
                        )
                    }
                }
                .collect { preferences ->
                    _state.update {
                        it.copy(loading = false, preferences = preferences, saving = false)
                    }
                }
        }
    }

    fun update(transform: (AdaptivePreferences) -> AdaptivePreferences) {
        if (_state.value.saving) return
        val previousRetentionPolicy =
            _state.value.preferences.historyRetentionPolicy
        val preferences =
            transform(
                _state.value.preferences,
            ).copy(
                pathShiftEnabled =
                    true,
            )
        _state.update { it.copy(preferences = preferences, saving = true, message = null) }
        viewModelScope.launch {
            try {
                repository.update(preferences, System.currentTimeMillis())
                if (preferences.historyRetentionPolicy != previousRetentionPolicy) {
                    AdaptiveRetentionDependencies
                        .coordinator(getApplication())
                        .runBounded()
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                logSafe("update adaptive preferences", error)
                _state.update {
                    it.copy(
                        saving = false,
                        message = "That setting could not be saved. Please try again.",
                    )
                }
            }
        }
    }
}

private fun MomentPlanSaveResult.userMessage(successMessage: String? = null): String? = when (this) {
    MomentPlanSaveResult.Applied -> successMessage
    MomentPlanSaveResult.AlreadyExists -> "This Moment Plan already exists."
    MomentPlanSaveResult.NotFound -> "This Moment Plan is no longer available."
    MomentPlanSaveResult.EnabledPlanLimitReached ->
        "You can keep up to six Moment Plans active. Disable one before enabling another."
    MomentPlanSaveResult.PreferredPlanMustBeEnabled ->
        "Enable this Moment Plan before making it preferred."
}

private fun logSafe(context: String, error: Throwable) {
    Log.w("MomentPlan", "$context failed (${error.javaClass.simpleName})")
}
