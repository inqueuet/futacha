package com.valoser.futacha

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class MainActivitySmokeTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launch_showsBoardManagementAndMenuActions() {
        rule.onNodeWithText("ふたば").assertIsDisplayed()
        rule.onNodeWithContentDescription("メニュー").assertIsDisplayed().performClick()
        rule.onNodeWithText("新規追加").assertIsDisplayed()
        rule.onNodeWithText("保存済み").assertIsDisplayed()
    }

    @Test
    fun addBoardMenuAction_opensDialog() {
        rule.onNodeWithContentDescription("メニュー").assertIsDisplayed().performClick()
        rule.onNodeWithText("新規追加").assertIsDisplayed().performClick()
        rule.onNodeWithText("板を追加").assertIsDisplayed()
        rule.onNodeWithText("板の名前").assertIsDisplayed()
        rule.onNodeWithText("板のURL").assertIsDisplayed()
    }

    @Test
    fun savedThreadsMenuAction_opensSavedThreadsScreen() {
        rule.onNodeWithContentDescription("メニュー").assertIsDisplayed().performClick()
        rule.onNodeWithText("保存済み").assertIsDisplayed().performClick()
        rule.onNodeWithText("保存済みスレッド").assertIsDisplayed()
        rule.onNodeWithText("保存済みスレッドがありません").assertIsDisplayed()
    }

    @Test
    fun savedThreadsScreen_backNavigation_returnsToBoardManagement() {
        rule.onNodeWithContentDescription("メニュー").assertIsDisplayed().performClick()
        rule.onNodeWithText("保存済み").assertIsDisplayed().performClick()
        rule.onNodeWithText("保存済みスレッド").assertIsDisplayed()
        rule.onNodeWithContentDescription("戻る").assertIsDisplayed().performClick()
        rule.onNodeWithText("ふたば").assertIsDisplayed()
    }

    @Test
    fun historyEntry_opensThreadAndSettingsSheet() {
        rule.onNodeWithContentDescription("履歴を開く").assertIsDisplayed().performClick()
        rule.onNodeWithText("チュートリアル").assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription("返信").assertIsDisplayed()
        rule.onNodeWithContentDescription("その他").assertIsDisplayed().performClick()
        rule.onNodeWithText("設定").assertIsDisplayed().performClick()
        rule.onNodeWithText("設定メニュー").assertIsDisplayed()
    }
}
