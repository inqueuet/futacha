@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.valoser.futacha.shared.ui.ftutter.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** M52/M80: the thread settings page follows the original — one レスのレイアウト row and dialog, the original's sections and names. */
class FtutterThreadSettingsPageInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val prefs = mutableStateMapOf<String, String>()

    private fun page() = rule.setContent {
        MaterialTheme { FtutterTheme(null, FtutterBrightness.LIGHT) {
            FtutterThreadSettingsPage(prefs, save = { k, v -> prefs[k] = v }, onEditToolbar = {}, onEditPostMenu = {}, onBack = {})
        } }
    }

    private fun slider(key: String) = rule.onNodeWithTag("ftutter-post-layout-${key.removePrefix(FtutterKeys.PREFIX)}")

    @Test fun theSectionsAndNamesAreTheOriginals() {
        page()
        val list = rule.onNodeWithTag("ftutter-settings-page-thread-list")
        listOf("スレッド", "レス", "画像", "あぷ小", "スクロールバー", "引っ張って更新", "更新時の表示", "人気レスの閾値", "履歴",
            "ツールバー・メニュー編集", "あぷ小のサムネイルサイズ", "履歴の保存数", "スクロールバーを左側に表示する", "ツールバー編集", "レス長押しメニュー編集").forEach {
            list.performScrollToNode(hasText(it)); rule.onAllNodesWithText(it).onFirst().assertExists()
        }
        list.performScrollToNode(hasTestTag("ftutter-setting-post-layout"))
        rule.onNodeWithText("余白（上）").assertDoesNotExist()
        rule.onNodeWithText("上：5\n左：5\n右：5\n下：5\nヘッダーと本文の間：3\n本文と画像の間：8").assertExists()
    }

    @Test fun theLayoutDialogSavesTheSixOnlyOnOk() {
        page()
        rule.onNodeWithTag("ftutter-setting-post-layout").performClick()
        rule.onNodeWithTag("ftutter-post-layout-preview").assertExists()
        slider(FtutterKeys.PADDING_TOP).performSemanticsAction(SemanticsActions.SetProgress) { it(12f) }
        slider(FtutterKeys.PADDING_TEXT_FILE).performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        assertNull(prefs[FtutterKeys.PADDING_TOP])
        rule.onNodeWithTag("ftutter-post-layout-ok").performClick()
        rule.waitForIdle()
        assertEquals("12", prefs[FtutterKeys.PADDING_TOP])
        assertEquals("0", prefs[FtutterKeys.PADDING_TEXT_FILE])
        assertEquals("5", prefs[FtutterKeys.PADDING_LEFT])
        rule.onNodeWithText("レスのレイアウト").assertExists()
        rule.onNodeWithText("上：12\n左：5\n右：5\n下：5\nヘッダーと本文の間：3\n本文と画像の間：0").assertExists()
    }

    @Test fun resetPutsTheDefaultsBackAndTheOutsideSavesNothing() {
        prefs[FtutterKeys.PADDING_TOP] = "20"
        page()
        rule.onNodeWithTag("ftutter-setting-post-layout").performClick()
        rule.onNodeWithTag("ftutter-post-layout-reset").performClick()
        rule.onNodeWithTag("ftutter-post-layout-ok").performClick()
        rule.waitForIdle()
        assertEquals("5", prefs[FtutterKeys.PADDING_TOP])
        prefs[FtutterKeys.PADDING_TOP] = "20"
        rule.onNodeWithTag("ftutter-setting-post-layout").performClick()
        rule.onNodeWithTag("ftutter-post-layout-reset").performClick()
        androidx.test.espresso.Espresso.pressBack()
        rule.waitForIdle()
        assertEquals("20", prefs[FtutterKeys.PADDING_TOP])
    }

    @Test fun theLegacySwitchKeepsTheSixRows() {
        prefs[FTUTTER_LEGACY_DIALOGS_KEY] = "true"
        page()
        val list = rule.onNodeWithTag("ftutter-settings-page-thread-list")
        list.performScrollToNode(hasText("余白（上）")); rule.onNodeWithText("余白（上）").assertExists()
        list.performScrollToNode(hasText("本文と画像の間")); rule.onNodeWithText("本文と画像の間").assertExists()
        rule.onNodeWithTag("ftutter-setting-post-layout").assertDoesNotExist()
    }

    @Test fun theTabAndViewerPagesUseTheOriginalsNames() {
        var which by androidx.compose.runtime.mutableStateOf(0)
        rule.setContent {
            MaterialTheme { FtutterTheme(null, FtutterBrightness.LIGHT) {
                if (which == 0) FtutterTabDrawerSettingsPage(prefs, save = { k, v -> prefs[k] = v }, onBack = {})
                else FtutterMediaSettingsPage(prefs, save = { k, v -> prefs[k] = v }, onBack = {})
            } }
        }
        listOf("タブ移動時に更新する（スレ）", "タブのドラッグを無効化", "※ジェスチャー無効時").forEach {
            rule.onNodeWithTag("ftutter-settings-page-tab-drawer-list").performScrollToNode(hasText(it)); rule.onNodeWithText(it).assertExists()
        }
        which = 1
        rule.waitForIdle()
        listOf("画像ビューワー", "動画ビューワー", "デフォルトでループ再生", "ビューワー内のタブの大きさ", "あぷ小", "サムネイルのサイズ",
            "あぷ小のサムネイルを低画質にしてメモリを節約します").forEach {
            rule.onNodeWithTag("ftutter-settings-page-media-list").performScrollToNode(hasText(it)); rule.onNodeWithText(it).assertExists()
        }
    }
}
