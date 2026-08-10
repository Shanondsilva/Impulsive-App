package com.impulsive.app.frontend.screens.safebrowse

import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SafeBrowseWebViewSecurityInstrumentedTest {

    @Test
    fun configureSafeBrowseWebViewAppliesEveryRestrictiveSetting() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var webView: WebView? = null

        try {
            instrumentation.runOnMainSync {
                val context = instrumentation.targetContext
                val view = WebView(context)
                view.configureSafeBrowseWebView(onDownloadBlocked = {})
                webView = view

                val settings = view.settings
                assertTrue(settings.javaScriptEnabled)
                // DOM storage is intentionally off; this asserted the opposite.
                assertFalse(settings.domStorageEnabled)
                assertFalse(settings.databaseEnabled)
                assertFalse(settings.allowFileAccess)
                assertFalse(settings.allowContentAccess)
                assertFalse(settings.allowFileAccessFromFileURLs)
                assertFalse(settings.allowUniversalAccessFromFileURLs)
                assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW, settings.mixedContentMode)
                assertFalse(settings.supportMultipleWindows())
                assertFalse(settings.javaScriptCanOpenWindowsAutomatically)
                assertTrue(settings.mediaPlaybackRequiresUserGesture)
                assertTrue(settings.safeBrowsingEnabled)
                assertTrue(settings.builtInZoomControls)
                assertFalse(settings.displayZoomControls)
                assertFalse(
                    CookieManager.getInstance().acceptThirdPartyCookies(view),
                )
            }
        } finally {
            instrumentation.runOnMainSync {
                webView?.destroySafeBrowseSession()
            }
        }
    }
}
