package com.impulsive.app.frontend.screens.safebrowse

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeBrowseWebViewSecuritySourceTest {
    private val root = File("src/main/java/com/impulsive/app/frontend/screens/safebrowse")
    private val webViewSource = File(root, "SafeBrowseWebView.kt").readText()
    private val browserScreenSource = File(root, "SafeBrowseBrowserScreen.kt").readText()

    @Test
    fun webViewConfigurationContainsEveryRequiredRestrictiveSetting() {
        listOf(
            "javaScriptEnabled = true",
            "domStorageEnabled = false",
            "databaseEnabled = false",
            "allowFileAccess = false",
            "allowContentAccess = false",
            "allowFileAccessFromFileURLs = false",
            "allowUniversalAccessFromFileURLs = false",
            "MIXED_CONTENT_NEVER_ALLOW",
            "setSupportMultipleWindows(false)",
            "javaScriptCanOpenWindowsAutomatically = false",
            "mediaPlaybackRequiresUserGesture = true",
            "safeBrowsingEnabled = true",
            "setGeolocationEnabled(false)",
            "setAcceptThirdPartyCookies",
            "removeJavascriptInterface",
            "handler.cancel()",
            "backToSafety",
            "request.deny()",
            "onShowFileChooser",
            "onCreateWindow",
            "shouldOverrideUrlLoading",
            "shouldInterceptRequest",
            "onRenderProcessGone",
            "destroySafeBrowseSession",
        ).forEach { required ->
            assertTrue("missing required setting: $required", webViewSource.contains(required))
        }
    }

    @Test
    fun webViewConfigurationContainsNoProhibitedBehaviour() {
        listOf(
            "addJavascriptInterface",
            "handler.proceed",
            "callback.proceed",
            "startActivity",
            "Intent(",
            "ACTION_VIEW",
            "setAcceptThirdPartyCookies(this, true)",
            "MIXED_CONTENT_ALWAYS_ALLOW",
            "allowFileAccess = true",
            "allowContentAccess = true",
            "domStorageEnabled = true",
            "WebView.setWebContentsDebuggingEnabled(true)",
        ).forEach { forbidden ->
            assertFalse("unexpectedly contains: $forbidden", webViewSource.contains(forbidden))
        }
    }

    @Test
    fun destroySafeBrowseSessionNeverTouchesProcessGlobalStorageOrCookies() {
        // WebStorage and CookieManager are process-wide singletons shared with every other
        // WebView in the app. Clearing them from a single Safe Browse session teardown would
        // silently destroy unrelated session state (for example an in-app auth WebView).
        assertFalse(webViewSource.contains("WebStorage"))
        assertFalse(webViewSource.contains("removeSessionCookies"))
    }

    @Test
    fun browserScreenLoadsOnlyEffectUrlsNeverTheRawSearchText() {
        assertTrue(browserScreenSource.contains("SafeBrowseBrowserEffect.LoadUrl"))
        assertFalse(browserScreenSource.contains("loadUrl(state.searchText"))
        assertFalse(browserScreenSource.contains("loadUrl(searchText"))
    }

    @Test
    fun browserScreenUsesDisposableEffectAndRendererGeneration() {
        assertTrue(browserScreenSource.contains("DisposableEffect"))
        assertTrue(browserScreenSource.contains("rendererGeneration"))
    }

    @Test
    fun browserScreenReleasesWebViewOwnershipThroughAndroidViewOnReleaseOnly() {
        // The WebView must be destroyed and its shared reference cleared from a single
        // onRelease callback tied to the exact view instance that is leaving composition --
        // never from a second DisposableEffect keyed on the mutable `webView` var, which can
        // race with a freshly created instance and destroy or route effects to a stale view.
        assertTrue(browserScreenSource.contains("onRelease = { released ->"))
        assertTrue(browserScreenSource.contains("if (webView === released)"))
        assertTrue(browserScreenSource.contains("webView = null"))
        assertTrue(browserScreenSource.contains("released.destroySafeBrowseSession()"))
        assertFalse(browserScreenSource.contains("DisposableEffect(webView)"))
    }

    @Test
    fun browserScreenContainsRequiredTestTagsAndBackHandler() {
        listOf(
            "safe_browse_search",
            "safe_browse_webview",
            "safe_browse_browser_back",
            "safe_browse_browser_forward",
            "safe_browse_browser_home",
            "safe_browse_browser_reload",
            "safe_browse_browser_stop",
        ).forEach { tag ->
            assertTrue("missing testTag($tag)", browserScreenSource.contains("\"$tag\""))
        }
        assertTrue(browserScreenSource.contains("BackHandler"))
    }

    @Test
    fun browserScreenContainsNoBrowsingHistoryPersistence() {
        listOf(
            "DataStore",
            "Room",
            "@Entity",
            "Dao",
            "SharedPreferences",
        ).forEach { forbidden ->
            assertFalse("unexpectedly contains: $forbidden", browserScreenSource.contains(forbidden))
        }
    }
}
