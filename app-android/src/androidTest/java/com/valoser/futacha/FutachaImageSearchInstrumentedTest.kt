package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import android.view.View
import android.webkit.WebView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import org.hamcrest.Matcher
import java.util.concurrent.atomic.AtomicReference
import coil3.ImageLoader
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.network.NetworkException
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.board.ThreadScreen
import com.valoser.futacha.shared.ui.board.ScreenPreferencesState
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import org.junit.After
import org.junit.Rule
import org.junit.Test

class FutachaImageSearchInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var images: ImageLoader? = null
    private var client: HttpClient? = null

    @After fun close() {
        rule.runOnUiThread { rule.activity.setContent {} }
        images?.shutdown()
        client?.close()
    }

    private fun openThread(useArchive: Boolean = false) {
        val source = "https://search-test.invalid/image.png"
        val page = ThreadPage("123", "画像検索テスト", null, null, listOf(
            Post("123", 0, null, null, "test", messageHtml = "検索用の画像", imageUrl = source, thumbnailUrl = source)
        ))
        val repo = object : BoardRepository by FakeBoardRepository() {
            private fun content(): ThreadPageContent {
                if (useArchive) throw NetworkException("gone", statusCode = 404)
                return ThreadPageContent(page)
            }
            override suspend fun getThreadContent(board: String, threadId: String) = content()
            override suspend fun getThreadContentByUrl(threadUrl: String) = content()
        }
        val http = HttpClient(MockEngine { request ->
            when (request.url.host) {
                "search-test.invalid" -> respond(byteArrayOf(1, 2, 3), headers = headersOf("Content-Type", "image/png"))
                "iqdb.org" -> respond("<html><body>Fixture image search result</body></html>")
                "futabaforest.net" -> respond("""<html><body><div class="thre" data-res="123">
                    <span class="cno">No.123</span><a href="/b/src/image.png"><img src="/b/thumb/image.png"></a>
                    <blockquote>フォレストから取得した本文</blockquote></div></body></html>""",
                    headers = headersOf("Content-Type", "text/html; charset=UTF-8"))
                else -> respond("unavailable", HttpStatusCode.ServiceUnavailable)
            }
        }).also { client = it }
        val loader = ImageLoader(rule.activity).also { images = it }
        rule.runOnUiThread {
            rule.activity.setContent {
                CompositionLocalProvider(LocalFutachaImageLoader provides loader) {
                    MaterialTheme {
                        ThreadScreen(
                            board = BoardSummary("search-test", "検索テスト", "test",
                                if (useArchive) "https://may.2chan.net/b/" else "https://search-test.invalid/b/", ""),
                            history = emptyList(), threadId = "123", threadTitle = "検索テスト", initialReplyCount = 0,
                            preferencesState = ScreenPreferencesState("test"),
                            repository = repo, httpClient = http, onBack = {}
                        )
                    }
                }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("image.png").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun previewSearchCanRecoverFromFailureAndDisplayProviderResult() {
        openThread()
        rule.onNodeWithText("image.png").performClick()
        rule.onNodeWithText("画像検索").performClick()
        rule.onNodeWithText("Google画像検索 (File)").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Google画像検索の結果URLを取得できませんでした").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("IQDB Search (File)").performScrollTo().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("IQDB", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        val webView = AtomicReference<WebView>()
        val body = AtomicReference<String>()
        onView(isAssignableFrom(WebView::class.java)).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isDisplayed()
            override fun getDescription() = "Read the rendered search result"
            override fun perform(uiController: UiController, view: View) { webView.set(view as WebView) }
        })
        rule.waitUntil(10_000) {
            rule.runOnUiThread {
                webView.get().evaluateJavascript("document.body && document.body.innerText") { body.set(it) }
            }
            body.get()?.contains("Fixture image search result") == true
        }
        rule.onNodeWithContentDescription("閉じる").performClick()
        rule.onNodeWithText("閉じる").performClick()
        rule.onNodeWithContentDescription("プレビューを閉じる").assertIsDisplayed()
    }

    @Test fun attachmentLongPressCanChooseAllSearchProvidersAndReturnToThread() {
        openThread()
        rule.onNodeWithText("image.png").performTouchInput { longClick() }
        rule.onNodeWithText("画像検索").performClick()
        rule.onNodeWithText("Bing Visual Search (URL)").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("閉じる").performClick()
        rule.onNodeWithText("image.png").assertIsDisplayed()
    }

    @Test fun goneThreadLoadsForestBodyAndOffersSearchForItsImage() {
        openThread(useArchive = true)
        rule.onNodeWithText("フォレストから取得した本文").assertIsDisplayed()
        rule.onNodeWithText("image.png").performClick()
        rule.onNodeWithText("画像検索").performClick()
        rule.onNodeWithText("Google Lens (URL)").assertIsDisplayed()
    }
}
