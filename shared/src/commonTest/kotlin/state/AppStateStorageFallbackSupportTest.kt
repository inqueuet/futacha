package com.valoser.futacha.shared.state

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppStateStorageFallbackSupportTest {
    @Test
    fun shouldEmitStorageReadFallback_requiresReadableSnapshot() {
        assertFalse(shouldEmitStorageReadFallback(hasReadableSnapshot = false))
        assertTrue(shouldEmitStorageReadFallback(hasReadableSnapshot = true))
    }
}
