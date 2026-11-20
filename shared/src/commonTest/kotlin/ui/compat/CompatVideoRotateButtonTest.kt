package com.valoser.futacha.shared.ui.compat

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompatVideoRotateButtonTest {
    @Test
    fun onlyLandscapeVideosOfferTheButtonAndARotatedOneCanTurnBack() {
        assertTrue(compatShowVideoRotateButton(true, 1920 to 1080, rotated = false))
        assertFalse(compatShowVideoRotateButton(true, 1080 to 1920, rotated = false))
        assertFalse(compatShowVideoRotateButton(true, null, rotated = false))
        assertTrue(compatShowVideoRotateButton(true, null, rotated = true))
        assertFalse(compatShowVideoRotateButton(false, 1920 to 1080, rotated = false))
    }
}
