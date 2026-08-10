package com.impulsive.app.frontend.screens.safebrowse

import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Message
import android.view.View
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowseBlockedReason
import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowseNavigationDecision
import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowseSearchPolicy
import java.io.ByteArrayInputStream

/**
 * Applies every restrictive Safe Browse WebView setting.
 *
 * Only used by the Safe Browse embedded browser. Nothing here loads content,
 * grants persistent storage of browsing data, or exposes a native JavaScript
 * bridge.
 */
@Suppress("DEPRECATION")
internal fun WebView.configureSafeBrowseWebView(
    onDownloadBlocked: () -> Unit,
) {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = false
    settings.databaseEnabled = false
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.allowFileAccessFromFileURLs = false
    settings.allowUniversalAccessFromFileURLs = false
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    settings.setSupportMultipleWindows(false)
    settings.javaScriptCanOpenWindowsAutomatically = false
    settings.mediaPlaybackRequiresUserGesture = true
    settings.safeBrowsingEnabled = true
    settings.setGeolocationEnabled(false)
    settings.builtInZoomControls = true
    settings.displayZoomControls = false
    settings.cacheMode = WebSettings.LOAD_NO_CACHE
    settings.disabledActionModeMenuItems =
        WebSettings.MENU_ITEM_SHARE or
            WebSettings.MENU_ITEM_WEB_SEARCH or
            WebSettings.MENU_ITEM_PROCESS_TEXT

    isSaveEnabled = false
    isLongClickable = false
    setOnLongClickListener { true }
    importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS

    removeJavascriptInterface("searchBoxJavaBridge_")
    removeJavascriptInterface("accessibility")
    removeJavascriptInterface("accessibilityTraversal")

    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)

    setDownloadListener { _, _, _, _, _ ->
        onDownloadBlocked()
    }
}

internal fun emptySafeBrowseBlockedResponse(): WebResourceResponse =
    WebResourceResponse(
        "text/plain",
        "utf-8",
        ByteArrayInputStream(ByteArray(0)),
    )

/**
 * Routes every main-frame navigation and subresource request through the
 * Safe Browse navigation policy before it is ever loaded.
 */
internal class SafeBrowseWebViewClient(
    private val evaluateNavigation: (String) -> SafeBrowseNavigationDecision?,
    private val onPageStartedSafely: (String) -> Unit,
    private val onPageFinishedSafely: (
        displayHost: String,
        title: String?,
        canGoBack: Boolean,
        canGoForward: Boolean,
    ) -> Unit,
    private val onBlocked: (
        reason: SafeBrowseBlockedReason,
        displayHost: String?,
    ) -> Unit,
    private val onError: (String) -> Unit,
    private val onRendererGone: () -> Unit,
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean {
        val requestUrl = request.url.toString()

        return when (val decision = evaluateNavigation(requestUrl)) {
            null -> {
                view.stopLoading()
                onError("Safe Browse protection is not ready.")
                true
            }

            is SafeBrowseNavigationDecision.Block -> {
                view.stopLoading()
                onBlocked(decision.reason, decision.displayHost)
                true
            }

            is SafeBrowseNavigationDecision.Allow -> {
                val securedUrl = SafeBrowseSearchPolicy.enforceSafeSearch(decision.canonicalUrl)
                if (securedUrl != requestUrl) {
                    view.loadUrl(securedUrl)
                    true
                } else {
                    false
                }
            }
        }
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        val requestUrl = request.url.toString()
        val decision = evaluateNavigation(requestUrl)

        if (decision !is SafeBrowseNavigationDecision.Allow) {
            if (request.isForMainFrame) {
                val reason = (decision as? SafeBrowseNavigationDecision.Block)?.reason
                    ?: SafeBrowseBlockedReason.InvalidUrl
                val displayHost = (decision as? SafeBrowseNavigationDecision.Block)?.displayHost
                view.post { onBlocked(reason, displayHost) }
            }
            return emptySafeBrowseBlockedResponse()
        }

        return super.shouldInterceptRequest(view, request)
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        val decision = url?.let(evaluateNavigation)
        if (decision is SafeBrowseNavigationDecision.Allow) {
            onPageStartedSafely(decision.displayHost)
        }
    }

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        val decision = url?.let(evaluateNavigation)
        if (decision is SafeBrowseNavigationDecision.Allow) {
            onPageFinishedSafely(
                decision.displayHost,
                view.title,
                view.canGoBack(),
                view.canGoForward(),
            )
        }
    }

    override fun onReceivedSslError(
        view: WebView,
        handler: SslErrorHandler,
        error: SslError,
    ) {
        handler.cancel()
        onError("The website's secure connection could not be verified.")
    }

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    override fun onSafeBrowsingHit(
        view: WebView,
        request: WebResourceRequest,
        threatType: Int,
        callback: SafeBrowsingResponse,
    ) {
        callback.backToSafety(true)
        onError("Android Safe Browsing blocked a dangerous page.")
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) {
        super.onReceivedError(view, request, error)
        if (request.isForMainFrame) {
            onError("This page could not be loaded.")
        }
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse,
    ) {
        super.onReceivedHttpError(view, request, errorResponse)
        if (request.isForMainFrame && errorResponse.statusCode >= 400) {
            onError("The website returned an error.")
        }
    }

    override fun onRenderProcessGone(
        view: WebView,
        detail: RenderProcessGoneDetail,
    ): Boolean {
        onRendererGone()
        return true
    }
}

/**
 * Denies every browser feature Safe Browse does not support: camera,
 * microphone, location, file selection and pop-up/second windows.
 */
internal class SafeBrowseWebChromeClient(
    private val onPermissionBlocked: () -> Unit,
) : WebChromeClient() {

    override fun onPermissionRequest(request: PermissionRequest) {
        request.deny()
        onPermissionBlocked()
    }

    override fun onGeolocationPermissionsShowPrompt(
        origin: String?,
        callback: GeolocationPermissions.Callback?,
    ) {
        callback?.invoke(origin, false, false)
        onPermissionBlocked()
    }

    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?,
    ): Boolean {
        onPermissionBlocked()
        return false
    }

    override fun onCreateWindow(
        view: WebView?,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message?,
    ): Boolean = false
}

/**
 * Permanently releases an owned Safe Browse WebView after it has left the
 * Compose hierarchy. Never call this while another Safe Browse WebView is
 * still active.
 *
 * SAFE BROWSE DISPOSAL MUST NOT CLEAR APPLICATION-WIDE WEBVIEW STATE.
 * WebView's resource cache is per-application, not per-instance, so the
 * cache-clearing call this once made wiped the cache of every WebView in the
 * app. Safe Browse already runs with [WebSettings.LOAD_NO_CACHE], so it has
 * nothing of its own to clear. The same applies to the process-wide cookie,
 * web-storage and form-database singletons, which unrelated features share.
 *
 * `destroy()` is the terminal operation and no WebView method may be called
 * after it. Nothing precedes it here: history and form data belong to the
 * instance being destroyed, and skipping any navigation or loading call keeps
 * this safe to run on a WebView whose renderer has already terminated.
 */
internal fun WebView.destroySafeBrowseSession() {
    destroy()
}
