package com.impulsive.app.frontend.screens.adaptive

import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.impulsive.app.R
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveMomentLimits
import com.impulsive.app.backend.domain.model.adaptive.ImpulsiveDestination
import com.impulsive.app.backend.domain.model.adaptive.MomentCue
import com.impulsive.app.backend.domain.model.adaptive.MomentPlan
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanActionType
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanRehearsalMode
import com.impulsive.app.backend.session.adaptive.AdaptivePreferencesViewModel
import com.impulsive.app.backend.session.adaptive.LaunchableAppUiModel
import com.impulsive.app.backend.session.adaptive.MomentPlanDetailViewModel
import com.impulsive.app.backend.session.adaptive.MomentPlanEditorUiState
import com.impulsive.app.backend.session.adaptive.MomentPlanEditorViewModel
import com.impulsive.app.backend.session.adaptive.MomentPlanHomeUiState
import com.impulsive.app.backend.session.adaptive.MomentPlanListViewModel
import com.impulsive.app.backend.session.adaptive.MomentPlanRehearsalViewModel
import com.impulsive.app.frontend.components.HomeSupportFeatureCard
import com.impulsive.app.frontend.components.ImpulsiveAmbientBackground
import com.impulsive.app.frontend.components.ImpulsiveTopAppBar
import com.impulsive.app.frontend.theme.ImpulsivePsychological
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

