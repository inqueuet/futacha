@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import coil3.ImageLoader
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.BaseInMemoryPlatformStateStorage
import com.valoser.futacha.shared.ui.*
import com.valoser.futacha.shared.ui.board.*
import com.valoser.futacha.shared.ui.compat.*
import com.valoser.futacha.shared.ui.image.LocalFutachaCatalogImageLoader
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.util.createFileSystem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * L30: a reply, a new thread and としあき(仮)'s post count as being sent while they run, so the app's links,
 * notifications and AI commands wait for them instead of leaving the screen (which cancelled the send). Local fake
 * sends only.
 */
class PostingInFlightInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val boardUrl = "https://may.2chan.net/b/"
    private val threadUrl = "${boardUrl}res/1001.htm"
    private val board = BoardSummary("l30", "L30", "test", boardUrl, "")
    private val boardKey = compatBoardKey(boardUrl)
    private val tabKey = compatTabKey(threadUrl)
    private val started = CompletableDeferred<Unit>()
    private val release = CompletableDeferred<Unit>()
    private val appStore = AppStateStore(BaseInMemoryPlatformStateStorage())
    private val database = "l30-${System.nanoTime()}.db"
    private lateinit var store: AndroidCompatibilityStore
    private lateinit var images: ImageLoader
    private val tracker = PostingInFlight()
    private val preferences = mapOf("compat.control.controlPostConfirm" to "OFF",
        "compat.control.controlPostDestinationConfirm" to "OFF", COMPAT_POST_DELETE_KEY_STORAGE_KEY to "l30key")
    private val compatBoard = CompatBoard(boardKey, "L30", boardUrl, boardUrl, 0)
    private val tab = CompatTab(key = tabKey, canonicalUrl = threadUrl, originalUrl = threadUrl, boardKey = boardKey,
        boardName = "L30", threadNo = "1001", title = "L30", insertedAtEpochMillis = 1, contentUpdatedAtEpochMillis = 1)

    @Before fun prepare() {
        store = AndroidCompatibilityStore(rule.activity, databaseName = database)
        images = ImageLoader.Builder(rule.activity).build()
        runBlocking {
            store.initialize(); store.upsertBoard(compatBoard); store.openTab(tab); store.savePreferences(preferences)
            appStore.setHasShownPostingNotice(true); appStore.setLastUsedDeleteKey("l30key")
        }
    }
    @After fun cleanup() {
        release.complete(Unit)
        rule.runOnUiThread { rule.activity.setContent {} }
        rule.waitForIdle()
        images.shutdown(); appStore.close()
        runBlocking { store.closeForTest() }
        rule.activity.deleteDatabase(database)
    }
    private fun page() = ThreadPage("1001", "L30", null, null, listOf(
        Post("1001", 0, "fixture", null, "now", messageHtml = "L30 ready", imageUrl = null, thumbnailUrl = null)))
    private val repository = object : BoardRepository by FakeBoardRepository() {
        override suspend fun getThread(board: String, threadId: String) = page()
        override suspend fun getThreadByUrl(threadUrl: String) = page()
        override suspend fun getThreadContent(board: String, threadId: String) = ThreadPageContent(page())
        override suspend fun getThreadContentByUrl(threadUrl: String) = ThreadPageContent(page())
        override suspend fun getCatalog(board: String, mode: CatalogMode): List<CatalogItem> = emptyList()
        override suspend fun getCatalogWithSettings(board: String, mode: CatalogMode, settings: CatalogFetchSettings): List<CatalogItem> = emptyList()
        override suspend fun replyToThread(board: String, threadId: String, name: String, email: String, subject: String,
            comment: String, password: String, imageFile: ByteArray?, imageFileName: String?, textOnly: Boolean, handwriting: Boolean): String? {
            started.complete(Unit); release.await(); return "1002"
        }
        override suspend fun createThread(board: String, name: String, email: String, subject: String, comment: String,
            password: String, imageFile: ByteArray?, imageFileName: String?, textOnly: Boolean, handwriting: Boolean): String? {
            started.complete(Unit); release.await(); return "1002"
        }
    }

    private fun modern(content: @Composable () -> Unit) {
        val fileSystem = createFileSystem(rule.activity)
        rule.setContent { MaterialTheme { CompositionLocalProvider(LocalFutachaImageLoader provides images,
            LocalFutachaCatalogImageLoader provides images, LocalPostingInFlight provides tracker) {
            ProvideFutachaSharedFeatures(store, null, repository, fileSystem, null, "test", appStateStore = appStore, catalogImageLoader = images) {
                content()
            }
        } } }
    }
    private fun clickLabelled(label: String) {
        val node = hasClickAction() and (hasContentDescription(label) or hasText(label))
        rule.waitUntil(10_000) { rule.onAllNodes(node).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(node).onFirst().performClick()
    }
    private fun sendAndCheckCount() {
        rule.onNode(hasSetTextAction() and hasText("コメント")).performTextInput("L30 body")
        rule.onAllNodesWithContentDescription("送信する").onFirst().performClick()
        rule.waitUntil(10_000) { started.isCompleted }
        rule.waitForIdle()
        assertEquals("The send in progress is counted", 1, tracker.count)
        release.complete(Unit)
        rule.waitUntil(10_000) { tracker.count == 0 }
    }

    @Test fun aReplyCountsAsBeingSentUntilItEnds() {
        modern {
            ThreadScreen(board, emptyList(), "1001", "L30", 1, repository = repository, stateStore = appStore,
                preferencesState = ScreenPreferencesState("test", isTelemetryCollectionEnabled = false), onBack = {})
        }
        rule.waitUntil(15_000) { rule.onAllNodesWithText("L30 ready", substring = true).fetchSemanticsNodes().isNotEmpty() }
        clickLabelled("返信")
        sendAndCheckCount()
    }

    @Test fun aNewThreadCountsAsBeingSentUntilItEnds() {
        modern {
            CatalogScreen(board, emptyList(), onBack = {}, onThreadSelected = {}, repository = repository,
                stateStore = appStore, preferencesState = ScreenPreferencesState("test", isTelemetryCollectionEnabled = false))
        }
        clickLabelled("スレッド作成")
        sendAndCheckCount()
    }

    @Test fun aToshiakiPostCountsAsBeingSentUntilItEnds() {
        rule.setContent { CompatibilityProfileTheme("mono") { CompositionLocalProvider(LocalFutachaImageLoader provides images,
            LocalFutachaCatalogImageLoader provides images, LocalPostingInFlight provides tracker) {
            CompatPostScreen(tab, compatBoard, repository, store = store, preferences = preferences, appVersion = "test",
                fileSystem = null, onToolbarEdit = {}, onPostSent = {}, onBack = {})
        } } }
        rule.waitUntil(10_000) { rule.onAllNodes(hasSetTextAction() and hasText("コメント")).fetchSemanticsNodes().isNotEmpty() }
        sendAndCheckCount()
    }
}
