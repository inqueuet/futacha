package com.valoser.futacha

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.ExperienceProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * ふたばー風モードの機能そろえ B1 前半（拡張機能のスイッチ・板の追加して開く）。
 * チュートリアル板の同梱データだけを使い、実サーバーへは送らない（つまんね。登録・板の追加後の取得は押さない）。
 */
private const val TUTORIAL_URL = "https://www.example.com/t/futaba.php"

class FutaberParityB1aInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private lateinit var app: FutachaApplication
    private lateinit var originalProfile: ExperienceProfile

    private val keys = listOf(
        "compat.futaber.ext.tabsManage",
        "compat.futaber.ext.archiveMenu",
        "compat.futaber.ext.savedDetail",
        "compat.futaber.ext.otherFavorites",
        "compat.futaber.ext.scrollMenu",
        "compat.futaber.ext.searchNav",
        "compat.futaber.tapScroll",
        "compat.control.controlPostDestinationConfirm",
        "compat.futaber.lastBoardId",
        "compat.futaber.tabs",
        "compat.futaber.favorites",
        "compat.futaber.seen"
    )

    @Before fun prepare() {
        app = ApplicationProvider.getApplicationContext()
        originalProfile = app.experienceProfileStore.readActiveProfile()
        runBlocking {
            app.compatibilityStore.ensureInitialized()
            app.compatibilityStore.savePreferences(keys.associateWith { null })
            // The tests read the bundled tutorial board; put it back if an earlier run lost it.
            if (app.appStateStore.boards.first().none { it.url == TUTORIAL_URL }) {
                app.appStateStore.updateBoards { boards ->
                    listOf(
                        com.valoser.futacha.shared.model.BoardSummary(
                            "t", "チュートリアル＠ふたちゃ", "チュートリアル", TUTORIAL_URL, "チュートリアル"
                        )
                    ) + boards
                }
            }
        }
        switchProfile(ExperienceProfile.FUTABER)
    }

    @After fun restore() {
        if (!::app.isInitialized) return
        runBlocking {
            app.appStateStore.updateBoards { boards -> boards.filterNot { it.url.contains("zip.2chan.net/1/") } }
            app.appStateStore.updateHistory { list -> list.filterNot { it.threadId == "999001" || it.threadId == "999002" } }
            app.compatibilityStore.savePreferences(keys.associateWith { null })
        }
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

    private fun absent(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()

    private fun preference(key: String): String? = runBlocking { app.compatibilityStore.loadPreference(key) }

    private fun turnOn(vararg extensionKeys: String) = runBlocking {
        app.compatibilityStore.savePreferences(extensionKeys.associate { it to "ON" })
    }

    private fun openTutorialThread() {
        waitForTag("futaber-drawer")
        waitForTag("futaber-drawer-board")
        rule.onAllNodesWithTag("futaber-drawer-board")[0].performClick()
        waitForTag("futaber-catalog-item")
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post")
    }

    private fun openMenu() {
        rule.onNodeWithTag("futaber-thread-menu").performClick()
        waitForTag("futaber-operation-menu")
    }

    private fun clickMenu(id: String) {
        waitForTag("futaber-menu-$id")
        runCatching { rule.onNodeWithTag("futaber-menu-$id").performScrollTo() }
        rule.onNodeWithTag("futaber-menu-$id").performClick()
    }

    private fun dismissMenu() {
        rule.onNodeWithTag("futaber-menu-scrim").performTouchInput { click(Offset(centerX, 40f)) }
        rule.waitUntil(5_000) { absent("futaber-operation-menu") }
    }

    /** The list's scroll position as its semantics report it (0 at the top). */
    private fun listScroll(): Float {
        rule.waitForIdle()
        return rule.onNodeWithTag("futaber-thread-list").fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.value?.invoke() ?: 0f
    }

    @Test fun byDefaultNoneOfTheNewEntriesIsThere() {
        openTutorialThread()
        openMenu()
        for (id in listOf("archive", "page-up", "page-down", "top")) assertTrue("menu $id", absent("futaber-menu-$id"))
        rule.onNodeWithTag("futaber-menu-search").performTextInput("テスト")
        rule.onNodeWithTag("futaber-menu-search").performImeAction()
        waitForTag("futaber-filter-chip")
        assertTrue(absent("futaber-search-next"))
        rule.onNodeWithTag("futaber-filter-chip").performClick()
        // The panel's favourites have no other modes' section.
        rule.onNodeWithTag("futaber-manage").performClick()
        rule.onNodeWithTag("futaber-manage-category-Favorites").performClick()
        assertTrue(absent("futaber-other-favorites-header"))
    }

    @Test fun theSettingsTurnTheNewSwitchesOn() {
        waitForTag("futaber-drawer")
        rule.onAllNodesWithTag("futaber-drawer-board")[0].performClick()
        waitForTag("futaber-catalog-item")
        rule.onNodeWithTag("futaber-open-settings").performClick()
        waitForTag("futaber-settings")
        rule.onNodeWithTag("futaber-settings-row-extras").performScrollTo().performClick()
        waitForTag("futaber-settings-switch-ext-search-nav")
        val switches = mapOf(
            "ext-search-nav" to "compat.futaber.ext.searchNav",
            "ext-scroll-menu" to "compat.futaber.ext.scrollMenu",
            "ext-archive-menu" to "compat.futaber.ext.archiveMenu",
            "ext-post-destination" to "compat.control.controlPostDestinationConfirm",
            "ext-tabs-manage" to "compat.futaber.ext.tabsManage",
            "ext-saved-detail" to "compat.futaber.ext.savedDetail",
            "ext-other-favorites" to "compat.futaber.ext.otherFavorites"
        )
        for ((id, key) in switches) {
            assertEquals(null, preference(key))
            rule.onNodeWithTag("futaber-settings-switch-$id").performScrollTo().performClick()
            rule.waitUntil(5_000) { preference(key) == "ON" }
        }
        rule.onNodeWithTag("futaber-settings-back").performClick()
        rule.onNodeWithTag("futaber-settings-row-gesture").performScrollTo().performClick()
        rule.onNodeWithTag("futaber-settings-switch-gesture-tap-scroll").performClick()
        rule.waitUntil(5_000) { preference("compat.futaber.tapScroll") == "ON" }
        rule.onNodeWithTag("futaber-settings-gesture-note").assertTextContains("画面をタップしてスクロール", substring = true)
    }

    @Test fun theMenuScrollsAPageAndToTheTopAndTheArchiveSheetExplainsAnUnsupportedBoard() {
        turnOn("compat.futaber.ext.scrollMenu", "compat.futaber.ext.archiveMenu")
        openTutorialThread()
        val start = listScroll()
        openMenu()
        clickMenu("page-down")
        rule.waitUntil(5_000) { listScroll() > start }
        openMenu()
        clickMenu("page-up")
        rule.waitUntil(5_000) { listScroll() <= start + 1f }
        openMenu()
        clickMenu("page-down")
        rule.waitUntil(5_000) { listScroll() > start }
        openMenu()
        clickMenu("top")
        rule.waitUntil(5_000) { listScroll() == 0f }

        // URL・アーカイブ: the four archives; the tutorial board is not kept by ふたばフォレスト (no browser is opened).
        openMenu()
        clickMenu("archive")
        waitForTag("futaber-action-archive-ftbucket")
        rule.onNodeWithTag("futaber-action-archive-tsumanne").assertIsDisplayed()
        rule.onNodeWithTag("futaber-action-archive-futapo").assertIsDisplayed()
        rule.onNodeWithTag("futaber-action-archive-forest").performClick()
        waitForTag("futaber-remote-notice")
        rule.onNodeWithTag("futaber-remote-notice").assertTextContains("may板のスレだけ", substring = true)
    }

    @Test fun theSearchChipMovesBetweenTheFoundPosts() {
        turnOn("compat.futaber.ext.searchNav")
        openTutorialThread()
        openMenu()
        rule.onNodeWithTag("futaber-menu-search").performTextInput("テスト")
        rule.onNodeWithTag("futaber-menu-search").performImeAction()
        waitForTag("futaber-search-next")
        val before = rule.onNodeWithTag("futaber-search-position", useUnmergedTree = true).fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.Text)?.joinToString("").orEmpty()
        assertTrue(before, before.startsWith("-/"))
        rule.onNodeWithTag("futaber-search-next").performClick()
        rule.onNodeWithTag("futaber-search-position", useUnmergedTree = true).assertTextContains("1/", substring = true)
        rule.onNodeWithTag("futaber-search-prev").performClick()
        // Back past the first is the last: n/N with n == N.
        val wrapped = rule.onNodeWithTag("futaber-search-position", useUnmergedTree = true).fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.Text)?.joinToString("").orEmpty()
        val (n, total) = wrapped.split("/")
        assertEquals(total, n)
        // The arrows do not clear the search; the chip itself still does.
        rule.onNodeWithTag("futaber-filter-chip").assertIsDisplayed()
    }

    @Test fun tappingTheLowerOrUpperThirdScrollsAPageAndTheMiddleDoesNot() {
        turnOn("compat.futaber.tapScroll")
        openTutorialThread()
        val list = rule.onNodeWithTag("futaber-thread-list")
        val start = listScroll()
        // The right end of a post is its padding: no quote, ID, link or picture is there.
        list.performTouchInput { click(Offset(width - 4f, height * 0.5f)) }
        rule.waitForIdle()
        assertEquals(start, listScroll(), 1f)
        list.performTouchInput { click(Offset(width - 4f, height * 0.9f)) }
        rule.waitUntil(5_000) { listScroll() > start }
        val down = listScroll()
        list.performTouchInput { click(Offset(width - 4f, height * 0.1f)) }
        rule.waitUntil(5_000) { listScroll() < down }
    }

    @Test fun closingAllTabsKeepsTheStarredOnesWithTheExtension() {
        turnOn("compat.futaber.ext.tabsManage")
        openTutorialThread()
        val first = runBlocking { app.appStateStore.history.first().first() }
        runBlocking {
            app.appStateStore.updateHistory { list ->
                list + first.copy(threadId = "999001", title = "もう1つのスレッド", boardUrl = first.boardUrl.replace(first.threadId, "999001"))
            }
            app.compatibilityStore.savePreferences(
                mapOf(
                    "compat.futaber.tabs" to "[\"${first.boardId}\\n${first.threadId}\",\"${first.boardId}\\n999001\"]",
                    // The second tab's thread is starred.
                    "compat.futaber.favorites" to "[\"${first.boardId}\\n999001\\nもう1つのスレッド\\n\\nチュートリアル\\n0\\n0\"]"
                )
            )
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-tab").fetchSemanticsNodes().size == 2 }
        rule.onAllNodesWithTag("futaber-tab")[0].performTouchInput { longClick() }
        waitForTag("futaber-action-tab-remove-all")
        rule.onNodeWithTag("futaber-action-tab-remove-all").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-tab").fetchSemanticsNodes().size == 1 }
        assertEquals("[\"${first.boardId}\\n999001\"]", preference("compat.futaber.tabs"))
    }

    @Test fun aHistoryRowOfAnUnregisteredOfficialBoardAsksToAddItAndCancelAddsNothing() {
        openTutorialThread()
        val first = runBlocking { app.appStateStore.history.first().first() }
        runBlocking {
            app.appStateStore.updateHistory { list ->
                listOf(
                    first.copy(
                        threadId = "999002", boardId = "zz-unregistered", title = "未登録の板のスレ", boardName = "テスト板",
                        boardUrl = "https://zip.2chan.net/1/res/999002.htm", lastVisitedEpochMillis = System.currentTimeMillis()
                    )
                ) + list
            }
        }
        rule.onNodeWithTag("futaber-manage").performClick()
        rule.onNodeWithTag("futaber-manage-category-History").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("未登録の板のスレ").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("未登録の板のスレ").performClick()
        waitForTag("futaber-board-add-question")
        rule.onNodeWithTag("futaber-board-add-question").assertTextContains("「テスト板」を板一覧に追加して開きますか？")
        rule.onNodeWithTag("futaber-board-add-confirm").assertIsDisplayed()
        rule.onNodeWithTag("futaber-board-add-cancel").performClick()
        rule.waitUntil(5_000) { absent("futaber-board-add-question") }
        assertTrue(runBlocking { app.appStateStore.boards.first() }.none { it.url.contains("zip.2chan.net/1/") })
    }

    @Test fun theSavedBoxShowsTheSaveStatusOnALongPressAndReopensTheThread() {
        turnOn("compat.futaber.ext.savedDetail")
        val saveDir = java.io.File(app.getExternalFilesDir(null), "futaber-b1a-save-test").apply { deleteRecursively(); mkdirs() }
        runBlocking { app.compatibilityStore.savePreference("compat.storage.dummyDownloadDir", saveDir.absolutePath) }
        try {
            openTutorialThread()
            openMenu()
            clickMenu("save")
            rule.waitUntil(10_000) { rule.onAllNodesWithText("保存形式").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("HTMLのみ").performClick()
            rule.waitUntil(60_000) { rule.onAllNodesWithText("保存先:", substring = true).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("閉じる").performClick()
            rule.onNodeWithTag("futaber-manage").performClick()
            rule.onNodeWithTag("futaber-manage-category-SavedBox").performClick()
            waitForTag("futaber-saved-row")
            rule.onAllNodesWithTag("futaber-saved-row")[0].performTouchInput { longClick() }
            waitForTag("futaber-saved-status")
            rule.onNodeWithTag("futaber-saved-status").assertTextContains("投稿", substring = true)
            rule.onNodeWithTag("futaber-saved-status").assertTextContains("容量", substring = true)
            rule.onNodeWithTag("futaber-saved-reopen").performClick()
            // The live thread opens (not the saved copy).
            waitForTag("futaber-post")
            rule.waitUntil(5_000) { absent("futaber-saved-status") }
            assertTrue(absent("futaber-offline-notice"))
        } finally {
            runBlocking { app.compatibilityStore.savePreference("compat.storage.dummyDownloadDir", "") }
            saveDir.deleteRecursively()
        }
    }

    @Test fun theOtherModesStarredThreadsAreListedReadOnly() {
        turnOn("compat.futaber.ext.otherFavorites")
        // A tab of the shared store needs a board of the shared store (an official one; the tutorial board is not there).
        // A board of its own is put there for the test and removed afterwards. It is registered in this mode too: the
        // shared store drops the boards the board list does not have (and their tabs with them).
        val boardUrl = "https://zip.2chan.net/1/"
        val board = com.valoser.futacha.shared.compat.CompatBoard(
            key = com.valoser.futacha.shared.compat.compatBoardKey(boardUrl), name = "テスト板", canonicalUrl = boardUrl,
            originalUrl = "${boardUrl}futaba.php", sortOrder = 9_999
        )
        val now = System.currentTimeMillis()
        val threadUrl = "${boardUrl}res/999003.htm"
        val tab = CompatTab(
            key = "futaber-b1a-other-favorite", canonicalUrl = threadUrl, originalUrl = threadUrl, boardKey = board.key,
            boardName = board.name, threadNo = "999003", title = "他のモードの星付き", favorite = true,
            insertedAtEpochMillis = now, contentUpdatedAtEpochMillis = now
        )
        runBlocking {
            app.appStateStore.updateBoards { it + com.valoser.futacha.shared.model.BoardSummary("zz-b1a", "テスト板", "", board.originalUrl, "") }
            app.compatibilityStore.upsertBoard(board)
            app.compatibilityStore.openTab(tab)
        }
        val stored = runBlocking { app.compatibilityStore.tabs.first() }.firstOrNull { it.key == tab.key }
        assertTrue("stored tab: $stored", stored?.favorite == true)
        try {
            waitForTag("futaber-drawer")
            rule.onAllNodesWithTag("futaber-drawer-board")[0].performClick()
            waitForTag("futaber-catalog-item")
            rule.onNodeWithTag("futaber-manage").performClick()
            rule.onNodeWithTag("futaber-manage-category-Favorites").performClick()
            waitForTag("futaber-other-favorites-header")
            waitForTag("futaber-other-favorite-row")
            rule.onNodeWithText("他のモードの星付き").assertIsDisplayed()
            // (Not opened: that would read the real board.)
            // Read only: editing the own favourites gives the other mode's rows no delete button.
            rule.onNodeWithTag("futaber-manage-edit").performClick()
            assertTrue(absent("futaber-manage-delete"))
        } finally {
            // The board is not deleted here: removing it from the board list (@After) lets the board bridge remove it
            // from the shared store too. Deleting it from the shared store directly could leave that store without
            // boards, which another mode's sync reads as "no boards".
            runBlocking { app.compatibilityStore.closeTabs(setOf(tab.key), System.currentTimeMillis()) }
        }
    }
}
