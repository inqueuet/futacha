package com.valoser.futacha

import android.content.pm.ApplicationInfo
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
import com.valoser.futacha.shared.compat.ExperienceProfile
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class MainActivitySmokeTest {
    private lateinit var app: FutachaApplication
    private lateinit var originalProfile: ExperienceProfile

    @get:Rule
    // The activity must be launched by the Android Compose rule itself. Using
    // an empty rule plus ActivityScenario.launch in @Before races the rule's
    // owner registration and intermittently leaves the test with no Compose
    // hierarchy, even though MainActivity is visible on the device.
    val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun launchInFutachaProfile() {
        app = ApplicationProvider.getApplicationContext()
        originalProfile = app.experienceProfileStore.readActiveProfile()
        switchProfileIfNeeded(ExperienceProfile.FUTACHA)
    }

    @After
    fun restoreSelectedProfile() {
        if (::app.isInitialized && ::originalProfile.isInitialized) {
            switchProfileIfNeeded(originalProfile)
        }
    }

    private fun switchProfileIfNeeded(target: ExperienceProfile) {
        val store = app.experienceProfileStore
        val current = store.readActiveProfile()
        if (current == target && store.readJournal() == null) return
        store.readJournal()?.let(store::completeSwitch)
        val journal = store.beginSwitch(current, target)
        store.completeSwitch(store.persistRequestedProfile(journal))
    }

    private fun openSeededThread() {
        rule.onNodeWithContentDescription("履歴を開く").assertIsDisplayed().performClick()
        rule.waitUntil(10_000L) {
            rule.onAllNodesWithText("チュートリアル")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        // The drawer is layered over the board card, whose description also contains
        // this title. The first matching node is the visible history entry.
        rule.onAllNodesWithText("チュートリアル")[0].assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription("返信").assertIsDisplayed()
    }

    @Test
    fun launch_showsBoardManagementAndMenuActions() {
        rule.onNodeWithText("ふたば").assertIsDisplayed()
        rule.onNodeWithContentDescription("メニュー").assertIsDisplayed().performClick()
        rule.onNodeWithText("新規追加").assertIsDisplayed()
        rule.onNodeWithText("保存済み").assertIsDisplayed()
    }

    @Test
    fun applicationDataIsExcludedFromAutomaticOsBackup() {
        val applicationInfo = app.packageManager.getApplicationInfo(app.packageName, 0)
        assertEquals(
            "Reference APK data must not be silently restored onto another device",
            0,
            applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP
        )
    }

    @Test
    fun addBoardMenuAction_opensDialog() {
        rule.onNodeWithContentDescription("メニュー").assertIsDisplayed().performClick()
        rule.onNodeWithText("新規追加").assertIsDisplayed().performClick()
        rule.onNodeWithText("板を追加").assertIsDisplayed()
        rule.onNodeWithText("板の名前").assertIsDisplayed()
        rule.onNodeWithText("板のURL").assertIsDisplayed()
        rule.onNodeWithText("板一覧から一括追加").assertIsDisplayed().performClick()
        rule.onNodeWithText("未登録の板をまとめて追加します。").assertIsDisplayed()
        assertTrue(
            rule.onAllNodesWithText("板一覧URL")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isEmpty()
        )
        assertTrue(
            rule.onAllNodesWithText("ふたばの板一覧ページから", substring = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isEmpty()
        )
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
    fun historyEntry_opensThreadAndGlobalSettings() {
        openSeededThread()
        rule.onNodeWithContentDescription("その他").assertIsDisplayed().performClick()
        // The action bar also contributes a "設定" semantics node. The dropdown popup is
        // composed after it and is therefore the second node while the menu is open.
        rule.onAllNodesWithText("設定")[1].assertIsDisplayed().performClick()
        rule.onNodeWithText("モード").assertIsDisplayed()
    }

    @Test
    fun historyEntry_opensReadAloudPlayerWithPlaybackControls() {
        openSeededThread()

        // ModalNavigationDrawer also keeps a hidden settings icon in semantics;
        // the first match is the visible thread action-bar button.
        rule.onAllNodesWithContentDescription("設定")[0].assertIsDisplayed().performClick()
        rule.onNodeWithText("設定メニュー").assertIsDisplayed()
        rule.onNodeWithText("読み上げ").assertIsDisplayed().performClick()
        rule.onNodeWithText("読み上げプレーヤー").assertIsDisplayed()
        rule.onNodeWithText("再生").assertIsDisplayed()
        rule.onNodeWithText("一時停止").assertIsDisplayed()
        rule.onNodeWithText("停止").assertIsDisplayed()
        rule.onNodeWithText("表示位置", substring = true).assertIsDisplayed()
    }

    @Test
    fun historyEntry_searchGalleryAndFilter_openAndReturnToThread() {
        openSeededThread()

        rule.onNodeWithContentDescription("スレ内検索").assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription("検索を閉じる").assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription("返信").assertIsDisplayed()

        rule.onNodeWithContentDescription("添付").assertIsDisplayed().performClick()
        rule.onNodeWithText("添付一覧 (", substring = true).assertIsDisplayed()
        pressBack()
        rule.onNodeWithContentDescription("返信").assertIsDisplayed()

        rule.onNodeWithContentDescription("レスフィルター").assertIsDisplayed().performClick()
        rule.onNodeWithText("絞り込みたい条件", substring = true).assertIsDisplayed()
        rule.onNodeWithText("閉じる").assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription("返信").assertIsDisplayed()
    }

    @Test
    fun threadLeftEdgeSwipe_opensHistoryDrawerWithoutNavigatingBack() {
        openSeededThread()

        rule.onNodeWithTag("futacha-thread-content").performTouchInput {
            val y = visibleSize.height / 2f
            // Deliberately cross the former 96dp custom-Back threshold slowly.
            // Use an interpolated swipe so this remains a drag throughout the
            // 500ms duration instead of becoming a long press followed by one
            // move event on devices whose long-press timeout is also 500ms.
            // Before the issue #36 fix this navigated to Catalog while the drawer
            // was only partially visible.
            swipe(
                start = Offset(1f, y),
                end = Offset(visibleSize.width * 0.8f, y),
                durationMillis = 500
            )
        }
        rule.waitForIdle()

        rule.onNodeWithTag("history-drawer").assertIsDisplayed()
        rule.onNodeWithContentDescription("返信").fetchSemanticsNode()
    }
}
