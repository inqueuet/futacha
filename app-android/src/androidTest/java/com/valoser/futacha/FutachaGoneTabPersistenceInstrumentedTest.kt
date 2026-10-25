@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.board.*
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.image.LocalFutachaCatalogImageLoader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.*
import org.junit.Assert.*

/** Actual feature host and local SQLite store; only its atomic death-state write is fault-injected. */
class FutachaGoneTabPersistenceInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val db = "audit_m50_gone_tab.db"
    private val board = BoardSummary("gone", "test board", "test", "https://may.2chan.net/b/", "")
    private val url = "${board.url}res/100.htm"
    private val tab = CompatTab(compatTabKey(url), url, url, compatBoardKey(board.url), board.name, "100", "retained tab",
        replyCount = 10, checkedReplyCount = 3, insertedAtEpochMillis = 1, contentUpdatedAtEpochMillis = 1)
    private val otherUrl = "${board.url}res/200.htm"
    private val other = tab.copy(key = compatTabKey(otherUrl), canonicalUrl = otherUrl, originalUrl = otherUrl,
        threadNo = "200", title = "other tab")
    private lateinit var store: AndroidCompatibilityStore
    private lateinit var failing: CompatibilityStore
    private lateinit var loader: ImageLoader
    private val repository = FakeBoardRepository()
    private val fail = AtomicBoolean(false)
    private val attempts = AtomicInteger()
    private val opened = mutableListOf<String>()
    private val page = ThreadPage("100", null, null, null, listOf(Post("100", author = null, subject = null, timestamp = "",
        messageHtml = "retained body", imageUrl = null, thumbnailUrl = null)))
    private var current: ThreadUiState by mutableStateOf(ThreadUiState.Success(page))
    @Before fun prepare() {
        rule.activity.deleteDatabase(db); store = AndroidCompatibilityStore(rule.activity, databaseName = db)
        runBlocking {
            store.initialize(); store.importModernBoards(listOf(board)); store.openTab(tab); store.openTab(other)
            store.savePreferences(mapOf("compat.background.backgroundThreadUpdateCheck" to "none",
                "compat.background.backgroundThreadExistCheck" to "none", COMPAT_WATCH_ENABLED_KEY to "OFF"))
        }
        loader = ImageLoader.Builder(rule.activity).build()
        failing = object : CompatibilityStore by store {
            override suspend fun updateTabIfPresent(tabKey: String, transform: (CompatTab) -> CompatTab?): Boolean {
                val attempt = attempts.incrementAndGet()
                android.util.Log.i("AuditM50", "atomic gone write attempt=$attempt injectFailure=${fail.get()} key=$tabKey")
                if (fail.get()) throw IOException("保存先の空き容量が不足しています")
                return store.updateTabIfPresent(tabKey, transform)
            }
        }
    }
    @After fun cleanup() {
        // Detach disposes the old composition even when the expected pre-fix effect exception left it non-idle.
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            rule.activity.setContentView(android.widget.FrameLayout(rule.activity))
        }
        if (::loader.isInitialized) loader.shutdown()
        if (::store.isInitialized) runBlocking { store.closeForTest() }; rule.activity.deleteDatabase(db)
    }
    private fun openAndConfirmGone() {
        rule.setContent { CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader) {
            MaterialTheme {
            ProvideFutachaSharedFeatures(failing, null, repository, null, null, "test", catalogImageLoader = loader) {
                FutachaThreadFeatureHost(board, "100", tab.title, current,
                    listState = rememberLazyListState(), repository = repository, onRestore = {}, onRefresh = {},
                    onOpenThread = { opened += it.threadId }, onShowPost = {}, onOpenDrawer = {}, onReply = {}, onClose = {}) {
                    val tools = LocalFutachaThreadTools.current
                    Column {
                        Text("retained body")
                        Text(if (tools.first { it.label == "画像一覧・選択保存" }.enabled) "保持ページあり" else "保持ページなし")
                        val move = tools.firstOrNull { it.enabled && it.label in setOf("前のスレッド", "次のスレッド") }
                        TextButton(onClick = { move?.action?.invoke() }, enabled = move != null) { Text("別のタブへ") }
                        val automatic = tools.first { it.label.startsWith("自動スクロール") }
                        TextButton(onClick = automatic.action, enabled = automatic.enabled) { Text("自動スクロール操作") }
                        tools.firstOrNull { it.label == "落ち状態の保存を再試行" }?.let { retry ->
                            TextButton(onClick = retry.action, enabled = retry.enabled) { Text(retry.label) }
                        }
                    }
                }
            }
        } } }
        rule.waitUntil(10_000) { runBlocking { store.loadThreadSnapshot(tab.key) != null &&
            store.tabs.first().any { it.key == tab.key && it.snapshotRevision > 0 } } }
        android.util.Log.i("AuditM50", "initial page and tab persisted; requesting confirmedGone transition")
        fail.set(true)
        rule.runOnUiThread { current = ThreadUiState.Error("スレッドは落ちています", confirmedGone = true) }
        rule.waitUntil(10_000) { attempts.get() == 1 }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("落ち状態を保存できませんでした").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("保存先の空き容量が不足しています", substring = true).assertIsDisplayed()
        rule.onNodeWithText("閉じる").performClick()
        rule.onNodeWithText("retained body").assertIsDisplayed()
        rule.onNodeWithText("保持ページあり").assertIsDisplayed()
        rule.onNodeWithText("自動スクロール操作").assertIsNotEnabled()
    }
    @Test fun aFailedDeathWriteKeepsThePageAndOtherTabsUsableThenRetriesTheLatestTab() {
        openAndConfirmGone()
        rule.onNodeWithText("別のタブへ").performClick(); assertEquals(listOf("200"), opened)
        val updated = tab.copy(favorite = true, checkedReplyCount = 9, title = "updated while save failed")
        runBlocking { store.updateTab(updated) }
        assertFalse(runBlocking { store.tabs.first().first { it.key == tab.key }.isDead })
        fail.set(false)
        rule.onNodeWithText("落ち状態の保存を再試行").performClick()
        rule.waitUntil(10_000) { runBlocking { store.tabs.first().first { it.key == tab.key }.isDead } }
        assertEquals(updated.copy(isDead = true), runBlocking { store.tabs.first().first { it.key == tab.key } })
        assertEquals(2, runBlocking { store.tabs.first().size })
        assertEquals(2, attempts.get())
        rule.onNodeWithText("保持ページあり").assertIsDisplayed()
    }
    @Test fun closingTheFailedTabBeforeRetryDoesNotRecreateIt() {
        openAndConfirmGone()
        runBlocking { store.closeTabs(setOf(tab.key), 123) }
        fail.set(false)
        rule.onNodeWithText("落ち状態の保存を再試行").performClick()
        rule.waitUntil(10_000) { attempts.get() == 2 }
        assertEquals(listOf(other.key), runBlocking { store.tabs.first().map { it.key } })
        rule.onNodeWithText("保持ページあり").assertIsDisplayed()
    }
}
