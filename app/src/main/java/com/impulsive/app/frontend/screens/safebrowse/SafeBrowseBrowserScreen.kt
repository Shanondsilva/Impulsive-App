package com.impulsive.app.frontend.screens.safebrowse

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowseAccessEffect
import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowseAccessState as DomainAccessState
import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowseBlockedReason
import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowseNavigationDecision
import com.impulsive.app.backend.session.safebrowse.SafeBrowseAccessViewModel
import com.impulsive.app.backend.session.safebrowse.SafeBrowseBrowserEffect
import com.impulsive.app.backend.session.safebrowse.SafeBrowseBrowserUiState
import com.impulsive.app.backend.session.safebrowse.SafeBrowseBrowserViewModel
import com.impulsive.app.backend.session.safebrowse.SafeBrowseBrowserViewModelFactory
import com.impulsive.app.backend.session.safebrowse.SafeBrowsePolicyStatus
import com.impulsive.app.frontend.components.ImpulsiveAmbientBackground
import com.impulsive.app.frontend.theme.ImpulsiveTheme
import kotlinx.coroutines.flow.Flow

/**
 * Secured browser engine. Production navigation is connected only after an
 * authoritative Safe Browse access entitlement is implemented.
 *
 * [accessViewModel] is the same shared instance the Safe Browse unlock screen uses --
 * never a second, independent access ledger. Usage is metered only while this
 * destination is actually in the foreground (ON_START/ON_STOP), never while merely
 * composed.
 */
@Composable
internal fun SafeBrowseBrowserRoute(
    accessViewModel: SafeBrowseAccessViewModel,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: SafeBrowseBrowserViewModel = viewModel(
        factory = SafeBrowseBrowserViewModelFactory(context.applicationContext),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val domainAccessState by accessViewModel.accessState.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current

    var destinationStarted by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    var timedUsageStarted by remember { mutableStateOf(false) }
    var exitRequested by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, accessViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> destinationStarted = true

                Lifecycle.Event.ON_STOP -> {
                    destinationStarted = false
                    if (timedUsageStarted) {
                        timedUsageStarted = false
                        accessViewModel.endBrowserUsage()
                    }
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (timedUsageStarted) {
                timedUsageStarted = false
                accessViewModel.endBrowserUsage()
            }
        }
    }

    // Usage is metered only while both this destination is genuinely in the foreground AND
    // access is Active -- a Pass never starts the timed ledger, Loading never begins usage,
    // and Locked/Error exit exactly once instead of silently continuing to meter nothing.
    LaunchedEffect(destinationStarted, domainAccessState) {
        when (domainAccessState) {
            DomainAccessState.Loading -> Unit

            is DomainAccessState.Active -> {
                if (destinationStarted && !timedUsageStarted) {
                    timedUsageStarted = true
                    accessViewModel.beginBrowserUsage()
                }
            }

            is DomainAccessState.PassActive -> {
                if (timedUsageStarted) {
                    timedUsageStarted = false
                    accessViewModel.endBrowserUsage()
                }
            }

            DomainAccessState.Locked,
            is DomainAccessState.Error,
            -> {
                if (timedUsageStarted) {
                    timedUsageStarted = false
                    accessViewModel.endBrowserUsage()
                }
                if (!exitRequested) {
                    exitRequested = true
                    onExit()
                }
            }
        }
    }

    LaunchedEffect(accessViewModel) {
        accessViewModel.effects.collect { effect ->
            if (effect is SafeBrowseAccessEffect.AccessExpired && !exitRequested) {
                timedUsageStarted = false
                exitRequested = true
                onExit()
            }
        }
    }

    val remainingTimeLabel = (domainAccessState as? DomainAccessState.Active)
        ?.remainingMillis
        ?.let { millis -> formatSafeBrowseRemainingTime(millis / 1_000L) }

    when (domainAccessState) {
        DomainAccessState.Loading ->
            SafeBrowseBrowserAccessPreparingScreen(onExit = onExit)

        is DomainAccessState.Active,
        is DomainAccessState.PassActive,
        ->
            SafeBrowseBrowserScreen(
                state = state,
                remainingTimeLabel = remainingTimeLabel,
                effects = viewModel.effects,
                evaluateNavigation = viewModel::evaluateNavigation,
                onSearchChanged = viewModel::updateSearchText,
                onSearchSubmitted = viewModel::submitSearch,
                onPageStarted = viewModel::onPageStarted,
                onPageFinished = viewModel::onPageFinished,
                onBlocked = viewModel::onBlockedNavigation,
                onPageError = viewModel::onPageError,
                onDownloadBlocked = viewModel::onDownloadBlocked,
                onPermissionBlocked = viewModel::onPermissionBlocked,
                onRendererGone = viewModel::onRendererGone,
                onRetryPolicy = viewModel::retryPolicyLoad,
                onGoBack = viewModel::goBack,
                onGoForward = viewModel::goForward,
                onReload = viewModel::reload,
                onStop = viewModel::stopLoading,
                onHome = viewModel::returnHome,
                onExit = onExit,
            )

        DomainAccessState.Locked,
        is DomainAccessState.Error,
        ->
            SafeBrowseBrowserAccessPreparingScreen(onExit = onExit)
    }
}

