@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.valoser.futacha.shared.billing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import platform.Foundation.NSError
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterCurrencyStyle
import platform.Foundation.NSUUID
import platform.StoreKit.SKPaymentQueue
import platform.StoreKit.SKPaymentTransaction
import platform.StoreKit.SKPaymentTransactionObserverProtocol
import platform.StoreKit.SKPaymentTransactionState
import platform.StoreKit.SKProduct
import platform.StoreKit.SKMutablePayment
import platform.StoreKit.SKProductsRequest
import platform.StoreKit.SKProductsRequestDelegateProtocol
import platform.StoreKit.SKProductsResponse
import platform.StoreKit.SKRequest
import platform.StoreKit.SKRequestDelegateProtocol
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.concurrent.AtomicReference
import kotlin.coroutines.resume

private const val TAG = "SupportPurchase.ios"
private const val STORE_KIT_PRODUCT_LOAD_TIMEOUT_MILLIS = 15_000L
private val supportProductIds = listOf(APP_STORE_SUPPORT_PRODUCT_ID)

private val retainedStoreKitDelegates = AtomicReference<List<NSObject>>(emptyList())

private class StoreKitOnceGate {
    private val completedMarker = Any()
    private val state = AtomicReference<Any?>(null)

    fun tryComplete(): Boolean = state.compareAndSet(null, completedMarker)
}

private class StoreKitBusyGate {
    private val busyMarker = Any()
    private val state = AtomicReference<Any?>(null)

    fun tryAcquire(): Boolean = state.compareAndSet(null, busyMarker)
    fun release() {
        state.value = null
    }
}

private fun retainStoreKitDelegate(delegate: NSObject) {
    while (true) {
        val current = retainedStoreKitDelegates.value
        if (retainedStoreKitDelegates.compareAndSet(current, current + delegate)) return
    }
}

private fun releaseStoreKitDelegate(delegate: NSObject?) {
    delegate ?: return
    while (true) {
        val current = retainedStoreKitDelegates.value
        val next = current.filterNot { it === delegate }
        if (next.size == current.size || retainedStoreKitDelegates.compareAndSet(current, next)) return
    }
}

actual class SupportPurchaseClient {
    private val paymentQueue = SKPaymentQueue.defaultQueue()
    private val observer = SupportPaymentObserver(::handleTransactions)
    private var productsById: Map<String, SKProduct> = emptyMap()
    private var pendingPurchase: PendingStoreKitPurchase? = null
    private val purchaseInFlight = StoreKitBusyGate()
    private var closed = false

    init {
        paymentQueue.addTransactionObserver(observer)
    }

    actual suspend fun loadProducts(): Result<List<SupportProduct>> =
        withContext(Dispatchers.Main.immediate) {
            runSuspendCatchingPreservingCancellation {
                check(!closed) { "購入機能は終了しています" }
                if (!SKPaymentQueue.canMakePayments()) {
                    return@runSuspendCatchingPreservingCancellation emptyList()
                }
                withTimeoutOrNull(STORE_KIT_PRODUCT_LOAD_TIMEOUT_MILLIS) {
                    suspendCancellableCoroutine<List<SupportProduct>> { continuation ->
                    var delegateRef: ProductsRequestDelegate? = null
                    val completed = StoreKitOnceGate()
                    val request = SKProductsRequest(productIdentifiers = supportProductIds.toSet())
                    val delegate = ProductsRequestDelegate(
                        onSuccess = { products ->
                            if (completed.tryComplete()) {
                                releaseStoreKitDelegate(delegateRef)
                                if (continuation.isActive) {
                                    productsById = products.associateBy { it.productIdentifier }
                                    continuation.resume(products.toSupportProducts())
                                }
                            }
                        },
                        onFailure = { error ->
                            if (completed.tryComplete()) {
                                releaseStoreKitDelegate(delegateRef)
                                if (continuation.isActive) {
                                    continuation.resumeWith(Result.failure(IllegalStateException(error)))
                                }
                            }
                        }
                    )
                    delegateRef = delegate
                    retainStoreKitDelegate(delegate)
                    continuation.invokeOnCancellation {
                        if (completed.tryComplete()) {
                            releaseStoreKitDelegate(delegateRef)
                            dispatch_async(dispatch_get_main_queue()) {
                                request.cancel()
                            }
                        }
                    }
                    request.delegate = delegate
                    request.start()
                }
                } ?: error("ストアの商品情報の読み込みがタイムアウトしました")
            }
        }

    actual suspend fun purchase(product: SupportProduct): SupportPurchaseResult =
        withContext(Dispatchers.Main.immediate) {
            if (closed) return@withContext SupportPurchaseResult.Unavailable("購入画面を開けませんでした")
            if (!SKPaymentQueue.canMakePayments()) {
                return@withContext SupportPurchaseResult.Unavailable("App内課金を利用できません")
            }
            if (!purchaseInFlight.tryAcquire()) {
                return@withContext SupportPurchaseResult.Unavailable("購入処理中です")
            }
            val storeProduct = productsById[product.id] ?: run {
                purchaseInFlight.release()
                return@withContext SupportPurchaseResult.Unavailable("ストアの商品情報を再読み込みしてください")
            }
            if (closed) {
                purchaseInFlight.release()
                return@withContext SupportPurchaseResult.Unavailable("購入画面を開けませんでした")
            }
            suspendCancellableCoroutine<SupportPurchaseResult> { continuation ->
                val purchaseToken = NSUUID.UUID().UUIDString
                val payment = SKMutablePayment.paymentWithProduct(storeProduct)
                // The inherited Kotlin property is read-only, while Kotlin/Native exposes
                // SKMutablePayment's documented Objective-C setter as an explicit function.
                payment.setApplicationUsername(purchaseToken)
                pendingPurchase = PendingStoreKitPurchase(continuation, purchaseToken)
                continuation.invokeOnCancellation {
                    dispatch_async(dispatch_get_main_queue()) {
                        if (pendingPurchase?.continuation === continuation) {
                            pendingPurchase = null
                            purchaseInFlight.release()
                        }
                    }
                }
                paymentQueue.addPayment(payment)
            }
        }

    actual fun close() {
        if (closed) return
        closed = true
        purchaseInFlight.release()
        pendingPurchase?.continuation?.let {
            pendingPurchase = null
            if (it.isActive) {
                runCatching { it.resume(SupportPurchaseResult.Canceled) }
                    .onFailure { error -> Logger.w(TAG, "Purchase continuation was already completed: ${error.message}") }
            }
        }
        paymentQueue.removeTransactionObserver(observer)
    }

    private fun handleTransactions(transactions: List<SKPaymentTransaction>) {
        transactions.forEach { transaction ->
            when (transaction.transactionState) {
                SKPaymentTransactionState.SKPaymentTransactionStatePurchased -> {
                    paymentQueue.finishTransaction(transaction)
                    resumePending(transaction, SupportPurchaseResult.Success)
                }
                SKPaymentTransactionState.SKPaymentTransactionStateFailed -> {
                    paymentQueue.finishTransaction(transaction)
                    val nsError = transaction.error
                    val isCanceled = nsError?.code == 2L
                    if (isCanceled) {
                        resumePending(transaction, SupportPurchaseResult.Canceled)
                    } else {
                        resumePending(
                            transaction,
                            SupportPurchaseResult.Failed(nsError?.localizedDescription ?: "購入に失敗しました")
                        )
                    }
                }
                SKPaymentTransactionState.SKPaymentTransactionStateRestored -> {
                    paymentQueue.finishTransaction(transaction)
                }
                SKPaymentTransactionState.SKPaymentTransactionStatePurchasing -> Unit
                else -> Logger.w(TAG, "Unhandled transaction state: ${transaction.transactionState}")
            }
        }
    }

    private fun resumePending(transaction: SKPaymentTransaction, result: SupportPurchaseResult) {
        val pending = pendingPurchase ?: return
        if (pending.purchaseToken != transaction.payment.applicationUsername) return
        pendingPurchase = null
        purchaseInFlight.release()
        val continuation = pending.continuation
        if (continuation.isActive) continuation.resume(result)
    }
}

