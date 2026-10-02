package com.valoser.futacha.shared.billing

import kotlin.test.Test
import kotlin.test.assertEquals

class SupportPurchaseFlowSelectionTest {
    @Test
    fun purchaseTaggedWithTheOpenFlowIsSelected() {
        assertEquals(1, selectCurrentFlowPurchaseIndex(listOf("earlier", "open", null), flowToken = "open"))
    }

    @Test
    fun purchaseWithoutAccountInfoIsTakenAsTheOpenFlowsInsteadOfWaitingForever() {
        assertEquals(0, selectCurrentFlowPurchaseIndex(listOf(null), flowToken = "open"))
        assertEquals(1, selectCurrentFlowPurchaseIndex(listOf("earlier", ""), flowToken = "open"))
    }

    @Test
    fun earlierFlowsPurchasesAndClosedFlowsSelectNothing() {
        assertEquals(-1, selectCurrentFlowPurchaseIndex(listOf("earlier"), flowToken = "open"))
        assertEquals(-1, selectCurrentFlowPurchaseIndex(emptyList(), flowToken = "open"))
        assertEquals(-1, selectCurrentFlowPurchaseIndex(listOf(null, "open"), flowToken = null))
    }
}
