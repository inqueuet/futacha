@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.valoser.futacha.shared.ui.ftutter.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** M79/M29/M44: the toolbar and long-press menu editors are the original's full screens. */
class FtutterToolbarEditorInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val prefs = mutableStateMapOf<String, String>()
    private var closed by mutableStateOf(false)

    private fun editor(kind: FtutterOrderEditorKind) = rule.setContent {
        MaterialTheme { FtutterTheme(null, FtutterBrightness.LIGHT) {
            if (!closed) FtutterOrderEditorHost(kind, prefs, { k, v -> prefs[k] = v }, onClose = { closed = true })
        } }
    }

    private fun moveDown(row: Int) {
        val node = rule.onAllNodesWithTag("ftutter-order-row")[row]
        val action = node.fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == "下へ" }
        rule.runOnUiThread { action.action() }
        rule.waitForIdle()
    }

    @Test fun theThreadEditorShowsRowsSizesAndAPreviewAndSavesOnOk() {
        editor(FtutterOrderEditorKind.THREAD)
        rule.onNodeWithText("ツールバー編集（スレッド）").assertExists()
        rule.onNodeWithText("画像レス抽出 / 長押しで画像一覧").assertExists()
        assertEquals(14, rule.onAllNodesWithTag("ftutter-order-row").fetchSemanticsNodes().size)
        rule.onNodeWithText("ボタン高さ").assertExists()
        rule.onNodeWithTag("ftutter-toolbar-preview").assertExists()
        moveDown(0)
        rule.onNodeWithTag("ftutter-order-switch-0").performClick() // ツリー表示切り替え (now first) off
        rule.onNodeWithTag("ftutter-toolbar-width").performSemanticsAction(SemanticsActions.SetProgress) { it(60f) }
        rule.onNodeWithTag("ftutter-toolbar-left").performSemanticsAction(SemanticsActions.SetProgress) { it(20f) }
        assertNull(prefs[FtutterKeys.THREAD_TOOLBAR_ORDER])
        rule.onNodeWithTag("ftutter-order-save").performClick()
        rule.waitForIdle()
        assertTrue(closed)
        assertEquals("tree", prefs[FtutterKeys.THREAD_TOOLBAR_ORDER]?.lines()?.first())
        assertEquals("drawer", prefs[FtutterKeys.THREAD_TOOLBAR_ORDER]?.lines()?.get(1))
        assertEquals("false", prefs[FtutterKeys.THREAD_TOOLBAR_ENABLED]?.lines()?.first())
        assertEquals("60", prefs[FtutterKeys.THREAD_ICON_WIDTH])
        assertEquals("20", prefs[FtutterKeys.THREAD_TOOLBAR_LEFT])
    }

    @Test fun cancelSavesNothingAndResetGoesBackToTheDefaultsInside() {
        prefs[FtutterKeys.CATALOG_ICON_WIDTH] = "70"
        editor(FtutterOrderEditorKind.CATALOG)
        rule.onNodeWithText("ツールバー編集（カタログ）").assertExists()
        moveDown(0)
        rule.onNodeWithTag("ftutter-order-cancel").performClick()
        rule.waitForIdle()
        assertTrue(closed)
        assertNull(prefs[FtutterKeys.CATALOG_TOOLBAR_ORDER])
        rule.runOnUiThread { closed = false }
        rule.waitForIdle()
        rule.onNodeWithTag("ftutter-order-reset").performClick()
        rule.onNodeWithTag("ftutter-order-save").performClick()
        rule.waitForIdle()
        assertEquals("45", prefs[FtutterKeys.CATALOG_ICON_WIDTH])
        assertEquals("drawer", prefs[FtutterKeys.CATALOG_TOOLBAR_ORDER]?.lines()?.first())
    }

    @Test fun thePostMenuEditorOnlyReordersAndKeepsHiddenItems() {
        prefs[FtutterKeys.POST_MENU_ORDER] = FtutterPostMenuItem.entries.joinToString("\n") { it.stored }
        prefs[FtutterKeys.POST_MENU_ENABLED] = FtutterPostMenuItem.entries.joinToString("\n") { (it != FtutterPostMenuItem.DEL).toString() }
        editor(FtutterOrderEditorKind.POST_MENU)
        rule.onNodeWithText("レス長押しメニュー編集").assertExists()
        rule.onNodeWithTag("ftutter-order-switch-0").assertDoesNotExist()
        rule.onNodeWithTag("ftutter-post-menu-preview").assertExists()
        moveDown(0)
        rule.onNodeWithTag("ftutter-order-save").performClick()
        rule.waitForIdle()
        val order = prefs[FtutterKeys.POST_MENU_ORDER]!!.lines()
        val flags = prefs[FtutterKeys.POST_MENU_ENABLED]!!.lines()
        assertEquals(listOf("repNo", "rep"), order.take(2))
        assertEquals("false", flags[order.indexOf("del")])
    }

    @Test fun theLegacySwitchKeepsTheOldDialog() {
        prefs[FTUTTER_LEGACY_DIALOGS_KEY] = "true"
        editor(FtutterOrderEditorKind.THREAD)
        rule.onNodeWithTag("ftutter-order-editor").assertExists()
        rule.onNodeWithTag("ftutter-toolbar-editor").assertDoesNotExist()
    }
}
