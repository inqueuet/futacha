package com.valoser.futacha

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.valoser.futacha.shared.compat.COMPAT_WATCH_RULES_KEY
import com.valoser.futacha.shared.compat.CompatWatchRule
import com.valoser.futacha.shared.compat.ExperienceProfile
import com.valoser.futacha.shared.compat.encodeValidCompatWatchRules
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * ふたばー風モードの機能そろえ B7a（カタログ系：そ順・監視、監視ワードの強調、レス数による優先表示、世代で戻す、
 * キャッシュサーバー検索・ブラウザ、アラート用の監視ワード）。同梱の模擬データ（チュートリアル板と、may板のアドレスで
 * 模擬リポジトリを読む板）だけを使い、実サーバーへは送らない（キャッシュ検索はダイアログを開くまで。検索・ブラウザで開くは
 * 押さない。閲覧スレ通知は試験中オフにする）。
 */
private const val B7A_TUTORIAL_URL = "https://www.example.com/t/futaba.php"
/** may's shared key (official address), read through the bundled mock repository (the address names example.com). */
private const val B7A_MAY_LIKE_URL = "https://may.2chan.net/b/futaba.php?mock=example.com"
private const val B7A_MAY_LIKE_ID = "zz-b7amay"
private const val B7A_MAY_LIKE_NAME = "B7a模擬may"
private const val ARCHIVE_REPORT_KEY = "compat.archive_report.enabled"

