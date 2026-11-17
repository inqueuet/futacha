@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.ui.ftutter.*
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 二画面：タブ帯が左右の下部バーの下（ナビゲーションバーの裏）へ押し出されない（スレ01a1e1d7）。 */
class FtutterTwoPaneStripInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun theTabStripSitsRightUnderThePanesBars() {
        // ナビゲーションバーの分が本当にあるよう、アプリと同じく端まで描く
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
        rule.setContent {
            FtutterTheme(null, FtutterBrightness.LIGHT) {
                FtutterTwoPane(FtutterTabletSettings(),
                    catalog = { _ -> Column(Modifier.fillMaxSize()) {
                        Spacer(Modifier.weight(1f))
                        // 下部バーと同じく自分でナビゲーションバーの分を空ける
                        Box(Modifier.fillMaxWidth().navigationBarsPadding().height(40.dp).testTag("bar"))
                    } },
                    thread = null,
                    tabStrip = { Text("tab", Modifier.height(30.dp).testTag("strip")) })
            }
        }
        rule.waitForIdle()
        val bar = rule.onNodeWithTag("bar").fetchSemanticsNode().boundsInRoot
        val strip = rule.onNodeWithTag("strip").fetchSemanticsNode().boundsInRoot
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val navBottom = rule.onNodeWithTag("ftutter-two-pane-strip").fetchSemanticsNode().boundsInRoot
        assertTrue("nav inset present ${navBottom.bottom - strip.bottom}", navBottom.bottom - strip.bottom > 10f)
        assertTrue("strip ${strip.top} right under bar ${bar.bottom}", kotlin.math.abs(strip.top - bar.bottom) < 2f)
        assertTrue("strip area ends at the screen ${navBottom.bottom} / ${root.bottom}", kotlin.math.abs(navBottom.bottom - root.bottom) < 2f)
    }
}
