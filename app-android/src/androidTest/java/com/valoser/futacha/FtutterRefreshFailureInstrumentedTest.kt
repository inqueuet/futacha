@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import coil3.ImageLoader
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.network.NetworkException
import com.valoser.futacha.shared.repo.*
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.ftutter.*
import com.valoser.futacha.shared.ui.image.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.*
import org.junit.Assert.*

/**
 * 更新の失敗（2026-11-08に原作で確かめた）：原作は本文と題名を消して「！」だけを出し、更新し直すと戻る。
 * 「更新に失敗しても本文を残す」（従来の動き）では本文を残して理由を知らせる。通信は手元の模擬だけ。
 */
class FtutterRefreshFailureInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val board = BoardSummary("fail", "Fail board", "test", "https://may.2chan.net/b/", "")
    private val page = ThreadPage("100", null, null, null, listOf(post("100"), post("101")))
    private val entry = ThreadHistoryEntry("100", board.id, "Failure thread", "", board.name, board.url, 1, 2)
    @Volatile private var failure: Throwable? = null
    @Volatile private var fetches = 0
    private val notices = CopyOnWriteArrayList<String>()
    private val repo = object : BoardRepository by FakeBoardRepository() {
        override suspend fun getThreadContent(board: String, threadId: String): ThreadPageContent {
            fetches++; failure?.let { throw it }; return ThreadPageContent(page)
        }
    }
    private lateinit var loader: ImageLoader
    private val client = HttpClient(MockEngine { respond("") })
    private fun post(id: String) = Post(id, author = null, subject = null, timestamp = "", messageHtml = "fail-body-$id", imageUrl = null, thumbnailUrl = null)
    @Before fun prepare() { loader = ImageLoader.Builder(rule.activity).build() }
    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }; rule.waitForIdle()
        loader.shutdown(); client.close()
    }
    private fun start(preferences: Map<String, String>) {
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader) {
                FtutterTheme(null, FtutterBrightness.LIGHT) {
                    FtutterThreadScreen(board, FtutterThreadRef(board.id, "100", entry.title), repo, client,
                        preferences, parseFtutterDesign(preferences), entry, emptySet(), null,
                        { _, _ -> }, {}, {}, {}, { _, _, _, _ -> }, {}, {}, {}, { notices += it })
                }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("fail-body-100").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun refreshAndWait(count: Int) {
        rule.onNodeWithTag("ftutter-thread-tool-refresh").performClick()
        rule.waitUntil(10_000) { fetches >= count }
        rule.waitForIdle()
    }
    @Test fun aFailedRefreshShowsOnlyTheMarkAndARetryBringsThePostsBack() {
        start(emptyMap())
        val before = fetches
        failure = NetworkException("offline fixture", statusCode = null)
        refreshAndWait(before + 1)
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("ftutter-thread-failed").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("ftutter-thread-title-failed").assertIsDisplayed()
        rule.onAllNodesWithText("fail-body-100").assertCountEquals(0)
        rule.onAllNodesWithText("Failure thread").assertCountEquals(0)
        assertTrue("原作は知らせを出さない: $notices", notices.isEmpty())
        failure = null
        refreshAndWait(before + 2)
        rule.waitUntil(5_000) { rule.onAllNodesWithText("fail-body-100").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithTag("ftutter-thread-failed").assertCountEquals(0)
        rule.onAllNodesWithTag("ftutter-thread-title-failed").assertCountEquals(0)
    }
    @Test fun theLegacySwitchKeepsThePostsAndTellsTheReason() {
        start(mapOf(FTUTTER_LEGACY_KEEP_ON_FAILURE_KEY to "true"))
        val before = fetches
        failure = NetworkException("offline fixture", statusCode = null)
        refreshAndWait(before + 1)
        rule.waitUntil(5_000) { notices.isNotEmpty() }
        rule.onNodeWithText("fail-body-100").assertExists()
        rule.onAllNodesWithTag("ftutter-thread-failed").assertCountEquals(0)
    }
    @Test fun aFallenThreadStillKeepsItsPosts() {
        start(emptyMap())
        val before = fetches
        failure = NetworkException("gone fixture", statusCode = 404)
        refreshAndWait(before + 1)
        rule.waitUntil(5_000) { notices.contains("スレッドは落ちています") }
        rule.onNodeWithText("fail-body-100").assertExists()
        rule.onAllNodesWithTag("ftutter-thread-failed").assertCountEquals(0)
    }
    @Test fun aThreadThatNeverLoadedAlsoShowsOnlyTheMark() {
        failure = NetworkException("offline fixture", statusCode = null)
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader) {
                FtutterTheme(null, FtutterBrightness.LIGHT) {
                    FtutterThreadScreen(board, FtutterThreadRef(board.id, "100", entry.title), repo, client,
                        emptyMap(), parseFtutterDesign(emptyMap()), entry, emptySet(), null,
                        { _, _ -> }, {}, {}, {}, { _, _, _, _ -> }, {}, {}, {}, { notices += it })
                }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("ftutter-thread-failed").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("ftutter-thread-title-failed").assertIsDisplayed()
        rule.onAllNodesWithTag("ftutter-thread-error").assertCountEquals(0)
        assertTrue(notices.isEmpty())
    }
    private fun openNeverLoaded(preferences: Map<String, String>) {
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader) {
                FtutterTheme(null, FtutterBrightness.LIGHT) {
                    FtutterThreadScreen(board, FtutterThreadRef(board.id, "100", entry.title), repo, client,
                        preferences, parseFtutterDesign(preferences), entry, emptySet(), null,
                        { _, _ -> }, {}, {}, {}, { _, _, _, _ -> }, {}, {}, {}, { notices += it })
                }
            }
        }
    }
    /** M23（2026-11-08に原作で確かめた）：1度も読めていない落ちたスレは「ここまで読んだ」と落ちた帯だけ、ボタンも知らせも無い。 */
    @Test fun aFallenThreadThatNeverLoadedShowsOnlyTheBandsLikeTheOriginal() {
        failure = NetworkException("gone fixture", statusCode = 404)
        openNeverLoaded(emptyMap())
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("ftutter-thread-fallen").fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasText("スレッドは落ちています") and hasAnyAncestor(hasTestTag("ftutter-end-band"))).assertIsDisplayed()
        rule.onNodeWithTag("ftutter-kokomade").assertIsDisplayed()
        rule.onAllNodesWithTag("ftutter-thread-error").assertCountEquals(0)
        rule.onAllNodesWithTag("ftutter-archive").assertCountEquals(0)
        rule.onAllNodesWithText("再読み込み").assertCountEquals(0)
        rule.waitForIdle()
        assertTrue("原作は知らせを出さない: $notices", notices.isEmpty())
    }
    @Test fun theLegacyFallenSwitchKeepsTheTextAndTheButtons() {
        failure = NetworkException("gone fixture", statusCode = 404)
        openNeverLoaded(mapOf(FTUTTER_LEGACY_FALLEN_KEY to "true"))
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("ftutter-thread-error").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("再読み込み").assertIsDisplayed()
        rule.onAllNodesWithTag("ftutter-thread-fallen").assertCountEquals(0)
        rule.waitUntil(5_000) { notices.contains("スレッドは落ちています") }
    }
}
