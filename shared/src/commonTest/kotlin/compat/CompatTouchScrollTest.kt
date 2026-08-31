package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.ui.compat.CompatTouchScrollAction
import com.valoser.futacha.shared.ui.compat.compatTouchScrollAction
import kotlin.test.Test
import kotlin.test.assertEquals

class CompatTouchScrollTest {
    @Test
    fun upperAndLowerThirdsPageWithoutChangingCenterTaps() {
        assertEquals(CompatTouchScrollAction.PAGE_UP, compatTouchScrollAction(99f, 300f))
        assertEquals(CompatTouchScrollAction.NONE, compatTouchScrollAction(150f, 300f))
        assertEquals(CompatTouchScrollAction.PAGE_DOWN, compatTouchScrollAction(250f, 300f))
        assertEquals(CompatTouchScrollAction.NONE, compatTouchScrollAction(-1f, 300f))
    }
}
