package com.impulsive.app.backend.data.repository

import android.content.Context
import com.impulsive.app.BuildConfig
import com.impulsive.app.backend.data.local.preferences.PremiumEntitlementDataSource
import com.impulsive.app.backend.domain.model.premium.EntitlementSource
import com.impulsive.app.backend.domain.model.premium.PremiumEntitlement
import com.impulsive.app.backend.domain.model.premium.PremiumEntitlementPolicy
import com.impulsive.app.backend.domain.model.premium.PremiumFeature
import com.impulsive.app.backend.domain.model.premium.hasFeatureAt
import com.impulsive.app.backend.domain.model.premium.playBillingAccessDeadlineMillisOrNull
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

class PremiumRepository(context: Context) {
    private val dataSource = PremiumEntitlementDataSource(context)

    val entitlement: Flow<PremiumEntitlement> = dataSource.entitlement

    fun hasFeature(
        feature: PremiumFeature,
    ): Flow<Boolean> =
        entitlement
            .flatMapLatest { cachedEntitlement ->
                flow {
                    while (currentCoroutineContext().isActive) {
                        val nowMillis = System.currentTimeMillis()

                        val available = cachedEntitlement.hasFeatureAt(
                            feature = feature,
                            nowMillis = nowMillis,
                            allowDebugEntitlement = BuildConfig.DEBUG,
                        )

                        emit(available)

                        if (
                            !available ||
                            cachedEntitlement.source != EntitlementSource.PlayBilling
                        ) {
                            break
                        }

                        val accessDeadline = cachedEntitlement
                            .playBillingAccessDeadlineMillisOrNull()
                            ?: break

                        val remainingMillis =
                            accessDeadline - System.currentTimeMillis()

                        if (remainingMillis <= 0L) {
                            continue
                        }

                        delay(
                            minOf(
                                remainingMillis,
                                PremiumEntitlementPolicy.MaximumClockRecheckMillis,
                            ),
                        )
                    }
                }
            }
            .distinctUntilChanged()

    /**
     * Once Play Billing is integrated it becomes the authoritative source:
     * a Debug write never downgrades or replaces a PlayBilling entitlement.
     */
    suspend fun setEntitlement(entitlement: PremiumEntitlement) {
        if (entitlement.source == EntitlementSource.Debug && !BuildConfig.DEBUG) {
            return
        }

        val current = dataSource.entitlement.first()
        if (current.source == EntitlementSource.PlayBilling &&
            entitlement.source == EntitlementSource.Debug
        ) {
            return
        }
        dataSource.setEntitlement(entitlement)
    }
}
