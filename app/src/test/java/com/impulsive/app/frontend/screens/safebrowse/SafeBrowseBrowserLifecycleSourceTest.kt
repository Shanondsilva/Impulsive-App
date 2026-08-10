package com.impulsive.app.frontend.screens.safebrowse

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pre-major correction gate: the secured browser destination now waits for a resolved
 * access decision before ever creating a WebView, meters timed usage only while the
 * destination is genuinely foregrounded AND access is Active (never for a Pass, never while
 * merely composed), exits exactly once per Locked/Error/AccessExpired event, and discards a
 * stale pending URL rather than loading it once Home is reached.
 */
class SafeBrowseBrowserLifecycleSourceTest {
    private val source = File(
        "src/main/java/com/impulsive/app/frontend/screens/safebrowse/SafeBrowseBrowserScreen.kt",
    ).readText()

    private val routeBlock: String by lazy {
        val start = source.indexOf("internal fun SafeBrowseBrowserRoute(")
        assertTrue(start >= 0)
        val end = source.indexOf("@Composable\ninternal fun SafeBrowseBrowserScreen(", start)
        assertTrue(end > start)
        source.substring(start, end)
    }

    @Test
    fun destinationStartedFlagExists() {
        assertTrue(routeBlock.contains("var destinationStarted by remember"))
    }

    @Test
    fun timedUsageStartedFlagExists() {
        assertTrue(routeBlock.contains("var timedUsageStarted by remember { mutableStateOf(false) }"))
    }

    @Test
    fun exitRequestedFlagExists() {
        assertTrue(routeBlock.contains("var exitRequested by remember { mutableStateOf(false) }"))
    }

    @Test
    fun onStartNeverDirectlyInvokesBeginBrowserUsageInsideTheLifecycleObserver() {
        val observerStart = routeBlock.indexOf("val observer = LifecycleEventObserver")
        val observerEnd = routeBlock.indexOf("lifecycleOwner.lifecycle.addObserver(observer)", observerStart)
        val observerBlock = routeBlock.substring(observerStart, observerEnd)
        assertFalse(observerBlock.contains("accessViewModel.beginBrowserUsage()"))
        assertTrue(observerBlock.contains("Lifecycle.Event.ON_START -> destinationStarted = true"))
    }

    @Test
    fun activeStateBeginsUsageOnlyWhileTheDestinationIsStarted() {
        val activeIndex = routeBlock.indexOf("is DomainAccessState.Active -> {")
        assertTrue(activeIndex >= 0)
        val activeEnd = routeBlock.indexOf("is DomainAccessState.PassActive", activeIndex)
        val activeBlock = routeBlock.substring(activeIndex, activeEnd)
        assertTrue(activeBlock.contains("destinationStarted && !timedUsageStarted"))
        assertTrue(activeBlock.contains("accessViewModel.beginBrowserUsage()"))
    }

    @Test
    fun passActiveNeverBeginsTimedUsage() {
        val passActiveIndex = routeBlock.indexOf("is DomainAccessState.PassActive -> {")
        assertTrue(passActiveIndex >= 0)
        val passActiveEnd = routeBlock.indexOf("DomainAccessState.Locked,", passActiveIndex)
        val passActiveBlock = routeBlock.substring(passActiveIndex, passActiveEnd)
        assertFalse(passActiveBlock.contains("beginBrowserUsage"))
        assertTrue(passActiveBlock.contains("accessViewModel.endBrowserUsage()"))
    }

    @Test
    fun onStopEndsTimedUsageExactlyOnceGuardedByTheFlag() {
        val onStopIndex = routeBlock.indexOf("Lifecycle.Event.ON_STOP -> {")
        assertTrue(onStopIndex >= 0)
        val onStopEnd = routeBlock.indexOf("else -> Unit", onStopIndex)
        val onStopBlock = routeBlock.substring(onStopIndex, onStopEnd)
        assertTrue(onStopBlock.contains("if (timedUsageStarted)"))
        assertTrue(onStopBlock.contains("timedUsageStarted = false"))
        assertTrue(onStopBlock.contains("accessViewModel.endBrowserUsage()"))
    }

    @Test
    fun onDisposeEndsTimedUsageExactlyOnceGuardedByTheFlag() {
        val onDisposeIndex = routeBlock.indexOf("onDispose {")
        assertTrue(onDisposeIndex >= 0)
        val onDisposeEnd = routeBlock.indexOf("}\n    }", onDisposeIndex)
        val onDisposeBlock = routeBlock.substring(onDisposeIndex, onDisposeEnd)
        assertTrue(onDisposeBlock.contains("if (timedUsageStarted)"))
        assertTrue(onDisposeBlock.contains("accessViewModel.endBrowserUsage()"))
    }

