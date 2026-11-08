@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.ui.ftutter.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** M56/M30: the NG pages are the original's pages per kind, and the add dialog is the original's. */
class FtutterNgPagesInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val rules = mutableStateListOf<FtutterNgRule>()
    private val notices = mutableListOf<String>()
    private val boards = listOf(BoardSummary(id = "neko", name = "ねこ", category = "", url = "https://may.2chan.net/27/futaba.php", description = ""))

    private fun page(type: FtutterNgType) = rule.setContent {
        MaterialTheme { FtutterTheme(null, FtutterBrightness.LIGHT) {
            FtutterNgTypeScreen(type, rules.toList(), boards, onEdit = { t -> val n = t(rules.toList()); rules.clear(); rules.addAll(n) },
                onNotice = { notices += it }, onBack = {})
        } }
    }

    @Test fun aWordPageAddsWithTheOriginalsDialogAndRowsSwitchAndDelete() {
        page(FtutterNgType.WORD)
        rule.onNodeWithText("この文字列が含まれるレスを非表示にします").assertExists()
        rule.onNodeWithTag("ftutter-ng-add").performClick()
        assertEquals(2, rule.onAllNodesWithText("NGワード").fetchSemanticsNodes().size) // the bar and the dialog title
        rule.onNodeWithTag("ftutter-ng-regex").assertExists()
        rule.onNodeWithText("全ての板").assertExists()
        rule.onNodeWithText("種類", substring = true).assertDoesNotExist()
        rule.onNodeWithText("有効").assertDoesNotExist()
        rule.onNodeWithTag("ftutter-ng-text").performTextInput("abc")
        rule.onNodeWithTag("ftutter-ng-save").performClick()
        rule.waitUntil(5_000) { rules.size == 1 }
        assertEquals(FtutterNgRule("abc", FtutterNgType.WORD.stored), rules.single())
        rule.onNodeWithText("abc").assertExists()
        rule.onNodeWithTag("ftutter-ng-row-switch").performClick()
        rule.waitUntil(5_000) { !rules.single().isActive }
        rule.onNodeWithTag("ftutter-ng-delete").performClick()
        rule.onNodeWithText("削除しますか？").assertExists()
        rule.onNodeWithTag("ftutter-ng-delete-ok").performClick()
        rule.waitUntil(5_000) { rules.isEmpty() }
    }

    @Test fun anEmptyEntryClosesTheDialogAndTellsWhy() {
        page(FtutterNgType.TITLE)
        rule.onNodeWithTag("ftutter-ng-add").performClick()
        rule.onNodeWithTag("ftutter-ng-save").performClick()
        rule.waitUntil(5_000) { notices.isNotEmpty() }
        assertEquals("文字列が空です！", notices.single())
        rule.onNodeWithTag("ftutter-ng-dialog").assertDoesNotExist()
        assertTrue(rules.isEmpty())
    }

    @Test fun tappingARowEditsItAndTheBoardCanBeChosen() {
        rules += FtutterNgRule("old", FtutterNgType.TITLE.stored)
        page(FtutterNgType.TITLE)
        rule.onNodeWithTag("ftutter-ng-row").performClick()
        rule.onNodeWithTag("ftutter-ng-text").performTextReplacement("new")
        rule.onNodeWithTag("ftutter-ng-board").performClick()
        rule.onNodeWithText("ねこ").performClick()
        rule.onNodeWithTag("ftutter-ng-save").performClick()
        rule.waitUntil(5_000) { rules.single().text == "new" }
        assertEquals("ねこ", rules.single().ita?.name)
        rule.onNodeWithText("ねこ").assertExists()
    }

    @Test fun theIdDialogHintsTheFormAndHasNoRegexAndPickupIsTitledPickup() {
        page(FtutterNgType.ID)
        rule.onNodeWithText("このID・IPからの投稿を非表示にします").assertExists()
        rule.onNodeWithTag("ftutter-ng-add").performClick()
        rule.onNodeWithText("'ID:'または'IP:'を含めて入力", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("ftutter-ng-regex").assertDoesNotExist()
        rule.onNodeWithTag("ftutter-ng-text").performTextInput("abc")
        rule.onNodeWithTag("ftutter-ng-save").performClick()
        rule.waitUntil(5_000) { notices.isNotEmpty() }
        assertEquals("'ID:'または'IP:'を含めて入力してください！", notices.single())
        assertEquals("ピックアップ", ftutterNgDialogTitle(FtutterNgType.PICKUP))
    }

    @Test fun theSettingsPageHasTheOriginalsSectionsAndLegacyKeepsTheList() {
        val prefs = mutableStateMapOf<String, String>()
        var opened by mutableStateOf<FtutterNgType?>(null)
        rule.setContent {
            MaterialTheme { FtutterTheme(null, FtutterBrightness.LIGHT) {
                FtutterNgSettingsPage(prefs, { k, v -> prefs[k] = v }, 3, 0, onOpenNgList = {}, onOpenHidden = {}, onBack = {},
                    ngCounts = mapOf(FtutterNgType.WORD to 2, FtutterNgType.ID to 1), onOpenNgType = { opened = it })
            } }
        }
        listOf("NG", "NGの設定", "ピックアップ", "ピックアップの設定").forEach { rule.onNodeWithText(it).assertExists() }
        rule.onNodeWithText("NGされたレスを引用するレスを全て連鎖非表示にする").assertExists()
        rule.onNodeWithTag("ftutter-settings-ng-WORD").assertTextContains("2 個", substring = true)
        rule.onNodeWithTag("ftutter-settings-ng-TITLE").assertTextContains("0 個", substring = true)
        rule.onNodeWithTag("ftutter-settings-ng-ID").performClick()
        assertEquals(FtutterNgType.ID, opened)
        rule.runOnUiThread { prefs[FTUTTER_LEGACY_DIALOGS_KEY] = "true" }
        rule.waitForIdle()
        rule.onNodeWithTag("ftutter-settings-ng-WORD").assertDoesNotExist()
        rule.onNodeWithTag("ftutter-settings-ng-list").assertExists()
    }
}
