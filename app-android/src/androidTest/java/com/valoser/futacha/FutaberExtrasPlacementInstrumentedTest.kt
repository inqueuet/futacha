@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.valoser.futacha.shared.ui.futaber.*
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** L26: in the extensions page, the shared bottom-aligned board list and the tab state sit where they belong. */
class FutaberExtrasPlacementInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @After fun cleanup() { rule.runOnUiThread { rule.activity.setContent {} } }

    private fun top(matcher: SemanticsMatcher) = rule.onNode(matcher, useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y

    @Test fun theBoardListAndTabStateRowsAreInTheirOwnSections() {
        rule.setContent {
            FutaberTheme(FutaberThemeMode.Light) {
                FutaberSettingsScreen(
                    themeMode = FutaberThemeMode.Light, onThemeModeChange = {},
                    settings = FutaberDisplaySettings(), onSettingChange = { _, _ -> },
                    postSettings = FutaberPostSettings(), deleteKey = "", onDeleteKeyChange = {},
                    onPostSettingsChange = { _, _, _ -> },
                    ngWords = emptyList(), ngHeaders = emptyList(), catalogNgWords = emptyList(),
                    onEditNgWords = {}, onEditNgHeaders = {}, onEditCatalogNgWords = {},
                    mediaServices = null, onOpenNotifications = {}, onClose = {}
                )
            }
        }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("futaber-settings").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("futaber-settings-row-extras").performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("futaber-settings-switch-boards-from-bottom").fetchSemanticsNodes().isNotEmpty() }
        // The shared switch has its own section, ahead of "スレッド", and says it is shared by every mode.
        val boards = top(hasTestTag("futaber-settings-switch-boards-from-bottom"))
        val boardsHeader = top(hasText("板一覧"))
        val thread = top(hasText("スレッド"))
        assertTrue("板一覧=$boardsHeader 下寄せ=$boards スレッド=$thread", boardsHeader < boards && boards < thread)
        rule.onNodeWithTag("futaber-settings-boards-from-bottom-note", useUnmergedTree = true).assertTextContains("4つのモードで共通", substring = true)
        // The tab state is with the tab items of "管理パネル".
        val tabState = top(hasTestTag("futaber-settings-switch-ext-tab-status"))
        val panel = top(hasText("管理パネル"))
        val patrol = top(hasText("巡回"))
        assertTrue("管理パネル=$panel タブ=$tabState 巡回=$patrol", panel < tabState && tabState < patrol)
    }
}
