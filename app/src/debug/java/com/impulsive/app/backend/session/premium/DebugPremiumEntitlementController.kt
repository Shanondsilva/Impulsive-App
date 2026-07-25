package com.impulsive.app.backend.session.premium

import android.content.Context
import com.impulsive.app.backend.data.repository.PremiumRepository
import com.impulsive.app.backend.domain.model.premium.EntitlementSource
import com.impulsive.app.backend.domain.model.premium.PremiumEntitlement

/**
 * Debug-build-only entitlement override entry point.
 *
 * This class is absent from release builds.
 */
class DebugPremiumEntitlementController(context: Context) {
    private val repository = PremiumRepository(context.applicationContext)

    suspend fun setDebugEntitlement(entitlement: PremiumEntitlement) {
        require(entitlement.source == EntitlementSource.Debug) {
            "Debug entitlement controller accepts only Debug-source entitlements."
        }

        repository.setEntitlement(entitlement)
    }
}
