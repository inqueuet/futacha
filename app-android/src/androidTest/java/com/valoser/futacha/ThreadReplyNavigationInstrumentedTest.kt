package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.board.ScreenPreferencesState
import com.valoser.futacha.shared.ui.board.ThreadScreen
import com.valoser.futacha.shared.ui.compat.CompatibilityApp
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

/** Real screen actions backed by fixtures; never sends posts to the live board. */
class ThreadReplyNavigationInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val images = ImageLoader(context)
    private val database = "thread_actions_${System.nanoTime()}.db"
    private var store: AndroidCompatibilityStore? = null
    private val boardUrl = "https://may.2chan.net/b/"
    private val threadUrl = "${boardUrl}res/1001.htm"
    private val currentPage = AtomicReference(page(20))
    private val repository = object : BoardRepository by FakeBoardRepository() {
        override suspend fun getThread(board: String, threadId: String) = currentPage.get()
        override suspend fun getThreadByUrl(threadUrl: String) = currentPage.get()
        override suspend fun getThreadContent(board: String, threadId: String) = ThreadPageContent(currentPage.get())
        override suspend fun getThreadContentByUrl(threadUrl: String) = ThreadPageContent(currentPage.get())
    }

    @After fun close() {
        rule.runOnUiThread { rule.activity.setContent {} }
        images.shutdown()
        runBlocking { store?.closeForTest() }
        context.deleteDatabase(database)
    }

    private fun page(count: Int, quoteLines: Int = 2) = ThreadPage(
        "1001", "操作確認", "END-OF-THREAD", null,
        (1..count).map { index ->
            Post("${1000 + index}", index - 1, "としあき", null, "09/16 12:00",
                messageHtml = (1..quoteLines).joinToString("<br>") { "POST-$index-LINE-$it" },
                imageUrl = null, thumbnailUrl = null)
        }
    )

    private fun open(compat: Boolean, tree: Boolean = false) {
        val compatStore = if (compat) runBlocking {
            AndroidCompatibilityStore(context, databaseName = database).also {
                store = it
                it.initialize()
                it.savePreference("compat.commonUsedVersion", "1.0")
                it.upsertBoard(CompatBoard(compatBoardKey(boardUrl), "操作確認", boardUrl, boardUrl, 0))
                it.saveToolbar(CompatToolbarSurface.THREAD,
                    compatToolbarMaster(CompatToolbarSurface.THREAD).mapIndexed { index, item ->
                        CompatToolbarItem(item.key, index, item.key in setOf("reload", "top", "bottom", "post"))
                    }
                )
            }
        } else null
        rule.runOnUiThread {
            rule.activity.setContent {
                CompositionLocalProvider(LocalFutachaImageLoader provides images) {
                    MaterialTheme {
                        if (compatStore != null) {
                            CompatibilityApp(store = compatStore, repository = repository,
                                initialThreadDeepLink = threadUrl, onExitApplication = {})
                        } else {
                            ThreadScreen(
                                board = BoardSummary("actions", "操作確認", "test", boardUrl, ""),
                                history = emptyList(), threadId = "1001", threadTitle = "操作確認", initialReplyCount = 20,
                                repository = repository,
                                preferencesState = ScreenPreferencesState("test", threadDisplayMode = if (tree) ThreadDisplayMode.Tree else ThreadDisplayMode.Flat),
                                onBack = {}
                            )
                        }
                    }
                }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("POST-", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription(if (compat) "ページ最上部へ" else "最上部").performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText("POST-1-LINE-1", substring = true).onFirst().assertIsDisplayed()
    }

    @Test fun futachaBulkQuoteSelectsOffscreenLinesAndClearsBeforeIndividualSelection() = bulkQuote(false)
    @Test fun compatBulkQuoteSelectsOffscreenLinesAndClearsBeforeIndividualSelection() = bulkQuote(true)

    private fun bulkQuote(compat: Boolean) {
        currentPage.set(page(1, quoteLines = 12))
        open(compat)
        if (compat) {
            rule.onNodeWithTag("compat-thread-post-1001").performTouchInput { longClick() }
        } else {
            rule.onNodeWithText("No.1001").performTouchInput { longClick() }
        }
        val quoteAction = if (compat) "返信" else "引用"
        rule.waitUntil(5_000) { rule.onAllNodesWithText(quoteAction).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(quoteAction).performClick()
        rule.onNodeWithText("全選択").assertIsDisplayed().performClick()
        rule.onNodeWithText("全選択").assertIsNotEnabled()
        rule.onNodeWithText("全解除").performClick()
        rule.onNodeWithText("全解除").assertIsNotEnabled()
        rule.onNodeWithText(if (compat) "上書き" else "コピー").assertIsNotEnabled()
        rule.onNodeWithText(if (compat) "No" else "レスNo.").performClick()
        rule.onNodeWithText(if (compat) "上書き" else "コピー").assertIsEnabled()
        rule.onNodeWithText("全選択").performClick()
        rule.onNodeWithText(if (compat) "上書き" else "コピー").performClick()
        val expected = ">No.1001\n" + (1..12).joinToString("\n", postfix = "\n") { ">POST-1-LINE-$it" }
        rule.waitUntil(10_000) {
            rule.onAllNodes(hasSetTextAction() and hasText(expected)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNode(hasSetTextAction() and hasText(expected)).assertExists()
    }

    @Test fun futachaBottomStopsAtNewMarkerThenReachesFooter() = bottom(false)
    @Test fun futachaTreeBottomStopsAtNewMarkerThenReachesFooter() = bottom(false, tree = true)
    @Test fun compatBottomStopsAtExistingNewMarkerThenReachesFooter() = bottom(true)

    private fun bottom(compat: Boolean, tree: Boolean = false) {
        open(compat, tree)
        rule.onAllNodesWithText(if (compat) "新着レス 20件" else "ここから新着（20件）").assertCountEquals(0)
        currentPage.set(page(40))
        rule.onAllNodesWithContentDescription(if (compat) "リロード" else "更新").onFirst().performClick()
        rule.waitForIdle()
        val bottomLabel = if (compat) "ページ最下部へ" else "最下部"
        rule.onNodeWithContentDescription(bottomLabel).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(if (compat) "新着レス 20件" else "ここから新着（20件）").assertIsDisplayed()
        rule.onAllNodesWithText("POST-21-LINE-1", substring = true).onFirst().assertIsDisplayed()
        rule.onNodeWithText("POST-40-LINE-1", substring = true).assertIsNotDisplayed()
        rule.onNodeWithContentDescription(bottomLabel).performClick()
        rule.waitForIdle()
        if (compat) rule.onNodeWithTag("compat-thread-footer").assertIsDisplayed()
        else rule.onNodeWithText("END-OF-THREAD").assertIsDisplayed()
        rule.onAllNodesWithText("POST-40-LINE-1", substring = true).onFirst().assertIsDisplayed()
    }
}