@Composable
fun MomentPlanHomeCard(
    state: MomentPlanHomeUiState,
    onOpenPlans: () -> Unit,
    onPractise: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.loading) return
    val activePlan = state.activePlan
    val body =
        activePlan?.let { plan ->
            buildString {
                append(cueLabel(plan.momentCue))
                append(" -> ")
                append(shortActionPreview(plan.actionText))
                plan.rehearsedAtMillis?.let {
                    append(" · Last practised ")
                    append(formatDate(it))
                }
            }
        } ?: "Create one small action you can have ready for later."

    HomeSupportFeatureCard(
        eyebrow = "MOMENT PLAN",
        title = activePlan?.title?.let(::shortActionPreview)
            ?: "Prepare for your next difficult moment",
        body = body,
        actionLabel = if (activePlan == null) "Create plan >" else "Practise plan >",
        icon = Icons.Filled.AutoAwesome,
        onClick = activePlan?.let { plan ->
            { onPractise(plan.planId) }
        } ?: onOpenPlans,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MomentPlanListScreen(
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
    onEdit: (String) -> Unit,
    onPractise: (String) -> Unit,
    viewModel: MomentPlanListViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var aboutMomentPlansVisible by remember { mutableStateOf(false) }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    state.deletingPlanId?.let {
        DeletePlanDialog(
            onDismiss = viewModel::cancelDelete,
            onDelete = viewModel::confirmDelete,
        )
    }
    if (aboutMomentPlansVisible) {
        MomentPlanInfoDialog(
            onDismiss = { aboutMomentPlansVisible = false },
        )
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.moment_plan_list_title),
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = { aboutMomentPlansVisible = true },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "About Moment Plans",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.76f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                containerColor = ImpulsivePsychological,
                icon = {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(R.string.moment_plan_add_description),
                    )
                },
                text = { Text(stringResource(R.string.moment_plan_create)) },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            ImpulsiveAmbientBackground()
            when {
                state.loading -> LoadingContent()
                state.isEmpty -> EmptyPlans(onCreate)
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 20.dp,
                        top = 12.dp,
                        end = 20.dp,
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.plans.count { it.enabled } >= AdaptiveMomentLimits.MaximumEnabledPlans) {
                        item(key = "enabled-limit") {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Text(
                                    stringResource(R.string.moment_plan_enabled_limit),
                                    modifier = Modifier.padding(14.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                    items(state.plans, key = { it.planId }) { plan ->
                        PlanListCard(
                            plan = plan,
                            onOpen = { onOpen(plan.planId) },
                            onEdit = { onEdit(plan.planId) },
                            onEnableChanged = { viewModel.setEnabled(plan, it) },
                            onPreferred = { viewModel.makePreferred(plan) },
                            onPractise = { onPractise(plan.planId) },
                            onDelete = { viewModel.requestDelete(plan.planId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MomentPlanInfoDialog(
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "What is a Moment Plan?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("A Moment Plan is one small action you prepare in advance for a difficult moment.")
                Text("It helps you decide what to do before the moment arrives.")
                Text("You can create a plan, practise it, and keep it ready for later.")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it")
            }
        },
    )
}

@Composable
private fun EmptyPlans(onCreate: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.moment_plan_empty),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(18.dp))
        Button(onClick = onCreate, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.moment_plan_create_first))
        }
    }
}

@Composable
private fun PlanListCard(
    plan: MomentPlan,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onEnableChanged: (Boolean) -> Unit,
    onPreferred: () -> Unit,
    onPractise: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onOpen),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            plan.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (plan.preferredForCue) {
                            AssistChip(
                                onClick = onOpen,
                                label = { Text(stringResource(R.string.moment_plan_preferred)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Star,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                            )
                        }
                    }
                    Text(
                        cueLabel(plan.momentCue),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = stringResource(
                                R.string.moment_plan_actions_description,
                            ),
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.moment_plan_practise)) },
                            onClick = { menuOpen = false; onPractise() },
                            leadingIcon = { Icon(Icons.Filled.PlayArrow, null) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.moment_plan_edit)) },
                            onClick = { menuOpen = false; onEdit() },
                            leadingIcon = { Icon(Icons.Filled.Edit, null) },
                        )
                        if (!plan.preferredForCue && plan.enabled) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.moment_plan_make_preferred)) },
                                onClick = { menuOpen = false; onPreferred() },
                                leadingIcon = { Icon(Icons.Outlined.StarOutline, null) },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.moment_plan_delete)) },
                            onClick = { menuOpen = false; onDelete() },
                            leadingIcon = { Icon(Icons.Filled.Delete, null) },
                        )
                    }
                }
            }
            Text(
                plan.actionText,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            plan.rehearsedAtMillis?.let {
                Text(
                    stringResource(R.string.moment_plan_last_practised, formatDate(it)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(
                        if (plan.enabled) R.string.moment_plan_enabled else R.string.moment_plan_disabled,
                    ),
                )
                Switch(
                    checked = plan.enabled,
                    onCheckedChange = onEnableChanged,
                )
            }
            TextButton(
                onClick = onOpen,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier
                    .align(Alignment.End)
                    .heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.moment_plan_open))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MomentPlanEditorScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    onPractise: (String) -> Unit,
    viewModel: MomentPlanEditorViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var appPickerVisible by remember { mutableStateOf(false) }
    if (appPickerVisible) {
        AppPickerSheet(
            apps = state.apps,
            onDismiss = { appPickerVisible = false },
            onSelected = {
                viewModel.selectApp(it)
                appPickerVisible = false
            },
        )
    }
    Scaffold(
        topBar = {
            ImpulsiveTopAppBar(
                title = stringResource(
                    if (state.editing) R.string.moment_plan_edit_title
                    else R.string.moment_plan_create_title,
                ),
                onBack = onBack,
            )
        },
    ) { padding ->
        when {
            state.loading -> LoadingContent(Modifier.padding(padding))
            state.missing -> NotFoundContent(onBack, Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .navigationBarsPadding(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp,
                    top = 20.dp,
                    end = 20.dp,
                    bottom = 40.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Text(
                        stringResource(R.string.moment_plan_step_count, state.step),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                item {
                    when (state.step) {
                        1 -> FutureStep(state, viewModel)
                        2 -> CueStep(state, viewModel)
                        3 -> ActionStep(
                            state = state,
                            viewModel = viewModel,
                            onChooseApp = { appPickerVisible = true },
                        )
                        else -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(
                                stringResource(R.string.moment_plan_step_rehearse),
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            PlanPreview(state.toPreviewPlan())
                        }
                    }
                }
                state.validationMessage?.let { message ->
                    item {
                        Text(
                            message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (state.step > 1) {
                            OutlinedButton(
                                onClick = { viewModel.setStep(state.step - 1) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                            ) {
                                Text(stringResource(R.string.moment_plan_back))
                            }
                        }
                        if (state.step < 4) {
                            Button(
                                onClick = viewModel::nextStep,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                            ) {
                                Text(stringResource(R.string.moment_plan_next))
                            }
                        }
                    }
                }
                if (state.step == 4) {
                    item {
                        Button(
                            onClick = { viewModel.saveAndPractise(onPractise) },
                            enabled = !state.saving,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                        ) {
                            Text(stringResource(R.string.moment_plan_practise_my_plan))
                        }
                    }
                    item {
                        OutlinedButton(
                            onClick = { viewModel.save(onSaved) },
                            enabled = !state.saving,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                        ) {
                            if (state.saving) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text(stringResource(R.string.moment_plan_save))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FutureStep(
    state: MomentPlanEditorUiState,
    viewModel: MomentPlanEditorViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.moment_plan_step_future), style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = state.title,
            onValueChange = viewModel::updateTitle,
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewWhenFocused(),
            label = { Text(stringResource(R.string.moment_plan_name_label)) },
            placeholder = { Text(stringResource(R.string.moment_plan_name_example)) },
            supportingText = {
                Text(
                    stringResource(
                        R.string.moment_plan_characters_remaining,
                        AdaptiveMomentLimits.PlanTitleCharacters - state.title.length,
                    ),
                )
            },
            singleLine = false,
        )
        val options = listOf(
            "Tomorrow morning, I want to feel clear and ready to work." to R.string.moment_plan_future_clear,
            "Tomorrow, I want to feel calm and steady." to R.string.moment_plan_future_calm,
            "Tomorrow, I want to feel proud of my choice." to R.string.moment_plan_future_proud,
            "Tomorrow, I want to feel focused on what matters." to R.string.moment_plan_future_focused,
        )
        options.forEach { (value, label) ->
            FilterChip(
                selected = state.futureCueText == value,
                onClick = { viewModel.updateFutureCue(value) },
                label = { Text(stringResource(label)) },
            )
        }
        FilterChip(
            selected = state.futureCueText.isBlank(),
            onClick = { viewModel.updateFutureCue("") },
            label = { Text(stringResource(R.string.moment_plan_future_custom)) },
        )
        OutlinedTextField(
            value = state.futureCueText,
            onValueChange = viewModel::updateFutureCue,
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewWhenFocused(),
            label = { Text(stringResource(R.string.moment_plan_future_label)) },
            placeholder = { Text(stringResource(R.string.moment_plan_future_example)) },
            supportingText = {
                Text(
                    stringResource(
                        R.string.moment_plan_characters_remaining,
                        AdaptiveMomentLimits.PlanFutureCueCharacters - state.futureCueText.length,
                    ),
                )
            },
            minLines = 3,
        )
    }
}

@Composable
private fun CueStep(
    state: MomentPlanEditorUiState,
    viewModel: MomentPlanEditorViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.moment_plan_step_cue), style = MaterialTheme.typography.headlineSmall)
        MomentCue.entries.forEach { cue ->
            FilterChip(
                selected = state.momentCue == cue,
                onClick = { viewModel.selectCue(cue) },
                label = { Text(cueLabel(cue)) },
                leadingIcon = if (state.momentCue == cue) {
                    { Icon(Icons.Filled.CheckCircle, contentDescription = null) }
                } else {
                    null
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionStep(
    state: MomentPlanEditorUiState,
    viewModel: MomentPlanEditorViewModel,
    onChooseApp: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.moment_plan_step_action), style = MaterialTheme.typography.headlineSmall)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                MomentPlanActionType.TextOnly to R.string.moment_plan_action_text,
                MomentPlanActionType.OpenImpulsiveDestination to R.string.moment_plan_action_destination,
                MomentPlanActionType.LaunchSelectedApp to R.string.moment_plan_action_app,
            ).forEach { (type, label) ->
                FilterChip(
                    selected = state.actionType == type,
                    onClick = { viewModel.selectActionType(type) },
                    label = { Text(stringResource(label), maxLines = 2) },
                )
            }
        }
        when (state.actionType) {
            MomentPlanActionType.TextOnly -> OutlinedTextField(
                value = state.actionText,
                onValueChange = viewModel::updateActionText,
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewWhenFocused(),
                label = { Text(stringResource(R.string.moment_plan_action_label)) },
                placeholder = { Text(stringResource(R.string.moment_plan_action_example)) },
                supportingText = {
                    Text(
                        stringResource(
                            R.string.moment_plan_characters_remaining,
                            AdaptiveMomentLimits.PlanActionCharacters - state.actionText.length,
                        ),
                    )
                },
                minLines = 3,
            )
            MomentPlanActionType.OpenImpulsiveDestination -> {
                Text(stringResource(R.string.moment_plan_choose_destination))
                ImpulsiveDestination.entries.forEach { destination ->
                    FilterChip(
                        selected = state.actionTarget == destination.storageValue,
                        onClick = { viewModel.selectDestination(destination) },
                        label = { Text(destinationLabel(destination)) },
                    )
                }
            }
            MomentPlanActionType.LaunchSelectedApp -> {
                OutlinedButton(
                    onClick = onChooseApp,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.moment_plan_choose_app))
                }
                state.selectedAppLabel?.let {
                    Text(stringResource(R.string.moment_plan_selected_app, it))
                }
                if (state.actionTarget != null && state.selectedAppLabel == null) {
                    Text(
                        stringResource(R.string.moment_plan_selected_app_missing),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPickerSheet(
    apps: List<LaunchableAppUiModel>,
    onDismiss: () -> Unit,
    onSelected: (LaunchableAppUiModel) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                stringResource(R.string.moment_plan_app_picker_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(12.dp))
            if (apps.isEmpty()) {
                Text(stringResource(R.string.moment_plan_no_apps))
                Spacer(Modifier.height(32.dp))
            } else {
                LazyColumn(Modifier.heightIn(max = 520.dp)) {
                    items(apps, key = { it.packageName }) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button) { onSelected(app) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            LauncherAppIcon(app)
                            Text(app.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LauncherAppIcon(app: LaunchableAppUiModel) {
    val packageManager = LocalContext.current.packageManager
    val bitmap = remember(app.packageName) {
        try {
            packageManager.getApplicationIcon(app.packageName).toBitmap(48, 48).asImageBitmap()
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = stringResource(R.string.moment_plan_app_icon_description, app.label),
            modifier = Modifier.size(40.dp),
        )
    } else {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MomentPlanDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onPractise: (String) -> Unit,
    onDeleted: () -> Unit,
    viewModel: MomentPlanDetailViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(state.deleted) {
        if (state.deleted) onDeleted()
    }
    if (confirmDelete) {
        DeletePlanDialog(
            onDismiss = { confirmDelete = false },
            onDelete = {
                confirmDelete = false
                viewModel.delete()
            },
        )
    }
    Scaffold(
        topBar = {
            ImpulsiveTopAppBar(
                title = stringResource(R.string.moment_plan_detail_title),
                onBack = onBack,
            )
        },
    ) { padding ->
        when {
            state.loading -> LoadingContent(Modifier.padding(padding))
            state.missing || state.plan == null -> NotFoundContent(onBack, Modifier.padding(padding))
            else -> {
                val plan = state.plan!!
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item { PlanPreview(plan) }
                    item {
                        DetailValue(
                            stringResource(R.string.moment_plan_status_heading),
                            stringResource(
                                if (plan.enabled) R.string.moment_plan_enabled
                                else R.string.moment_plan_disabled,
                            ) + if (plan.preferredForCue) {
                                " · " + stringResource(R.string.moment_plan_preferred)
                            } else {
                                ""
                            },
                        )
                    }
                    item {
                        DetailValue(
                            stringResource(R.string.moment_plan_practise),
                            plan.rehearsedAtMillis?.let(::formatDate)
                                ?: stringResource(R.string.moment_plan_never_practised),
                        )
                    }
                    state.message?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                    item {
                        Button(
                            onClick = { onPractise(plan.planId) },
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Text(stringResource(R.string.moment_plan_practise))
                        }
                    }
                    item {
                        OutlinedButton(
                            onClick = { onEdit(plan.planId) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Text(stringResource(R.string.moment_plan_edit))
                        }
                    }
                    item {
                        OutlinedButton(
                            onClick = { viewModel.setEnabled(!plan.enabled) },
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Text(
                                stringResource(
                                    if (plan.enabled) R.string.moment_plan_disable
                                    else R.string.moment_plan_enable,
                                ),
                            )
                        }
                    }
                    if (plan.enabled && !plan.preferredForCue) {
                        item {
                            OutlinedButton(
                                onClick = viewModel::makePreferred,
                                enabled = !state.busy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) {
                                Text(stringResource(R.string.moment_plan_make_preferred))
                            }
                        }
                    }
                    item {
                        TextButton(
                            onClick = { confirmDelete = true },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Text(
                                stringResource(R.string.moment_plan_delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MomentPlanRehearsalScreen(
    onFinished: () -> Unit,
    viewModel: MomentPlanRehearsalViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmLeave by remember { mutableStateOf(false) }

    LaunchedEffect(state.completed, state.dismissed) {
        if (state.completed || state.dismissed) onFinished()
    }
    BackHandler(enabled = !state.loading && !state.completed && !state.dismissed) {
        confirmLeave = true
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave practice?") },
            text = { Text("You can continue this practice or leave it without marking it complete.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLeave = false
                        viewModel.leave()
                    },
                    enabled = !state.busy,
                ) {
                    Text("Leave Practice")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) {
                    Text("Continue Practice")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            ImpulsiveTopAppBar(
                title = when (state.mode) {
                    MomentPlanRehearsalMode.Quick -> "Quick Practice"
                    else -> "Guided Practice"
                },
                onBack = { confirmLeave = true },
                navigationEnabled = !state.loading,
            )
        },
    ) { padding ->
        when {
            state.loading -> LoadingContent(Modifier.padding(padding))
            state.missing || state.plan == null || state.mode == null ->
                NotFoundContent(onFinished, Modifier.padding(padding))
            state.mode == MomentPlanRehearsalMode.Quick -> {
                QuickRehearsalContent(
                    plan = state.plan!!,
                    busy = state.busy,
                    message = state.message,
                    onFinish = viewModel::finish,
                    modifier = Modifier.padding(padding),
                )
            }
            else -> {
                GuidedRehearsalContent(
                    plan = state.plan!!,
                    stage = state.stage,
                    busy = state.busy,
                    message = state.message,
                    onPrevious = viewModel::previousStage,
                    onNext = viewModel::nextStage,
                    onFinish = viewModel::finish,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

@Composable
private fun GuidedRehearsalContent(
    plan: MomentPlan,
    stage: Int,
    busy: Boolean,
    message: String?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val heading = when (stage) {
        1 -> "NOTICE THE MOMENT"
        2 -> "PICTURE TOMORROW"
        3 -> "REMEMBER THE ACTION"
        else -> "READY"
    }
    val prompt = when (stage) {
        1 -> "Imagine noticing this moment:"
        2 -> "Take a moment to picture how you would like to feel afterward."
        3 -> "Say the action to yourself once."
        else -> "You have practised this Moment Plan."
    }
    val privateContent = when (stage) {
        1 -> cueLabel(plan.momentCue)
        2 -> plan.futureCueText
        3 -> plan.actionText
        else -> plan.title
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = "STEP $stage OF 4",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(heading, style = MaterialTheme.typography.headlineSmall)
        Text(prompt, style = MaterialTheme.typography.bodyLarge)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                text = privateContent,
                modifier = Modifier.padding(22.dp),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        message?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(8.dp))
        if (stage > 1) {
            OutlinedButton(
                onClick = onPrevious,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text("Previous")
            }
        }
        Button(
            onClick = if (stage == 4) onFinish else onNext,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(if (stage == 4) "Finish Practice" else "Continue")
            }
        }
    }
}

@Composable
private fun QuickRehearsalContent(
    plan: MomentPlan,
    busy: Boolean,
    message: String?,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            top = 20.dp,
            end = 20.dp,
            bottom = 40.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Text(
                "QUICK PRACTICE",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        item { PlanPreview(plan) }
        message?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        item {
            Button(
                onClick = onFinish,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("I'm Ready")
                }
            }
        }
    }
}

@Composable
private fun PlanPreview(plan: MomentPlan) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.moment_plan_preview_eyebrow),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(plan.title, style = MaterialTheme.typography.headlineSmall)
            DetailValue(
                stringResource(R.string.moment_plan_when),
                stringResource(
                    R.string.moment_plan_i_notice,
                    cueLabel(plan.momentCue).lowercase(),
                ),
            )
            DetailValue(
                stringResource(R.string.moment_plan_then),
                stringResource(R.string.moment_plan_i_will, plan.actionText.replaceFirstChar { it.lowercase() }),
            )
            DetailValue(stringResource(R.string.moment_plan_why), plan.futureCueText)
        }
    }
}

@Composable
private fun DetailValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PracticeDialog(
    plan: MomentPlan,
    complete: Boolean,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.CheckCircle, contentDescription = null) },
        title = {
            Text(
                stringResource(
                    if (complete) R.string.moment_plan_practised
                    else R.string.moment_plan_preview_eyebrow,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (complete) Text(stringResource(R.string.moment_plan_practised_body))
                PlanPreview(plan)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.moment_plan_done))
            }
        },
    )
}

@Composable
private fun DeletePlanDialog(
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.moment_plan_delete_title)) },
        text = { Text(stringResource(R.string.moment_plan_delete_body)) },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.moment_plan_cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = onDelete) {
                Text(
                    stringResource(R.string.moment_plan_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
    )
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.moment_plan_loading))
    }
}

@Composable
private fun NotFoundContent(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.moment_plan_not_found))
        Spacer(Modifier.height(16.dp))
        Button(onClick = onBack) {
            Text(stringResource(R.string.moment_plan_return_list))
        }
    }
}

private fun MomentPlanEditorUiState.toPreviewPlan(): MomentPlan {
    val now = System.currentTimeMillis()
    return MomentPlan(
        planId = planId,
        title = title.trim(),
        momentCue = momentCue,
        actionText = actionText.trim(),
        futureCueText = futureCueText.trim(),
        actionType = actionType,
        actionTarget = actionTarget,
        enabled = enabled,
        preferredForCue = false,
        createdAtMillis = now,
        updatedAtMillis = now,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.bringIntoViewWhenFocused(): Modifier {
    var focused by remember { mutableStateOf(false) }
    val requester = remember { BringIntoViewRequester() }

    LaunchedEffect(focused) {
        if (focused) {
            delay(260)
            requester.bringIntoView()
        }
    }

    return this
        .bringIntoViewRequester(requester)
        .onFocusChanged { focused = it.isFocused }
}

@Composable
private fun cueLabel(cue: MomentCue?): String = stringResource(
    when (cue) {
        MomentCue.Boredom -> R.string.moment_plan_cue_boredom
        MomentCue.Stress -> R.string.moment_plan_cue_stress
        MomentCue.BeingAlone -> R.string.moment_plan_cue_alone
        MomentCue.Tiredness -> R.string.moment_plan_cue_tired
        MomentCue.AvoidingSomething -> R.string.moment_plan_cue_avoiding
        MomentCue.AutomaticHabit -> R.string.moment_plan_cue_habit
        null -> R.string.moment_plan_cue_any
    },
)

@Composable
private fun destinationLabel(destination: ImpulsiveDestination): String = stringResource(
    when (destination) {
        ImpulsiveDestination.Focus -> R.string.moment_plan_destination_focus
        ImpulsiveDestination.Journal -> R.string.moment_plan_destination_journal
        ImpulsiveDestination.PivotGames -> R.string.moment_plan_destination_games
        ImpulsiveDestination.ResetReading -> R.string.moment_plan_destination_reading
    },
)

private fun shortActionPreview(action: String, maximumCharacters: Int = 72): String {
    val trimmed = action.trim()
    return if (trimmed.length <= maximumCharacters) {
        trimmed
    } else {
        trimmed.take(maximumCharacters - 1).trimEnd() + "…"
    }
}

private fun formatDate(epochMillis: Long): String =
    DateTimeFormatter.ofPattern("d MMM yyyy")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epochMillis))
