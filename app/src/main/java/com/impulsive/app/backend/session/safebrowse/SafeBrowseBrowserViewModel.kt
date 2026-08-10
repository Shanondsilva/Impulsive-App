package com.impulsive.app.backend.session.safebrowse

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.impulsive.app.backend.data.repository.SafeBrowsePolicyRepository
import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowseBlockedReason
import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowseNavigationDecision
import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowseNavigationPolicy
import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowsePolicySnapshot
import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowseSearchPolicy
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns Safe Browse browser UI state and the local navigation policy snapshot.
 *
 * The policy snapshot is kept only in memory here; it is never exposed
 * through [uiState], and the complete current URL is never retained anywhere
 * in this class.
 */
class SafeBrowseBrowserViewModel internal constructor(
    private val loadPolicySnapshot: suspend () -> SafeBrowsePolicySnapshot,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SafeBrowseBrowserUiState())
    val uiState: StateFlow<SafeBrowseBrowserUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<SafeBrowseBrowserEffect>(extraBufferCapacity = 4)
    val effects: SharedFlow<SafeBrowseBrowserEffect> = _effects

    private var policySnapshot: SafeBrowsePolicySnapshot? = null

    init {
        loadPolicy()
    }

    private fun loadPolicy() {
        policySnapshot = null
        _uiState.update { it.copy(policyStatus = SafeBrowsePolicyStatus.Loading) }

        viewModelScope.launch {
            runCatching { loadPolicySnapshot() }
                .onSuccess { snapshot ->
                    policySnapshot = snapshot
                    _uiState.update { it.copy(policyStatus = SafeBrowsePolicyStatus.Ready) }
                }
                .onFailure {
                    policySnapshot = null
                    _uiState.update {
                        it.copy(
                            policyStatus = SafeBrowsePolicyStatus.Error(
                                "Safe Browse protection could not be prepared.",
                            ),
                        )
                    }
                }
        }
    }

    fun retryPolicyLoad() {
        loadPolicy()
    }

    fun updateSearchText(value: String) {
        _uiState.update {
            it.copy(searchText = value.take(SafeBrowseSearchPolicy.MaximumQueryLength))
        }
    }

    fun submitSearch() {
        val snapshot = policySnapshot ?: return

        val searchUrl = SafeBrowseSearchPolicy.buildSearchUrl(_uiState.value.searchText)
        if (searchUrl == null) {
            _uiState.update { it.copy(errorMessage = "Enter something to search for.") }
            return
        }

        when (val decision = SafeBrowseNavigationPolicy.evaluate(searchUrl, snapshot)) {
            is SafeBrowseNavigationDecision.Allow -> {
                _uiState.update {
                    it.copy(
                        searchText = "",
                        blockedNotice = null,
                        errorMessage = null,
                        isHome = false,
                    )
                }
                _effects.tryEmit(
                    SafeBrowseBrowserEffect.LoadUrl(
                        SafeBrowseSearchPolicy.enforceSafeSearch(decision.canonicalUrl),
                    ),
                )
            }

            is SafeBrowseNavigationDecision.Block -> {
                _uiState.update {
                    it.copy(
                        blockedNotice = SafeBrowseBlockedNotice(
                            reason = decision.reason,
                            displayHost = decision.displayHost,
                        ),
                    )
                }
            }
        }
    }

    internal fun evaluateNavigation(rawUrl: String): SafeBrowseNavigationDecision? {
        val snapshot = policySnapshot ?: return null
        return SafeBrowseNavigationPolicy.evaluate(rawUrl, snapshot)
    }

    fun onPageStarted(displayHost: String) {
        _uiState.update {
            it.copy(
                isHome = false,
                currentDisplayHost = displayHost,
                isPageLoading = true,
                errorMessage = null,
            )
        }
    }

    fun onPageFinished(
        displayHost: String,
        title: String?,
        canGoBack: Boolean,
        canGoForward: Boolean,
    ) {
        val trimmedTitle = title?.trim()?.take(120)?.takeIf(String::isNotEmpty)
        _uiState.update {
            it.copy(
                currentDisplayHost = displayHost,
                pageTitle = trimmedTitle,
                canGoBack = canGoBack,
                canGoForward = canGoForward,
                isPageLoading = false,
            )
        }
    }

    fun onBlockedNavigation(reason: SafeBrowseBlockedReason, displayHost: String?) {
        _uiState.update {
            it.copy(
                blockedNotice = SafeBrowseBlockedNotice(reason = reason, displayHost = displayHost),
                isPageLoading = false,
            )
        }
    }

    fun onPageError(message: String) {
        _uiState.update { it.copy(errorMessage = message, isPageLoading = false) }
    }

    fun onDownloadBlocked() {
        _uiState.update {
            it.copy(errorMessage = "Downloads are unavailable in Safe Browse.")
        }
    }

    fun onPermissionBlocked() {
        _uiState.update {
            it.copy(errorMessage = "This website permission is unavailable in Safe Browse.")
        }
    }

    fun onRendererGone() {
        _uiState.update {
            it.copy(
                rendererGeneration = it.rendererGeneration + 1,
                isHome = true,
                currentDisplayHost = null,
                pageTitle = null,
                canGoBack = false,
                canGoForward = false,
                isPageLoading = false,
                blockedNotice = null,
                errorMessage = "The browser restarted safely.",
            )
        }
    }

    fun goBack() {
        _effects.tryEmit(SafeBrowseBrowserEffect.GoBack)
    }

    fun goForward() {
        _effects.tryEmit(SafeBrowseBrowserEffect.GoForward)
    }

    fun reload() {
        _effects.tryEmit(SafeBrowseBrowserEffect.Reload)
    }

    fun stopLoading() {
        _effects.tryEmit(SafeBrowseBrowserEffect.StopLoading)
    }

    fun returnHome() {
        _effects.tryEmit(SafeBrowseBrowserEffect.ReturnHome)
        _uiState.update {
            it.copy(
                isHome = true,
                currentDisplayHost = null,
                pageTitle = null,
                canGoBack = false,
                canGoForward = false,
                blockedNotice = null,
                errorMessage = null,
            )
        }
    }
}

class SafeBrowseBrowserViewModelFactory(
    private val context: Context,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val repository = SafeBrowsePolicyRepository(context.applicationContext)
        return SafeBrowseBrowserViewModel(repository::loadSnapshot) as T
    }
}
