package com.impulsive.app.backend.service.billing

import android.app.Activity
import android.content.Context
import android.util.Log
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
import com.google.firebase.functions.FirebaseFunctions
import com.impulsive.app.backend.data.repository.PremiumRepository
import com.impulsive.app.backend.domain.model.premium.BillingPeriod
import com.impulsive.app.backend.domain.model.premium.EntitlementSource
import com.impulsive.app.backend.domain.model.premium.PremiumEntitlement
import com.impulsive.app.backend.domain.model.premium.PremiumTier
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Wraps Google Play Billing for the Plus subscription.
 *
 * The device-side Play Billing response is not trusted as the entitlement source.
 * Plus is granted locally only after the backend callable `verifyPlusSubscription`
 * verifies the purchase token with Google Play, acknowledges the purchase server-side,
 * and returns an active entitlement for the expected product ID.
 */
class BillingManager(
    context: Context,
) : PurchasesUpdatedListener {
    private val appContext = context.applicationContext
    private val repository = PremiumRepository(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val functions = FirebaseFunctions.getInstance(FunctionsRegion)
    private val verifyingPurchaseTokens = Collections.newSetFromMap(
        ConcurrentHashMap<String, Boolean>(),
    )

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
        purchases
            .filter { purchase ->
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                    purchase.products.contains(PlusProductId)
            }
            .forEach(::verifyAndGrantIfActive)
    }

    private fun verifyAndGrantIfActive(purchase: Purchase) {
        if (!verifyingPurchaseTokens.add(purchase.purchaseToken)) {
            return
        }

        scope.launch {
            try {
                val verified = verifyPurchaseWithBackend(purchase)
                if (verified) {
                    grantEntitlement()
                } else {
                    Log.w(
                        Tag,
                        "Plus purchase verification returned inactive or invalid data.",
                    )
                }
            } catch (throwable: Throwable) {
                Log.w(
                    Tag,
                    "Plus purchase verification failed; entitlement was not granted.",
                    throwable,
                )
            } finally {
                verifyingPurchaseTokens.remove(purchase.purchaseToken)
            }
        }
    }

    private suspend fun verifyPurchaseWithBackend(purchase: Purchase): Boolean {
        val result = functions
            .getHttpsCallable(VerifyPlusSubscriptionFunction)
            .call(
                mapOf(
                    "productId" to PlusProductId,
                    "purchaseToken" to purchase.purchaseToken,
                ),
            )
            .await()

        val data = result.getData() as? Map<*, *> ?: return false
        val active = data["active"] as? Boolean ?: false
        val productId = data["productId"] as? String

        return active && productId == PlusProductId
    }

    private suspend fun grantEntitlement() {
        repository.setEntitlement(
            PremiumEntitlement(
                tier = PremiumTier.Basic,
                period = BillingPeriod.Monthly,
                source = EntitlementSource.PlayBilling,
            ),
        )
    }

    fun release() {
        billingClient.endConnection()
        verifyingPurchaseTokens.clear()
    }

    companion object {
        private const val Tag = "BillingManager"
        private const val FunctionsRegion = "us-central1"
        private const val VerifyPlusSubscriptionFunction = "verifyPlusSubscription"

        // Must match the subscription product ID created in Google Play Console.
        const val PlusProductId = "impulsive_plus_monthly"
    }
}
