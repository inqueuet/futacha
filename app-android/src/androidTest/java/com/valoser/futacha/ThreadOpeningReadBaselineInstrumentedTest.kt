@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import coil3.ImageLoader
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.*
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.state.*
import com.valoser.futacha.shared.ui.board.*
import com.valoser.futacha.shared.ui.compat.*
import com.valoser.futacha.shared.ui.futaber.*
import com.valoser.futacha.shared.ui.ftutter.*
import com.valoser.futacha.shared.ui.image.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

/** All repositories are local fixtures. No posting or audio. */
class ThreadOpeningReadBaselineInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val board = BoardSummary("baseline", "基準検証板", "検証", "https://may.2chan.net/b/", "")
    private val url = "${board.url}res/100.htm"
    private val db = "audit_m13_baseline.db"
    private lateinit var store: AndroidCompatibilityStore
    private lateinit var state: AppStateStore
    private lateinit var loader: ImageLoader
    private val entry = ThreadHistoryEntry("100", board.id, "基準検証スレ", "", board.name, board.url, 1, 900,
        lastConfirmedAliveEpochMillis = 100, lastViewedSnapshot = ThreadViewedSnapshot(2, "101", 1))
    private fun post(id: String) = Post(id, author = null, subject = null, timestamp = "", messageHtml = "本文$id", imageUrl = null, thumbnailUrl = null)
    private var page = ThreadPage("100", null, null, null, (100..102).map { post("$it") })
    private var fetches = 0
    private val repo = object : BoardRepository by FakeBoardRepository() {
        override suspend fun getThreadContent(board: String, threadId: String): ThreadPageContent { fetches++; return ThreadPageContent(page) }
        override suspend fun getThreadContentByUrl(threadUrl: String): ThreadPageContent { fetches++; return ThreadPageContent(page) }
        override suspend fun getThreadByUrl(threadUrl: String): ThreadPage { fetches++; return page }
        override suspend fun getCatalogPage(board: String, mode: CatalogMode) = CatalogPageContent(listOf(item()))
        override suspend fun getCatalogWithSettings(board: String, mode: CatalogMode, settings: CatalogFetchSettings) = listOf(item())
        private fun item() = CatalogItem(id = "100", threadUrl = url, title = "基準検証スレ", thumbnailUrl = null, fullImageUrl = null, replyCount = 899)
    }
    @Before fun prepare() {
        rule.activity.deleteDatabase(db)
        store = AndroidCompatibilityStore(rule.activity, databaseName = db)
        loader = ImageLoader.Builder(rule.activity).build()
        state = AppStateStore(BaseInMemoryPlatformStateStorage())
        runBlocking {
            store.initialize(); store.importModernBoards(listOf(board)); state.updateBoards { listOf(board) }
            state.updateHistory { listOf(entry) }
        }
    }
    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        state.close(); loader.shutdown()
        runBlocking { store.closeForTest() }; rule.activity.deleteDatabase(db)
    }
    @Composable private fun Images(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader) {
            MaterialTheme { content() }
        }
    }
    private fun waitSummary() = rule.waitUntil(20_000) {
        rule.onAllNodesWithText("新着1件（自分の投稿記録なし）", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }

    @Test fun futachaBackgroundMetadataDoesNotConsumeUnreadPosts() {
        rule.setContent { Images {
            ProvideFutachaSharedFeatures(store, null, repo, null, null, "test", appStateStore = state) {
                ThreadScreen(board, listOf(entry), "100", entry.title, 900, onBack = {}, repository = repo,
                    stateStore = state, preferencesState = ScreenPreferencesState("test"))
            }
        } }
        waitSummary()
        rule.waitUntil(10_000) { runBlocking { state.history.first().single().lastViewedSnapshot?.lastPostId == "102" } }
    }

    @Test fun toshiakiCatalogCountDoesNotReplaceThePreOpenReadBoundary() {
        rule.setContent { Images { CompatibilityApp(store = store, repository = repo, stateStore = state, onExitApplication = {}) } }
        rule.waitUntil(15_000) { rule.onAllNodesWithText(board.url).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(board.url).performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("compat-catalog-item-100").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("compat-catalog-item-100").performClick()
        waitSummary()
        rule.onNodeWithTag("compat-thread-opening-summary").assertIsDisplayed()
        rule.waitUntil(10_000) { runBlocking { state.history.first().single().lastViewedSnapshot?.lastPostId == "102" } }
    }

    @Test fun futaberOwnSeenCountWinsOverBackgroundMetadata() {
        val otherModeEntry = entry.copy(lastViewedSnapshot = ThreadViewedSnapshot(1, "100", 1))
        runBlocking { state.updateHistory { listOf(otherModeEntry) } }
        val preferences = mapOf(
            FutaberSettingKeys.EXT_OPEN_SUMMARY to "ON",
            FutaberPreferenceKeys.SEEN to encodeFutaberSeenCounts(listOf(FutaberSeenCount(board.id, "100", 2, "101")))
        )
        rule.setContent { Images {
            FutaberApp(listOf(board), listOf(otherModeEntry), repo, state, preferences, emptyList(), { _, _ -> },
                initialThreadDeepLink = url)
        } }
        waitSummary()
    }

    @Composable private fun FtutterScreenFixture(initial: ThreadPage? = null, refresh: Boolean = true,
        tracker: ThreadOpeningSummaryTracker? = null, notices: MutableList<String>, summaryEnabled: Boolean = true) {
        Images { FtutterTheme(null, FtutterBrightness.LIGHT) {
            FtutterThreadScreen(board, FtutterThreadRef(board.id, "100", entry.title), repo, null,
                mapOf(FtutterKeys.SHOW_NEW_COUNT to "true"), parseFtutterDesign(emptyMap()),
                // A held page is reusable only when background metadata has not outgrown it (M31).
                if (initial != null && !refresh) entry.copy(replyCount = initial.posts.size) else entry, emptySet(), 2,
                { _, _ -> }, {}, {}, {}, { _, _, _, _ -> }, {}, {}, {}, { notices += it },
                extensions = FtutterExtensions(if (summaryEnabled) setOf(FtutterExtension.OPEN_SUMMARY) else emptySet()), historyStateStore = state,
                initialPage = initial, refreshOnOpen = refresh, openingTracker = tracker)
        } }
    }

    @Test fun ftutterOwnReadCountWinsOverBackgroundMetadata() {
        val notices = mutableListOf<String>()
        rule.setContent { FtutterScreenFixture(notices = notices) }
        rule.waitUntil(20_000) { notices.contains("新着1件（自分の投稿記録なし）") }
        assertEquals(1, notices.size)
    }

    @Test fun ftutterHeldPageAnnouncesOnOpenAndChildReturnDoesNotAnnounceAgain() {
        val notices = mutableListOf<String>()
        val memory = FtutterThreadMemory().apply { open(url, false, 2, "101"); put(url, page) }
        var child by mutableStateOf(false)
        rule.setContent {
            if (child) Text("子画面") else FtutterScreenFixture(memory.page(url), false, memory.opening(url).tracker, notices)
        }
        rule.waitUntil(20_000) { notices.size == 1 }
        assertEquals("新着1件（自分の投稿記録なし）", notices.single())
        assertEquals(0, fetches)
        rule.runOnUiThread { child = true }; rule.waitForIdle()
        page = page.copy(posts = page.posts + post("103"))
        memory.put(url, page)
        rule.runOnUiThread { child = false }; rule.waitForIdle()
        assertEquals(1, notices.size)
        assertEquals(0, fetches)
    }
    @Test fun ftutterDisabledOpeningSummaryRetainsTheNormalRefreshNotice() {
        val notices = mutableListOf<String>()
        rule.setContent { FtutterScreenFixture(page.copy(posts = page.posts.take(2)), true,
            notices = notices, summaryEnabled = false) }
        rule.waitUntil(20_000) { notices.isNotEmpty() }
        assertEquals(listOf("新着 1 件"), notices)
    }

    @Test fun ftutterEnabledOpeningSummaryDoesNotOverwriteANormalNoticeOnOpening() {
        val notices = mutableListOf<String>()
        rule.setContent { FtutterScreenFixture(page.copy(posts = page.posts.take(2)), true, notices = notices) }
        rule.waitUntil(20_000) { notices.isNotEmpty() }
        assertEquals(listOf("新着1件（自分の投稿記録なし）"), notices)
    }

}
