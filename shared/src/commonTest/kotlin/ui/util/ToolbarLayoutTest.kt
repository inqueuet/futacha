package com.valoser.futacha.shared.ui.util

import kotlin.test.Test
import kotlin.test.assertEquals

class ToolbarLayoutTest {
    @Test fun existingManyButtonsScrollWithoutChangingTheirOrderOrCount() {
        assertEquals(800f, toolbarContentWidth(240f, 20, 40f))
        assertEquals(384f, toolbarContentWidth(240f, 8, 48f))
        assertEquals(411f, toolbarContentWidth(411f, 5, 48f))
        assertEquals(240f, toolbarContentWidth(240f, 0, 40f))
    }
}
