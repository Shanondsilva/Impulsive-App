package com.impulsive.app.backend.service.billing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.impulsive.app.backend.domain.model.premium.BillingPeriod
import com.impulsive.app.backend.domain.model.premium.EntitlementSource
import com.impulsive.app.backend.domain.model.premium.PremiumEntitlement
import com.impulsive.app.backend.domain.model.premium.PremiumTier
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val GooglePlaySubscriptionsBaseUrl =
    "https://play.google.com/store/account/subscriptions"

private const val GooglePlayStorePackage = "com.android.vending"

internal fun activePlaySubscriptionProductId(
    entitlement: PremiumEntitlement,
    nowMillis: Long,
): String? {
    if (entitlement.source != EntitlementSource.PlayBilling) {
        return null
    }

    if (entitlement.tier == PremiumTier.Free) {
        return null
    }

    if (entitlement.expiryTimeMillis <= nowMillis) {
        return null
    }

    return when (entitlement.period) {
        BillingPeriod.Monthly -> BillingManager.PlusProductId
        BillingPeriod.Yearly -> BillingManager.PlusYearlyProductId
        null -> null
    }
}

internal fun buildGooglePlaySubscriptionManagementUrl(
    packageName: String,
    productId: String,
): String {
    val normalizedPackageName = packageName.trim()
    val normalizedProductId = productId.trim()

    require(normalizedPackageName.isNotEmpty()) {
        "Package name must not be blank."
    }

    require(normalizedProductId.isNotEmpty()) {
        "Product ID must not be blank."
    }

    val charset = StandardCharsets.UTF_8.name()
    val encodedProductId = URLEncoder.encode(normalizedProductId, charset)
    val encodedPackageName = URLEncoder.encode(normalizedPackageName, charset)

    return "$GooglePlaySubscriptionsBaseUrl" +
        "?sku=$encodedProductId" +
        "&package=$encodedPackageName"
}

internal fun openGooglePlaySubscriptionManagement(
    context: Context,
    productId: String,
): Boolean {
    val managementUrl = buildGooglePlaySubscriptionManagementUrl(
        packageName = context.packageName,
        productId = productId,
    )

    val uri = Uri.parse(managementUrl)

    val playStoreIntent = Intent(Intent.ACTION_VIEW, uri).apply {
        setPackage(GooglePlayStorePackage)
        addNewTaskFlagWhenNeeded(context)
    }

    if (startActivitySafely(context, playStoreIntent)) {
        return true
    }

    val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
        addNewTaskFlagWhenNeeded(context)
    }

    return startActivitySafely(context, browserIntent)
}

private fun Intent.addNewTaskFlagWhenNeeded(context: Context) {
    if (context !is Activity) {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

private fun startActivitySafely(
    context: Context,
    intent: Intent,
): Boolean = runCatching {
    context.startActivity(intent)
    true
}.getOrDefault(false)
