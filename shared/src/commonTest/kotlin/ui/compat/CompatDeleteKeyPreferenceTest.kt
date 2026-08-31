package com.valoser.futacha.shared.ui.compat

import kotlin.test.Test
import kotlin.test.assertEquals

class CompatDeleteKeyPreferenceTest {
    @Test
    fun storedPostDeleteKey_prefersCommonApkKey() {
        assertEquals(
            "12345678",
            mapOf(
                COMPAT_POST_DELETE_KEY_STORAGE_KEY to " 1234567890 ",
                "compat.lastDeleteKey" to "legacy"
            ).compatStoredPostDeleteKey()
        )
    }

    @Test
    fun storedPostDeleteKey_readsTemporaryLegacyKey() {
        assertEquals(
            "legacy",
            mapOf("compat.lastDeleteKey" to " legacy ").compatStoredPostDeleteKey()
        )
    }

    @Test
    fun postDeleteKeyForStorage_trimsAndClampsToApkLength() {
        assertEquals("12345678", compatPostDeleteKeyForStorage(" 1234567890 "))
    }
}
