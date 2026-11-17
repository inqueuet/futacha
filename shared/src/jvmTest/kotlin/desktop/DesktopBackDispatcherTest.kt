package com.valoser.futacha.shared.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * L124: while the app lock covers the window, the menu's "戻る" (Cmd+[ / Alt+←) and Esc do not reach the screens under
 * it: they moved the hidden screens back and, at としあき(仮)'s top level, closed the app.
 */
class DesktopBackDispatcherTest {
    @Test
    fun backIsIgnoredWhileTheAppIsLockedAndWorksAgainOnceUnlocked() {
        val key = Any()
        var backs = 0
        DesktopBackDispatcher.add(key) { backs++ }
        try {
            assertTrue(DesktopBackDispatcher.dispatch())
            assertEquals(1, backs)

            DesktopBackDispatcher.setAppUnlocked(false)
            assertFalse(DesktopBackDispatcher.dispatch(), "a locked app took the back")
            assertEquals(1, backs)

            DesktopBackDispatcher.setAppUnlocked(true)
            assertTrue(DesktopBackDispatcher.dispatch())
            assertEquals(2, backs)
        } finally {
            DesktopBackDispatcher.remove(key)
            DesktopBackDispatcher.setAppUnlocked(true)
        }
    }
}
