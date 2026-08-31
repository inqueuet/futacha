package com.valoser.futacha.shared.billing

import kotlin.test.Test
import kotlin.test.assertEquals

class SupportPurchaseTest {
    @Test
    fun knownStoreProductsUseTheSameNoFeatureUnlockContract() {
        listOf(GOOGLE_PLAY_SUPPORT_PRODUCT_ID, APP_STORE_SUPPORT_PRODUCT_ID).forEach { id ->
            assertEquals("開発を応援", supportProductFallbackTitle(id))
            assertEquals("広告なしで続けるための任意支援です。", supportProductFallbackDescription(id))
        }
    }

    @Test
    fun unknownProductsNeverPromiseAFeatureUnlock() {
        assertEquals("開発を応援", supportProductFallbackTitle("unknown"))
        assertEquals("任意の支援です。機能解放はありません。", supportProductFallbackDescription("unknown"))
    }
}
