package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import coil3.ImageLoader
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.compat.CompatibilityApp
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

/** All replies are hidden before invoking speech; no audio backend should be prepared. */
class CompatHiddenReadAloudInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val db = "audit_m16_hidden_speech.db"
    private val boardUrl = "https://may.2chan.net/b/"
    private val url = "${boardUrl}res/100.htm"
    private val boardKey = compatBoardKey(boardUrl)
    private val tabKey = compatTabKey(url)
    private lateinit var store: AndroidCompatibilityStore
    private lateinit var loader: ImageLoader
    @Before fun prepare() {
        rule.activity.deleteDatabase(db)
        store = AndroidCompatibilityStore(rule.activity, databaseName = db)
        loader = ImageLoader.Builder(rule.activity).build()
        runBlocking {
            store.initialize(); store.savePreference("compat.commonUsedVersion", "1.0"); store.upsertBoard(CompatBoard(boardKey, "板", boardUrl, boardUrl, 0))
            store.openTab(CompatTab(tabKey, url, url, boardKey, "板", "100", "NG編集の検証", insertedAtEpochMillis = 1, contentUpdatedAtEpochMillis = 1))
        }
    }
    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        loader.shutdown(); runBlocking { store.closeForTest() }; rule.activity.deleteDatabase(db)
    }
    private fun post(id: String) = Post(id, author = null, subject = null, timestamp = "", messageHtml = "本文$id", imageUrl = null, thumbnailUrl = null)
    private fun open(kind: CompatNgKind, scope: String, value: String, memo: String = "") {
        runBlocking { assertTrue(store.upsertNgRule(CompatNgRule("seed", kind, scope, value, 1, memo = memo))) }
        val page = ThreadPage("100", null, null, null, listOf(post("100"), post("101").copy(posterId = "AbCd"), post("102").copy(posterId = "XyZ")))
        val repo = object : BoardRepository by FakeBoardRepository() {
            override suspend fun getThreadByUrl(threadUrl: String) = page
        }
        rule.setContent { MaterialTheme { CompositionLocalProvider(LocalFutachaImageLoader provides loader) {
            CompatibilityApp(store, repository = repo, initialThreadDeepLink = url, onExitApplication = {})
        } } }
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("compat-thread-pager").fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(15_000) { runBlocking { store.loadThreadSnapshot(tabKey)?.posts?.size == 3 } }
        rule.waitUntil(15_000) {
            rule.onAllNodesWithTag("compat-thread-initial-loading").fetchSemanticsNodes().isEmpty() &&
                rule.onAllNodesWithTag("compat-thread-post-100").fetchSemanticsNodes().isEmpty()
        }
        rule.onNodeWithTag("compat-toolbar-command-other", useUnmergedTree = true).performScrollTo().performClick()
        rule.onNodeWithText("読み上げ", substring = true).performClick()
    }
    @Test fun hidingEveryReplyReportsNoReadableTextBeforePreparingAudio() {
        open(CompatNgKind.THREAD_WORD, "*", "本文")
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText("読み上げ対象がありません").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("compat-speech-status").assertTextEquals("読み上げ対象がありません")
        rule.onNodeWithText("1 / 3 レス").assertIsDisplayed()
    }
}
