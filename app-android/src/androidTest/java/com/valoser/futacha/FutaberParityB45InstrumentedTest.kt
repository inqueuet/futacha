@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "DEPRECATION")

package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
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
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import coil3.ImageLoader
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.ARCHIVE_REPORT_ENABLED_PREFERENCE_KEY
import com.valoser.futacha.shared.compat.CompatWatchRule
import com.valoser.futacha.shared.compat.CompatWatcherRepository
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.network.HttpConditionalValidators
import com.valoser.futacha.shared.network.NetworkException
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.ConditionalThreadFetchResult
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.BaseInMemoryPlatformStateStorage
import com.valoser.futacha.shared.ui.futaber.FUTABER_TAB_CHECK_RUNNING_NOTICE
import com.valoser.futacha.shared.ui.futaber.FutaberApp
import com.valoser.futacha.shared.ui.futaber.FutaberMediaServices
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.util.createFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * ふたばー風モードの機能そろえ B4（履歴のアーカイブ・全件削除・全タブの確認）と B5（前面の巡回・外部にじろぐ・閲覧スレ通知）。
 * FutaberApp をテスト用の保存先（専用のデータベースとメモリ上の AppStateStore）で直接表示する。板・スレッドは同梱の模擬データ
 * （may板のアドレスで読むが、中身は模擬リポジトリ）で、実サーバー・外部サービスへは何も送らない。閲覧スレ通知は
 * テスト用データベースの送信待ち（outbox）に入るところまでを確かめ、送信処理（Worker）は呼ばない。
 */
