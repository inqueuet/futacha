package com.valoser.futacha.shared.ui.board

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreferredFileManagerRowTest {
    @Test
    fun desktopHidesThePreferredFileManagerRowThatCannotDoAnything() {
        assertFalse(shouldShowPreferredFileManagerRow(isDesktopPlatform = true))
        assertTrue(shouldShowPreferredFileManagerRow(isDesktopPlatform = false))
    }
}
