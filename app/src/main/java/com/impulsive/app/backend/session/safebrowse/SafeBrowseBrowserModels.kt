package com.impulsive.app.backend.session.safebrowse

import androidx.compose.runtime.Immutable
import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowseBlockedReason

@Immutable
sealed interface SafeBrowsePolicyStatus {
    data object Loading : SafeBrowsePolicyStatus

    data object Ready : SafeBrowsePolicyStatus

    data class Error(
        val message: String,
    ) : SafeBrowsePolicyStatus
}

@Immutable
data class SafeBrowseBlockedNotice(
    val reason: SafeBrowseBlockedReason,
    val displayHost: String?,
)

/**
 * UI-facing browser state.
 *
 * Deliberately excludes the complete current URL, browsing history and any
 * WebView/Context/Uri references. Only the normalized display host may be
 * retained.
 */
@Immutable
data class SafeBrowseBrowserUiState(
    val policyStatus: SafeBrowsePolicyStatus = SafeBrowsePolicyStatus.Loading,
    val searchText: String = "",
    val currentDisplayHost: String? = null,
    val pageTitle: String? = null,
    val isHome: Boolean = true,
    val isPageLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val blockedNotice: SafeBrowseBlockedNotice? = null,
    val errorMessage: String? = null,
    val rendererGeneration: Int = 0,
)

sealed interface SafeBrowseBrowserEffect {
    data class LoadUrl(
        val canonicalUrl: String,
    ) : SafeBrowseBrowserEffect

    data object GoBack : SafeBrowseBrowserEffect

    data object GoForward : SafeBrowseBrowserEffect

    data object Reload : SafeBrowseBrowserEffect

    data object StopLoading : SafeBrowseBrowserEffect

    data object ReturnHome : SafeBrowseBrowserEffect
}
