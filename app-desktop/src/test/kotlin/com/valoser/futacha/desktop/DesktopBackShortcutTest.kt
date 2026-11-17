package com.valoser.futacha.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class DesktopBackShortcutTest {
    @Test
    fun macBackDoesNotTakeCommandLeftFromTextFields() {
        assertEquals(KeyShortcut(Key.LeftBracket, meta = true), desktopBackShortcut(isMac = true))
        assertNotEquals(KeyShortcut(Key.DirectionLeft, meta = true), desktopBackShortcut(isMac = true))
    }

    @Test
    fun windowsKeepsAltLeft() {
        assertEquals(KeyShortcut(Key.DirectionLeft, alt = true), desktopBackShortcut(isMac = false))
    }
}
