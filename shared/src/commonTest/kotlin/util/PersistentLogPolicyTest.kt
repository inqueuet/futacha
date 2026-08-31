package com.valoser.futacha.shared.util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PersistentLogPolicyTest {
    @Test
    fun tenMegabytesIsRetainedAndLargerLogIsReset() {
        assertFalse(shouldResetPersistentLog(PERSISTENT_ERROR_LOG_MAX_BYTES))
        assertTrue(shouldResetPersistentLog(PERSISTENT_ERROR_LOG_MAX_BYTES + 1L))
    }
}
