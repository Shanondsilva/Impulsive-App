package com.impulsive.app.backend.domain.model.premium

enum class PremiumTier(val rank: Int) {
    Free(0),
    Basic(1),
    Pro(2),
    ProPlus(3),
}

enum class BillingPeriod {
    Monthly,
    Yearly,
}

enum class EntitlementSource {
    None,
    Debug,
    PlayBilling,
}

/**
 * Every gated capability in the app. The tier here is the single source of
 * truth for what unlocks what; UI and feature code must gate through
 * [PremiumTier.includes] and never hardcode tier checks.
 */
enum class PremiumFeature(val requiredTier: PremiumTier) {
    VpnWebsiteBlocker(PremiumTier.Basic),
    BodyMode(PremiumTier.Pro),
    SoulMode(PremiumTier.Pro),
    NexusEngine(PremiumTier.ProPlus),
}

fun PremiumTier.includes(feature: PremiumFeature): Boolean =
    rank >= feature.requiredTier.rank

data class PremiumEntitlement(
    val tier: PremiumTier = PremiumTier.Free,
    val period: BillingPeriod? = null,
    val source: EntitlementSource = EntitlementSource.None,
    val expiryTimeMillis: Long = 0L,
    val lastVerifiedMillis: Long = 0L,
)

object PremiumEntitlementPolicy {
    const val OfflineGraceMillis: Long =
        3L * 24L * 60L * 60L * 1_000L

    internal const val MaximumClockRecheckMillis: Long =
        60_000L
}

fun PremiumEntitlement.playBillingAccessDeadlineMillisOrNull(): Long? {
    if (
        source != EntitlementSource.PlayBilling ||
        expiryTimeMillis <= 0L
    ) {
        return null
    }

    val grace = PremiumEntitlementPolicy.OfflineGraceMillis

    return if (expiryTimeMillis > Long.MAX_VALUE - grace) {
        Long.MAX_VALUE
    } else {
        expiryTimeMillis + grace
    }
}

fun PremiumEntitlement.isValidAt(
    nowMillis: Long,
    allowDebugEntitlement: Boolean,
): Boolean {
    if (tier == PremiumTier.Free) {
        return false
    }

    return when (source) {
        EntitlementSource.None -> false

        EntitlementSource.Debug -> allowDebugEntitlement

        EntitlementSource.PlayBilling -> {
            val accessDeadline =
                playBillingAccessDeadlineMillisOrNull() ?: return false

            nowMillis < accessDeadline
        }
    }
}

fun PremiumEntitlement.hasFeatureAt(
    feature: PremiumFeature,
    nowMillis: Long,
    allowDebugEntitlement: Boolean,
): Boolean {
    return tier.includes(feature) &&
        isValidAt(
            nowMillis = nowMillis,
            allowDebugEntitlement = allowDebugEntitlement,
        )
}
