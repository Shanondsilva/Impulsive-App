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
    val source: EntitlementSource = EntitlementSource.Debug,
)
