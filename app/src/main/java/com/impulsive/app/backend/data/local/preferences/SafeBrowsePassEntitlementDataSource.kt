package com.impulsive.app.backend.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowsePassEntitlement
import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowsePassRenewalState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Deliberately excluded from backup_rules.xml / data_extraction_rules.xml (matching
// premium_entitlement): Google Play re-verifies server-side, so restoring a stale cached
// value onto a new device would only ever be immediately overwritten or downgraded.
private val Context.safeBrowsePassEntitlementDataStore by
    preferencesDataStore(name = "safe_browse_pass_entitlement")

/**
 * Caches the server-verified Safe Browse Pass entitlement. Entirely separate storage from
 * both the Impulsive Plus entitlement cache and the timed Safe Browse reward ledger --
 * never shares a file, a key, or an in-memory instance with either.
 */
class SafeBrowsePassEntitlementDataSource internal constructor(
    private val dataStore: DataStore<Preferences>,
) {
    constructor(context: Context) : this(
        context.applicationContext.safeBrowsePassEntitlementDataStore,
    )

    internal data class SafeBrowsePassEntitlementRecord(
        val ownerUid: String?,
        val entitlement: SafeBrowsePassEntitlement,
    )

    internal val record: Flow<SafeBrowsePassEntitlementRecord> = dataStore.data.map { preferences ->
        SafeBrowsePassEntitlementRecord(
            ownerUid =
                preferences[OwnerUidKey]
                    ?.trim()
                    ?.takeIf(String::isNotBlank),
            entitlement =
                SafeBrowsePassEntitlement(
                    active = preferences[ActiveKey] ?: false,
                    productId = preferences[ProductIdKey],
                    basePlanId = preferences[BasePlanIdKey],
                    expiryTimeMillis = preferences[ExpiryKey] ?: 0L,
                    isPrepaid = preferences[IsPrepaidKey] ?: false,
                    renewalState =
                        renewalStateFromStoredValue(
                            preferences[
                                RenewalStateKey
                            ],
                        ),
                    lastVerifiedMillis = preferences[LastVerifiedKey] ?: 0L,
                ),
        )
    }

    private fun renewalStateFromStoredValue(
        value: String?,
    ): SafeBrowsePassRenewalState =
        SafeBrowsePassRenewalState
            .entries
            .firstOrNull { state ->
                state.name == value
            }
            ?: SafeBrowsePassRenewalState
                .Unknown

    internal suspend fun currentRecord(): SafeBrowsePassEntitlementRecord = record.first()

    internal suspend fun setEntitlement(
        ownerUid: String,
        entitlement: SafeBrowsePassEntitlement,
    ) {
        val normalisedUid =
            ownerUid
                .trim()
                .takeIf(String::isNotBlank)
                ?: throw IllegalArgumentException("Safe Browse Pass owner UID must not be blank.")

        dataStore.edit { preferences ->
            preferences[OwnerUidKey] = normalisedUid
            preferences[ActiveKey] = entitlement.active
            entitlement.productId
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?.let { preferences[ProductIdKey] = it }
                ?: preferences.remove(ProductIdKey)
            entitlement.basePlanId
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?.let { preferences[BasePlanIdKey] = it }
                ?: preferences.remove(BasePlanIdKey)
            preferences[ExpiryKey] = entitlement.expiryTimeMillis.coerceAtLeast(0L)
            preferences[IsPrepaidKey] = entitlement.isPrepaid
            preferences[RenewalStateKey] = entitlement.renewalState.name
            preferences[LastVerifiedKey] = entitlement.lastVerifiedMillis.coerceAtLeast(0L)
        }
    }

    internal suspend fun clearUnlessOwnedBy(expectedOwnerUid: String?): Boolean {
        val normalisedExpectedUid =
            expectedOwnerUid
                ?.trim()
                ?.takeIf(String::isNotBlank)

        var cleared = false

        dataStore.edit { preferences ->
            val storedOwnerUid =
                preferences[OwnerUidKey]
                    ?.trim()
                    ?.takeIf(String::isNotBlank)

            if (
                normalisedExpectedUid == null ||
                storedOwnerUid == null ||
                storedOwnerUid != normalisedExpectedUid
            ) {
                if (preferences.asMap().isNotEmpty()) {
                    preferences.clear()
                    cleared = true
                }
            }
        }

        return cleared
    }

    internal suspend fun expireIfOwnedBy(
        expectedOwnerUid: String,
        nowMillis: Long,
    ): Boolean {
        val normalisedUid =
            expectedOwnerUid
                .trim()
                .takeIf(String::isNotBlank)
                ?: return false

        var expired = false

        dataStore.edit { preferences ->
            val storedOwnerUid =
                preferences[OwnerUidKey]
                    ?.trim()
                    ?.takeIf(String::isNotBlank)

            val active = preferences[ActiveKey] ?: false
            val expiry = preferences[ExpiryKey] ?: 0L

            if (
                storedOwnerUid == normalisedUid &&
                active &&
                expiry > 0L &&
                nowMillis >= expiry
            ) {
                preferences[ActiveKey] = false
                expired = true
            }
        }

        return expired
    }

    /** Clears the cache. Called on sign-out so a new account never inherits stale access. */
    internal suspend fun clear() {
        dataStore.edit { preferences -> preferences.clear() }
    }

    private companion object {
        val ActiveKey = booleanPreferencesKey("safe_browse_pass_active")
        val ProductIdKey = stringPreferencesKey("safe_browse_pass_product_id")
        val BasePlanIdKey = stringPreferencesKey("safe_browse_pass_base_plan_id")
        val ExpiryKey = longPreferencesKey("safe_browse_pass_expiry_millis")
        val IsPrepaidKey = booleanPreferencesKey("safe_browse_pass_is_prepaid")
        val RenewalStateKey =
            stringPreferencesKey(
                "safe_browse_pass_renewal_state",
            )
        val LastVerifiedKey = longPreferencesKey("safe_browse_pass_last_verified_millis")
        val OwnerUidKey = stringPreferencesKey("safe_browse_pass_owner_uid")
    }
}
