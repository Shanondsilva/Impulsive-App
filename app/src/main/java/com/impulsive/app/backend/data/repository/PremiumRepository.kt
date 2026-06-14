package com.impulsive.app.backend.data.repository

import android.content.Context
import com.impulsive.app.backend.data.local.preferences.PremiumEntitlementDataSource
import com.impulsive.app.backend.domain.model.premium.EntitlementSource
import com.impulsive.app.backend.domain.model.premium.PremiumEntitlement
import com.impulsive.app.backend.domain.model.premium.PremiumFeature
import com.impulsive.app.backend.domain.model.premium.includes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class PremiumRepository(context: Context) {
    private val dataSource = PremiumEntitlementDataSource(context)

    val entitlement: Flow<PremiumEntitlement> = dataSource.entitlement

    fun hasFeature(feature: PremiumFeature): Flow<Boolean> =
        entitlement.map { it.tier.includes(feature) }

    /**
     * Once Play Billing is integrated it becomes the authoritative source:
     * a Debug write never downgrades or replaces a PlayBilling entitlement.
     */
    suspend fun setEntitlement(entitlement: PremiumEntitlement) {
        val current = dataSource.entitlement.first()
        if (current.source == EntitlementSource.PlayBilling &&
            entitlement.source == EntitlementSource.Debug
        ) {
            return
        }
        dataSource.setEntitlement(entitlement)
    }
}
