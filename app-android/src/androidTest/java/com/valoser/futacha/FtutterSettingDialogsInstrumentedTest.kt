@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.valoser.futacha.shared.ui.ftutter.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/**
 * M74/M75/L1: the settings' number and choice dialogs look like the original's — a number dialog has its
 * title, a plain slider and リセット/OK only; a choice dialog has no title; the brightness has plain rows.
 * 「設定のダイアログを前の形にする」 brings back the earlier form.
 */
class FtutterSettingDialogsInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val saved = mutableListOf<String>()

    private fun slider(prefs: Map<String, String>, own: Boolean = false) = rule.setContent {
        MaterialTheme { FtutterTheme(null, FtutterBrightness.LIGHT) {
            FtutterSliderSetting("文字の大きさ", "test-size", 1.0, 40.0, 1.0, 15.0, prefs, save = { _, v -> saved += v }, own = own)
        } }
    }

    @Test fun aNumberDialogHasOnlyItsTitleASliderResetAndOk() {
        slider(mapOf("test-size" to "15"))
        rule.onNodeWithTag("ftutter-setting-test-size").performClick()
        // The row's name and the dialog's title.
        rule.onAllNodesWithText("文字の大きさ").assertCountEquals(2)
        rule.onNodeWithTag("ftutter-slider-test-size").assertExists()
        rule.onNodeWithTag("ftutter-slider-reset").assertExists()
        rule.onNodeWithTag("ftutter-slider-ok").assertExists()
        rule.onNodeWithTag("ftutter-slider-minus").assertDoesNotExist()
        rule.onNodeWithTag("ftutter-slider-plus").assertDoesNotExist()
        rule.onNodeWithText("キャンセル").assertDoesNotExist()
        // The number is shown only in the row below the name, not in the dialog.
        assertEquals(1, rule.onAllNodesWithText("15").fetchSemanticsNodes().size)
        rule.onNodeWithTag("ftutter-slider-test-size").performSemanticsAction(SemanticsActions.SetProgress) { it(20.4f) }
        rule.onNodeWithTag("ftutter-slider-ok").performClick()
        assertEquals(listOf("20"), saved)
    }

    @Test fun theLegacySwitchAndOurOwnSettingsKeepTheFullDialog() {
        slider(mapOf("test-size" to "15", FTUTTER_LEGACY_DIALOGS_KEY to "true"))
        rule.onNodeWithTag("ftutter-setting-test-size").performClick()
        rule.onNodeWithTag("ftutter-slider-plus").performClick()
        rule.onNodeWithText("16").assertExists()
        rule.onNodeWithText("キャンセル").assertExists()
        rule.onNodeWithTag("ftutter-slider-ok").performClick()
        assertEquals(listOf("16"), saved)
    }

    @Test fun anOwnSettingKeepsTheFullDialogWithTheSwitchOff() {
        slider(mapOf("test-size" to "15"), own = true)
        rule.onNodeWithTag("ftutter-setting-test-size").performClick()
        rule.onNodeWithTag("ftutter-slider-minus").assertExists()
        rule.onNodeWithText("キャンセル").assertExists()
    }

    @Test fun aChoiceDialogHasNoTitleAndTheSortSaysカタログ() {
        rule.setContent { MaterialTheme { FtutterTheme(null, FtutterBrightness.LIGHT) {
            FtutterChoiceSetting("デフォルトの並び順", "test-order",
                FtutterOrder.entries.map { it.stored to it.label }, 0, emptyMap(), save = { _, v -> saved += v },
                dialogOptions = FtutterOrder.entries.map { it.stored to it.dialogLabel })
        } } }
        rule.onNodeWithTag("ftutter-setting-test-order").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("ftutter-choice-dialog").fetchSemanticsNodes().isNotEmpty() }
        // The row's name stays; the dialog has no title of its own.
        assertEquals(1, rule.onAllNodesWithText("デフォルトの並び順").fetchSemanticsNodes().size)
        rule.onNode(hasTestTag("ftutter-option-0") and hasText("カタログ")).assertExists()
        rule.onNodeWithTag("ftutter-option-3").performClick()
        assertEquals(listOf("3"), saved)
    }

    @Test fun theBrightnessRowsArePlainText() {
        rule.setContent { MaterialTheme { FtutterTheme(null, FtutterBrightness.LIGHT) {
            FtutterChoiceSetting("明るさ", "test-bright", listOf(0 to "ライトモード", 1 to "ダークモード", 2 to "ダークモード（＋背景真っ黒）"),
                0, emptyMap(), save = { _, v -> saved += v }, plainRows = true)
        } } }
        rule.onNodeWithTag("ftutter-setting-test-bright").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("ftutter-choice-dialog").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(rule.onAllNodes(isSelectable()).fetchSemanticsNodes().isEmpty())
        rule.onNodeWithText("ダークモード（＋背景真っ黒）").performClick()
        assertEquals(listOf("2"), saved)
    }
}
