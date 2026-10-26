package com.valoser.futacha

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.CompatHistoryEntry
import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.CompatThreadSnapshot
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.compatTabKey
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repository.HISTORY_ARCHIVE_DIRECTORY
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.ui.compat.CompatibilityApp
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.util.AndroidFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * としあき(仮) feature-parity batches 4-8: the history tools (filter / marks / archive / refresh), the catalog orders
 * "そ順" / "監視", and the common NG lists. The shared AppStateStore is the application's own, so each test puts back
 * what it changed. Nothing is sent to a server (the refresh entry is only shown, the catalog is the tutorial fixture).
 */
class ToshiakiParityBatch48InstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val app = context as FutachaApplication
    private val sharedStore: AppStateStore get() = app.appStateStore
    private val databaseName = "toshiaki_batch48_${System.nanoTime()}.db"
    private lateinit var store: AndroidCompatibilityStore
    private lateinit var imageLoader: ImageLoader
    private val mayUrl = "https://may.2chan.net/b/"
    private val mayKey = compatBoardKey(mayUrl)
    private val TUTORIAL_URL = "https://www.example.com/t/futaba.php"

    private var originalBoards: List<BoardSummary> = emptyList()
    private var originalHistory: List<ThreadHistoryEntry> = emptyList()
    private var originalNgWords: List<String> = emptyList()
    private var archiveNamesBefore: Set<String> = emptySet()
    private val archiveRoot: File get() = File(AndroidFileSystem(context).resolveAbsolutePath(HISTORY_ARCHIVE_DIRECTORY))

    @Before fun prepare() {
        runBlocking {
            store = AndroidCompatibilityStore(context, databaseName = databaseName)
            store.initialize()
            store.savePreference("compat.commonUsedVersion", "1.0")
            originalBoards = sharedStore.boards.first()
            originalHistory = sharedStore.history.first()
            originalNgWords = sharedStore.ngWords.first()
        }
        archiveNamesBefore = archiveRoot.list()?.toSet().orEmpty()
        imageLoader = ImageLoader.Builder(context).build()
    }

    @After fun restore() {
        runBlocking {
            sharedStore.setBoards(originalBoards)
            sharedStore.setHistory(originalHistory)
            sharedStore.setNgWords(originalNgWords)
        }
        // An archive a test wrote is removed, so a later import never finds it.
        archiveRoot.listFiles()?.filter { it.name !in archiveNamesBefore }?.forEach { it.deleteRecursively() }
        context.deleteDatabase(databaseName)
    }

    private fun waitForText(text: String, timeoutMillis: Long = 10_000) {
        rule.waitUntil(timeoutMillis) {
            rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }

    private fun waitForTag(tag: String, timeoutMillis: Long = 10_000) {
        rule.waitUntil(timeoutMillis) {
            rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }

    private fun showApp(withStateStore: Boolean = true, initialThreadDeepLink: String? = null) {
        val files = AndroidFileSystem(context)
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(
                        store = store,
                        repository = null,
                        stateStore = if (withStateStore) sharedStore else null,
                        fileSystem = files,
                        historyRefresher = app.historyRefresher,
                        initialThreadDeepLink = initialThreadDeepLink,
                        onExitApplication = {}
                    )
                }
            }
        }
    }

    private fun compatHistory(no: String, title: String, visited: Long) = CompatHistoryEntry(
        canonicalUrl = "${mayUrl}res/$no.htm",
        originalUrl = "${mayUrl}res/$no.htm",
        boardKey = mayKey,
        boardName = "mayb",
        threadNo = no,
        title = title,
        replyCount = 3,
        contentUpdatedAtEpochMillis = visited,
        lastVisitedEpochMillis = visited
    )

    private fun seedMayBoardAndHistory(): Pair<CompatHistoryEntry, CompatHistoryEntry> {
        val alpha = compatHistory("7101", "TOOLS-ALPHA", 2_000L)
        val beta = compatHistory("7102", "TOOLS-BETA", 1_000L)
        runBlocking {
            store.upsertBoard(CompatBoard(mayKey, "mayb", mayUrl, mayUrl, 0))
            sharedStore.setBoards(listOf(BoardSummary("may", "mayb", "cat", mayUrl, "d")))
            store.upsertHistory(alpha)
            store.upsertHistory(beta)
            // The shared history knows ALPHA fell off; BETA is written in (this mode's own-post marker).
            sharedStore.setHistory(
                listOf(
                    ThreadHistoryEntry(
                        threadId = "7101", boardId = "may", title = "TOOLS-ALPHA", titleImageUrl = "",
                        boardName = "mayb", boardUrl = alpha.canonicalUrl, lastVisitedEpochMillis = 2_000L,
                        replyCount = 3, isAutoRefreshDisabled = true
                    )
                )
            )
            store.savePreference("compat.ownpost.${compatTabKey(beta.canonicalUrl)}.7105", "1")
        }
        return alpha to beta
    }

    private fun openHistoryDrawer() {
        rule.onNodeWithContentDescription("ドロワー").performClick()
        rule.onNodeWithContentDescription("履歴").performClick()
        waitForText("TOOLS-ALPHA")
    }

    private fun longPressHistory(entry: CompatHistoryEntry) {
        rule.onNodeWithTag("compat-drawer-history-row-${entry.canonicalUrl}").performTouchInput { longClick() }
        waitForText("全て削除する")
    }

    @Test fun historyLongPressAndRowsAreUnchangedWhileTheSwitchIsOff() {
        val (alpha, _) = seedMayBoardAndHistory()
        showApp()
        openHistoryDrawer()
        rule.onAllNodesWithTag("compat-drawer-history-marks-${alpha.canonicalUrl}", useUnmergedTree = true).assertCountEquals(0)
        longPressHistory(alpha)
        rule.onNodeWithText("削除する").assertIsDisplayed()
        rule.onNodeWithText("全て削除する").assertIsDisplayed()
        rule.onAllNodesWithText("絞り込み・並べ替え…").assertCountEquals(0)
        rule.onAllNodesWithText("履歴のエクスポート／インポート…").assertCountEquals(0)
        rule.onAllNodesWithText("履歴を一括更新").assertCountEquals(0)
    }

    /** L61: "全て削除する" asks first, with the count and every mode it reaches; キャンセル keeps everything. */
    @Test fun historyClearAllAsksFirstWithTheCountAndEveryMode() {
        val (alpha, _) = seedMayBoardAndHistory()
        showApp()
        openHistoryDrawer()
        longPressHistory(alpha)
        rule.onNodeWithText("全て削除する").performClick()
        waitForTag("compat-history-clear-confirm")
        rule.onNodeWithText("2件", substring = true).assertExists()
        rule.onNodeWithText("ふたばー風", substring = true).assertExists()
        rule.onNodeWithText("キャンセル").performClick()
        rule.waitForIdle()
        assertEquals(2, runBlocking { store.history.first() }.size)
        rule.onNodeWithText("TOOLS-ALPHA").assertIsDisplayed()

        longPressHistory(alpha)
        rule.onNodeWithText("全て削除する").performClick()
        waitForTag("compat-history-clear-confirm")
        rule.onNodeWithText("削除する").performClick()
        rule.waitUntil(10_000) { runBlocking { store.history.first() }.isEmpty() }
    }

    /** L61: the count is of the shared history that "全て削除する" clears, not only of this mode's rows. */
    @Test fun historyClearAllCountsTheSharedHistoryItClears() {
        val (alpha, _) = seedMayBoardAndHistory()
        runBlocking {
            sharedStore.setHistory(sharedStore.history.first() + (1..5).map { n ->
                ThreadHistoryEntry(threadId = "880$n", boardId = "img", title = "OTHER-$n", titleImageUrl = "",
                    boardName = "img", boardUrl = "https://img.2chan.net/b/res/880$n.htm", lastVisitedEpochMillis = n.toLong(),
                    replyCount = 1)
            })
        }
        showApp()
        openHistoryDrawer()
        longPressHistory(alpha)
        rule.onNodeWithText("全て削除する").performClick()
        waitForTag("compat-history-clear-confirm")
        rule.waitUntil(5_000) { rule.onAllNodesWithText("6件", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("キャンセル").performClick()
    }

    private fun settingCheckbox(key: String) =
        rule.onNode(isToggleable() and hasAnyAncestor(hasTestTag("compat-setting-$key")), useUnmergedTree = true)

    @Test fun theControlPageOffersTheTwoSwitchesOffAndSavesThem() {
        showApp()
        rule.onNodeWithContentDescription("その他").performClick()
        rule.onNodeWithText("設定").performClick()
        rule.onNodeWithText("コントロール").performClick()
        val list = rule.onNodeWithTag("compat-settings-list-control")
        list.performScrollToNode(hasText("履歴の絞り込み・並べ替え・印"))
        settingCheckbox("controlHistoryTools").assertIsOff()
        rule.onNodeWithTag("compat-setting-controlHistoryTools").performClick()
        rule.waitUntil(5_000) { runBlocking { store.loadPreference("compat.control.controlHistoryTools") } == "ON" }
        settingCheckbox("controlHistoryTools").assertIsOn()
        list.performScrollToNode(hasText("共通NGをこのモードでも適用"))
        rule.onNodeWithText("ONにすると、ふたちゃ・ふたばー風で登録したNGワードでもレスやスレが隠れます").assertIsDisplayed()
        settingCheckbox("controlCommonNgApply").assertIsOff()
    }

    @Test fun theControlPageHasNoSwitchesWithoutASharedStore() {
        showApp(withStateStore = false)
        rule.onNodeWithContentDescription("その他").performClick()
        rule.onNodeWithText("設定").performClick()
        rule.onNodeWithText("コントロール").performClick()
        rule.onNodeWithTag("compat-settings-list-control").performScrollToNode(hasText("板名の誤投稿確認"))
        rule.onAllNodesWithTag("compat-setting-controlHistoryTools").assertCountEquals(0)
        rule.onAllNodesWithTag("compat-setting-controlCommonNgApply").assertCountEquals(0)
    }

    @Test fun withTheSwitchOnTheRowsAreMarkedAndTheFilterNarrowsTheList() {
        val (alpha, beta) = seedMayBoardAndHistory()
        runBlocking { store.savePreference("compat.control.controlHistoryTools", "ON") }
        showApp()
        openHistoryDrawer()
        waitForTag("compat-drawer-history-marks-${alpha.canonicalUrl}")
        rule.onNodeWithTag("compat-drawer-history-marks-${alpha.canonicalUrl}", useUnmergedTree = true).assertTextContains("落ちた")
        rule.onNodeWithTag("compat-drawer-history-marks-${beta.canonicalUrl}", useUnmergedTree = true).assertTextContains("書き込み済み")

        longPressHistory(alpha)
        rule.onNodeWithText("履歴を一括更新").assertIsDisplayed()
        rule.onNodeWithText("履歴のエクスポート／インポート…").assertIsDisplayed()
        rule.onNodeWithText("絞り込み・並べ替え…").performClick()
        waitForText("履歴の表示設定")
        rule.onNodeWithText("2件中", substring = true).assertExists()
        // The two dates this mode does not keep are not offered.
        rule.onAllNodesWithText("最終生存確認日時").assertCountEquals(0)
        rule.onAllNodesWithText("自分が書き込んだ日時").assertCountEquals(0)
        rule.onNodeWithText("スレタイ").assertExists()
        rule.onNodeWithText("自分が書き込んだスレだけ").performClick()
        waitForText("2件中 1件を表示")
        rule.onNodeWithText("適用").performClick()

        waitForTag("compat-history-tools-banner")
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("compat-drawer-history-row-${alpha.canonicalUrl}").fetchSemanticsNodes().isEmpty()
        }
        rule.onNodeWithTag("compat-drawer-history-row-${beta.canonicalUrl}").assertExists()
        rule.onNodeWithText("初期表示に戻す").performClick()
        waitForTag("compat-drawer-history-row-${alpha.canonicalUrl}")
        rule.onAllNodesWithTag("compat-history-tools-banner", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun theArchiveEntryExplainsTheImportAndExportsTheSharedHistory() {
        val (alpha, _) = seedMayBoardAndHistory()
        runBlocking { store.savePreference("compat.control.controlHistoryTools", "ON") }
        showApp()
        openHistoryDrawer()
        longPressHistory(alpha)
        rule.onNodeWithText("履歴のエクスポート／インポート…").performClick()
        waitForTag("compat-history-archive-chooser")
        rule.onNodeWithText("としあき(仮)では履歴の一覧だけが戻り、保存した本文はふたちゃで開けます", substring = true).assertIsDisplayed()
        rule.onNodeWithText("としあき(仮)の履歴は新しい順に200件まで保存され、ドロワーには100件まで表示されます", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("compat-history-archive-export").performClick()
        waitForText("すべてエクスポート")
        // Export then clear is not offered here (the drawer has its own "全て削除する").
        rule.onAllNodesWithText("エクスポートして一括削除").assertCountEquals(0)
        rule.onNodeWithText("すべてエクスポート").performClick()
        waitForText("履歴アーカイブを作成しました", 20_000)
        // The result is a notice at the bottom now, not a dialog (audit L34): a tap puts it away.
        rule.onNodeWithTag("compat-app-notice").performClick()
        val written = archiveRoot.list()?.toSet().orEmpty() - archiveNamesBefore
        assertEquals(1, written.size)

        // The import side opens and says what comes back in this mode.
        longPressHistory(alpha)
        rule.onNodeWithText("履歴のエクスポート／インポート…").performClick()
        waitForTag("compat-history-archive-chooser")
        rule.onNodeWithTag("compat-history-archive-import").performClick()
        waitForText("すべてインポート")
        rule.onNodeWithText("選択してインポート").assertIsDisplayed()
        rule.onNodeWithText("閉じる").performClick()
    }

    /** The tutorial fixture (example.com, served by the bundled mock repository): no network. */
    private fun seedTutorialBoard(name: String): String {
        val canonical = "https://img.2chan.net/b/"
        val key = compatBoardKey(canonical)
        runBlocking { store.upsertBenchmarkTutorialBoard(CompatBoard(key, name, canonical, TUTORIAL_URL, 0)) }
        return key
    }

    @Test fun theCatalogOrderDialogOffersSoAndWatchAndKeepsThemInTheirOwnKey() {
        val boardKey = seedTutorialBoard("BATCH5-TUTORIAL")
        runBlocking {
            sharedStore.setBoards(listOf(BoardSummary("tutorial", "BATCH5-TUTORIAL", "cat", TUTORIAL_URL, "d")))
        }
        showApp()
        rule.onNodeWithText("BATCH5-TUTORIAL").performClick()
        waitForTag("compat-toolbar-command-sort")
        rule.onNodeWithTag("compat-toolbar-command-sort").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("勢い順")
        rule.onNodeWithText("新しい順").assertIsDisplayed()
        rule.onNodeWithTag("compat-catalog-sort-extra-SO").assertIsDisplayed()
        rule.onNodeWithTag("compat-catalog-sort-extra-WATCH").assertIsDisplayed()
        rule.onNodeWithTag("compat-catalog-sort-extra-SO").performClick()
        val key = "compat.catalog.extraMode.$boardKey"
        rule.waitUntil(5_000) { runBlocking { store.loadPreference(key) } == "SO" }
        waitForText("そ順")

        rule.onNodeWithTag("compat-toolbar-command-sort").performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithTag("compat-catalog-sort-extra-WATCH").performClick()
        rule.waitUntil(5_000) { runBlocking { store.loadPreference(key) } == "WATCH" }
        waitForText("監視")

        // One of the six removes the extra order.
        rule.onNodeWithTag("compat-toolbar-command-sort").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("勢い順")
        rule.onNodeWithText("勢い順").performClick()
        rule.waitUntil(5_000) { runBlocking { store.loadPreference(key) }.isNullOrEmpty() }
    }

    @Test fun theCatalogOrderDialogIsUnchangedWithoutASharedStore() {
        seedTutorialBoard("BATCH5-PLAIN")
        showApp(withStateStore = false)
        rule.onNodeWithText("BATCH5-PLAIN").performClick()
        waitForTag("compat-toolbar-command-sort")
        rule.onNodeWithTag("compat-toolbar-command-sort").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("勢い順")
        rule.onAllNodesWithTag("compat-catalog-sort-extra-SO").assertCountEquals(0)
        rule.onAllNodesWithTag("compat-catalog-sort-extra-WATCH").assertCountEquals(0)
    }

    private fun seedThread(): String {
        val threadUrl = "${mayUrl}res/7201.htm"
        val tabKey = compatTabKey(threadUrl)
        runBlocking {
            store.upsertBoard(CompatBoard(mayKey, "mayb", mayUrl, mayUrl, 0))
            sharedStore.setBoards(listOf(BoardSummary("may", "mayb", "cat", mayUrl, "d")))
            store.openTab(
                CompatTab(
                    key = tabKey, canonicalUrl = threadUrl, originalUrl = threadUrl, boardKey = mayKey,
                    boardName = "mayb", threadNo = "7201", title = "共通NGの確認", replyCount = 2,
                    insertedAtEpochMillis = 1L, contentUpdatedAtEpochMillis = 1L, snapshotRevision = 1L
                )
            )
            store.saveThreadSnapshot(
                CompatThreadSnapshot(
                    tabKey = tabKey, revision = 1L, fetchedAtEpochMillis = 1L,
                    posts = listOf(
                        CompatPostSnapshot(position = 0, postNo = "7201", timestamp = "10/12 10:00", messageHtml = "スレの本文"),
                        CompatPostSnapshot(position = 1, postNo = "7202", timestamp = "10/12 10:01", messageHtml = "ここに共通NGテスト語がある")
                    )
                )
            )
            sharedStore.setNgWords(listOf("共通NGテスト語"))
        }
        return threadUrl
    }

    @Test fun theCommonNgWordsDoNotHideWhileTheSwitchIsOff() {
        val threadUrl = seedThread()
        showApp(initialThreadDeepLink = threadUrl)
        waitForTag("compat-thread-post-7201")
        waitForTag("compat-thread-post-7202")
    }

    @Test fun theCommonNgWordsHideWithTheSwitchOnAndTheNgMenuEditsTheSharedList() {
        val threadUrl = seedThread()
        runBlocking { store.savePreference("compat.control.controlCommonNgApply", "ON") }
        showApp(initialThreadDeepLink = threadUrl)
        waitForTag("compat-thread-post-7201")
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("compat-thread-post-7202", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }

        rule.onNodeWithTag("compat-toolbar-command-other").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("NG管理")
        rule.onNodeWithText("NG管理", substring = true).performClick()
        waitForText("共通NG（ふたちゃ・ふたばー風と共有）")
        rule.onNodeWithText("共通NG（ふたちゃ・ふたばー風と共有）").performClick()
        waitForText("NGワード")
        rule.onNodeWithText("NGワード").performClick()
        waitForText("共通NGテスト語")
        // Removing it from the shared list shows the reply again.
        rule.onNodeWithContentDescription("削除").performClick()
        rule.waitUntil(5_000) { runBlocking { sharedStore.ngWords.first() }.isEmpty() }
        assertTrue(runBlocking { store.ngRules.first() }.isEmpty())
        rule.onNodeWithContentDescription("閉じる").performClick()
        waitForTag("compat-thread-post-7202")
    }
}
