@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
package com.valoser.futacha

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThemeMode
import com.valoser.futacha.shared.model.ThemePalette
import com.valoser.futacha.shared.ui.board.HistoryDrawerContent
import com.valoser.futacha.shared.ui.board.resolveFutabaThreadColorScheme
import com.valoser.futacha.shared.ui.theme.resolveFutachaColorScheme
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.media.MediaHelpButton
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class HistoryLayoutInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val images = ImageLoader.Builder(context).build()
    @After fun close() { rule.runOnUiThread { rule.activity.setContent {} }; images.shutdown() }

    @Test fun longTitlesBoardNamesAndActionsFitAtNormalAndLargeTextSizes() {
        val title = "週末の模型制作についてゆっくり話すスレッド・完成写真と制作途中の相談はこちら"
        val board = "二次元裏（模型・制作相談）"
        val url = "https://may.2chan.net/b/"
        val entry = ThreadHistoryEntry("layout", "b", title, "", board, url, 1_789_700_000_000, 1234,
            hasAutoSave = true, hasSelfPost = true, lastConfirmedAliveEpochMillis = 1_789_700_000_000)
        var scale by mutableStateOf(1f)
        var palette by mutableStateOf(ThemePalette.FutabaClassic)
        var mode by mutableStateOf(ThemeMode.Light)
        var selected = false
        rule.runOnUiThread { rule.activity.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale), LocalFutachaImageLoader provides images) {
                MaterialTheme(colorScheme = resolveFutabaThreadColorScheme(palette,
                    resolveFutachaColorScheme(mode == ThemeMode.Dark, palette))) {
                    HistoryDrawerContent(listOf(entry), {}, { selected = true })
                }
            }
        } }
        for ((testPalette, testMode, fontScale) in listOf(
            Triple(ThemePalette.FutabaClassic, ThemeMode.Light, 1f),
            Triple(ThemePalette.FutabaClassic, ThemeMode.Light, 1.4f),
            Triple(ThemePalette.FutabaBlack, ThemeMode.Light, 1f),
            Triple(ThemePalette.FutabaBlack, ThemeMode.Dark, 1.4f)
        )) {
            rule.runOnIdle { scale = fontScale; palette = testPalette; mode = testMode }
            rule.onNodeWithText(board, useUnmergedTree = true).assertIsDisplayed()
            rule.onNodeWithText(url, useUnmergedTree = true).assertDoesNotExist()
            rule.onNodeWithText("1234レス", useUnmergedTree = true).assertIsDisplayed()
            rule.onNodeWithTag("history-title-layout", useUnmergedTree = true).assertIsDisplayed()
            val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            java.io.File(context.getExternalFilesDir(null), "history-layout-$testPalette-$testMode-$fontScale.png").outputStream().use {
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            screenshot.recycle()
            val nodes = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true).fetchSemanticsNodes()
            assertTrue(nodes.isNotEmpty())
            for (node in nodes) {
                val layouts = mutableListOf<TextLayoutResult>()
                rule.runOnIdle { node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts) }
                layouts.forEach {
                    // The swipe-delete background is deliberately zero-width until swiped.
                    if (it.layoutInput.text.text != "削除") {
                        // FlowRow may reuse a paragraph measured wider than its short text.
                        // Check the rendered lines rather than that unused paragraph width.
                        for (line in 0 until it.lineCount) {
                            assertTrue("Text width clipped: ${it.layoutInput.text.text} (${it.getLineLeft(line)}..${it.getLineRight(line)} / ${it.size.width})",
                                it.getLineRight(line) - it.getLineLeft(line) <= it.size.width + 1f)
                        }
                        assertFalse("Text height clipped: ${it.layoutInput.text.text}", it.didOverflowHeight)
                        assertTrue((0 until it.lineCount).none(it::isLineEllipsized))
                    }
                }
            }
        }
        rule.onNodeWithTag("history-entry-layout").performClick()
        rule.runOnIdle { assertTrue(selected) }
    }

    @Test fun mediaHelpOpensPackagedLicensesWithoutEnablingAnEditor() {
        rule.runOnUiThread { rule.activity.setContent { MaterialTheme { MediaHelpButton() } } }
        rule.onNodeWithTag("media-help-open").performClick()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("オープンソースライセンスを読む"))
        rule.onNodeWithText("オープンソースライセンスを読む").performClick()
        rule.onNodeWithTag("compat-license-onnxruntime-license").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Permission is hereby granted", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Copyright (c) Microsoft Corporation", substring = true).assertIsDisplayed()
    }
}