@Composable
private fun SafeBrowseBrowserAccessPreparingScreen(
    onExit: () -> Unit,
) {
    BackHandler(onBack = onExit)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("safe_browse_browser_access_loading"),
    ) {
        ImpulsiveAmbientBackground(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()

            Text(
                text = "Safe Browse",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp),
            )

            Text(
                text = "Checking your Safe Browse access…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
internal fun SafeBrowseBrowserScreen(
    state: SafeBrowseBrowserUiState,
    remainingTimeLabel: String? = null,
    effects: Flow<SafeBrowseBrowserEffect>,
    evaluateNavigation: (String) -> SafeBrowseNavigationDecision?,
    onSearchChanged: (String) -> Unit,
    onSearchSubmitted: () -> Unit,
    onPageStarted: (String) -> Unit,
    onPageFinished: (String, String?, Boolean, Boolean) -> Unit,
    onBlocked: (SafeBrowseBlockedReason, String?) -> Unit,
    onPageError: (String) -> Unit,
    onDownloadBlocked: () -> Unit,
    onPermissionBlocked: () -> Unit,
    onRendererGone: () -> Unit,
    onRetryPolicy: () -> Unit,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    onHome: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = MaterialTheme.colorScheme.onBackground
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary

    var webView by remember { mutableStateOf<WebView?>(null) }
    var pendingUrl by remember { mutableStateOf<String?>(null) }

    BackHandler {
        if (webView?.canGoBack() == true) {
            onGoBack()
        } else {
            onExit()
        }
    }

    // A single long-lived collector avoids missing effects emitted before the
    // WebView instance exists (for example the very first search).
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is SafeBrowseBrowserEffect.LoadUrl -> {
                    val current = webView
                    if (current != null) {
                        current.loadUrl(effect.canonicalUrl)
                    } else {
                        pendingUrl = effect.canonicalUrl
                    }
                }

                SafeBrowseBrowserEffect.GoBack ->
                    webView?.takeIf { it.canGoBack() }?.goBack()

                SafeBrowseBrowserEffect.GoForward ->
                    webView?.takeIf { it.canGoForward() }?.goForward()

                SafeBrowseBrowserEffect.Reload -> webView?.reload()

                SafeBrowseBrowserEffect.StopLoading -> webView?.stopLoading()

                SafeBrowseBrowserEffect.ReturnHome -> {
                    pendingUrl = null
                    webView?.stopLoading()
                }
            }
        }
    }

    LaunchedEffect(webView, state.isHome) {
        val current = webView ?: return@LaunchedEffect
        val url = pendingUrl ?: return@LaunchedEffect

        if (state.isHome) {
            pendingUrl = null
            return@LaunchedEffect
        }

        pendingUrl = null
        current.loadUrl(url)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        ImpulsiveAmbientBackground(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SafeBrowseBrowserTopBar(
                displayHost = state.currentDisplayHost,
                remainingTimeLabel = remainingTimeLabel,
                isPageLoading = state.isPageLoading,
                searchText = state.searchText,
                onSearchChanged = onSearchChanged,
                onSearchSubmitted = onSearchSubmitted,
                onExit = onExit,
                text = text,
                muted = muted,
                accent = accent,
            )

            state.blockedNotice?.let { notice ->
                SafeBrowseBlockedNoticeCard(displayHost = notice.displayHost, text = text, muted = muted)
            }

            state.errorMessage?.let { message ->
                SafeBrowseErrorNoticeCard(message = message, muted = muted)
            }

            when (val status = state.policyStatus) {
                SafeBrowsePolicyStatus.Loading -> {
                    SafeBrowsePreparingCard(modifier = Modifier.weight(1f), muted = muted)
                }

                is SafeBrowsePolicyStatus.Error -> {
                    SafeBrowsePolicyErrorCard(
                        modifier = Modifier.weight(1f),
                        message = status.message,
                        onRetryPolicy = onRetryPolicy,
                        accent = accent,
                        text = text,
                        muted = muted,
                    )
                }

                SafeBrowsePolicyStatus.Ready -> {
                    if (state.isHome) {
                        SafeBrowseHomeContentCard(
                            modifier = Modifier.weight(1f),
                            text = text,
                            muted = muted,
                        )
                    } else {
                        key(state.rendererGeneration) {
                            AndroidView(
                                factory = { context ->
                                    WebView(context).apply {
                                        configureSafeBrowseWebView(onDownloadBlocked = onDownloadBlocked)
                                        webViewClient = SafeBrowseWebViewClient(
                                            evaluateNavigation = evaluateNavigation,
                                            onPageStartedSafely = onPageStarted,
                                            onPageFinishedSafely = onPageFinished,
                                            onBlocked = onBlocked,
                                            onError = onPageError,
                                            onRendererGone = onRendererGone,
                                        )
                                        webChromeClient =
                                            SafeBrowseWebChromeClient(onPermissionBlocked = onPermissionBlocked)
                                    }.also { created -> webView = created }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .testTag("safe_browse_webview"),
                                // Runs exactly once when this view instance leaves composition
                                // (renderer-generation change, navigating Home, or leaving the
                                // route). Clearing the shared `webView` reference here -- instead
                                // of in a separate DisposableEffect keyed on `webView` -- avoids a
                                // window where a stale, already-destroyed instance can still be
                                // targeted by an in-flight browser effect.
                                onRelease = { released ->
                                    if (webView === released) {
                                        webView = null
                                    }
                                    released.destroySafeBrowseSession()
                                },
                            )
                        }
                    }
                }
            }

            SafeBrowseBrowserBottomControls(
                canGoBack = state.canGoBack,
                canGoForward = state.canGoForward,
                isPageLoading = state.isPageLoading,
                onGoBack = onGoBack,
                onGoForward = onGoForward,
                onHome = onHome,
                onReload = onReload,
                onStop = onStop,
                text = text,
                muted = muted,
            )
        }
    }
}