class FutaberParityB7aInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private lateinit var app: FutachaApplication
    private lateinit var originalProfile: ExperienceProfile
    private var originalWatchWords = emptyList<String>()
    private var originalWatchRules: String? = null
    private var originalThreshold: String? = null
    private var originalArchiveReport: String? = null

    private val keys = listOf(
        "compat.futaber.ext.catalogModes",
        "compat.futaber.ext.catalogWatchHighlight",
        "compat.futaber.ext.replyPriority",
        "compat.futaber.ext.catalogHistory",
        "compat.futaber.ext.catalogTools",
        "compat.futaber.ext.sortByBoard",
        "compat.futaber.catalog.sort",
        "compat.futaber.catalog.sortByBoard",
        "compat.futaber.lastBoardId",
        "compat.futaber.tabs",
        "compat.futaber.seen"
    )

    @Before fun prepare() {
        app = ApplicationProvider.getApplicationContext()
        originalProfile = app.experienceProfileStore.readActiveProfile()
        runBlocking {
            app.compatibilityStore.ensureInitialized()
            app.compatibilityStore.savePreferences(keys.associateWith { null })
            originalWatchWords = app.appStateStore.watchWords.first()
            originalWatchRules = app.compatibilityStore.loadPreference(COMPAT_WATCH_RULES_KEY)
            originalThreshold = app.compatibilityStore.loadPreference(THRESHOLD_KEY)
            app.compatibilityStore.savePreferences(mapOf(COMPAT_WATCH_RULES_KEY to null, THRESHOLD_KEY to null))
            // Visiting the may-like board's thread must not queue its address for the external archive.
            originalArchiveReport = app.compatibilityStore.loadPreference(ARCHIVE_REPORT_KEY)
            app.compatibilityStore.savePreference(ARCHIVE_REPORT_KEY, "OFF")
            app.appStateStore.updateBoards { boards -> boards.filterNot { it.id == B7A_MAY_LIKE_ID } }
            // The tests read the bundled tutorial board; put it back if an earlier run lost it.
            if (app.appStateStore.boards.first().none { it.url == B7A_TUTORIAL_URL }) {
                app.appStateStore.updateBoards { boards ->
                    listOf(
                        com.valoser.futacha.shared.model.BoardSummary(
                            "t", "チュートリアル＠ふたちゃ", "チュートリアル", B7A_TUTORIAL_URL, "チュートリアル"
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
            // Only the AppState board is removed; the shared store's board is left (it is may's, which others may use).
            app.appStateStore.updateBoards { boards -> boards.filterNot { it.id == B7A_MAY_LIKE_ID } }
            app.appStateStore.updateHistory { list -> list.filterNot { it.boardId == B7A_MAY_LIKE_ID } }
            app.appStateStore.setWatchWords(originalWatchWords)
            app.compatibilityStore.savePreferences(
                keys.associateWith { null } + mapOf(
                    COMPAT_WATCH_RULES_KEY to originalWatchRules,
                    THRESHOLD_KEY to originalThreshold,
                    ARCHIVE_REPORT_KEY to originalArchiveReport
                )
            )
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

    private fun badgeAbsent() =
        rule.onAllNodesWithTag("futaber-catalog-watch-badge", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()

    private fun preference(key: String): String? = runBlocking { app.compatibilityStore.loadPreference(key) }

    private fun turnOn(vararg extensionKeys: String) = runBlocking {
        app.compatibilityStore.savePreferences(extensionKeys.associate { it to "ON" })
    }

    private fun setWatchRule(word: String) = runBlocking {
        app.compatibilityStore.savePreference(COMPAT_WATCH_RULES_KEY, encodeValidCompatWatchRules(listOf(CompatWatchRule(word))))
    }

    private fun tutorialCatalog(mode: CatalogMode = CatalogMode.Catalog): List<CatalogItem> =
        runBlocking { FakeBoardRepository().getCatalog(B7A_TUTORIAL_URL, mode) }

    private fun itemCount(): Int = rule.onAllNodesWithTag("futaber-catalog-item").fetchSemanticsNodes().size

    private fun openTutorialCatalog() {
        waitForTag("futaber-drawer")
        waitForTag("futaber-drawer-board")
        rule.onAllNodesWithTag("futaber-drawer-board")[0].performClick()
        waitForTag("futaber-catalog-item")
    }

    private fun openRefreshSheet() {
        rule.onNodeWithTag("futaber-refresh").performTouchInput { longClick() }
        waitForTag("futaber-post-actions")
    }

    private fun closeSheet() {
        rule.onNodeWithTag("futaber-action-cancel").performClick()
        rule.waitUntil(5_000) { absent("futaber-post-actions") }
    }

    private fun action(id: String): SemanticsNodeInteraction {
        waitForTag("futaber-action-$id")
        return rule.onNodeWithTag("futaber-action-$id").performScrollTo()
    }

    private fun chooseSort(mode: CatalogMode) {
        rule.onNodeWithTag("futaber-sort").performClick()
        waitForTag("futaber-sort-${mode.name}")
        rule.onNodeWithTag("futaber-sort-${mode.name}").performClick()
    }

    private fun openMenu() {
        rule.onNodeWithTag("futaber-thread-menu").performClick()
        waitForTag("futaber-operation-menu")
    }

    private fun openSettings() {
        rule.onNodeWithTag("futaber-open-settings").performClick()
        waitForTag("futaber-settings")
    }

    /**
     * A word of the bundled catalog's title (the bundled catalog has one thread, titled "No.<number>" for want of a
     * subject), so a watch rule with it matches that thread.
     */
    private fun wordOfTheTitle(): String = tutorialCatalog().first().title!!.trim()

    @Test fun byDefaultNoneOfTheNewEntriesIsThere() {
        setWatchRule(wordOfTheTitle())
        openTutorialCatalog()
        // No badge, and the sort menu keeps its six entries.
        assertTrue(badgeAbsent())
        rule.onNodeWithTag("futaber-sort").performClick()
        waitForTag("futaber-sort-Momentum")
        assertTrue(absent("futaber-sort-So"))
        assertTrue(absent("futaber-sort-WatchWords"))
        rule.onNodeWithTag("futaber-sort-Catalog").performClick()
        // The refresh sheet is as before: one going-back row, the dropped threads and "キャンセル".
        openRefreshSheet()
        waitForTag("futaber-action-catalog-back")
        for (id in listOf("catalog-back-1", "reply-priority", "reply-priority-hide", "cache-search", "catalog-browser")) {
            assertTrue(id, absent("futaber-action-$id"))
        }
        closeSheet()
        // The catalog settings have no threshold.
        openSettings()
        rule.onNodeWithTag("futaber-settings-row-catalog").performScrollTo().performClick()
        waitForTag("futaber-settings-switch-catalog-scroll-top")
        assertTrue(absent("futaber-settings-row-reply-priority-threshold"))
        rule.onNodeWithTag("futaber-settings-back").performClick()
        waitForTag("futaber-settings-done")
        rule.onNodeWithTag("futaber-settings-done").performClick()
        rule.waitUntil(5_000) { absent("futaber-settings") }
        // The thread menu has no "キャッシュ検索".
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post")
        openMenu()
        waitForTag("futaber-menu-share-url")
        assertTrue(absent("futaber-menu-cache-search"))
        rule.onNodeWithTag("futaber-menu-scrim").performTouchInput { click(androidx.compose.ui.geometry.Offset(centerX, 40f)) }
        rule.waitUntil(5_000) { absent("futaber-operation-menu") }
    }

    @Test fun soAndWatchAreOfferedFetchAndAStoredWatchReadsAsUsualWhenOff() {
        turnOn("compat.futaber.ext.catalogModes")
        val all = tutorialCatalog()
        val soCount = tutorialCatalog(CatalogMode.So).size
        setWatchRule(wordOfTheTitle())
        openTutorialCatalog()
        chooseSort(CatalogMode.So)
        rule.waitUntil(10_000) { preference("compat.futaber.catalog.sort") == "So" }
        rule.waitUntil(20_000) { itemCount() == soCount }
        // "監視" with a word of the title keeps that thread.
        chooseSort(CatalogMode.WatchWords)
        rule.waitUntil(10_000) { preference("compat.futaber.catalog.sort") == "WatchWords" }
        rule.waitUntil(20_000) { itemCount() == all.size }
        // A word nothing holds: empty, and it says so.
        setWatchRule("b7a該当なしの語")
        rule.onNodeWithTag("futaber-refresh").performClick()
        rule.waitUntil(20_000) { rule.onAllNodesWithText("監視ワードに一致するスレッドはありません").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, itemCount())
        // Without words, "監視" says why it is empty.
        runBlocking { app.compatibilityStore.savePreference(COMPAT_WATCH_RULES_KEY, encodeValidCompatWatchRules(emptyList())) }
        rule.onNodeWithTag("futaber-refresh").performClick()
        rule.waitUntil(20_000) { rule.onAllNodesWithText("監視ワードが登録されていません", substring = true).fetchSemanticsNodes().isNotEmpty() }
        // The extension off: the stored "監視" reads as "通常" and the menu has its six entries again.
        runBlocking { app.compatibilityStore.savePreference("compat.futaber.ext.catalogModes", "OFF") }
        rule.waitUntil(20_000) { itemCount() == all.size }
        assertEquals("WatchWords", preference("compat.futaber.catalog.sort"))
        rule.onNodeWithTag("futaber-sort").performClick()
        waitForTag("futaber-sort-Momentum")
        assertTrue(absent("futaber-sort-WatchWords"))
        rule.onNodeWithTag("futaber-sort-Catalog").performClick()
    }

    @Test fun watchedThreadsGetABadgeAndComeFirst() {
        turnOn("compat.futaber.ext.catalogWatchHighlight")
        setWatchRule(wordOfTheTitle())
        openTutorialCatalog()
        // The badge is inside the thread's clickable cell, which merges its children: read the unmerged tree.
        rule.waitUntil(20_000) { !badgeAbsent() }
        val withBadge = rule.onAllNodes(
            hasTestTag("futaber-catalog-item") and hasAnyDescendant(hasTestTag("futaber-catalog-watch-badge")),
            useUnmergedTree = true
        ).fetchSemanticsNodes().map { it.id }
        assertTrue(withBadge.isNotEmpty())
        val first = rule.onAllNodesWithTag("futaber-catalog-item", useUnmergedTree = true).fetchSemanticsNodes().first().id
        assertTrue("the first thread holds the word", first in withBadge)
        assertEquals(tutorialCatalog().size, itemCount())
    }

    @Test fun replyPriorityOffersItsRowsAndTheSharedThreshold() {
        turnOn("compat.futaber.ext.replyPriority")
        openTutorialCatalog()
        openRefreshSheet()
        // The tutorial board is not in the shared store: the board's setting cannot be kept, so the rows are off.
        action("reply-priority").assertIsNotEnabled()
        action("reply-priority-hide").assertIsNotEnabled()
        closeSheet()
        openSettings()
        rule.onNodeWithTag("futaber-settings-row-catalog").performScrollTo().performClick()
        waitForTag("futaber-settings-row-reply-priority-threshold")
        rule.onNodeWithTag("futaber-settings-plus-reply-priority-threshold").performScrollTo().performClick()
        rule.waitUntil(10_000) { preference(THRESHOLD_KEY) == "1" }
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText("少ないレスの基準 = 1").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun generationsKeepTheOneDisabledRowWhileNothingIsKept() {
        turnOn("compat.futaber.ext.catalogHistory")
        openTutorialCatalog()
        openRefreshSheet()
        // The bundled catalog never changes between reloads, so nothing is kept: the one row, disabled, as before.
        action("catalog-back").assertIsNotEnabled()
        assertTrue(absent("futaber-action-catalog-back-1"))
        closeSheet()
    }

    @Test fun theCacheSearchIsDisabledOnTheTutorialBoard() {
        // As in Futacha, the search is for official boards only: it would send the board's address to the outside server.
        turnOn("compat.futaber.ext.catalogTools")
        openTutorialCatalog()
        openRefreshSheet()
        action("catalog-browser").assertIsEnabled()
        action("cache-search").assertIsNotEnabled()
        closeSheet()
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post")
        openMenu()
        waitForTag("futaber-menu-cache-search")
        runCatching { rule.onNodeWithTag("futaber-menu-cache-search").performScrollTo() }
        rule.onNodeWithTag("futaber-menu-cache-search").assertIsNotEnabled()
        assertTrue(rule.onAllNodesWithText("スレッド名・キーワード").fetchSemanticsNodes().isEmpty())
    }

    @Test fun theCacheSearchOpensFromTheRefreshSheetAndTheThreadMenu() {
        turnOn("compat.futaber.ext.catalogTools")
        val mayLike = com.valoser.futacha.shared.model.BoardSummary(B7A_MAY_LIKE_ID, B7A_MAY_LIKE_NAME, "", B7A_MAY_LIKE_URL, "")
        runBlocking {
            app.appStateStore.updateBoards { boards -> listOf(mayLike) + boards }
            app.compatibilityStore.importModernBoards(listOf(mayLike))
        }
        waitForTag("futaber-drawer")
        rule.waitUntil(20_000) {
            rule.onAllNodes(hasTestTag("futaber-drawer-board") and hasText(B7A_MAY_LIKE_NAME, substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNode(hasTestTag("futaber-drawer-board") and hasText(B7A_MAY_LIKE_NAME, substring = true)).performScrollTo().performClick()
        waitForTag("futaber-catalog-item")
        openRefreshSheet()
        action("catalog-browser").assertIsEnabled()
        action("cache-search").assertIsEnabled().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("キャッシュ検索").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(absent("futaber-post-actions"))
        rule.onNodeWithText("閉じる").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("キャッシュ検索").fetchSemanticsNodes().isEmpty() }
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post")
        openMenu()
        waitForTag("futaber-menu-cache-search")
        runCatching { rule.onNodeWithTag("futaber-menu-cache-search").performScrollTo() }
        rule.onNodeWithTag("futaber-menu-cache-search").assertIsEnabled().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("スレッド名・キーワード").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("閉じる").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("スレッド名・キーワード").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun theAlertsWatchWordsAreEditedInTheSettings() {
        runBlocking { app.appStateStore.setWatchWords(emptyList()) }
        openTutorialCatalog()
        openSettings()
        waitForTag("futaber-settings-row-watch-words")
        rule.onNodeWithTag("futaber-settings-row-watch-words").performScrollTo().performClick()
        waitForTag("futaber-ng-input")
        rule.onNodeWithTag("futaber-ng-input").performTextInput("b7a監視")
        rule.onNodeWithTag("futaber-ng-add").performClick()
        rule.waitUntil(10_000) { runBlocking { app.appStateStore.watchWords.first() } == listOf("b7a監視") }
        rule.onNodeWithTag("futaber-ng-delete").performClick()
        rule.waitUntil(10_000) { runBlocking { app.appStateStore.watchWords.first() }.isEmpty() }
        // Back goes to the settings list, not to the NG page.
        rule.onNodeWithTag("futaber-settings-back").performClick()
        waitForTag("futaber-settings-row-watch-words")
    }

    private companion object {
        const val THRESHOLD_KEY = "compat.catalog.delayFewReplies"
    }
}
