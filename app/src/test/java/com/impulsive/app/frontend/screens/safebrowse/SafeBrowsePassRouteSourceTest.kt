package com.impulsive.app.frontend.screens.safebrowse

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeBrowsePassRouteSourceTest {
    private val routeSource = File(
        "src/main/java/com/impulsive/app/frontend/screens/safebrowse/SafeBrowsePassRoute.kt",
    ).readText()
    private val screenSource = File(
        "src/main/java/com/impulsive/app/frontend/screens/safebrowse/SafeBrowsePassScreen.kt",
    ).readText()

    @Test
    fun routeConsumesDedicatedViewModelAndLifecycleState() {
        assertTrue(routeSource.contains("passViewModel: SafeBrowsePassViewModel"))
        assertTrue(routeSource.contains("collectAsStateWithLifecycle"))
        assertFalse(routeSource.contains("billingManager: BillingManager"))
        assertFalse(routeSource.contains("accessViewModel: SafeBrowseAccessViewModel"))
        assertFalse(routeSource.contains("billingManager.safeBrowsePassCatalogState"))
        assertFalse(routeSource.contains("billingManager.safeBrowsePassBillingUiState"))
        assertFalse(routeSource.contains("billingManager.launchSafeBrowsePassPurchase"))
        assertFalse(routeSource.contains("billingManager.restore"))
    }

    @Test
    fun routeConnectsAccountGateForSafeBrowsePass() {
        assertTrue(routeSource.contains("PurchaseAccountGateDialog"))
        assertTrue(routeSource.contains("productName = \"Safe Browse Pass\""))
        assertTrue(routeSource.contains("purchaseAccountGatePhase"))
        assertTrue(routeSource.contains("durableAccountReady = true"))
    }

    @Test
    fun screenReceivesPlainStateAndCallbacksOnly() {
        assertTrue(screenSource.contains("state: SafeBrowsePassUiState"))
        assertTrue(screenSource.contains("onSelectPeriod"))
        assertTrue(screenSource.contains("state.selectedPeriod"))
        assertFalse(screenSource.contains("BillingManager"))
        assertFalse(screenSource.contains("FirebaseAuth"))
        assertFalse(screenSource.contains("SelectedSafeBrowsePassPlan"))
    }

    @Test
    fun screenContainsRequiredTestTagsForEveryAccessState() {
        listOf(
            "safe_browse_pass_back",
            "safe_browse_pass_heading",
            "safe_browse_pass_loading",
            "safe_browse_pass_active",
            "safe_browse_pass_offers",
            "safe_browse_pass_purchase",
            "safe_browse_pass_restore",
            "safe_browse_pass_manage",
            "safe_browse_pass_top_up",
            "safe_browse_pass_expiry",
            "safe_browse_pass_expired",
        ).forEach { tag ->
            assertTrue("missing testTag($tag)", screenSource.contains("\"$tag\""))
        }
    }

    // -------------------------------------------------------------------
    // Phase 5B: dual account-gated purchase actions and Manage subscription.
    // -------------------------------------------------------------------

    @Test
    fun ordinaryPurchaseAndPrepaidTopUpBothReuseTheSameAccountGate() {
        assertTrue(routeSource.contains("fun requestPurchaseAction("))
        assertTrue(
            Regex("PurchaseAccountGateDialog[\\s\\S]*?onDismiss").containsMatchIn(routeSource),
        )
        // Only one PurchaseAccountGateDialog call site exists, shared by both actions.
        assertEquals(1, Regex("PurchaseAccountGateDialog\\(").findAll(routeSource).count())
        assertTrue(routeSource.contains("requestPurchaseAction(prepaidTopUp = false)"))
        assertTrue(routeSource.contains("requestPurchaseAction(prepaidTopUp = true)"))
    }

    @Test
    fun thePendingActionIsClearedOnDismissal() {
        val dismissBlock = routeSource
            .substringAfter("onDismiss = {")
            .substringBefore("},\n        )")
        assertTrue(dismissBlock.contains("standardPurchasePendingAfterAccountGate = false"))
        assertTrue(dismissBlock.contains("prepaidTopUpPendingAfterAccountGate = false"))
        assertTrue(dismissBlock.contains("showAccountGate = false"))
    }

    @Test
    fun onlyReadyAutoLaunches() {
        val effectBlock = routeSource
            .substringAfter("LaunchedEffect(")
            .substringBefore("SafeBrowsePassScreen(")
        assertTrue(
            effectBlock.contains("purchaseAccountGatePhase != PurchaseAccountGatePhase.Ready"),
        )
        assertTrue(effectBlock.contains("return@LaunchedEffect"))
    }

    @Test
    fun routeCallsLaunchPrepaidTopUp() {
        assertTrue(routeSource.contains("passViewModel.launchPrepaidTopUp("))
    }

    @Test
    fun routeCallsManageSubscriptionUri() {
        assertTrue(routeSource.contains("passViewModel.manageSubscriptionUri()"))
    }

    @Test
    fun managementUriIsLaunchedThroughActionView() {
        val launcherBlock = routeSource
            .substringAfter("fun openSafeBrowsePassManagement(")
            .substringBefore("return runCatching")
        assertTrue(launcherBlock.contains("Intent.ACTION_VIEW"))
    }

    @Test
    fun managementLaunchValidatesHttpsAndPlayGoogleCom() {
        val launcherBlock = routeSource
            .substringAfter("fun openSafeBrowsePassManagement(")
            .substringBefore("return runCatching")
        assertTrue(launcherBlock.contains("uri.scheme != \"https\""))
        assertTrue(launcherBlock.contains("uri.host != \"play.google.com\""))
    }

    @Test
    fun routeShowsAStableErrorWhenManagementCannotOpen() {
        val onManageBlock = routeSource
            .substringAfter("onManageSubscription = {")
            .substringBefore("onRetry = passViewModel::refresh")
        assertTrue(onManageBlock.contains("!opened"))
        assertTrue(onManageBlock.contains("Toast.makeText("))
        assertTrue(onManageBlock.contains("Toast.LENGTH_SHORT"))
    }

    @Test
    fun noBillingManagerParameterOrDirectBillingCallWasAdded() {
        assertFalse(routeSource.contains("billingManager: BillingManager"))
        assertFalse(routeSource.contains("BillingManager("))
        assertFalse(routeSource.contains("launchSafeBrowsePassPurchase"))
    }

    @Test
    fun noActivityIsRetainedInAField() {
        assertFalse(routeSource.contains("private val activity"))
        assertFalse(routeSource.contains("private var activity"))
        assertFalse(routeSource.contains("val storedActivity"))
        assertFalse(routeSource.contains("var storedActivity"))
    }
}
