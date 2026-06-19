package com.impulsive.app.backend.service.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.impulsive.app.backend.data.repository.PremiumRepository
import com.impulsive.app.backend.domain.model.premium.BillingPeriod
import com.impulsive.app.backend.domain.model.premium.EntitlementSource
import com.impulsive.app.backend.domain.model.premium.PremiumEntitlement
import com.impulsive.app.backend.domain.model.premium.PremiumTier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Wraps Google Play Billing for the Plus subscription. Connects, reads the product and price,
 * restores an existing purchase, runs the purchase flow, acknowledges the purchase, and writes a
 * PlayBilling entitlement into PremiumRepository. The repository protects a PlayBilling entitlement
 * from being downgraded by a Debug write, so this is the authoritative grant.
 *
 * Note: this trusts the Play response on device and acknowledges locally. An app with a backend
 * would also verify the purchase server side. This app has no backend, so local acknowledgement is
 * the pragmatic choice for now.
 */
class BillingManager(
    context: Context,
) : PurchasesUpdatedListener {
    private val appContext = context.applicationContext
    private val repository = PremiumRepository(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val billingClient: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build(),
        )
        .build()

    private var productDetails: ProductDetails? = null

    private val _formattedPrice = MutableStateFlow<String?>(null)
    val formattedPrice: StateFlow<String?> = _formattedPrice.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    fun connect() {
        if (billingClient.isReady) {
            onConnected()
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _connected.value = true
                    onConnected()
                }
            }

            override fun onBillingServiceDisconnected() {
                _connected.value = false
            }
        })
    }

    private fun onConnected() {
        queryProduct()
        refreshPurchases()
    }

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PlusProductId)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                ),
            )
            .build()
        billingClient.queryProductDetailsAsync(params) { result, queryResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val details = queryResult.productDetailsList.firstOrNull()
                productDetails = details
                _formattedPrice.value = details
                    ?.subscriptionOfferDetails
                    ?.firstOrNull()
                    ?.pricingPhases
                    ?.pricingPhaseList
                    ?.firstOrNull()
                    ?.formattedPrice
            }
        }
    }

    fun launchPurchase(activity: Activity) {
        val details = productDetails ?: return
        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: return
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offerToken)
                        .build(),
                ),
            )
            .build()
        billingClient.launchBillingFlow(activity, params)
    }

    fun refreshPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                handlePurchases(purchases)
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            handlePurchases(purchases)
        }
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        val active = purchases.any { purchase ->
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                purchase.products.contains(PlusProductId)
        }
        purchases
            .filter { purchase ->
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                    purchase.products.contains(PlusProductId) &&
                    !purchase.isAcknowledged
            }
            .forEach { acknowledge(it) }
        if (active) {
            grantEntitlement()
        }
    }

    private fun acknowledge(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { }
    }

    private fun grantEntitlement() {
        scope.launch {
            repository.setEntitlement(
                PremiumEntitlement(
                    tier = PremiumTier.Basic,
                    period = BillingPeriod.Monthly,
                    source = EntitlementSource.PlayBilling,
                ),
            )
        }
    }

    fun release() {
        billingClient.endConnection()
    }

    companion object {
        // Must match the subscription product ID created in Google Play Console.
        const val PlusProductId = "impulsive_plus_monthly"
    }
}
