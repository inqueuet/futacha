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
import com.android.billingclient.api.QueryPurchasesParams
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
    // Purchase tokens whose consume request is in flight. All access happens on
    // the main thread (Play callbacks and close()).
    // Maps each token to the purchase flow waiting for its result, if any.
    private val consumingTokens = mutableMapOf<String, String?>()
    private var unconsumedPurchasesRecovered = false

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val supportPurchases = purchases.orEmpty().filter { it.isSupportProduct() }
                val currentFlowToken = pendingPurchase?.flowToken
                val currentFlowPurchase = supportPurchases.getOrNull(
                    selectCurrentFlowPurchaseIndex(
                        accountIds = supportPurchases.map { it.accountIdentifiers?.obfuscatedAccountId },
                        flowToken = currentFlowToken
                    )
                )
                // Completed purchases are consumed even when they belong to an earlier
                // flow (e.g. a pending payment that was approved later); otherwise the
                // item stays owned, blocks every later purchase and is auto-refunded.
                supportPurchases
                    .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                    .forEach { purchase ->
                        consumePurchase(
                            purchase = purchase,
                            flowToken = currentFlowToken?.takeIf { purchase === currentFlowPurchase }
                        )
                    }
                when {
                    currentFlowPurchase == null -> {
                        // Earlier pending payments may update while a new purchase is open.
                        // Consume those above, but keep waiting for this flow's own result.
                        if (supportPurchases.isEmpty()) {
                            resumePending(SupportPurchaseResult.Failed("購入情報を確認できませんでした"))
                        }
                    }
                    currentFlowPurchase.purchaseState == Purchase.PurchaseState.PENDING -> {
                        resumePending(SupportPurchaseResult.Unavailable(SUPPORT_PURCHASE_PENDING_MESSAGE))
                    }
                    currentFlowPurchase.purchaseState != Purchase.PurchaseState.PURCHASED -> {
                        resumePending(SupportPurchaseResult.Failed("購入情報を確認できませんでした"))
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                resumePending(SupportPurchaseResult.Canceled)
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                // An earlier purchase was never consumed. Consume it so the next
                // attempt can succeed.
                recoverUnconsumedPurchases(force = true)
                resumePending(SupportPurchaseResult.Failed(SUPPORT_PURCHASE_ALREADY_OWNED_MESSAGE))
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
        } catch (timeout: TimeoutCancellationException) {
            // withTimeout expiry is a CancellationException too, but not a dismissed screen:
            // report it as a failed load so the UI leaves its loading state. A real cancellation
            // of the caller is re-thrown by ensureActive().
            currentCoroutineContext().ensureActive()
            Logger.w(TAG, "Google Play product query timed out: ${timeout.message.orEmpty()}")
            Result.failure(java.io.IOException("Google Play との通信がタイムアウトしました。しばらくしてからもう一度お試しください", timeout))
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
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                            recoverUnconsumedPurchases(force = true)
                            resumePending(SupportPurchaseResult.Failed(SUPPORT_PURCHASE_ALREADY_OWNED_MESSAGE))
                        } else if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
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
        // Ending the connection while a consume is in flight drops its callback and
        // leaves the purchase unconsumed. The last consume callback ends it instead.
        if (consumingTokens.isEmpty()) {
            endConnectionIfReady()
        }
    }

    private fun endConnectionIfReady() {
        if (billingClient.isReady) {
            runCatching { billingClient.endConnection() }
                .onFailure { error -> Logger.w(TAG, "Failed to end billing connection: ${error.message}") }
        }
    }

    private suspend fun ensureReady() {
        if (billingClient.isReady) {
            recoverUnconsumedPurchases(force = false)
            return
        }
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
        recoverUnconsumedPurchases(force = false)
    }

    /**
     * Consumes support purchases that completed while no flow was waiting (approved
     * pending payments, a crash before consume, a closed screen). Runs once per
     * client unless [force]d. Must be called on the main thread.
     */
    private fun recoverUnconsumedPurchases(force: Boolean) {
        if (closed.get() || !billingClient.isReady) return
        if (unconsumedPurchasesRecovered && !force) return
        unconsumedPurchasesRecovered = true
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        runCatching {
            billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
                if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                    Logger.w(TAG, "Failed to query owned purchases: ${billingResult.responseCode}")
                    unconsumedPurchasesRecovered = false
                    return@queryPurchasesAsync
                }
                val owned = purchases.filter {
                    it.isSupportProduct() && it.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                if (owned.isEmpty()) return@queryPurchasesAsync
                runOnMain {
                    owned.forEach { purchase -> consumePurchase(purchase, flowToken = null) }
                }
            }
        }.onFailure { error ->
            unconsumedPurchasesRecovered = false
            Logger.w(TAG, "Failed to start owned purchase query: ${error.message}")
        }
    }

    private fun runOnMain(block: () -> Unit) {
        val mainLooper = android.os.Looper.getMainLooper()
        if (android.os.Looper.myLooper() == mainLooper) {
            block()
        } else {
            android.os.Handler(mainLooper).post(block)
        }
    }

    private fun consumePurchase(purchase: Purchase, flowToken: String?) {
        val token = purchase.purchaseToken
        if (consumingTokens.containsKey(token)) {
            // Already being consumed (e.g. by the owned-purchase recovery); let that
            // callback report to this flow too.
            if (flowToken != null) consumingTokens[token] = flowToken
            return
        }
        consumingTokens[token] = flowToken
        val params = ConsumeParams.newBuilder()
            .setPurchaseToken(token)
            .build()
        billingClient.consumeAsync(params) { billingResult, _ ->
            runOnMain {
                val waitingFlowToken = consumingTokens.remove(token) ?: flowToken
                if (closed.get() && consumingTokens.isEmpty()) {
                    endConnectionIfReady()
                }
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    resumePendingForFlow(waitingFlowToken, SupportPurchaseResult.Success)
                } else {
                    Logger.w(TAG, "Failed to consume support purchase: ${billingResult.responseCode}")
                    resumePendingForFlow(
                        waitingFlowToken,
                        SupportPurchaseResult.Failed(billingResult.debugMessage.ifBlank {
                            "購入の確定に失敗しました"
                        })
                    )
                }
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

private const val SUPPORT_PURCHASE_PENDING_MESSAGE =
    "購入は保留中です。支払いが完了すると自動的に確定されます"
private const val SUPPORT_PURCHASE_ALREADY_OWNED_MESSAGE =
    "前回の購入が未確定でした。確定処理を行ったので、少し待ってからもう一度お試しください"

/**
 * Index of the open flow's purchase among purchases with these obfuscated account
 * ids: the one tagged with [flowToken], else one Play returned without account
 * identifiers (Play may omit them; waiting for a tag that never comes kept the
 * purchase screen waiting forever). Every flow of this app tags its purchase, so
 * one tagged with another token is an earlier flow's and is not taken.
 * -1 when none, or when no flow is open.
 */
internal fun selectCurrentFlowPurchaseIndex(accountIds: List<String?>, flowToken: String?): Int {
    if (flowToken == null) return -1
    val tagged = accountIds.indexOf(flowToken)
    return if (tagged >= 0) tagged else accountIds.indexOfFirst { it.isNullOrBlank() }
}

private fun Purchase.isSupportProduct(): Boolean =
    products.any { it in supportProductIds }

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
