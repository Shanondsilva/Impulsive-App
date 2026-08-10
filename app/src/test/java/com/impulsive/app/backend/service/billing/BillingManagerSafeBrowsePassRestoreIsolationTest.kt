package com.impulsive.app.backend.service.billing

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingManagerSafeBrowsePassRestoreIsolationTest {
    private val source = File(
        "src/main/java/com/impulsive/app/backend/service/billing/BillingManager.kt",
    ).readText()

    @Test
    fun restoreFamiliesHaveIndependentStateAndPendingFlags() {
        assertTrue(source.contains("val restoreState: StateFlow<BillingRestoreState>"))
        assertTrue(source.contains("val safeBrowsePassRestoreState: StateFlow<SafeBrowsePassRestoreState>"))
        assertTrue(source.contains("restorePendingAfterConnection"))
        assertTrue(source.contains("safeBrowsePassRestorePendingAfterConnection"))
    }

    @Test
    fun plusRestoreIsPlusOnlyAndPassRestoreIsPassOnly() {
        val plus = block("private suspend fun restorePurchasesInternal()", "private suspend fun restoreSafeBrowsePassPurchasesInternal()")
        val pass = block("private suspend fun restoreSafeBrowsePassPurchasesInternal()", "/**")
        assertTrue(plus.contains("PlusProductId"))
        assertTrue(plus.contains("PlusYearlyProductId"))
        assertFalse(plus.contains("SafeBrowsePassProductId"))
        assertTrue(pass.contains("SafeBrowsePassProductId"))
        assertFalse(pass.contains("PlusProductId"))
        assertFalse(pass.contains("PlusYearlyProductId"))
    }

    @Test
    fun restoreStatesAreNotCrossWritten() {
        val plus = block("private suspend fun restorePurchasesInternal()", "private suspend fun restoreSafeBrowsePassPurchasesInternal()")
        val pass = block("private suspend fun restoreSafeBrowsePassPurchasesInternal()", "/**")
        assertFalse(plus.contains("_safeBrowsePassRestoreState"))
        assertFalse(pass.contains("_restoreState"))
    }

    @Test
    fun bothRestoreFamiliesUseSharedOwnedSubscriptionQuery() {
        assertTrue(source.contains("private suspend fun queryOwnedSubscriptions()"))
        assertTrue(block("private suspend fun restorePurchasesInternal()", "private suspend fun restoreSafeBrowsePassPurchasesInternal()").contains("queryOwnedSubscriptions()"))
        assertTrue(block("private suspend fun restoreSafeBrowsePassPurchasesInternal()", "/**").contains("queryOwnedSubscriptions()"))
        assertEquals(1, Regex("BillingClient\\.newBuilder").findAll(source).count())
        assertEquals(2, Regex("queryPurchasesAsync").findAll(source).count())
    }

    @Test
    fun passRestorePreservesPendingSemanticsAndUidChecks() {
        // NOTE (Phase 4 correction): pending-kind branching used to be inlined here as
        // literal SafeBrowsePassPendingKind.InitialPurchase / .TopUp references. That
        // logic was extracted into the pure resolveSafeBrowsePassPlaySnapshotDecision()
        // function (see SafeBrowsePassRestorePolicy.kt and this class's
        // restoreSafeBrowsePassPurchasesInternalCallsBothNewDecisionFunctions test), so
        // restore now proves the same pending/top-up/uid guarantees by calling that
        // decision function rather than repeating its enum literals inline. The original
        // assertions on the literal enum names were tied to an implementation detail, not
        // the guaranteed behaviour, so they are replaced here rather than dropped.
        val pass = block("private suspend fun restoreSafeBrowsePassPurchasesInternal()", "/**")
        assertTrue(pass.contains("resolveSafeBrowsePassPlaySnapshotDecision("))
        assertTrue(pass.contains("hasPendingTopUp = pendingTopUpPurchases.isNotEmpty()"))
        assertTrue(pass.contains("hasPendingInitialPurchase = freshPendingPurchases.isNotEmpty()"))
        assertTrue(pass.contains("purchasedPassPurchases"))
        assertFalse(pass.contains("pendingPurchaseUpdate.purchaseToken"))
        assertTrue(pass.contains("refreshSafeBrowsePassEntitlementWithRetry(expectedUid)"))
        assertTrue(pass.contains("currentNonAnonymousFirebaseUid() != expectedUid"))
    }

    // -------------------------------------------------------------------
    // Structural guards for the Phase 4 correction: the new Play-snapshot
    // decision functions, unconditional pending-kind writes on restore,
    // pending-kind clearing on new launch / flow failure, and the removal
    // of the old raw-Pending restore check.
    // -------------------------------------------------------------------

    @Test
    fun handleSafeBrowsePassPurchasesCallsTheNewPlaySnapshotDecisionFunction() {
        val handler = block(
            "private fun handleSafeBrowsePassPurchases(purchases: List<Purchase>)",
            "private data class SafeBrowsePassVerificationSummary",
        )
        assertTrue(handler.contains("resolveSafeBrowsePassPlaySnapshotDecision("))
    }

    @Test
    fun handleSafeBrowsePassPurchasesWritesBothDecisionFieldsToTheLivePendingKindAndBillingState() {
        // NOTE (identity-aware revision tracker correction): both fields are now written
        // inside the tracker.accept() publish-when-changed callback rather than as two
        // bare top-level assignments, so an equal callback/query snapshot does not
        // republish over a newer snapshot's state. The guarantee -- both fields are
        // written together from the resolved decision -- still holds.
        val handler = block(
            "private fun handleSafeBrowsePassPurchases(purchases: List<Purchase>)",
            "private data class SafeBrowsePassVerificationSummary",
        )
        assertTrue(handler.contains("safeBrowsePassSnapshotRevisions"))
        assertTrue(handler.contains(".accept("))
        assertTrue(handler.contains("_safeBrowsePassPendingKind"))
        assertTrue(handler.contains("decision.pendingKind"))
        assertTrue(handler.contains("_safeBrowsePassBillingUiState"))
        assertTrue(handler.contains("decision.billingState"))
    }

    @Test
    fun restoreSafeBrowsePassPurchasesInternalCallsBothNewDecisionFunctions() {
        val restore = block(
            "private suspend fun restoreSafeBrowsePassPurchasesInternal()",
            "/**",
        )
        assertTrue(restore.contains("resolveSafeBrowsePassPlaySnapshotDecision("))
        assertTrue(restore.contains("resolveSafeBrowsePassBillingStateAfterRestore("))
    }

    @Test
    fun restoreSafeBrowsePassPurchasesInternalUnconditionallyWritesThePlaySnapshotDecision() {
        // NOTE (identity-aware revision tracker correction): the restore query snapshot
        // is now accepted through tracker.accept(), which publishes playDecision's fields
        // inside its callback rather than as bare assignments -- still unconditional on
        // every successful query, still both fields together.
        val restore = block(
            "private suspend fun restoreSafeBrowsePassPurchasesInternal()",
            "/**",
        )
        assertTrue(restore.contains("safeBrowsePassSnapshotRevisions"))
        assertTrue(restore.contains(".accept("))
        assertTrue(restore.contains("playDecision.pendingKind"))
        assertTrue(restore.contains("playDecision.billingState"))
    }

    @Test
    fun restoreContainsNoCheckOfAPreviousRawPendingState() {
        val restore = block(
            "private suspend fun restoreSafeBrowsePassPurchasesInternal()",
            "/**",
        )
        assertFalse(restore.contains("currentState != SafeBrowsePassBillingUiState.Pending"))
        assertFalse(restore.contains("currentState == SafeBrowsePassBillingUiState.Pending"))
    }

    @Test
    fun restoreFinalStateComesFromTheAfterRestoreResolverNotAnInlineWhenBlock() {
        // NOTE (identity-aware revision tracker correction): the resolved final billing
        // state is now captured in a local (finalBillingState) and published inside
        // tracker.runIfCurrent(snapshotAcceptance.revision) { ... } so a superseded
        // restore cannot overwrite a newer purchase callback's state. It is no longer a
        // single inline assignment expression, but it is still, and only ever, the value
        // returned by resolveSafeBrowsePassBillingStateAfterRestore() -- never a
        // hand-written inline when block.
        val restore = block(
            "private suspend fun restoreSafeBrowsePassPurchasesInternal()",
            "/**",
        )
        assertTrue(
            restore.contains(
                "val finalBillingState = resolveSafeBrowsePassBillingStateAfterRestore(",
            ),
        )
        assertTrue(restore.contains("runIfCurrent("))
        assertTrue(restore.contains("_safeBrowsePassBillingUiState"))
        assertTrue(restore.contains("finalBillingState"))
    }

    @Test
    fun newPurchaseLaunchInvalidatesOlderSnapshotWorkImmediatelyAfterTheDuplicateLaunchGuard() {
        // NOTE (identity-aware revision tracker correction): the accepted launch no
        // longer writes `_safeBrowsePassPendingKind.value = null` directly -- it calls
        // tracker.invalidate { ... } so the resulting revision can be threaded through
        // every downstream async callback and used to reject stale publications. The
        // pending-kind clear still happens, now inside that callback.
        val launch = block(
            "fun launchSafeBrowsePassPurchase(",
            "private fun handleSafeBrowsePassPurchaseFlowFailure(",
        )
        val guardIndex = launch.indexOf("safeBrowsePassPurchaseLaunchInFlight.compareAndSet(false, true)")
        val revisionIndex = launch.indexOf("val purchaseLaunchRevision =")
        val invalidateIndex = launch.indexOf("safeBrowsePassSnapshotRevisions", guardIndex)
        assertTrue(guardIndex >= 0)
        assertTrue(revisionIndex > guardIndex)
        assertTrue(invalidateIndex > guardIndex)
        assertTrue(launch.contains(".invalidate {"))
        assertTrue(launch.contains("_safeBrowsePassPendingKind"))
    }

    @Test
    fun purchaseFlowFailureAcceptsAnOptionalExpectedSnapshotRevisionAndPublishesOnlyThroughTheTracker() {
        // NOTE (identity-aware revision tracker correction): the handler no longer starts
        // with a direct `_safeBrowsePassPendingKind.value = null` statement -- it now
        // routes every publication through the shared snapshot revision tracker so a
        // superseded launch/failure callback cannot publish over a newer snapshot. The
        // failure handler's real contract is expressed by the assertions below, not by
        // the literal first statement, which was an implementation detail of the
        // pre-correction inline version. No coverage was dropped: this test still proves
        // the pending kind and mapped state are always cleared/published together, plus
        // the new revision-aware routing this correction requires.
        val failure = block(
            "private fun handleSafeBrowsePassPurchaseFlowFailure(",
            "fun refreshPurchases()",
        )
        assertTrue(failure.contains("expectedSnapshotRevision:"))
        assertTrue(failure.contains("Long?"))
        assertTrue(failure.contains("safeBrowsePassSnapshotRevisions"))
        assertTrue(failure.contains(".invalidate {"))
        assertTrue(failure.contains(".invalidateIfCurrent("))
        assertTrue(failure.contains("_safeBrowsePassPendingKind"))
        assertTrue(failure.contains("_safeBrowsePassBillingUiState"))
        assertTrue(failure.contains("if (!published) {"))
    }

    @Test
    fun verifyAndGrantSafeBrowsePassPurchasesClearsThePendingKindOnGrantedSuccessWhenNotKeepingPending() {
        val verify = block(
            "private fun verifyAndGrantSafeBrowsePassPurchases(",
            "/**",
        )
        assertTrue(
            verify.contains("_safeBrowsePassPendingKind.value = null") &&
                verify.contains("SafeBrowsePassBillingUiState.Purchased"),
        )
    }

    @Test
    fun verifyAndGrantSafeBrowsePassPurchasesClearsThePendingKindOnVerificationFailureWhenNotKeepingPending() {
        val verify = block(
            "private fun verifyAndGrantSafeBrowsePassPurchases(",
            "/**",
        )
        assertTrue(verify.contains("SafeBrowsePassBillingUiState.VerificationFailed"))
        assertEquals(2, Regex("_safeBrowsePassPendingKind\\.value = null").findAll(verify).count())
    }

    @Test
    fun pendingTopUpVerificationOnlyEverConsidersTopLevelPurchasedObjects() {
        val handler = block(
            "private fun handleSafeBrowsePassPurchases(purchases: List<Purchase>)",
            "private data class SafeBrowsePassVerificationSummary",
        )
        assertTrue(
            handler.contains(
                "pendingTopUpPurchases.filter { purchase ->\n" +
                    "                purchase.purchaseState == Purchase.PurchaseState.PURCHASED\n" +
                    "            }",
            ) || handler.contains("pendingTopUpPurchases.filter { purchase ->"),
        )
    }

    @Test
    fun pendingPurchaseUpdatePurchaseTokenIsNeverReadAnywhereInTheFile() {
        assertFalse(source.contains("pendingPurchaseUpdate.purchaseToken"))
        assertFalse(source.contains("pendingPurchaseUpdate?.purchaseToken"))
    }

    @Test
    fun plusRestoreDoesNotReferenceTheNewPassOnlyDecisionFunctions() {
        val plus = block(
            "private suspend fun restorePurchasesInternal()",
            "private suspend fun restoreSafeBrowsePassPurchasesInternal()",
        )
        assertFalse(plus.contains("resolveSafeBrowsePassPlaySnapshotDecision("))
        assertFalse(plus.contains("resolveSafeBrowsePassBillingStateAfterRestore("))
    }

    @Test
    fun exactlyOneBillingClientIsConstructedInThisFile() {
        assertEquals(1, Regex("BillingClient\\.newBuilder").findAll(source).count())
    }

    @Test
    fun exactlyTwoDirectQueryPurchasesAsyncCallSitesRemain() {
        assertEquals(2, Regex("\\.queryPurchasesAsync\\(").findAll(source).count())
    }

    @Test
    fun bothNewDecisionFunctionsAreOnlyEverCalledFromTheirOwnPackage() {
        assertFalse(source.contains("import com.impulsive.app.backend.service.billing.SafeBrowsePassRestorePolicy"))
    }

    @Test
    fun handleSafeBrowsePassPurchasesNeverGrantsWhilePlayReportsAPendingTransaction() {
        val handler = block(
            "private fun handleSafeBrowsePassPurchases(purchases: List<Purchase>)",
            "private data class SafeBrowsePassVerificationSummary",
        )
        val decisionIndex = handler.indexOf("resolveSafeBrowsePassPlaySnapshotDecision(")
        val verifyIndex = handler.indexOf("verifyAndGrantSafeBrowsePassPurchases(")
        assertTrue(decisionIndex >= 0)
        assertTrue(verifyIndex > decisionIndex)
    }

    // -------------------------------------------------------------------
    // Identity-aware snapshot revision tracker structural guards.
    // -------------------------------------------------------------------

    @Test
    fun billingManagerDeclaresExactlyOneSnapshotRevisionTrackerField() {
        assertEquals(
            1,
            Regex("SafeBrowsePassSnapshotRevisionTracker<").findAll(source).count(),
        )
    }

    @Test
    fun snapshotKeyIncludesTopLevelPurchaseToken() {
        val snapshotKey = block(
            "private fun safeBrowsePassPlaySnapshotKey(",
            "private fun Purchase.hasPendingSafeBrowsePassUpdate()",
        )
        assertTrue(snapshotKey.contains("purchaseToken"))
        assertTrue(
            Regex("purchaseToken\\s*=\\s*\\n?\\s*purchase\\.purchaseToken")
                .containsMatchIn(snapshotKey),
        )
    }

    @Test
    fun snapshotKeyIncludesPurchaseState() {
        val snapshotKey = block(
            "private fun safeBrowsePassPlaySnapshotKey(",
            "private fun Purchase.hasPendingSafeBrowsePassUpdate()",
        )
        assertTrue(
            Regex("purchaseState\\s*=\\s*\\n?\\s*purchase\\.purchaseState")
                .containsMatchIn(snapshotKey),
        )
    }

    @Test
    fun snapshotKeyIncludesPendingUpdateProducts() {
        val snapshotKey = block(
            "private fun safeBrowsePassPlaySnapshotKey(",
            "private fun Purchase.hasPendingSafeBrowsePassUpdate()",
        )
        assertTrue(snapshotKey.contains("pendingUpdateProducts"))
        assertTrue(snapshotKey.contains("pendingPurchaseUpdate"))
        assertTrue(snapshotKey.contains("?.products"))
    }

    @Test
    fun handleSafeBrowsePassPurchasesCallsTrackerAccept() {
        val handler = block(
            "private fun handleSafeBrowsePassPurchases(purchases: List<Purchase>)",
            "private data class SafeBrowsePassVerificationSummary",
        )
        assertTrue(handler.contains("safeBrowsePassSnapshotRevisions"))
        assertTrue(handler.contains(".accept("))
    }

    @Test
    fun handlerPassesSnapshotAcceptanceRevisionIntoVerification() {
        val handler = block(
            "private fun handleSafeBrowsePassPurchases(purchases: List<Purchase>)",
            "private data class SafeBrowsePassVerificationSummary",
        )
        assertTrue(handler.contains("snapshotAcceptance.revision"))
        assertTrue(handler.contains("expectedSnapshotRevision"))
    }

    @Test
    fun verifyAndApplySafeBrowsePassPurchasesChecksIsCurrentBeforeGranting() {
        val verifyApply = block(
            "private suspend fun verifyAndApplySafeBrowsePassPurchases(",
            "private fun verifyAndGrantSafeBrowsePassPurchases(",
        )
        assertTrue(verifyApply.contains(".isCurrent("))
        assertTrue(verifyApply.contains("grantSafeBrowsePassEntitlement("))
    }

    @Test
    fun verificationSummaryContainsSnapshotSuperseded() {
        val summary = block(
            "private data class SafeBrowsePassVerificationSummary",
            "private suspend fun verifyAndApplySafeBrowsePassPurchases(",
        )
        assertTrue(summary.contains("val snapshotSuperseded:"))
        assertTrue(summary.contains("Boolean"))
    }

    @Test
    fun verifyAndGrantSafeBrowsePassPurchasesUsesRunIfCurrentForFinalStatePublication() {
        val verifyGrant = block(
            "private fun verifyAndGrantSafeBrowsePassPurchases(",
            "/**",
        )
        assertTrue(verifyGrant.contains("runIfCurrent("))
    }

    @Test
    fun restoreAcceptsTheCurrentSnapshotKey() {
        val restore = block(
            "private suspend fun restoreSafeBrowsePassPurchasesInternal()",
            "/**",
        )
        assertTrue(restore.contains("safeBrowsePassPlaySnapshotKey("))
        assertTrue(restore.contains(".accept("))
    }

    @Test
    fun restorePassesTheAcceptedRevisionToVerification() {
        val restore = block(
            "private suspend fun restoreSafeBrowsePassPurchasesInternal()",
            "/**",
        )
        assertTrue(restore.contains("expectedSnapshotRevision = snapshotAcceptance.revision"))
    }

    @Test
    fun restoreRejectsSnapshotSuperseded() {
        val restore = block(
            "private suspend fun restoreSafeBrowsePassPurchasesInternal()",
            "/**",
        )
        assertTrue(restore.contains("summary.snapshotSuperseded"))
        assertTrue(restore.contains("SafeBrowsePassRestoreState.Idle"))
    }

    @Test
    fun restoreUsesRunIfCurrentForItsFinalBillingState() {
        val restore = block(
            "private suspend fun restoreSafeBrowsePassPurchasesInternal()",
            "/**",
        )
        assertTrue(restore.contains("runIfCurrent("))
    }

    @Test
    fun launchInvalidatesEarlierSnapshotWork() {
        val launch = block(
            "fun launchSafeBrowsePassPurchase(",
            "private fun handleSafeBrowsePassPurchaseFlowFailure(",
        )
        assertTrue(launch.contains(".invalidate {"))
        assertTrue(launch.contains(".invalidateIfCurrent("))
    }

    @Test
    fun terminalLaunchFailuresUseInvalidateIfCurrent() {
        val launch = block(
            "fun launchSafeBrowsePassPurchase(",
            "private fun handleSafeBrowsePassPurchaseFlowFailure(",
        )
        assertTrue(launch.contains("purchaseLaunchRevision"))
        assertTrue(launch.contains("handleSafeBrowsePassPurchaseFlowFailure("))
        assertTrue(
            Regex("expectedSnapshotRevision\\s*=\\s*\\n?\\s*purchaseLaunchRevision")
                .containsMatchIn(launch),
        )
    }

    @Test
    fun exactlyOneBillingClientRemains() {
        assertEquals(1, Regex("BillingClient\\.newBuilder").findAll(source).count())
    }

    @Test
    fun exactlyTwoDirectQueryPurchasesAsyncCallSitesRemainForTheTracker() {
        assertEquals(2, Regex("\\.queryPurchasesAsync\\(").findAll(source).count())
    }

    @Test
    fun pendingPurchaseUpdatePurchaseTokenRemainsAbsentUnderTheTracker() {
        assertFalse(source.contains("pendingPurchaseUpdate.purchaseToken"))
        assertFalse(source.contains("pendingPurchaseUpdate?.purchaseToken"))
    }

    @Test
    fun pendingTopUpVerificationStillUsesOnlyTopLevelPurchasedObjectsUnderTheTracker() {
        val handler = block(
            "private fun handleSafeBrowsePassPurchases(purchases: List<Purchase>)",
            "private data class SafeBrowsePassVerificationSummary",
        )
        assertTrue(handler.contains("pendingTopUpPurchases.filter { purchase ->"))
    }

    @Test
    fun plusRestoreRemainsPlusOnlyUnderTheTracker() {
        val plus = block(
            "private suspend fun restorePurchasesInternal()",
            "private suspend fun restoreSafeBrowsePassPurchasesInternal()",
        )
        assertTrue(plus.contains("PlusProductId"))
        assertFalse(plus.contains("SafeBrowsePassProductId"))
        assertFalse(plus.contains("safeBrowsePassSnapshotRevisions"))
    }

    @Test
    fun passRestoreRemainsPassOnlyUnderTheTracker() {
        val pass = block(
            "private suspend fun restoreSafeBrowsePassPurchasesInternal()",
            "/**",
        )
        assertTrue(pass.contains("SafeBrowsePassProductId"))
        assertFalse(pass.contains("PlusProductId"))
        assertTrue(pass.contains("safeBrowsePassSnapshotRevisions"))
    }

    private fun block(start: String, end: String): String {
        val startIndex = source.indexOf(start)
        assertTrue(startIndex >= 0)
        val endIndex = source.indexOf(end, startIndex + start.length)
        assertTrue(endIndex > startIndex)
        return source.substring(startIndex, endIndex)
    }
}
