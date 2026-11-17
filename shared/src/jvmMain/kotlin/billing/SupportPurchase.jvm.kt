package com.valoser.futacha.shared.billing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

actual class SupportPurchaseClient {
    actual suspend fun loadProducts(): Result<List<SupportProduct>> {
        return Result.success(emptyList())
    }

    actual suspend fun purchase(product: SupportProduct): SupportPurchaseResult {
        return SupportPurchaseResult.Unavailable("この環境ではストア決済を利用できません")
    }

    actual fun close() = Unit
}

@Composable
actual fun rememberSupportPurchaseClient(): SupportPurchaseClient {
    return remember { SupportPurchaseClient() }
}
