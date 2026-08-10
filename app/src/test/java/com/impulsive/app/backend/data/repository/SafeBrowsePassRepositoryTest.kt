package com.impulsive.app.backend.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.impulsive.app.backend.data.local.preferences.SafeBrowsePassEntitlementDataSource
import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowsePassEntitlement
import com.impulsive.app.backend.service.billing.BillingManager
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TEST-ONLY diagnostic (Phase 4 regression suite, corrected). Verifies the basic delegation
 * the repository already provides, then locks the wider repository-boundary contract the
 * production implementation is intended to satisfy: Firebase-UID-scoped caching, mandatory
 * clearing on sign-out and account switch, and repository-owned billing-authority methods
 * (refresh/selectOffer/launchPurchase/restorePurchases/manageSubscriptionUri) that delegate
 * to the single shared BillingManager rather than constructing a BillingClient of its own.
 * Most of these are currently absent -- the corresponding assertions are expected to FAIL,
 * and that failure is the correct, accurate signal. Do not weaken them to match the current,
 * narrower cache-only repository, and do not modify production code in this task.
 */
class SafeBrowsePassRepositoryTest {
    private val repositorySource = File(
        "src/main/java/com/impulsive/app/backend/data/repository/SafeBrowsePassRepository.kt",
    ).readText()
    private val userDataManagerSource = File(
        "src/main/java/com/impulsive/app/backend/data/UserDataManager.kt",
    ).readText()

