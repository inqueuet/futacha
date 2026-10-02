package com.valoser.futacha.shared.ui.board

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NgManagementPrefillTest {
    @Test
    fun firstOpenPrefillsTheInitialInput() {
        val applied = ngManagementPrefillToApply(null, "Header", "ID:abc", "ID:abc")
        assertEquals("ID:abc", applied?.second)
    }

    @Test
    fun restoredStateAfterRotationKeepsTheTypedInput() {
        val (key, _) = assertNotNull(ngManagementPrefillToApply(null, "Header", "ID:abc", "ID:abc"))
        // The saved key survives the configuration change, so the typed text is not overwritten.
        assertNull(ngManagementPrefillToApply(key, "Header", "ID:abc", "ID:abc"))
    }

    @Test
    fun switchingSectionOrInitialInputStillPrefills() {
        val (key, _) = assertNotNull(ngManagementPrefillToApply(null, "Header", "ID:abc", "ID:abc"))
        assertEquals("", ngManagementPrefillToApply(key, "Word", "ID:abc", "")?.second)
        assertEquals("ID:xyz", ngManagementPrefillToApply(key, "Header", "ID:xyz", "ID:xyz")?.second)
    }
}
