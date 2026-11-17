package com.valoser.futacha.shared.ui.compat

import kotlin.test.Test
import kotlin.test.assertEquals

class CompatTopBarLayoutSupportTest {
    @Test
    fun topBarGrowsWithAccessibilityFontScaleWithoutShrinkingAtDefault() {
        assertEquals(56f, compatTopBarHeightDp(0.85f))
        assertEquals(56f, compatTopBarHeightDp(1f))
        assertEquals(72.8f, compatTopBarHeightDp(1.3f), absoluteTolerance = 0.001f)
        assertEquals(112f, compatTopBarHeightDp(2f))
    }
}
