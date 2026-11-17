package com.valoser.futacha.shared.ui.ftutter

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** App Store review 2026-11-16: the stretch setting (on by default) also removed the iPhone's bounce. */
class FtutterOverscrollTest {
    @Test
    fun onlyAndroidLosesItsEdgeEffect() {
        assertTrue(ftutterRemovesOverscroll(disableStretch = true, android = true))
        assertFalse(ftutterRemovesOverscroll(disableStretch = true, android = false))
        assertFalse(ftutterRemovesOverscroll(disableStretch = false, android = true))
    }
}
