package com.impulsive.app.frontend.screens.safebrowse

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * APP-007H: Safe Browse disposal must release only its own instance.
 *
 * The helper previously called `clearCache(true)`, but WebView's resource cache
 * is per-application, so every Safe Browse WebView leaving composition wiped the
 * cache of unrelated WebViews too. Safe Browse already runs with LOAD_NO_CACHE
 * and so has nothing of its own to clear.
 */
class SafeBrowseWebViewDisposalSourceTest {

    private val webView = source("SafeBrowseWebView.kt")
    private val browserScreen = source("SafeBrowseBrowserScreen.kt")

    private fun disposalBody(): String = Regex(
        """fun WebView\.destroySafeBrowseSession\(\) \{(.*?)\n\}""",
        RegexOption.DOT_MATCHES_ALL,
    ).find(webView)?.groupValues?.get(1)
        ?: error("Unable to isolate destroySafeBrowseSession body.")

    @Test
    fun `disposal destroys the instance`() {
        assertTrue(disposalBody().contains("destroy()"))
    }

    @Test
    fun `disposal performs no operation before destroy`() {
        val disposal = disposalBody()

        listOf(
            "stopLoading(",
            "clearHistory(",
            "clearCache(",
            "clearFormData(",
            "loadUrl(",
            "reload(",
            "removeAllViews(",
        ).forEach {
            assertFalse("Disposal must only destroy, but calls $it", disposal.contains(it))
        }

        // destroy() is terminal, so it must be the only statement present.
        assertEquals(
            listOf("destroy()"),
            disposal.lines().map(String::trim).filter(String::isNotEmpty),
        )
    }

    @Test
    fun `no application-wide WebView state is cleared during disposal`() {
        val disposal = disposalBody()

        listOf(
            "CookieManager",
            "removeAllCookies",
            "WebStorage",
            "deleteAllData",
            "WebViewDatabase",
            "clearHttpAuthUsernamePassword",
        ).forEach {
            assertFalse("Disposal must not touch $it", disposal.contains(it))
        }

        // No Safe Browse path may clear the app-wide resource cache.
        assertFalse(webView.contains("clearCache("))
    }

    @Test
    fun `the false per-instance cache comment is gone`() {
        assertFalse(webView.contains("Per-instance\n * cache"))
        assertTrue(
            webView.contains("SAFE BROWSE DISPOSAL MUST NOT CLEAR APPLICATION-WIDE WEBVIEW STATE."),
        )
    }

    @Test
    fun `Safe Browse still disables its own caching`() {
        assertTrue(webView.contains("settings.cacheMode = WebSettings.LOAD_NO_CACHE"))
    }

    @Test
    fun `third-party cookies remain rejected`() {
        // This is configuration, not disposal, and must survive the change.
        assertTrue(
            webView.contains(
                "CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)",
            ),
        )
    }

    @Test
    fun `renderer loss returns true without touching the dead view`() {
        val handler = Regex(
            """override fun onRenderProcessGone\((?s).*?\{(.*?)\n    \}""",
        ).find(webView)?.groupValues?.get(1)
            ?: error("Unable to isolate onRenderProcessGone body.")

        assertTrue(handler.contains("return true"))
        assertFalse(handler.contains("return false"))
        assertFalse(handler.contains("view."))
    }

    @Test
    fun `onRelease clears the shared reference only for the released instance`() {
        assertTrue(browserScreen.contains("onRelease = { released ->"))
        assertTrue(browserScreen.contains("if (webView === released) {"))
        assertTrue(browserScreen.contains("webView = null"))
        assertTrue(browserScreen.contains("released.destroySafeBrowseSession()"))

        // The reference must stop being authoritative before destruction.
        val release = browserScreen.substringAfter("onRelease = { released ->")
        assertTrue(
            release.indexOf("webView = null") < release.indexOf("destroySafeBrowseSession"),
        )
    }

    @Test
    fun `renderer recovery still replaces the keyed WebView`() {
        assertTrue(browserScreen.contains("key(state.rendererGeneration)"))
    }

    private fun source(fileName: String): String = File(
        "src/main/java/com/impulsive/app/frontend/screens/safebrowse/$fileName",
    ).readText()
}
