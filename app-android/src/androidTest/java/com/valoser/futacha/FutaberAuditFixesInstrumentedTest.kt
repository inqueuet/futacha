package com.valoser.futacha

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import com.valoser.futacha.shared.compat.ExperienceProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * 2026-10-07の監査で直した、ふたばー風モードの画面の振る舞い（戻る・貫通・カタログの保持・板が0件・スイッチの意味）。
 * チュートリアル板の同梱データだけを使い、他のモードの設定には触れない。
 */
class FutaberAuditFixesInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private lateinit var app: FutachaApplication
    private lateinit var originalProfile: ExperienceProfile

    private val futaberKeys = listOf(
        "compat.futaber.lastBoardId", "compat.futaber.tabs", "compat.futaber.drafts", "compat.futaber.favorites"
    )

    @Before fun prepare() {
        app = ApplicationProvider.getApplicationContext()
        originalProfile = app.experienceProfileStore.readActiveProfile()
        runBlocking {
            app.compatibilityStore.ensureInitialized()
            app.compatibilityStore.savePreferences(futaberKeys.associateWith { null })
        }
        switchProfile(ExperienceProfile.FUTABER)
    }

    @After fun restore() {
        if (!::app.isInitialized) return
        runBlocking { app.compatibilityStore.savePreferences(futaberKeys.associateWith { null }) }
        switchProfile(originalProfile)
    }

    private fun switchProfile(target: ExperienceProfile) {
        val store = app.experienceProfileStore
        val current = store.readActiveProfile()
        if (current == target) return
        store.readJournal()?.let(store::completeSwitch)
        val journal = store.beginSwitch(current, target)
        store.completeSwitch(store.persistRequestedProfile(journal))
    }

    private fun waitForTag(tag: String, timeoutMillis: Long = 20_000) {
        rule.waitUntil(timeoutMillis) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitForNoTag(tag: String, timeoutMillis: Long = 10_000) {
        rule.waitUntil(timeoutMillis) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() }
    }

    private fun openTutorialCatalog() {
        waitForTag("futaber-drawer")
        waitForTag("futaber-drawer-board")
        rule.onAllNodesWithTag("futaber-drawer-board")[0].performClick()
        waitForTag("futaber-board-address")
        waitForTag("futaber-catalog-item")
    }

    @Test fun backClosesTheCatalogSheetInsteadOfLeavingTheScreen() {
        openTutorialCatalog()
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performTouchInput { longClick() }
        waitForTag("futaber-post-actions")
        Espresso.pressBack()
        waitForNoTag("futaber-post-actions")
        // まだカタログにいる。
        rule.onNodeWithTag("futaber-board-address").assertIsDisplayed()
    }

    @Test fun backClosesTheCatalogRefreshSheetToo() {
        openTutorialCatalog()
        rule.onNodeWithTag("futaber-refresh").performTouchInput { longClick() }
        waitForTag("futaber-post-actions")
        Espresso.pressBack()
        waitForNoTag("futaber-post-actions")
        rule.onNodeWithTag("futaber-board-address").assertIsDisplayed()
    }

    @Test fun anEmptyPartOfThePanelDoesNotOpenTheThreadBehindIt() {
        openTutorialCatalog()
        rule.onNodeWithTag("futaber-manage").performClick()
        waitForTag("futaber-manage-panel")
        // お気に入りは空：何も無い所をタップしても、背後のカタログのスレッドは開かない。
        rule.onNodeWithTag("futaber-manage-category-Favorites").performClick()
        rule.onNodeWithTag("futaber-manage-panel").performTouchInput { click(center) }
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithTag("futaber-thread-title").fetchSemanticsNodes().isEmpty())
        rule.onNodeWithTag("futaber-manage-panel").assertIsDisplayed()
    }

    @Test fun theCatalogSearchBarSurvivesOpeningAThreadAndGoingBack() {
        openTutorialCatalog()
        rule.onNodeWithTag("futaber-search").performClick()
        waitForTag("futaber-search-field")
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-thread-title")
        rule.onNodeWithTag("futaber-thread-back").performClick()
        waitForTag("futaber-catalog-grid")
        // 検索バーは開いたまま、一覧も読み込み直さずにそこにある。
        rule.onNodeWithTag("futaber-search-field").assertIsDisplayed()
        assertTrue(rule.onAllNodesWithTag("futaber-catalog-item").fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun withNoBoardTheScreenOffersAddingOneAndTheSettings() {
        val original = runBlocking { app.appStateStore.boards.first() }
        try {
            runBlocking { app.appStateStore.updateBoards { emptyList() } }
            waitForTag("futaber-no-boards")
            rule.onNodeWithTag("futaber-no-boards-settings").assertIsDisplayed()
            rule.onNodeWithTag("futaber-no-boards-add").performClick()
            // 板一覧は編集モードで開き、「板を追加」の行がある。
            waitForTag("futaber-board-add")
        } finally {
            runBlocking { app.appStateStore.updateBoards { original } }
        }
    }

    @Test fun aSettingsSwitchIsASwitchForAScreenReader() {
        openTutorialCatalog()
        rule.onNodeWithTag("futaber-open-settings").performClick()
        waitForTag("futaber-settings")
        val isSwitch = SemanticsMatcher("has the Switch role") { it.config.getOrNull(SemanticsProperties.Role) == Role.Switch }
        rule.onNodeWithTag("futaber-settings-switch-update-check").performScrollTo().assert(isSwitch)
    }
}
