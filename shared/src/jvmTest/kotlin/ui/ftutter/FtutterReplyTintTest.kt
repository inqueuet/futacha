package com.valoser.futacha.shared.ui.ftutter

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FtutterReplyTintTest {
    private val highlight = Color.Yellow
    private val henshin = Color.Red

    @Test
    fun aReplyToMeGetsAPaleTintWhileThePressedColourStillWins() {
        assertEquals(henshin.copy(alpha = 0.14f), ftutterPostBackground(false, FtutterPostMark.REPLY_TO_SELF, true, highlight, henshin))
        assertEquals(highlight.copy(alpha = 0.2f), ftutterPostBackground(true, FtutterPostMark.REPLY_TO_SELF, true, highlight, henshin))
        assertEquals(Color.Transparent, ftutterPostBackground(false, FtutterPostMark.SELF, true, highlight, henshin))
        assertEquals(Color.Transparent, ftutterPostBackground(false, FtutterPostMark.REPLY_TO_SELF, false, highlight, henshin))
    }

    @Test
    fun theTintIsOnByDefault() {
        assertTrue(FtutterExtension.REPLY_TO_SELF_TINT in FtutterExtensions())
    }
}
