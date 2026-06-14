package com.impulsive.app.backend.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.impulsive.app.backend.domain.model.premium.BillingPeriod
import com.impulsive.app.backend.domain.model.premium.EntitlementSource
import com.impulsive.app.backend.domain.model.premium.PremiumEntitlement
import com.impulsive.app.backend.domain.model.premium.PremiumTier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.premiumDataStore by preferencesDataStore(name = "premium_entitlement")

class PremiumEntitlementDataSource(context: Context) {
    private val dataStore = context.applicationContext.premiumDataStore

    val entitlement: Flow<PremiumEntitlement> = dataStore.data.map { preferences ->
        PremiumEntitlement(
            tier = preferences[TierKey].toEnumOrDefault(PremiumTier.Free),
            period = preferences[PeriodKey]?.toEnumOrNull<BillingPeriod>(),
            source = preferences[SourceKey].toEnumOrDefault(EntitlementSource.Debug),
        )
    }

    suspend fun setEntitlement(entitlement: PremiumEntitlement) {
        dataStore.edit { preferences ->
            preferences[TierKey] = entitlement.tier.name
            entitlement.period?.let { preferences[PeriodKey] = it.name }
                ?: preferences.remove(PeriodKey)
            preferences[SourceKey] = entitlement.source.name
        }
    }

    private inline fun <reified T : Enum<T>> String?.toEnumOrNull(): T? =
        this?.let { value -> runCatching { enumValueOf<T>(value) }.getOrNull() }

    private inline fun <reified T : Enum<T>> String?.toEnumOrDefault(default: T): T =
        toEnumOrNull<T>() ?: default

    private companion object {
        val TierKey = stringPreferencesKey("premium_tier")
        val PeriodKey = stringPreferencesKey("premium_period")
        val SourceKey = stringPreferencesKey("premium_source")
    }
}
