package com.valoser.futacha.shared.billing

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsResult
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume

private const val TAG = "SupportPurchase"
private const val BILLING_CONNECT_TIMEOUT_MILLIS = 8_000L
private const val BILLING_QUERY_TIMEOUT_MILLIS = 8_000L
private val supportProductIds = listOf(GOOGLE_PLAY_SUPPORT_PRODUCT_ID)

actual class SupportPurchaseClient internal constructor(
    private val context: Context
) {
    private var pendingPurchase: PendingGooglePurchase? = null
    private var productDetailsById: Map<String, ProductDetails> = emptyMap()
    private val closed = AtomicBoolean(false)

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val purchase = purchases
                    ?.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                if (purchase == null) {
                    resumePending(SupportPurchaseResult.Failed("購入情報を確認できませんでした"))
                } else {
                    consumePurchase(
                        purchase = purchase,
                        flowToken = purchase.accountIdentifiers?.obfuscatedAccountId
                            ?.takeIf { token -> token == pendingPurchase?.flowToken }
                    )
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                resumePending(SupportPurchaseResult.Canceled)
            }
            else -> {
                resumePending(SupportPurchaseResult.Failed(billingResult.debugMessage.ifBlank {
                    "Google Play Billing error ${billingResult.responseCode}"
                }))
            }
        }
    }

    private val billingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    actual suspend fun loadProducts(): Result<List<SupportProduct>> {
        return try {
            check(!closed.get()) { "購入機能は終了しています" }
            withContext(Dispatchers.Main.immediate) { ensureReady() }
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    supportProductIds.map { productId ->
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(productId)
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build()
                    }
                )
                .build()
            val response = withTimeout(BILLING_QUERY_TIMEOUT_MILLIS) {
                withContext(Dispatchers.Main.immediate) {
                    suspendCancellableCoroutine<QueryProductDetailsResponse> { continuation ->
                        val completed = AtomicBoolean(false)
                        continuation.invokeOnCancellation { completed.set(true) }
                        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsResult ->
                            if (completed.compareAndSet(false, true) && continuation.isActive) {
                                continuation.resume(QueryProductDetailsResponse(billingResult, productDetailsResult))
                            }
                        }
                    }
                }
            }
            require(response.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                response.billingResult.debugMessage.ifBlank { "商品を取得できませんでした" }
            }
            val products = response.productDetailsResult.productDetailsList
            val purchasableProducts = products.filter { it.oneTimePurchaseOfferDetails != null }
            productDetailsById = purchasableProducts.associateBy { it.productId }
            Result.success(
                purchasableProducts
                .sortedBy { supportProductIds.indexOf(it.productId).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
                .map { details ->
                    SupportProduct(
                        id = details.productId,
                        title = supportProductFallbackTitle(details.productId),
                        description = supportProductFallbackDescription(details.productId),
                        formattedPrice = details.oneTimePurchaseOfferDetails?.formattedPrice.orEmpty()
                    )
                }
            )
        } catch (cancellation: CancellationException) {
            // A dismissed settings screen must cancel the billing query instead
            // of turning cancellation into a normal failed Result.  Otherwise a
            // late Play callback can continue updating a disposed Compose tree.
            throw cancellation
        } catch (error: Exception) {
            Logger.w(TAG, "Google Play product query failed: ${error.message.orEmpty()}")
            Result.failure(error)
        }
    }

    actual suspend fun purchase(product: SupportProduct): SupportPurchaseResult {
        return try {
            // ProductDetails is short-lived.  Google recommends querying it again
            // before launching the flow because stale details can make
            // launchBillingFlow fail.  Do this before creating the pending
            // continuation so a catalog/query failure never leaves a purchase
            // suspended while the Play proxy activity is being launched.
            val refreshedProducts = loadProducts().getOrElse { error ->
                return SupportPurchaseResult.Failed(
                    error.message ?: "ストアの商品情報を取得できませんでした"
                )
            }
            val activity = context.findActivity()
                ?: return SupportPurchaseResult.Unavailable("購入画面を開けませんでした")
            if (closed.get() || activity.isFinishing || activity.isDestroyed) {
                return SupportPurchaseResult.Unavailable("購入画面を開けませんでした")
            }
            if (refreshedProducts.none { it.id == product.id }) {
                return SupportPurchaseResult.Unavailable("この商品は現在購入できません")
            }
            val details = productDetailsById[product.id]
                ?: return SupportPurchaseResult.Unavailable("ストアの商品情報を再読み込みしてください")
            if (details.oneTimePurchaseOfferDetails == null) {
                return SupportPurchaseResult.Unavailable("ストアの商品情報を再読み込みしてください")
            }
            withContext(Dispatchers.Main.immediate) {
                if (pendingPurchase != null) {
                    return@withContext SupportPurchaseResult.Unavailable("購入処理中です")
                }
                suspendCancellableCoroutine<SupportPurchaseResult> { continuation ->
                    val flowToken = UUID.randomUUID().toString()
                    pendingPurchase = PendingGooglePurchase(continuation, flowToken)
                    continuation.invokeOnCancellation {
                        if (pendingPurchase?.continuation === continuation) pendingPurchase = null
                    }
                    val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                    val billingFlowParams = BillingFlowParams.newBuilder()
                        .setProductDetailsParamsList(listOf(productDetailsParams))
                        .setObfuscatedAccountId(flowToken)
                        .build()
                    try {
                        if (closed.get() || activity.isFinishing || activity.isDestroyed) {
                            resumePending(SupportPurchaseResult.Unavailable("購入画面を開けませんでした"))
                            return@suspendCancellableCoroutine
                        }
                        val billingResult = billingClient.launchBillingFlow(activity, billingFlowParams)
                        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                            resumePending(SupportPurchaseResult.Failed(billingResult.debugMessage.ifBlank {
                                "購入画面を開けませんでした"
                            }))
                        }
                    } catch (error: Exception) {
                        Logger.e(TAG, "Failed to launch Google Play billing flow", error)
                        resumePending(SupportPurchaseResult.Failed("購入画面を開けませんでした"))
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Logger.e(TAG, "Google Play billing purchase failed", error)
            SupportPurchaseResult.Failed(error.message ?: "購入処理に失敗しました")
        }
    }

    actual fun close() {
        if (!closed.compareAndSet(false, true)) return
        pendingPurchase?.continuation?.let {
            pendingPurchase = null
            if (it.isActive) {
                runCatching { it.resume(SupportPurchaseResult.Canceled) }
                    .onFailure { error ->
                        Logger.w(TAG, "Purchase continuation was already completed during close: ${error.message}")
                    }
            }
        }
        if (billingClient.isReady) {
            billingClient.endConnection()
        }
    }

    private suspend fun ensureReady() {
        if (billingClient.isReady) return
        val result = withTimeout(BILLING_CONNECT_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine<BillingResult> { continuation ->
                val completed = AtomicBoolean(false)
                continuation.invokeOnCancellation { completed.set(true) }
                billingClient.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(billingResult: BillingResult) {
                        if (completed.compareAndSet(false, true) && continuation.isActive) {
                            continuation.resume(billingResult)
                        }
                    }

                    override fun onBillingServiceDisconnected() {
                        Logger.w(TAG, "Google Play Billing service disconnected")
                    }
                })
            }
        }
        require(result.responseCode == BillingClient.BillingResponseCode.OK) {
            result.debugMessage.ifBlank { "Google Play Billing に接続できませんでした" }
        }
    }

    private fun consumePurchase(purchase: Purchase, flowToken: String?) {
        val params = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.consumeAsync(params) { billingResult, _ ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                resumePendingForFlow(flowToken, SupportPurchaseResult.Success)
            } else {
                resumePendingForFlow(
                    flowToken,
                    SupportPurchaseResult.Failed(billingResult.debugMessage.ifBlank {
                        "購入の確定に失敗しました"
                    })
                )
            }
        }
    }

    private fun resumePendingForFlow(flowToken: String?, result: SupportPurchaseResult) {
        if (flowToken == null || pendingPurchase?.flowToken != flowToken) return
        resumePending(result)
    }

    private fun resumePending(result: SupportPurchaseResult) {
        val continuation = pendingPurchase?.continuation ?: return
        pendingPurchase = null
        if (!continuation.isActive) return
        runCatching { continuation.resume(result) }
            .onFailure { error -> Logger.w(TAG, "Purchase callback arrived after cancellation: ${error.message}") }
    }
}

private data class PendingGooglePurchase(
    val continuation: CancellableContinuation<SupportPurchaseResult>,
    val flowToken: String
)

@Composable
actual fun rememberSupportPurchaseClient(): SupportPurchaseClient {
    val context = LocalContext.current
    val client = remember(context) { SupportPurchaseClient(context) }
    DisposableEffect(client) {
        onDispose { client.close() }
    }
    return client
}

private data class QueryProductDetailsResponse(
    val billingResult: BillingResult,
    val productDetailsResult: QueryProductDetailsResult
)

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
