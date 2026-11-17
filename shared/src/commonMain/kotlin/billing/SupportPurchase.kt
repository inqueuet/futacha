package com.valoser.futacha.shared.billing

import androidx.compose.runtime.Composable

const val GOOGLE_PLAY_SUPPORT_PRODUCT_ID = "com.valoser.futacha.support"
const val APP_STORE_SUPPORT_PRODUCT_ID = "com.valoser.futacha.support.ios"

data class SupportProduct(
    val id: String,
    val title: String,
    val description: String,
    val formattedPrice: String
)

sealed interface SupportPurchaseResult {
    data object Success : SupportPurchaseResult
    data object Canceled : SupportPurchaseResult
    data class Unavailable(val message: String) : SupportPurchaseResult
    data class Failed(val message: String) : SupportPurchaseResult
}

expect class SupportPurchaseClient {
    suspend fun loadProducts(): Result<List<SupportProduct>>
    suspend fun purchase(product: SupportProduct): SupportPurchaseResult
    fun close()
}

@Composable
expect fun rememberSupportPurchaseClient(): SupportPurchaseClient

fun supportProductFallbackTitle(id: String): String {
    return when (id) {
        GOOGLE_PLAY_SUPPORT_PRODUCT_ID,
        APP_STORE_SUPPORT_PRODUCT_ID -> "開発を応援"
        else -> "開発を応援"
    }
}

fun supportProductFallbackDescription(id: String): String {
    return when (id) {
        GOOGLE_PLAY_SUPPORT_PRODUCT_ID,
        APP_STORE_SUPPORT_PRODUCT_ID -> "広告なしで続けるための任意支援です。"
        else -> "任意の支援です。機能解放はありません。"
    }
}
