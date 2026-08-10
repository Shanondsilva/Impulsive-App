package com.impulsive.app.backend.data.local.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowsePassEntitlement
import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowsePassRenewalState
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

private const val TestOwnerUid = "test-safe-browse-user"

class SafeBrowsePassEntitlementDataSourceTest {
private val SafeBrowsePassEntitlementDataSource.entitlement: Flow<SafeBrowsePassEntitlement>
        get() = record.map { it.entitlement }

    private suspend fun SafeBrowsePassEntitlementDataSource.setEntitlement(
        entitlement: SafeBrowsePassEntitlement,
    ) {
        setEntitlement(
            ownerUid = TestOwnerUid,
            entitlement = entitlement,
        )
    }

    private fun newFile(): File {
        val directory = Files.createTempDirectory("safe-browse-pass-entitlement").toFile()
        return File(directory, "safe_browse_pass_entitlement.preferences_pb")
    }

    private fun newSource(file: File): Pair<SafeBrowsePassEntitlementDataSource, CoroutineScope> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        return SafeBrowsePassEntitlementDataSource(dataStore) to scope
    }

    private data class RawBackedSource(
        val source: SafeBrowsePassEntitlementDataSource,
        val dataStore: androidx.datastore.core.DataStore<Preferences>,
        val scope: CoroutineScope,
    )

    private fun newRawBackedSource(file: File): RawBackedSource {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        return RawBackedSource(
            source = SafeBrowsePassEntitlementDataSource(dataStore),
            dataStore = dataStore,
            scope = scope,
        )
    }

    @Test
    fun defaultEntitlementIsInactive() = runBlocking {
        val (source, scope) = newSource(newFile())
        try {
            assertEquals(SafeBrowsePassEntitlement(), source.entitlement.first())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun setEntitlementRoundTripsAllFields() = runBlocking {
        val (source, scope) = newSource(newFile())
        try {
            source.setEntitlement(
                SafeBrowsePassEntitlement(
                    active = true,
                    productId = "safe_browse_pass",
                    basePlanId = "monthly",
                    expiryTimeMillis = 123_456L,
                    isPrepaid = false,
                    lastVerifiedMillis = 999L,
                ),
            )

            assertEquals(
                SafeBrowsePassEntitlement(
                    active = true,
                    productId = "safe_browse_pass",
                    basePlanId = "monthly",
                    expiryTimeMillis = 123_456L,
                    isPrepaid = false,
                    lastVerifiedMillis = 999L,
                ),
                source.entitlement.first(),
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun setEntitlementRoundTripsPrepaid() = runBlocking {
        val (source, scope) = newSource(newFile())
        try {
            source.setEntitlement(
                SafeBrowsePassEntitlement(
                    active = true,
                    productId = "safe_browse_pass",
                    basePlanId = "prepaid-30",
                    expiryTimeMillis = 555L,
                    isPrepaid = true,
                    lastVerifiedMillis = 111L,
                ),
            )

            assertEquals(true, source.entitlement.first().isPrepaid)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun basePlanIdPersists() = runBlocking {
        val (source, scope) = newSource(newFile())
        try {
            source.setEntitlement(
                SafeBrowsePassEntitlement(
                    active = true,
                    productId = "safe_browse_pass",
                    basePlanId = "prepaid-30",
                    expiryTimeMillis = 123_456L,
                    lastVerifiedMillis = 999L,
                ),
            )

            assertEquals("prepaid-30", source.entitlement.first().basePlanId)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun nullBasePlanIdRemovesItsKey() = runBlocking {
        val (source, scope) = newSource(newFile())
        try {
            source.setEntitlement(
                SafeBrowsePassEntitlement(
                    active = true,
                    productId = "safe_browse_pass",
                    basePlanId = "monthly",
                    expiryTimeMillis = 123_456L,
                    lastVerifiedMillis = 999L,
                ),
            )
            assertEquals("monthly", source.entitlement.first().basePlanId)

            source.setEntitlement(
                SafeBrowsePassEntitlement(
                    active = true,
                    productId = "safe_browse_pass",
                    basePlanId = null,
                    expiryTimeMillis = 123_456L,
                    lastVerifiedMillis = 999L,
                ),
            )

            assertNull(source.entitlement.first().basePlanId)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun clearResetsToDefault() = runBlocking {
        val (source, scope) = newSource(newFile())
        try {
            source.setEntitlement(
                SafeBrowsePassEntitlement(
                    active = true,
                    productId = "safe_browse_pass",
                    basePlanId = "monthly",
                    expiryTimeMillis = 123_456L,
                    lastVerifiedMillis = 999L,
                ),
            )
            source.clear()

            val cleared = source.entitlement.first()
            assertFalse(cleared.active)
            assertEquals(SafeBrowsePassEntitlement(), cleared)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun noTokenOrderOrOfferDetailsArePersisted() {
        val source = File(
            "src/main/java/com/impulsive/app/backend/data/local/preferences/SafeBrowsePassEntitlementDataSource.kt",
        ).readText()
        listOf("purchaseToken", "orderId", "offerToken", "signature").forEach { sensitive ->
            assertFalse(
                "SafeBrowsePassEntitlementDataSource unexpectedly persists: $sensitive",
                source.contains(sensitive, ignoreCase = true),
            )
        }
    }

    @Test
    fun noRawSubscriptionStateStringIsPersisted() {
        val source = File(
            "src/main/java/com/impulsive/app/backend/data/local/preferences/SafeBrowsePassEntitlementDataSource.kt",
        ).readText()
        assertFalse(source.contains("subscriptionState"))
        listOf(
            "SUBSCRIPTION_STATE_ACTIVE",
            "SUBSCRIPTION_STATE_CANCELED",
            "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
        ).forEach { rawState ->
            assertFalse(
                "SafeBrowsePassEntitlementDataSource unexpectedly references raw state: $rawState",
                source.contains(rawState),
            )
        }
    }

    // -------------------------------------------------------------------
    // renewalState persistence
    // -------------------------------------------------------------------

    @Test
    fun renewalStateRoundTrips() = runBlocking {
        val (source, scope) = newSource(newFile())
        try {
            source.setEntitlement(
                SafeBrowsePassEntitlement(
                    active = true,
                    productId = "safe_browse_pass",
                    basePlanId = "monthly",
                    expiryTimeMillis = 123_456L,
                    isPrepaid = false,
                    renewalState = SafeBrowsePassRenewalState.CancelledUntilExpiry,
                    lastVerifiedMillis = 999L,
                ),
            )

            assertEquals(
                SafeBrowsePassRenewalState.CancelledUntilExpiry,
                source.entitlement.first().renewalState,
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun prepaidNotApplicableRoundTrips() = runBlocking {
        val (source, scope) = newSource(newFile())
        try {
            source.setEntitlement(
                SafeBrowsePassEntitlement(
                    active = true,
                    productId = "safe_browse_pass",
                    basePlanId = "prepaid-30",
                    expiryTimeMillis = 123_456L,
                    isPrepaid = true,
                    renewalState = SafeBrowsePassRenewalState.NotApplicable,
                    lastVerifiedMillis = 999L,
                ),
            )

            assertEquals(
                SafeBrowsePassRenewalState.NotApplicable,
                source.entitlement.first().renewalState,
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun missingLegacyRenewalKeyReadsUnknown() = runBlocking {
        val backed = newRawBackedSource(newFile())
        try {
            // Write every field except renewalState directly, simulating a pre-Phase-5A
            // cache record that has never seen the new preference key at all.
            backed.dataStore.edit { preferences ->
                preferences[stringPreferencesKey("safe_browse_pass_owner_uid")] = TestOwnerUid
                preferences[
                    androidx.datastore.preferences.core.booleanPreferencesKey(
                        "safe_browse_pass_active",
                    ),
                ] = true
                preferences[
                    stringPreferencesKey("safe_browse_pass_product_id"),
                ] = "safe_browse_pass"
                preferences[
                    stringPreferencesKey("safe_browse_pass_base_plan_id"),
                ] = "monthly"
                preferences[
                    androidx.datastore.preferences.core.longPreferencesKey(
                        "safe_browse_pass_expiry_millis",
                    ),
                ] = 123_456L
                preferences[
                    androidx.datastore.preferences.core.booleanPreferencesKey(
                        "safe_browse_pass_is_prepaid",
                    ),
                ] = false
                preferences[
                    androidx.datastore.preferences.core.longPreferencesKey(
                        "safe_browse_pass_last_verified_millis",
                    ),
                ] = 999L
                // Deliberately no safe_browse_pass_renewal_state key written.
            }

            assertEquals(
                SafeBrowsePassRenewalState.Unknown,
                backed.source.entitlement.first().renewalState,
            )
        } finally {
            backed.scope.cancel()
        }
    }

    @Test
    fun malformedStoredRenewalValueReadsUnknown() = runBlocking {
        val backed = newRawBackedSource(newFile())
        try {
            backed.source.setEntitlement(
                ownerUid = TestOwnerUid,
                entitlement = SafeBrowsePassEntitlement(
                    active = true,
                    productId = "safe_browse_pass",
                    basePlanId = "monthly",
                    expiryTimeMillis = 123_456L,
                    isPrepaid = false,
                    renewalState = SafeBrowsePassRenewalState.Renewing,
                    lastVerifiedMillis = 999L,
                ),
            )

            // Directly corrupt the stored renewal-state string to a value with no matching
            // enum constant, simulating a malformed or legacy-mismatched cache entry.
            backed.dataStore.edit { preferences ->
                preferences[
                    stringPreferencesKey("safe_browse_pass_renewal_state"),
                ] = "not-a-real-enum-value"
            }

            assertEquals(
                SafeBrowsePassRenewalState.Unknown,
                backed.source.entitlement.first().renewalState,
            )
        } finally {
            backed.scope.cancel()
        }
    }

    // -------------------------------------------------------------------
    // expireIfOwnedBy -- exact expiry preserves presentation metadata
    // -------------------------------------------------------------------

    @Test
    fun exactExpirySetsActiveFalse() = runBlocking {
        val (source, scope) = newSource(newFile())
        try {
            source.setEntitlement(activeAutoRenewingEntitlement())
            source.expireIfOwnedBy(expectedOwnerUid = TestOwnerUid, nowMillis = 500_000L)

            assertFalse(source.entitlement.first().active)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun exactExpiryPreservesProductId() = runBlocking {
        val (source, scope) = newSource(newFile())
        try {
            source.setEntitlement(activeAutoRenewingEntitlement())
            source.expireIfOwnedBy(expectedOwnerUid = TestOwnerUid, nowMillis = 500_000L)

            assertEquals("safe_browse_pass", source.entitlement.first().productId)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun exactExpiryPreservesBasePlanId() = runBlocking {
        val (source, scope) = newSource(newFile())
        try {
            source.setEntitlement(activeAutoRenewingEntitlement())
            source.expireIfOwnedBy(expectedOwnerUid = TestOwnerUid, nowMillis = 500_000L)

            assertEquals("monthly", source.entitlement.first().basePlanId)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun exactExpiryPreservesExpiryTimeMillis() = runBlocking {
        val (source, scope) = newSource(newFile())
        try {
            source.setEntitlement(activeAutoRenewingEntitlement())
            source.expireIfOwnedBy(expectedOwnerUid = TestOwnerUid, nowMillis = 500_000L)

            assertEquals(500_000L, source.entitlement.first().expiryTimeMillis)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun exactExpiryPreservesIsPrepaid() = runBlocking {
        val (source, scope) = newSource(newFile())
        try {
            source.setEntitlement(
                activeAutoRenewingEntitlement().copy(
                    isPrepaid = true,
                    basePlanId = "prepaid-30",
                    renewalState = SafeBrowsePassRenewalState.NotApplicable,
                ),
            )
            source.expireIfOwnedBy(expectedOwnerUid = TestOwnerUid, nowMillis = 500_000L)

            assertEquals(true, source.entitlement.first().isPrepaid)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun exactExpiryPreservesRenewalState() = runBlocking {
        val (source, scope) = newSource(newFile())
        try {
            source.setEntitlement(activeAutoRenewingEntitlement())
            source.expireIfOwnedBy(expectedOwnerUid = TestOwnerUid, nowMillis = 500_000L)

            assertEquals(
                SafeBrowsePassRenewalState.Renewing,
                source.entitlement.first().renewalState,
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun explicitClearStillRemovesEveryField() = runBlocking {
        val (source, scope) = newSource(newFile())
        try {
            source.setEntitlement(activeAutoRenewingEntitlement())
            source.expireIfOwnedBy(expectedOwnerUid = TestOwnerUid, nowMillis = 500_000L)
            source.clear()

            assertEquals(SafeBrowsePassEntitlement(), source.entitlement.first())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun accountMismatchClearStillRemovesEveryField() = runBlocking {
        val (source, scope) = newSource(newFile())
        try {
            source.setEntitlement(activeAutoRenewingEntitlement())
            source.expireIfOwnedBy(expectedOwnerUid = TestOwnerUid, nowMillis = 500_000L)

            source.clearUnlessOwnedBy("a-different-uid")

            assertEquals(SafeBrowsePassEntitlement(), source.entitlement.first())
        } finally {
            scope.cancel()
        }
    }

    private fun activeAutoRenewingEntitlement(): SafeBrowsePassEntitlement =
        SafeBrowsePassEntitlement(
            active = true,
            productId = "safe_browse_pass",
            basePlanId = "monthly",
            expiryTimeMillis = 500_000L,
            isPrepaid = false,
            renewalState = SafeBrowsePassRenewalState.Renewing,
            lastVerifiedMillis = 1_000L,
        )

    @Test
    fun persistsAcrossDataStoreRecreation() = runBlocking {
        val file = newFile()
        val (firstSource, firstScope) = newSource(file)
        firstSource.setEntitlement(
            SafeBrowsePassEntitlement(
                active = true,
                productId = "safe_browse_pass",
                basePlanId = "monthly",
                expiryTimeMillis = 42_000L,
                lastVerifiedMillis = 1_000L,
            ),
        )
        firstScope.cancel()
        firstScope.coroutineContext[Job]?.join()

        val (secondSource, secondScope) = newSource(file)
        try {
            assertEquals(42_000L, secondSource.entitlement.first().expiryTimeMillis)
            assertEquals("monthly", secondSource.entitlement.first().basePlanId)
        } finally {
            secondScope.cancel()
        }
    }
}
