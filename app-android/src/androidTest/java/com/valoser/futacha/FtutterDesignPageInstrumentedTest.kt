@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.valoser.futacha.shared.ui.ftutter.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/**
 * M50/M51/M76/M80: the design page follows the original — テーマ (メインカラー row and dialog, 明るさ, 個別設定 page),
 * フォント (フォント指定 last), プライバシーモード (one 画像の透過度 row and dialog), アニメーション.
 */
class FtutterDesignPageInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val prefs = mutableStateMapOf<String, String>()
    private var openedColors = false

    private fun design() = rule.setContent {
        MaterialTheme { FtutterTheme(null, FtutterBrightness.LIGHT) {
            FtutterDesignSettings(prefs, save = { k, v -> prefs[k] = v }, onBack = {}, onChooseFont = {}, onOpenColors = { openedColors = true })
        } }
    }

    @Test fun theSectionsAreTheOriginals() {
        design()
        val list = rule.onNodeWithTag("ftutter-settings-page-design-list")
        listOf("テーマ", "フォント", "プライバシーモード", "アニメーション").forEach {
            list.performScrollToNode(hasText(it)); rule.onNodeWithText(it).assertExists()
        }
        rule.onNodeWithText("色個別設定").assertDoesNotExist()
        list.performScrollToNode(hasTestTag("ftutter-settings-font"))
        rule.onNodeWithText("フォント指定").assertExists()
        rule.onNodeWithText("カスタムフォント").assertDoesNotExist()
    }

    @Test fun theMainColourRowOpensTheChooser() {
        design()
        rule.onNodeWithText("テーマの元となる色").assertExists()
        rule.onNodeWithTag("ftutter-main-color-ff800000").assertDoesNotExist()
        rule.onNodeWithTag("ftutter-setting-main-color").performClick()
        rule.onNodeWithText("メインカラー選択").assertExists()
        assertEquals(19, rule.onAllNodes(hasTestTagPrefix("ftutter-main-color-ff")).fetchSemanticsNodes().size)
        rule.onNodeWithTag("ftutter-main-color-ff800000").performClick()
        rule.waitForIdle()
        assertEquals((0xFF800000L).toString(), prefs[FtutterKeys.MAIN_COLOR])
        rule.onNodeWithText("メインカラー選択").assertDoesNotExist()
    }

    @Test fun oneTransparencySliderMovesAllFiveAndTheyCanBeSetOneByOne() {
        design()
        rule.onNodeWithTag("ftutter-settings-page-design-list").performScrollToNode(hasTestTag("ftutter-setting-privacy"))
        rule.onNodeWithTag("ftutter-setting-privacy").performClick()
        rule.onNodeWithTag("ftutter-privacy-all").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(0.3f) }
        rule.onNodeWithTag("ftutter-privacy-each").performClick()
        rule.onNodeWithTag("ftutter-slider-${FtutterKeys.PRIVACY_THREAD}")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(0.6f) }
        rule.onNodeWithTag("ftutter-privacy-ok").performClick()
        rule.waitForIdle()
        assertEquals("0.3", prefs[FtutterKeys.PRIVACY_CATALOG])
        assertEquals("0.3", prefs[FtutterKeys.PRIVACY_TAB_DRAWER])
        assertEquals("0.6", prefs[FtutterKeys.PRIVACY_THREAD])
    }

    @Test fun cancelKeepsTheTransparency() {
        design()
        rule.onNodeWithTag("ftutter-settings-page-design-list").performScrollToNode(hasTestTag("ftutter-setting-privacy"))
        rule.onNodeWithTag("ftutter-setting-privacy").performClick()
        rule.onNodeWithTag("ftutter-privacy-all").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(0.3f) }
        rule.onNodeWithText("キャンセル").performClick()
        assertNull(prefs[FtutterKeys.PRIVACY_CATALOG])
    }

    @Test fun theLegacySwitchKeepsTheCirclesAndFiveRowsOnThePage() {
        prefs[FTUTTER_LEGACY_DIALOGS_KEY] = "true"
        design()
        rule.onNodeWithTag("ftutter-main-color-ff800000").assertExists()
        rule.onNodeWithTag("ftutter-settings-page-design-list").performScrollToNode(hasTestTag("ftutter-setting-${FtutterKeys.PRIVACY_THREAD}"))
        rule.onNodeWithTag("ftutter-setting-${FtutterKeys.PRIVACY_THREAD}").assertExists()
    }

    @Test fun theColourPageResetsEverythingAfterAsking() {
        prefs[FtutterKeys.colorOverride("henshin")] = (0xFF123456L).toString()
        rule.setContent { MaterialTheme { FtutterTheme(null, FtutterBrightness.LIGHT) {
            FtutterColorSettingsPage(prefs, save = { k, v -> prefs[k] = v }, onBack = {})
        } } }
        listOf("全般", "カタログ", "スレ").forEach {
            rule.onNodeWithTag("ftutter-settings-page-colors-list").performScrollToNode(hasText(it))
        }
        rule.onNodeWithTag("ftutter-colors-reset").performClick()
        rule.onNodeWithText("全ての個別設定をリセットしますか？").assertExists()
        rule.onNodeWithTag("ftutter-colors-reset-ok").performClick()
        rule.waitForIdle()
        assertEquals("", prefs[FtutterKeys.colorOverride("henshin")])
    }

    @Test fun thePickerHasRgbAndThemeTabsAndResetOk() {
        prefs.clear()
        rule.setContent { MaterialTheme { FtutterTheme(null, FtutterBrightness.LIGHT) {
            FtutterColorSettingsPage(prefs, save = { k, v -> prefs[k] = v }, onBack = {})
        } } }
        rule.onNodeWithTag("ftutter-settings-page-colors-list").performScrollToNode(hasTestTag("ftutter-color-henshin"))
        rule.onNodeWithTag("ftutter-color-henshin").performClick()
        rule.onNodeWithTag("ftutter-color-plane").assertExists()
        rule.onNodeWithTag("ftutter-color-hue").assertExists()
        rule.onNodeWithTag("ftutter-color-alpha").assertExists()
        rule.onNodeWithText("キャンセル").assertDoesNotExist()
        rule.onNodeWithTag("ftutter-color-tab-theme").performClick()
        assertEquals(19, rule.onAllNodes(hasTestTagPrefix("ftutter-theme-color-")).fetchSemanticsNodes().size)
        rule.onNodeWithTag("ftutter-theme-color-0").performClick()
        rule.onNodeWithTag("ftutter-color-ok").performClick()
        rule.waitForIdle()
        assertTrue(prefs[FtutterKeys.colorOverride("henshin")]!!.isNotEmpty())
        rule.onNodeWithTag("ftutter-color-henshin").performClick()
        rule.onNodeWithTag("ftutter-color-reset").performClick()
        rule.waitForIdle()
        assertEquals("", prefs[FtutterKeys.colorOverride("henshin")])
    }

    private fun hasTestTagPrefix(prefix: String) = SemanticsMatcher("tag starts with $prefix") {
        it.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.TestTag) { null }?.startsWith(prefix) == true
    }
}
