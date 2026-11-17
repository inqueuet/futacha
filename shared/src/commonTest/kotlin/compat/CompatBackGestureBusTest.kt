package com.valoser.futacha.shared.compat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompatBackGestureBusTest {
    @Test
    fun gestureCompletesOnceAndUsesOnlyItsOriginalScreen() {
        val owner = Any()
        var opens = 0
        try {
            CompatBackGestureBus.register(owner) { opens++; true }
            val request = checkNotNull(CompatBackGestureBus.captureDrawerRequest())
            assertEquals(0, opens)
            assertTrue(request())
            assertFalse(request())
            assertEquals(1, opens)
            val staleRequest = checkNotNull(CompatBackGestureBus.captureDrawerRequest())
            CompatBackGestureBus.unregister(owner)
            assertFalse(staleRequest())
            assertNull(CompatBackGestureBus.captureDrawerRequest())
            assertEquals(1, opens)
        } finally {
            CompatBackGestureBus.unregister(owner)
        }
    }

    @Test
    fun screenReplacementInvalidatesGestureAndOldDisposalKeepsNewOwner() {
        val first = Any()
        val second = Any()
        var opens = 0
        try {
            CompatBackGestureBus.register(first) { error("Disposed screen received gesture") }
            val staleRequest = checkNotNull(CompatBackGestureBus.captureDrawerRequest())
            CompatBackGestureBus.register(second) { opens++; true }
            CompatBackGestureBus.unregister(first)
            assertFalse(staleRequest())
            assertTrue(checkNotNull(CompatBackGestureBus.captureDrawerRequest())())
            assertEquals(1, opens)
        } finally {
            CompatBackGestureBus.unregister(first)
            CompatBackGestureBus.unregister(second)
        }
    }
}
