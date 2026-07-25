package com.impulsive.app.backend.service.billing

import com.impulsive.app.backend.domain.model.premium.BillingPeriod

internal enum class EntitlementRefreshOutcome {
    AppliedActive,
    AppliedInactive,
    RetryableFailure,
    AppCheckTemporarilyUnavailable,
    SkippedNoAuthenticatedUser,
}

internal fun EntitlementRefreshOutcome.isRetryableFailure(): Boolean =
    this == EntitlementRefreshOutcome.RetryableFailure ||
        this == EntitlementRefreshOutcome.AppCheckTemporarilyUnavailable

internal fun shouldAttemptProtectedEntitlementRefresh(
    hasAuthenticatedUser: Boolean,
): Boolean = hasAuthenticatedUser

internal sealed interface ServerEntitlementResolution {

    data class Active(
        val productId: String,
        val period: BillingPeriod,
        val expiryTimeMillis: Long,
        val subscriptionState: String?,
    ) : ServerEntitlementResolution

    data class Inactive(
        val subscriptionState: String?,
    ) : ServerEntitlementResolution

    data object RetryableFailure : ServerEntitlementResolution
}

internal fun resolveServerEntitlementResponse(
    data: Any?,
    nowMillis: Long,
): ServerEntitlementResolution {
    val map = data as? Map<*, *>
        ?: return ServerEntitlementResolution.RetryableFailure

    val active = map["active"] as? Boolean
        ?: return ServerEntitlementResolution.RetryableFailure

    val subscriptionState = (map["subscriptionState"] as? String)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    if (!active) {
        return ServerEntitlementResolution.Inactive(
            subscriptionState = subscriptionState,
        )
    }

    val productId = (map["productId"] as? String)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: return ServerEntitlementResolution.RetryableFailure

    val expiryTimeMillis = (map["expiryTimeMillis"] as? Number)
        ?.toLong()
        ?: return ServerEntitlementResolution.RetryableFailure

    if (expiryTimeMillis <= nowMillis) {
        return ServerEntitlementResolution.Inactive(
            subscriptionState = subscriptionState,
        )
    }

    val period = when (productId) {
        BillingManager.PlusProductId -> BillingPeriod.Monthly
        BillingManager.PlusYearlyProductId -> BillingPeriod.Yearly
        else -> return ServerEntitlementResolution.RetryableFailure
    }

    return ServerEntitlementResolution.Active(
        productId = productId,
        period = period,
        expiryTimeMillis = expiryTimeMillis,
        subscriptionState = subscriptionState,
    )
}

internal object EntitlementRefreshRetryPolicy {

    private val retryDelaysMillis = longArrayOf(
        2_000L,
        5_000L,
        15_000L,
    )

    fun delayAfterFailure(failureIndex: Int): Long? {
        if (failureIndex < 0) {
            return null
        }

        return retryDelaysMillis.getOrNull(failureIndex)
    }
}
