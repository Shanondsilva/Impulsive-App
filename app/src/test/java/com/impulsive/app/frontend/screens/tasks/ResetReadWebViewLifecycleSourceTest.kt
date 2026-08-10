package com.impulsive.app.frontend.screens.tasks

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * APP-007: the Reset Reading WebView must own and release its instance the same
 * way Safe Browse does.
 *
 * Renderer processes are shared, so a renderer crash anywhere can take this
 * WebView down too. Without a renderer callback the dead view stays in the
 * hierarchy and the next touch kills the process; without onRelease the
 * instance leaks. Both were missing here while Safe Browse already handled it.
 *
 * These are source locks, not behavioural proof -- renderer loss cannot be
 * induced from a JVM test. Device verification is still required.
 */
class ResetReadWebViewLifecycleSourceTest {

    private val resetRead = source(
        "frontend/screens/tasks/ResetReadScreen.kt",
    )
    private val safeBrowseScreen = source(
        "frontend/screens/safebrowse/SafeBrowseBrowserScreen.kt",
    )
    private val safeBrowseWebView = source(
        "frontend/screens/safebrowse/SafeBrowseWebView.kt",
    )

    @Test
    fun `Reset Reading handles renderer loss and returns true`() {
        assertTrue(resetRead.contains("override fun onRenderProcessGone("))
        assertTrue(resetRead.contains("import android.webkit.RenderProcessGoneDetail"))
        assertTrue(resetRead.contains("onRendererGone()"))

        /*
         * Returning false hands the dead renderer back to the system, which
         * kills the whole app process -- the reported shutdown.
         */
        val handler = Regex(
            """onRenderProcessGone\((?s).*?\{(.*?)\n    \}""",
        ).find(resetRead)?.groupValues?.get(1)
            ?: error("Unable to isolate onRenderProcessGone body.")

        assertTrue(handler.contains("return true"))
        assertFalse(handler.contains("return false"))
    }

    @Test
    fun `the renderer handler never touches the dead WebView`() {
        val handler = Regex(
            """onRenderProcessGone\((?s).*?\{(.*?)\n    \}""",
        ).find(resetRead)?.groupValues?.get(1)
            ?: error("Unable to isolate onRenderProcessGone body.")

        // Any call on a gone renderer can crash the process immediately.
        listOf(
            "view.reload(",
            "view.loadUrl(",
            "view.goBack(",
            "view.clearCache(",
            "view.destroy(",
            "view.stopLoading(",
        ).forEach {
            assertFalse("Dead WebView must not be used: $it", handler.contains(it))
        }
    }

    @Test
    fun `renderer loss replaces the instance rather than reusing it`() {
        assertTrue(resetRead.contains("rememberSaveable { mutableIntStateOf(0) }"))
        assertTrue(resetRead.contains("key(rendererGeneration)"))
        assertTrue(resetRead.contains("rendererGeneration += 1"))
    }

    @Test
    fun `the Reset Reading WebView is destroyed when it leaves composition`() {
        assertTrue(resetRead.contains("onRelease = { released -> released.destroyResetReadSession() }"))
        assertTrue(resetRead.contains("private fun WebView.destroyResetReadSession()"))
        assertTrue(resetReadDisposal().contains("destroy()"))
    }

    @Test
    fun `terminal release performs no operation before destroy`() {
        /*
         * APP-007H: destroy() is terminal, and by the time disposal runs the
         * renderer may already be gone -- any navigation, loading or history
         * call would then be made against a dead view.
         */
        val disposal = resetReadDisposal()

        listOf(
            "stopLoading(",
            "loadUrl(",
            "reload(",
            "clearHistory(",
            "clearCache(",
            "clearFormData(",
            "removeAllViews(",
        ).forEach {
            assertFalse("Disposal must only destroy, but calls $it", disposal.contains(it))
        }
    }

    private fun resetReadDisposal(): String = Regex(
        """fun WebView\.destroyResetReadSession\(\) \{(.*?)\n\}""",
        RegexOption.DOT_MATCHES_ALL,
    ).find(resetRead)?.groupValues?.get(1)
        ?: error("Unable to isolate destroyResetReadSession body.")

    @Test
    fun `disposal never clears storage shared with the rest of the app`() {
        // Lifecycle repair must not become a data-wiping workaround.
        listOf(
            "removeAllCookies",
            "WebStorage",
            "deleteAllData",
            "clearCache(true)",
        ).forEach {
            assertFalse("Global storage must not be cleared: $it", resetRead.contains(it))
        }
    }

    @Test
    fun `no WebView reference escapes the composition`() {
        listOf("companion object", "object ResetReadWebViewHolder", "@Volatile").forEach {
            assertFalse(resetRead.contains("$it\n    var webView"))
        }
        assertFalse(resetRead.contains("lateinit var webView"))
    }

    @Test
    fun `the trusted-host policy is unchanged`() {
        assertTrue(resetRead.contains("""ResetReadTrustedArticleHost = "useimpulsive.com""""))
        assertTrue(
            resetRead.contains("""uri.scheme.equals("https", ignoreCase = true)"""),
        )
        assertTrue(
            resetRead.contains(
                "uri.host.equals(ResetReadTrustedArticleHost, ignoreCase = true)",
            ),
        )
        assertTrue(resetRead.contains("override fun shouldOverrideUrlLoading("))
        assertTrue(resetRead.contains("override fun shouldInterceptRequest("))
    }

    @Test
    fun `Reset Reading browsing capabilities stay disabled`() {
        listOf(
            "javaScriptEnabled = false",
            "domStorageEnabled = false",
            "databaseEnabled = false",
            "allowFileAccess = false",
            "allowContentAccess = false",
            "allowFileAccessFromFileURLs = false",
            "allowUniversalAccessFromFileURLs = false",
            "MIXED_CONTENT_NEVER_ALLOW",
            "setSupportMultipleWindows(false)",
            "javaScriptCanOpenWindowsAutomatically = false",
        ).forEach {
            assertTrue("Reset Reading must keep: $it", resetRead.contains(it))
        }
    }

    @Test
    fun `Safe Browse renderer recovery is left intact`() {
        assertTrue(safeBrowseScreen.contains("key(state.rendererGeneration)"))
        assertTrue(safeBrowseScreen.contains("destroySafeBrowseSession()"))
        assertTrue(safeBrowseWebView.contains("override fun onRenderProcessGone("))
    }

    private fun source(relativePath: String): String = File(
        "src/main/java/com/impulsive/app/$relativePath",
    ).readText()
}
