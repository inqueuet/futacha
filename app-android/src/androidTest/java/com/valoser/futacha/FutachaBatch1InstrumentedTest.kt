@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "DEPRECATION")

package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import coil3.ImageLoader
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.board.*
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.theme.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** ふたちゃバッチ1: 巡回結果の一致語・落ち・すべて削除、カタログ長押しのURL操作、画像一覧の列数。 */
class FutachaBatch1InstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var store: AndroidCompatibilityStore
    private lateinit var loader: ImageLoader
    private val database = "futacha_batch1_ui_test.db"

    @Before fun prepare() {
        rule.activity.deleteDatabase(database)
        store = AndroidCompatibilityStore(rule.activity, databaseName = database)
        runBlocking { store.initialize() }
        loader = ImageLoader(rule.activity)
    }

    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        loader.shutdown()
        runBlocking { store.closeForTest() }
        rule.activity.deleteDatabase(database)
    }

    private fun history(no: String, title: String, replies: Int) = CompatHistoryEntry(
        canonicalUrl = "https://may.2chan.net/b/res/$no.htm", originalUrl = "https://may.2chan.net/b/res/$no.htm",
        boardKey = compatBoardKey("https://may.2chan.net/b/"), boardName = "may", threadNo = no, title = title,
        replyCount = replies, contentUpdatedAtEpochMillis = 1_000L + no.toLong())

    private fun host(content: @Composable () -> Unit) {
        rule.setContent { FutachaTheme(ThemeMode.Light, ThemePalette.Current) {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader) {
                ProvideFutachaSharedFeatures(store, null, FakeBoardRepository(), null, null, "test") { content() }
            }
        } }
    }

    @Test fun watcherRowsShowMatchedWordAndDroppedStateAndDeleteAllAsksFirst() {
        val repo = CompatWatcherRepository(store)
        val now = System.currentTimeMillis()
        runBlocking {
            repo.record(CompatWatchMatch(history("11", "ねこスレ", 12).copy(contentUpdatedAtEpochMillis = now), true, "ねこ"))
            repo.record(CompatWatchMatch(history("12", "いぬスレ", 3).copy(contentUpdatedAtEpochMillis = now - 1), true, "いぬ"))
            repo.load(now).firstOrNull { it.keyword == "いぬ" }?.let { repo.markGone(it) }
        }
        host { FutachaDrawerTools {} }
        rule.onNodeWithText("巡回").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("ねこスレ").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("「ねこ」may・12レス").assertIsDisplayed()
        rule.onNodeWithText("「いぬ」may・3レス・落ち").assertIsDisplayed()
        // Cancelling the confirmation keeps every result.
        rule.onNodeWithText("すべて削除").performClick()
        rule.onNodeWithText(FUTACHA_WATCHER_DELETE_ALL_CONFIRMATION).assertIsDisplayed()
        rule.onNodeWithText("キャンセル").performClick()
        rule.onNodeWithText("ねこスレ").assertIsDisplayed()
        assertEquals(2, runBlocking { repo.load(now).size })
        // Confirming deletes them.
        rule.onNodeWithText("すべて削除").performClick()
        rule.onNodeWithText("すべて削除する").performClick()
        rule.waitUntil(10_000) { runBlocking { repo.load(now).isEmpty() } }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("ねこスレ").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun catalogLongPressOffersCopyUrlAndOpenInBrowserAfterTheExistingRows() {
        val board = BoardSummary("batch1-catalog", "テスト", "test", "https://may.2chan.net/b/", "")
        val item = CatalogItem("123456", "res/123456.htm", "テストスレ", null, null, replyCount = 5)
        var clipboard: androidx.compose.ui.platform.ClipboardManager? = null
        host {
            clipboard = LocalClipboardManager.current
            FutachaCatalogFeatureHost(board, CatalogMode.Catalog, CatalogUiState.Success(CatalogPageContent(listOf(item))),
                FakeBoardRepository(), {}, {}, {}, {}, {}, {}) { _ ->
                val longPress = LocalFutachaCatalogLongPress.current
                TextButton(onClick = { longPress?.invoke(item) }) { Text("長押しを開く") }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("長押しを開く").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("長押しを開く").performClick()
        // The five existing rows are unchanged and stay first.
        listOf("スレッドを開く", "タブに追加", "NGスレッドに登録", "DEL依頼を送信").forEach { rule.onNodeWithText(it).assertExists() }
        rule.onNodeWithText("URLをコピー").assertExists()
        rule.onNodeWithText("ブラウザで開く").assertExists()
        rule.onNodeWithText("URLをコピー").performClick()
        rule.runOnIdle { assertEquals("https://may.2chan.net/b/res/123456.htm", clipboard?.getText()?.text) }
        rule.onNodeWithText("URLをコピーしました").assertIsDisplayed()
    }

    private fun galleryColumns(vararg saved: Pair<String, String>): Int {
        runBlocking { saved.forEach { (key, value) -> store.savePreference(key, value) } }
        val posts = (1..8).map { n ->
            Post("$n", n, null, null, "", messageHtml = "p$n",
                imageUrl = "https://may.2chan.net/b/src/$n.jpg", thumbnailUrl = "https://may.2chan.net/b/thumb/${n}s.jpg")
        }
        host { ThreadImageGallery(posts, {}, {}, {}, {}) }
        // The file names are always shown, whether or not the (offline) thumbnails load.
        rule.waitUntil(10_000) { rule.onAllNodesWithText("1.jpg").fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
        val top = rule.onAllNodesWithText("1.jpg").fetchSemanticsNodes().first().boundsInRoot.top
        return (1..8).mapNotNull { n ->
            rule.onAllNodesWithText("$n.jpg").fetchSemanticsNodes().firstOrNull()?.boundsInRoot
        }.filter { it.top == top }.map { it.left }.distinct().size
    }

    @Test fun galleryKeepsTwoColumnsWithoutASavedSetting() { assertEquals(2, galleryColumns()) }

    @Test fun galleryFollowsASavedColumnCount() {
        assertEquals(3, galleryColumns("compat.viewer.galleryGridViewPortraitClmNum" to "3",
            "compat.viewer.galleryGridViewLandscapeClmNum" to "3"))
    }

    @Test fun galleryClampsAWideSavedColumnCountToFour() {
        assertEquals(4, galleryColumns("compat.viewer.galleryGridViewPortraitClmNum" to "12",
            "compat.viewer.galleryGridViewLandscapeClmNum" to "12"))
    }
}
