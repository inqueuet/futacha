@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.ui.compat.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

/** Existing help API, actual glyph pixels and guide taps; no network request or preference mutation. */
class CompatM49HelpContrastInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        rule.waitForIdle()
    }
    @Test fun whiteTextPreferenceDoesNotHideTheGuideOnALightTheme() = verify("ふたば", "白")
    @Test fun lightGreyTextPreferenceDoesNotHideTheGuideOnALightTheme() = verify("ブルー", "薄い灰")
    @Test fun blackTextPreferenceDoesNotHideTheGuideOnTheBlackTheme() = verify("ブラック", "黒")

    private fun verify(theme: String, preference: String) {
        val palette = compatibilityPaletteFor(theme, preference)
        rule.setContent {
            CompatibilityProfileTheme(theme, preference) {
                CompositionLocalProvider(LocalExperienceProfileUiController provides
                    ExperienceProfileUiController(activeProfile = ExperienceProfile.TOSHIAKI_COMPAT)) {
                    CompatHelpScreen(onBack = {}, appVersion = "13.1")
                }
            }
        }
        val button = rule.onNodeWithTag("help-operation-guide").assertIsDisplayed()
        // Wait for the actual displayed window, including AndroidView, using window coordinates.
        // Compose layer capture can draw WebView at the wrong origin; the first OS frame can also predate theme drawing.
        val buttonBounds = button.fetchSemanticsNode().boundsInWindow
        val label = rule.onNodeWithText("操作ガイド：書き込み・添付・保存・戻る", useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        rule.runOnIdle { label.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts) }
        assertTrue(layouts.isNotEmpty())
        val textContrasts = layouts.map { contrast(it.layoutInput.style.color, palette.background) }
        assertTrue("$theme/$preference text contrast: $textContrasts", textContrasts.all { it >= 4.5f })
        val sampleX = (buttonBounds.left + 4).toInt()
        val sampleY = buttonBounds.center.y.toInt()
        val labelBounds = label.boundsInWindow
        var readablePixels = 0
        var background = Color.Unspecified
        try {
            rule.waitUntil(5_000) {
                val frame = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                try {
                    background = Color(frame.getPixel(sampleX, sampleY))
                    val left = labelBounds.left.toInt().coerceAtLeast(0)
                    val top = labelBounds.top.toInt().coerceAtLeast(0)
                    val width = (labelBounds.right.toInt().coerceAtMost(frame.width) - left).coerceAtLeast(0)
                    val height = (labelBounds.bottom.toInt().coerceAtMost(frame.height) - top).coerceAtLeast(0)
                    val glyphs = IntArray(width * height)
                    frame.getPixels(glyphs, 0, width, left, top, width, height)
                    readablePixels = glyphs.count { contrast(Color(it), background) >= 4.5f }
                    // A matching background alone can precede the label's first displayed frame.
                    distance(background, palette.background) < 0.02f && readablePixels > 25
                } finally { frame.recycle() }
            }
        } catch (failure: Throwable) {
            throw AssertionError("$theme/$preference displayed background=$background; readable glyph pixels=$readablePixels", failure)
        }
        button.performClick()
        rule.onNodeWithText("操作ガイド：としあき(仮)モード").assertIsDisplayed()
        rule.onNodeWithTag("operation-guide-write").assertIsDisplayed()
        rule.onNodeWithTag("operation-guide-close").performClick()
        rule.onNodeWithTag("help-operation-guide").assertIsDisplayed()
        assertTrue("$theme/$preference text contrast: $textContrasts", textContrasts.all { it >= 4.5f })
        assertTrue("$theme/$preference has only $readablePixels readable glyph pixels", readablePixels > 25)
    }
    private fun contrast(a: Color, b: Color): Float =
        (maxOf(a.luminance(), b.luminance()) + 0.05f) / (minOf(a.luminance(), b.luminance()) + 0.05f)
    private fun distance(a: Color, b: Color): Float =
        maxOf(kotlin.math.abs(a.red - b.red), kotlin.math.abs(a.green - b.green), kotlin.math.abs(a.blue - b.blue))
}
