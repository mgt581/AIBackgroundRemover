package com.aiphotostudio.bgremover

import android.app.Activity
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BillingManager(private val activity: Activity) : PurchasesUpdatedListener {

    private val billingClient = BillingClient.newBuilder(activity)
        .setListener(this)
        .apply {
            @Suppress("DEPRECATION")
            enablePendingPurchases()
        }
        .build()

    interface BillingListener {
        fun onBillingSetupFinished()
        fun onPurchaseSuccess(purchase: Purchase)
        fun onPurchaseFailure(error: String)
    }

    var listener: BillingListener? = null

    init {
        startConnection()
    }

    private fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    listener?.onBillingSetupFinished()
                    queryPurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                // Try to restart the connection on the next request to
                // Google Play by calling the startConnection() method.
            }
        })
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.i("BillingManager", "onPurchasesUpdated: User canceled the purchase")
        } else {
            listener?.onPurchaseFailure(billingResult.debugMessage)
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        processPurchase(purchase)
                    }
                }
            } else {
                processPurchase(purchase)
            }
        }
    }

    private fun processPurchase(purchase: Purchase) {
        CoroutineScope(Dispatchers.Main).launch {
            for (product in purchase.products) {
                when {
                    product.startsWith("credits_") -> {
                        val amount = product.substringAfter("credits_").toIntOrNull() ?: 0
                        UserManager.addCredits(amount)
                        consumePurchase(purchase)
                    }
                    product == "aips_monthly_pro" || product == "aips_yearly_pro" -> {
                        UserManager.setSubscription(true)
                    }
                }
            }
            listener?.onPurchaseSuccess(purchase)
        }
    }

    private fun consumePurchase(purchase: Purchase) {
        val consumeParams = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.consumeAsync(consumeParams) { billingResult, _ ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.e("BillingManager", "Error consuming purchase: ${billingResult.debugMessage}")
            }
        }
    }

    fun queryPurchases() {
        // Query Subscriptions
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val isSubscribed = purchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                UserManager.setSubscription(isSubscribed)
            }
        }
    }

    fun launchPurchaseFlow(productId: String, productType: String, basePlanId: String? = null, offerId: String? = null) {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(productType)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder().setProductList(productList).build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                val productDetails = productDetailsList[0]
                val billingFlowParamsBuilder = BillingFlowParams.newBuilder()

                if (productType == BillingClient.ProductType.SUBS && productDetails.subscriptionOfferDetails != null) {
                    // Look for a specific base plan and offer if provided
                    val offerDetail = productDetails.subscriptionOfferDetails?.find { detail ->
                        val matchesBasePlan = basePlanId == null || detail.basePlanId == basePlanId
                        val matchesOffer = offerId == null || detail.offerId == offerId
                        matchesBasePlan && matchesOffer
                    } ?: productDetails.subscriptionOfferDetails?.get(0)

                    offerDetail?.let {
                        billingFlowParamsBuilder.setProductDetailsParamsList(
                            listOf(
                                BillingFlowParams.ProductDetailsParams.newBuilder()
                                    .setProductDetails(productDetails)
                                    .setOfferToken(it.offerToken)
                                    .build()
                            )
                        )
                    }
                } else {
                    billingFlowParamsBuilder.setProductDetailsParamsList(
                        listOf(
                            BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(productDetails)
                                .build()
                        )
                    )
                }

                billingClient.launchBillingFlow(activity, billingFlowParamsBuilder.build())
            } else {
                listener?.onPurchaseFailure("Product not found: ${billingResult.debugMessage}")
            }
        }
    }
}