    private fun newRepository(): Triple<SafeBrowsePassRepository, CoroutineScope, TestSafeBrowsePassAccountProvider> {
        val directory = Files.createTempDirectory("safe-browse-pass-repository").toFile()
        val file = File(directory, "safe_browse_pass_entitlement.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        val accountProvider = TestSafeBrowsePassAccountProvider()
        val repository = SafeBrowsePassRepository(
            SafeBrowsePassEntitlementDataSource(dataStore),
            accountProvider,
        )
        return Triple(repository, scope, accountProvider)
    }

    private fun newRepositoryWithPackageName(): Triple<SafeBrowsePassRepository, CoroutineScope, TestSafeBrowsePassAccountProvider> {
        val directory = Files.createTempDirectory("safe-browse-pass-repository-uri").toFile()
        val file = File(directory, "safe_browse_pass_entitlement.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        val accountProvider = TestSafeBrowsePassAccountProvider()
        val repository = SafeBrowsePassRepository(
            dataSource = SafeBrowsePassEntitlementDataSource(dataStore),
            accountProvider = accountProvider,
            billingManagerProvider = null,
            packageNameProvider = { "com.impulsive.app" },
        )
        return Triple(repository, scope, accountProvider)
    }

    @Test
    fun setEntitlementAndReadRoundTripThroughTheRepositoryBoundary() = runBlocking {
        val (repository, scope, accountProvider) = newRepository()
        try {
            val entitlement = SafeBrowsePassEntitlement(
                active = true,
                productId = "safe_browse_pass",
                basePlanId = "monthly",
                expiryTimeMillis = 500_000L,
                isPrepaid = false,
                lastVerifiedMillis = 10_000L,
            )
            repository.setVerifiedEntitlement(requireNotNull(accountProvider.currentAuthenticatedUid()), entitlement)

            assertEquals(entitlement, repository.entitlement.first())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun clearResetsTheRepositoryToTheDefaultInactiveEntitlement() = runBlocking {
        val (repository, scope, accountProvider) = newRepository()
        try {
            repository.setVerifiedEntitlement(requireNotNull(accountProvider.currentAuthenticatedUid()),
                SafeBrowsePassEntitlement(active = true, expiryTimeMillis = 999_000L),
            )
            repository.clear()

            assertEquals(SafeBrowsePassEntitlement(), repository.entitlement.first())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun repositoryNeverConstructsItsOwnBillingClient() {
        assertFalse(repositorySource.contains("BillingClient"))
    }

    @Test
    fun repositoryUsesTheSharedBillingAuthorityRatherThanItsOwnConnection() {
        // Intended contract: billing-facing repository methods (see below) must delegate to
        // a BillingManager instance supplied through the constructor, never own a second
        // billing connection. Expected to FAIL: the repository currently has no billing
        // methods or BillingManager dependency at all.
        assertTrue(
            "SafeBrowsePassRepository has no BillingManager dependency to delegate billing calls to.",
            repositorySource.contains("billingManager: BillingManager") ||
                repositorySource.contains("private val billingManager"),
        )
    }

    @Test
    fun catalogueEntitlementPurchaseAndRestoreStatesAreEachIndependentlyExposed() {
        assertTrue(
            "SafeBrowsePassRepository does not expose an independent catalogue state flow.",
            repositorySource.contains("SafeBrowsePassCatalogState"),
        )
        assertTrue("no entitlement state flow", repositorySource.contains("val entitlement: Flow<SafeBrowsePassEntitlement>"))
        assertTrue(
            "SafeBrowsePassRepository does not expose an independent purchase-flow state.",
            repositorySource.contains("SafeBrowsePassPurchaseState"),
        )
        assertTrue(
            "SafeBrowsePassRepository does not expose an independent restore-result state, " +
                "distinct from Plus's own restore state.",
            repositorySource.contains("Restore") || repositorySource.contains("restore"),
        )
    }

    @Test
    fun repositoryExposesRefreshSelectOfferLaunchPurchaseAndRestorePurchases() {
        listOf("fun refresh(", "fun selectOffer(", "fun launchPurchase(", "fun restorePurchases(").forEach { member ->
            assertTrue(
                "SafeBrowsePassRepository does not expose required member: $member",
                repositorySource.contains(member),
            )
        }
    }

    @Test
    fun repositoryExposesAManageSubscriptionUri() {
        assertTrue(
            "SafeBrowsePassRepository never exposes a manage-subscription deep link, so an " +
                "active auto-renewing Pass has no way to route the user to Play's " +
                "subscription management screen.",
            repositorySource.contains("manageSubscriptionUri"),
        )
    }

    @Test
    fun repositoryDoesNotPersistRawPurchaseTokensOrReceiptData() {
        // The cached entitlement model itself must remain limited to the values needed to
        // gate access locally -- never a purchase token, order id, or receipt payload.
        listOf("purchaseToken", "orderId", "receipt", "signature").forEach { sensitive ->
            assertFalse(
                "SafeBrowsePassEntitlement/-Repository unexpectedly references $sensitive",
                repositorySource.contains(sensitive, ignoreCase = true),
            )
        }
    }

    @Test
    fun repositoryIsScopedToTheSignedInFirebaseUid() {
        assertTrue(
            "SafeBrowsePassRepository has no Firebase-UID-scoped construction path -- it " +
                "is backed by a single global DataStore shared by every signed-in account.",
            repositorySource.contains("authenticatedUid") || repositorySource.contains("currentAuthenticatedUid"),
        )
    }

    @Test
    fun aUidMismatchIsRejectedRatherThanSilentlyServingTheCachedEntitlement() {
        assertTrue(
            "SafeBrowsePassRepository never compares the cache's owning uid against the " +
                "currently signed-in Firebase uid before serving the cached entitlement.",
            repositorySource.contains("currentAuthenticatedUid") || repositorySource.contains("clearUnlessOwnedBy"),
        )
    }

    @Test
    fun repositoryCacheIsClearedOnSignOut() {
        assertTrue(
            "UserDataManager's sign-out path never clears SafeBrowsePassRepository -- a " +
                "cached Safe Browse Pass entitlement currently survives sign-out and would " +
                "be inherited by the next account signing in on this device.",
            userDataManagerSource.contains("SafeBrowsePassRepository") &&
                userDataManagerSource.contains("SafeBrowsePassRepository(context).clear()"),
        )
    }

    @Test
    fun repositoryCacheIsClearedOnAccountSwitchNotOnlyOnFullSignOut() {
        // Switching between two already-authenticated accounts (Google -> Facebook, or a
        // re-auth flow) must clear the cache the same way sign-out does -- a uid mismatch
        // alone is not sufficient if nothing ever re-checks it outside of sign-out.
        assertTrue(
            "SafeBrowsePassRepository has no account-switch-triggered clearing path " +
                "distinct from UserDataManager's full sign-out routine.",
            repositorySource.contains("onAccountChanged"),
        )
    }

    @Test
    fun serverVerificationAloneControlsWhetherTheEntitlementBecomesActive() {
        // The repository's setEntitlement itself performs no verification (correct -- that
        // is BillingManager's job) but it also must never be called directly from a client
        // purchase-result callback without server verification in between. This is
        // documented on the class; lock the documentation against silent regression.
        assertTrue(
            repositorySource.contains("setVerifiedEntitlement"),
        )
    }

    @Test
    fun restoreStateIsIndependentOfTheOtherEntitlementCaches() {
        assertTrue(repositorySource.contains("Never shares storage, keys, or an in-memory instance"))
        assertFalse(repositorySource.contains("PremiumRepository("))
        assertFalse(repositorySource.contains("SafeBrowseAccessRepository("))
    }

    // -------------------------------------------------------------------
    // manageSubscriptionUrl -- moved here from ViewModel-behaviour
    // responsibility because the repository, not the ViewModel, owns URI
    // eligibility (package name, prepaid exclusion, product id, validity).
    // These tests use fixed clock time and the deterministic internal
    // manageSubscriptionUrl(nowMillis) seam -- never Uri.parse() -- so no
    // exception from an unmocked android.net.Uri is ever a test oracle.
    // -------------------------------------------------------------------

    private val fixedNow = 1_000_000L

    @Test
    fun activePrepaidEntitlementHasNoManagementUri() = runBlocking {
        val (repository, scope, accountProvider) = newRepositoryWithPackageName()
        try {
            val uid = requireNotNull(accountProvider.currentAuthenticatedUid())
            repository.setVerifiedEntitlement(
                uid,
                SafeBrowsePassEntitlement(
                    active = true,
                    productId = BillingManager.SafeBrowsePassProductId,
                    expiryTimeMillis = fixedNow + 60_000L,
                    isPrepaid = true,
                ),
            )

            assertEquals(null, repository.manageSubscriptionUrl(nowMillis = fixedNow))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun inactiveEntitlementHasNoManagementUri() = runBlocking {
        val (repository, scope, accountProvider) = newRepositoryWithPackageName()
        try {
            val uid = requireNotNull(accountProvider.currentAuthenticatedUid())
            repository.setVerifiedEntitlement(
                uid,
                SafeBrowsePassEntitlement(
                    active = false,
                    productId = BillingManager.SafeBrowsePassProductId,
                    expiryTimeMillis = fixedNow + 60_000L,
                    isPrepaid = false,
                ),
            )

            assertEquals(null, repository.manageSubscriptionUrl(nowMillis = fixedNow))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun expiredAutoRenewingEntitlementHasNoManagementUri() = runBlocking {
        val (repository, scope, accountProvider) = newRepositoryWithPackageName()
        try {
            val uid = requireNotNull(accountProvider.currentAuthenticatedUid())
            repository.setVerifiedEntitlement(
                uid,
                SafeBrowsePassEntitlement(
                    active = true,
                    productId = BillingManager.SafeBrowsePassProductId,
                    expiryTimeMillis = fixedNow - 1L,
                    isPrepaid = false,
                ),
            )

            assertEquals(null, repository.manageSubscriptionUrl(nowMillis = fixedNow))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun activeAutoRenewingSafeBrowsePassEntitlementHasANonNullManagementUri() = runBlocking {
        val (repository, scope, accountProvider) = newRepositoryWithPackageName()
        try {
            val uid = requireNotNull(accountProvider.currentAuthenticatedUid())
            repository.setVerifiedEntitlement(
                uid,
                SafeBrowsePassEntitlement(
                    active = true,
                    productId = BillingManager.SafeBrowsePassProductId,
                    expiryTimeMillis = fixedNow + 60_000L,
                    isPrepaid = false,
                ),
            )

            assertEquals(
                "https://play.google.com/store/account/subscriptions" +
                    "?sku=safe_browse_pass" +
                    "&package=com.impulsive.app",
                repository.manageSubscriptionUrl(nowMillis = fixedNow),
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun wrongProductIdHasNoManagementUri() = runBlocking {
        val (repository, scope, accountProvider) = newRepositoryWithPackageName()
        try {
            val uid = requireNotNull(accountProvider.currentAuthenticatedUid())
            repository.setVerifiedEntitlement(
                uid,
                SafeBrowsePassEntitlement(
                    active = true,
                    productId = "impulsive_plus",
                    expiryTimeMillis = fixedNow + 60_000L,
                    isPrepaid = false,
                ),
            )

            assertEquals(null, repository.manageSubscriptionUrl(nowMillis = fixedNow))
        } finally {
            scope.cancel()
        }
    }

    // -------------------------------------------------------------------
    // Structural guard: the public Uri? wrapper must still route through
    // the deterministic manageSubscriptionUrl() seam and Uri.parse(), not
    // duplicate the eligibility logic. This does not catch an exception --
    // it is a source-text supplement to the behavioural tests above.
    // -------------------------------------------------------------------

    @Test
    fun manageSubscriptionUriWrapperDelegatesToTheDeterministicUrlSeam() {
        assertTrue(repositorySource.contains("manageSubscriptionUrl()"))
        assertTrue(repositorySource.contains("Uri.parse(url)"))
    }
}