private data class PendingStoreKitPurchase(
    val continuation: CancellableContinuation<SupportPurchaseResult>,
    val purchaseToken: String
)

@Composable
actual fun rememberSupportPurchaseClient(): SupportPurchaseClient {
    val client = remember { SupportPurchaseClient() }
    DisposableEffect(client) {
        onDispose { client.close() }
    }
    return client
}

private class ProductsRequestDelegate(
    private val onSuccess: (List<SKProduct>) -> Unit,
    private val onFailure: (String) -> Unit
) : NSObject(), SKProductsRequestDelegateProtocol, SKRequestDelegateProtocol {
    override fun productsRequest(
        request: SKProductsRequest,
        didReceiveResponse: SKProductsResponse
    ) {
        val products = didReceiveResponse.products.mapNotNull { it as? SKProduct }
        onSuccess(products)
    }

    @ObjCSignatureOverride
    override fun request(
        request: SKRequest,
        didFailWithError: NSError
    ) {
        onFailure(didFailWithError.localizedDescription)
    }
}

private class SupportPaymentObserver(
    private val onTransactionsUpdated: (List<SKPaymentTransaction>) -> Unit
) : NSObject(), SKPaymentTransactionObserverProtocol {
    override fun paymentQueue(
        queue: SKPaymentQueue,
        updatedTransactions: List<*>
    ) {
        onTransactionsUpdated(updatedTransactions.mapNotNull { it as? SKPaymentTransaction })
    }
}

private fun List<SKProduct>.toSupportProducts(): List<SupportProduct> {
    return sortedBy { supportProductIds.indexOf(it.productIdentifier).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
        .map { product ->
            SupportProduct(
                id = product.productIdentifier,
                title = supportProductFallbackTitle(product.productIdentifier),
                description = supportProductFallbackDescription(product.productIdentifier),
                formattedPrice = product.formattedPrice()
            )
        }
}

private fun SKProduct.formattedPrice(): String {
    val formatter = NSNumberFormatter().apply {
        numberStyle = NSNumberFormatterCurrencyStyle
        locale = priceLocale
    }
    return formatter.stringFromNumber(price).orEmpty()
}