class FutaberParityB45InstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var store: AndroidCompatibilityStore
    private lateinit var appState: AppStateStore
    private lateinit var loader: ImageLoader
    private val database = "futaber_b45_ui_test.db"
    private val may = BoardSummary("may", "may/b", "", "https://may.2chan.net/b/futaba.php", "")
    @Volatile private var gone = false
    private val enqueued = java.util.Collections.synchronizedList(mutableListOf<Int>())

    /** The bundled mock data under may's address; the history check sees 404 once [gone] is set. */
    private val repository = object : BoardRepository by FakeBoardRepository() {
        private val fake = FakeBoardRepository()
        // The patrol only records official thread addresses: the mock threads are listed under may's.
        override suspend fun getCatalogWithSettings(board: String, mode: CatalogMode, settings: com.valoser.futacha.shared.model.CatalogFetchSettings) =
            fake.getCatalog(board, mode).map { it.copy(threadUrl = "https://may.2chan.net/b/res/${it.id}.htm") }
        override suspend fun getThreadIfModified(board: String, threadId: String, validators: HttpConditionalValidators?) =
            if (gone) throw NetworkException("gone", statusCode = 404)
            else ConditionalThreadFetchResult.Modified(fake.getThread(board, threadId), null)
        override suspend fun getThreadContent(board: String, threadId: String) =
            if (gone) throw NetworkException("gone", statusCode = 404) else fake.getThreadContent(board, threadId)
    }

    @Before fun prepare() {
        rule.activity.deleteDatabase(database)
        store = AndroidCompatibilityStore(rule.activity, databaseName = database)
        appState = AppStateStore(BaseInMemoryPlatformStateStorage())
        runBlocking {
            store.initialize()
            appState.setBoards(listOf(may))
            appState.setHistory(emptyList())
        }
        loader = ImageLoader(rule.activity)
    }

    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        loader.shutdown()
        runBlocking { store.closeForTest() }
        rule.activity.deleteDatabase(database)
    }

    private fun host(preferences: Map<String, String>) {
        runBlocking { store.savePreferences(preferences + ("compat.futaber.lastBoardId" to "may")) }
        val services = FutaberMediaServices(store, null, createFileSystem(rule.activity), null)
        rule.setContent {
            val boards by appState.boards.collectAsState(emptyList())
            val history by appState.history.collectAsState(emptyList())
            val prefs by store.preferences.collectAsState(emptyMap())
            val ngRules by store.ngRules.collectAsState(emptyList())
            CompositionLocalProvider(LocalFutachaImageLoader provides loader) {
                if (boards.isNotEmpty() && prefs["compat.futaber.lastBoardId"] != null) FutaberApp(
                    boards = boards,
                    history = history,
                    repository = repository,
                    stateStore = appState,
                    preferences = prefs,
                    ngRules = ngRules,
                    savePreference = { key, value -> runBlocking { store.savePreference(key, value) } },
                    mediaServices = services,
                    appVersion = "test",
                    onArchiveReportEnqueued = { enqueued += it }
                )
            }
        }
    }

    private fun waitForTag(tag: String, timeoutMillis: Long = 20_000) {
        rule.waitUntil(timeoutMillis) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun absent(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()

    private fun preference(key: String): String? = runBlocking { store.loadPreference(key) }

    private fun notice(): String = rule.onAllNodesWithTag("futaber-app-notice").fetchSemanticsNodes().firstOrNull()
        ?.config?.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    /** The catalog's first thread; returns its number (the history row the visit wrote). */
    private fun openFirstThread(): String {
        waitForTag("futaber-catalog-item")
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post")
        rule.waitUntil(10_000) { runBlocking { appState.history.first() }.isNotEmpty() }
        return runBlocking { appState.history.first() }.first().threadId
    }

    private fun tabOn(threadId: String) {
        runBlocking { store.savePreference("compat.futaber.tabs", "[\"may\\n$threadId\"]") }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-tab").fetchSemanticsNodes().size == 1 }
    }

    private fun openHistoryPanel() {
        rule.onNodeWithTag("futaber-manage").performClick()
        waitForTag("futaber-manage-panel")
        rule.onNodeWithTag("futaber-manage-category-History").performClick()
    }

    @Test fun byDefaultNoneOfTheNewEntriesIsThereAndTheViewedThreadIsNoted() {
        host(emptyMap())
        val threadId = openFirstThread()
        // "閲覧スレ通知" is on with nothing stored: the may thread is in the outbox (nothing is sent from here).
        rule.waitUntil(10_000) { runBlocking { store.archiveReportOutboxStats() }.pendingOrRetry == 1 }
        assertTrue(enqueued.isNotEmpty())
        tabOn(threadId)
        rule.onAllNodesWithTag("futaber-tab")[0].performTouchInput { longClick() }
        waitForTag("futaber-action-tab-cancel")
        assertTrue(absent("futaber-action-tab-check-all"))
        rule.onNodeWithTag("futaber-action-tab-cancel").performClick()
        openHistoryPanel()
        waitForTag("futaber-history-list")
        assertTrue(absent("futaber-history-band"))
        assertTrue(absent("futaber-history-archive"))
        assertTrue(absent("futaber-history-clear-all"))
    }

    @Test fun theArchiveChipOpensTheSharedArchiveAndClearAllEmptiesTheHistoryButKeepsTheTabs() {
        host(mapOf("compat.futaber.ext.history" to "ON", "compat.futaber.ext.historyArchive" to "ON"))
        val threadId = openFirstThread()
        tabOn(threadId)
        val tabs = preference("compat.futaber.tabs")
        openHistoryPanel()
        waitForTag("futaber-history-archive")
        rule.onNodeWithTag("futaber-history-archive").performClick()
        waitForTag("futaber-history-archive-chooser")
        rule.onNodeWithTag("futaber-history-archive-export").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("すべてエクスポート").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("閉じる").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("すべてエクスポート").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("futaber-history-clear-all").performClick()
        waitForTag("futaber-history-clear-dialog")
        rule.onNodeWithTag("futaber-history-clear-confirm").performClick()
        rule.waitUntil(10_000) { runBlocking { appState.history.first() }.isEmpty() }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("閲覧したスレッドはまだありません").fetchSemanticsNodes().isNotEmpty() }
        // The archive chip stays (an archive can be read into an empty history); the tabs are not touched.
        assertFalse(absent("futaber-history-archive"))
        assertEquals(tabs, preference("compat.futaber.tabs"))
    }

    @Test fun checkingAllTabsMarksTheThreadThatFell() {
        host(mapOf("compat.futaber.ext.tabsManage" to "ON"))
        val threadId = openFirstThread()
        tabOn(threadId)
        gone = true
        rule.onAllNodesWithTag("futaber-tab")[0].performTouchInput { longClick() }
        waitForTag("futaber-action-tab-check-all")
        rule.onNodeWithTag("futaber-action-tab-check-all").performClick()
        rule.waitUntil(30_000) {
            runBlocking { appState.history.first() }.firstOrNull { it.threadId == threadId }?.isAutoRefreshDisabled == true
        }
        rule.waitUntil(10_000) { notice().startsWith("タブを1件確認しました") }
        assertEquals("タブを1件確認しました（落ちたスレ 1件）", notice())
        assertTrue(notice() != FUTABER_TAB_CHECK_RUNNING_NOTICE)
    }

    /** L108: the thread on screen confirmed gone (404) marks its shared history row fallen, as the tab check does. */
    @Test fun aThreadThatFallsWhileShownIsMarkedFallenInTheHistory() {
        host(emptyMap())
        val threadId = openFirstThread()
        rule.waitUntil(10_000) { runBlocking { appState.history.first() }.any { it.threadId == threadId } }
        gone = true
        rule.onNodeWithTag("futaber-thread-refresh").performClick()
        rule.waitUntil(30_000) {
            runBlocking { appState.history.first() }.firstOrNull { it.threadId == threadId }
                ?.let { it.isAutoRefreshDisabled && it.threadStatus?.isDead == true } == true
        }
    }

    @Test fun theSettingsSwitchTurnsTheSharedArchiveReportOffAndNothingIsNotedThen() {
        host(emptyMap())
        waitForTag("futaber-catalog-item")
        rule.onNodeWithTag("futaber-open-settings").performClick()
        waitForTag("futaber-settings")
        rule.onNodeWithTag("futaber-settings-switch-archive-report").performScrollTo().performClick()
        rule.waitUntil(5_000) { preference(ARCHIVE_REPORT_ENABLED_PREFERENCE_KEY) == "OFF" }
        rule.onNodeWithTag("futaber-settings-done").performClick()
        openFirstThread()
        Thread.sleep(2_000)
        rule.waitForIdle()
        assertEquals(0, runBlocking { store.archiveReportOutboxStats() }.total)
        assertTrue(enqueued.isEmpty())
    }

    @Test fun theExternalWatcherIsOfferedOnlyWithTheExtension() {
        host(emptyMap())
        waitForTag("futaber-catalog-item")
        fun openManager() {
            rule.onNodeWithTag("futaber-manage").performClick()
            waitForTag("futaber-manage-panel")
            rule.onNodeWithTag("futaber-manage-category-Notifications").performClick()
            waitForTag("futaber-watch-manage")
            rule.onNodeWithTag("futaber-watch-manage").performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithText("巡回管理").fetchSemanticsNodes().isNotEmpty() }
            // The manager's list composes only what is in view: bring its last block (after "今すぐ巡回") in.
            rule.onNode(hasScrollAction() and hasAnyDescendant(hasText("キーワードを含むカタログ", substring = true)))
                .performScrollToNode(hasText("今すぐ巡回（", substring = true))
            rule.waitForIdle()
        }
        openManager()
        assertTrue(rule.onAllNodesWithText("外部にじろぐの結果を表示", substring = true).fetchSemanticsNodes().isEmpty())
        rule.onNodeWithText("閉じる").performClick()
        runBlocking {
            store.savePreferences(mapOf("compat.futaber.ext.externalWatcher" to "ON", "compat.watcher.external" to "ON"))
        }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("巡回管理").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("futaber-manage-close-top").performClick()
        openManager()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("外部にじろぐの結果を表示", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("閉じる").performClick()
        // With the external list chosen, "通知" shows にじろぐ's own words (it is not installed on the emulator).
        rule.waitUntil(10_000) {
            rule.onAllNodes(hasTestTag("futaber-notifications-status") and hasText("にじろぐ", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun thePatrolRunsWhileOnScreenWithTheExtension() {
        val word = runBlocking { FakeBoardRepository().getCatalog(may.url, CatalogMode.Catalog) }
            .map { it.title.orEmpty() }.first { it.length >= 2 }.take(2)
        runBlocking { CompatWatcherRepository(store).saveRules(listOf(CompatWatchRule(word))) }
        host(mapOf("compat.futaber.ext.foregroundPatrol" to "ON"))
        waitForTag("futaber-catalog-item")
        rule.waitUntil(30_000) {
            runBlocking { store.preferences.first() }.keys.any { it.startsWith("compat.watcher.result.") }
        }
        rule.waitUntil(10_000) { notice().startsWith("巡回で") }
    }
}
