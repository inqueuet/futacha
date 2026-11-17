package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.ui.compat.CompatPostContextDialog
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 参考アプリ 18-20 「レスメニューの大きさ」: the 3×3 menu grows, stays on screen and still works. */
class CompatPostMenuScaleInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val post = CompatPostSnapshot(position = 1, postNo = "100", timestamp = "t", messageHtml = "本文")

    private fun widthOf(scale: Int, onCopy: () -> Unit = {}): Float {
        rule.setContent {
            MaterialTheme {
                CompatPostContextDialog(post, {}, {}, {}, {}, {}, {}, {}, {}, {}, onCopy, menuScalePercent = scale)
            }
        }
        rule.waitForIdle()
        val node = rule.onNodeWithTag("compat-post-menu").fetchSemanticsNode()
        return with(rule.density) { node.size.width.toDp().value }
    }

    @Test fun theStandardMenuIs300dpWide() {
        val width = widthOf(100)
        assertTrue("$width", width in 299f..301f)
    }

    @Test fun twoHundredPercentWidensTheMenuButKeepsItOnTheScreen() {
        var copied = false
        val width = widthOf(200) { copied = true }
        val screen = with(rule.density) { rule.activity.window.decorView.width.toDp().value }
        assertTrue("$width on $screen", width > 301f && width <= screen - 31f)
        rule.onNodeWithText("コピー").performScrollTo().performClick()
        assertTrue(copied)
    }
}