@Composable
private fun SafeBrowseBrowserTopBar(
    displayHost: String?,
    remainingTimeLabel: String?,
    isPageLoading: Boolean,
    searchText: String,
    onSearchChanged: (String) -> Unit,
    onSearchSubmitted: () -> Unit,
    onExit: () -> Unit,
    text: androidx.compose.ui.graphics.Color,
    muted: androidx.compose.ui.graphics.Color,
    accent: androidx.compose.ui.graphics.Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onExit,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Exit Safe Browse",
                    tint = text,
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Safe Browse",
                    color = text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (displayHost != null) {
                    Text(
                        text = displayHost,
                        color = muted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                }
            }

            if (remainingTimeLabel != null) {
                Text(
                    text = remainingTimeLabel,
                    color = muted,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.testTag("safe_browse_browser_remaining_time"),
                )
            }

            if (isPageLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = accent,
                )
            }
        }

        if (isPageLoading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 2.dp),
                color = accent,
            )
        }

        OutlinedTextField(
            value = searchText,
            onValueChange = onSearchChanged,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("safe_browse_search"),
            label = { Text("Search the web") },
            singleLine = true,
            leadingIcon = {
                Icon(imageVector = Icons.Filled.Search, contentDescription = null, tint = muted)
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Search,
            ),
            keyboardActions = KeyboardActions(onSearch = { onSearchSubmitted() }),
        )
    }
}