    @Test
    fun loadingRendersTheAccessPreparingScreenWithItsTestTag() {
        assertTrue(routeBlock.contains("DomainAccessState.Loading ->"))
        assertTrue(routeBlock.contains("SafeBrowseBrowserAccessPreparingScreen(onExit = onExit)"))
        assertTrue(source.contains("\"safe_browse_browser_access_loading\""))
    }

    @Test
    fun loadingNeverRendersSafeBrowseBrowserScreenDirectly() {
        val loadingIndex = routeBlock.indexOf("DomainAccessState.Loading ->")
        val nextBranchIndex = routeBlock.indexOf("is DomainAccessState.Active,", loadingIndex)
        val loadingBlock = routeBlock.substring(loadingIndex, nextBranchIndex)
        assertFalse(loadingBlock.contains("SafeBrowseBrowserScreen("))
    }

    @Test
    fun lockedAndErrorRenderTheAccessPreparingScreenNotTheBrowser() {
        val lockedIndex = routeBlock.lastIndexOf("DomainAccessState.Locked,")
        assertTrue(lockedIndex >= 0)
        val lockedBlock = routeBlock.substring(lockedIndex)
        assertTrue(lockedBlock.contains("is DomainAccessState.Error,"))
        assertTrue(lockedBlock.contains("SafeBrowseBrowserAccessPreparingScreen(onExit = onExit)"))
        assertFalse(lockedBlock.contains("SafeBrowseBrowserScreen("))
    }

    @Test
    fun returnHomeSetsPendingUrlToNull() {
        val returnHomeIndex = source.indexOf("SafeBrowseBrowserEffect.ReturnHome -> {")
        assertTrue(returnHomeIndex >= 0)
        val returnHomeEnd = source.indexOf("}\n            }\n        }\n    }", returnHomeIndex)
        val returnHomeBlock = source.substring(returnHomeIndex, returnHomeEnd)
        assertTrue(returnHomeBlock.contains("pendingUrl = null"))
        assertTrue(returnHomeBlock.contains("webView?.stopLoading()"))
    }

    @Test
    fun pendingUrlIsDiscardedWhileStateIsHomeRatherThanLoaded() {
        val effectIndex = source.indexOf("LaunchedEffect(webView, state.isHome)")
        assertTrue(effectIndex >= 0)
        val effectEnd = source.indexOf("\n\n    Box(", effectIndex)
        val effectBlock = source.substring(effectIndex, effectEnd)
        assertTrue(effectBlock.contains("if (state.isHome) {"))
        assertTrue(effectBlock.contains("pendingUrl = null"))

        val homeCheckIndex = effectBlock.indexOf("if (state.isHome) {")
        val loadUrlIndex = effectBlock.indexOf("current.loadUrl(url)")
        assertTrue("loadUrl must appear after the isHome discard check", loadUrlIndex > homeCheckIndex)
    }

    @Test
    fun androidViewOnReleaseClearsExactlyTheOwnedWebViewInstance() {
        assertTrue(source.contains("onRelease = { released ->"))
        assertTrue(source.contains("if (webView === released) {"))
        assertTrue(source.contains("webView = null"))
    }

    @Test
    fun destroySafeBrowseSessionIsCalledFromOnRelease() {
        val onReleaseIndex = source.indexOf("onRelease = { released ->")
        val onReleaseEnd = source.indexOf("},", onReleaseIndex)
        val onReleaseBlock = source.substring(onReleaseIndex, onReleaseEnd)
        assertTrue(onReleaseBlock.contains("released.destroySafeBrowseSession()"))
    }

    @Test
    fun disposableEffectKeyedOnWebViewIsAbsent() {
        assertFalse(source.contains("DisposableEffect(webView)"))
    }

    @Test
    fun accessExpiredEffectChecksExitRequestedBeforeExiting() {
        val effectIndex = routeBlock.indexOf("SafeBrowseAccessEffect.AccessExpired && !exitRequested")
        assertTrue(effectIndex >= 0)
        val blockEnd = routeBlock.indexOf("}\n    }\n\n    val remainingTimeLabel", effectIndex)
        val block = routeBlock.substring(effectIndex, blockEnd)
        assertTrue(block.contains("exitRequested = true"))
        assertTrue(block.contains("onExit()"))
    }

    @Test
    fun lockedAndErrorCheckExitRequestedBeforeExiting() {
        val lockedIndex = routeBlock.indexOf("DomainAccessState.Locked,\n            is DomainAccessState.Error,")
        assertTrue(lockedIndex >= 0)
        val nextSectionIndex = routeBlock.indexOf("LaunchedEffect(accessViewModel)", lockedIndex)
        val block = routeBlock.substring(lockedIndex, nextSectionIndex)
        assertTrue(block.contains("if (!exitRequested) {"))
        assertTrue(block.contains("exitRequested = true"))
    }
}
