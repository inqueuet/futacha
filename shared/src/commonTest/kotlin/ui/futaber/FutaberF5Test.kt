package com.valoser.futacha.shared.ui.futaber

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** F-B5: the shared fast scroll bars' switches (off until turned on) and the keys they share with the other modes. */
class FutaberF5Test {
    @Test
    fun theFastScrollBarsAreOffByDefaultAndReadTheSharedKeys() {
        val d = FutaberDisplaySettings.from(emptyMap())
        assertFalse(d.threadFastScroll || d.catalogFastScroll)
        assertEquals("compat.thread.threadFastScroll", FutaberSettingKeys.THREAD_FAST_SCROLL)
        assertEquals("compat.catalog.catalogFastScroll", FutaberSettingKeys.CATALOG_FAST_SCROLL)
        val on = FutaberDisplaySettings.from(mapOf(
            FutaberSettingKeys.THREAD_FAST_SCROLL to "ON", FutaberSettingKeys.CATALOG_FAST_SCROLL to "ON"
        ))
        assertTrue(on.threadFastScroll && on.catalogFastScroll)
    }
}