@Composable
private fun SafeBrowsePreparingCard(
    modifier: Modifier = Modifier,
    muted: androidx.compose.ui.graphics.Color,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator()
                Text(
                    text = "Preparing Safe Browse protection…",
                    color = muted,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun SafeBrowsePolicyErrorCard(
    modifier: Modifier = Modifier,
    message: String,
    onRetryPolicy: () -> Unit,
    accent: androidx.compose.ui.graphics.Color,
    text: androidx.compose.ui.graphics.Color,
    muted: androidx.compose.ui.graphics.Color,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(24.dp),
            ) {
                Text(
                    text = message,
                    color = text,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                OutlinedButton(
                    onClick = onRetryPolicy,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("safe_browse_policy_retry"),
                ) {
                    Text("Try again")
                }
            }
        }
    }
}

@Composable
private fun SafeBrowseHomeContentCard(
    modifier: Modifier = Modifier,
    text: androidx.compose.ui.graphics.Color,
    muted: androidx.compose.ui.graphics.Color,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(24.dp),
            ) {
                Text(
                    text = "Search for an ordinary website",
                    color = text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Adult domains and unsafe navigation are restricted.",
                    color = muted,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun SafeBrowseBlockedNoticeCard(
    displayHost: String?,
    text: androidx.compose.ui.graphics.Color,
    muted: androidx.compose.ui.graphics.Color,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Navigation blocked",
                color = text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Adult or unsafe website navigation is not available in Safe Browse.",
                color = muted,
                style = MaterialTheme.typography.bodySmall,
            )
            if (displayHost != null) {
                Text(
                    text = displayHost,
                    color = muted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SafeBrowseErrorNoticeCard(
    message: String,
    muted: androidx.compose.ui.graphics.Color,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = message,
            color = muted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun SafeBrowseBrowserBottomControls(
    canGoBack: Boolean,
    canGoForward: Boolean,
    isPageLoading: Boolean,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onHome: () -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    text: androidx.compose.ui.graphics.Color,
    muted: androidx.compose.ui.graphics.Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onGoBack,
            enabled = canGoBack,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("safe_browse_browser_back"),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = if (canGoBack) text else muted,
            )
        }

        IconButton(
            onClick = onGoForward,
            enabled = canGoForward,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("safe_browse_browser_forward"),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Forward",
                tint = if (canGoForward) text else muted,
            )
        }

        IconButton(
            onClick = onHome,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("safe_browse_browser_home"),
        ) {
            Icon(imageVector = Icons.Filled.Home, contentDescription = "Home", tint = text)
        }

        if (isPageLoading) {
            IconButton(
                onClick = onStop,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("safe_browse_browser_stop"),
            ) {
                Icon(imageVector = Icons.Filled.Close, contentDescription = "Stop", tint = text)
            }
        } else {
            IconButton(
                onClick = onReload,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("safe_browse_browser_reload"),
            ) {
                Icon(imageVector = Icons.Filled.Refresh, contentDescription = "Reload", tint = text)
            }
        }
    }
}

@Preview(name = "Browser - home ready", showBackground = true)
@Composable
private fun SafeBrowseBrowserScreenHomePreview() {
    ImpulsiveTheme(darkTheme = false) {
        SafeBrowseBrowserScreen(
            state = SafeBrowseBrowserUiState(
                policyStatus = SafeBrowsePolicyStatus.Ready,
                isHome = true,
            ),
            effects = kotlinx.coroutines.flow.emptyFlow(),
            evaluateNavigation = { null },
            onSearchChanged = {},
            onSearchSubmitted = {},
            onPageStarted = {},
            onPageFinished = { _, _, _, _ -> },
            onBlocked = { _, _ -> },
            onPageError = {},
            onDownloadBlocked = {},
            onPermissionBlocked = {},
            onRendererGone = {},
            onRetryPolicy = {},
            onGoBack = {},
            onGoForward = {},
            onReload = {},
            onStop = {},
            onHome = {},
            onExit = {},
        )
    }
}
