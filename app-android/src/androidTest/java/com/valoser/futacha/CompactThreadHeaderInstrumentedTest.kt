package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.state.createAppStateStore
import com.valoser.futacha.shared.ui.board.*
import com.valoser.futacha.shared.ui.compat.CompatibilityApp
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Measures actual Compose layout after changing the persisted user setting. */
class CompactThreadHeaderInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = createAppStateStore(context)
    private val originalCompact = runBlocking { store.isCompactThreadHeaderEnabled.first() }
    private val images = ImageLoader(context)
    private val database = "compact_header_${System.nanoTime()}.db"
    private var compatStore: AndroidCompatibilityStore? = null
    private val boardUrl = "https://may.2chan.net/b/"
    private val title = "非常に長いスレッドタイトルがある場合にも高さを維持する確認"
    private val board = BoardSummary("compact-test", "非常に長い板名でも折り返さない確認", "test", boardUrl, "")
    private val page = ThreadPage("1001", title, "", null, listOf(
        Post("1001", 0, "としあき", "長い題名の表示確認", "26/09/16(水)12:34:56", messageHtml = "HEADER-TEST-OP", imageUrl = null, thumbnailUrl = null),
        Post("1002", 1, "としあき", "無題", "26/09/16(水)12:34:57", messageHtml = "<font color=\"#789922\">&gt;&gt;1001</font><br>HEADER-TEST-REPLY", imageUrl = null, thumbnailUrl = null,
            quoteReferences = listOf(QuoteReference(">>1001", listOf("1001"))))
    ))
    private val repository = object : BoardRepository by FakeBoardRepository() {
        override suspend fun getThread(board: String, threadId: String) = page
        override suspend fun getThreadByUrl(threadUrl: String) = page
        override suspend fun getThreadContent(board: String, threadId: String) = ThreadPageContent(page)
        override suspend fun getThreadContentByUrl(threadUrl: String) = ThreadPageContent(page)
    }

    @After fun close() {
        rule.runOnUiThread { rule.activity.setContent {} }
        images.shutdown()
        runBlocking {
            store.setCompactThreadHeaderEnabled(originalCompact)
            compatStore?.closeForTest()
        }
        context.deleteDatabase(database)
    }

    private fun open(tree: Boolean = false, fontScale: Float = 1f, compat: Boolean = false) {
        runBlocking { store.setCompactThreadHeaderEnabled(false) }
        val compatibility = if (compat) runBlocking {
            AndroidCompatibilityStore(context, databaseName = database).also {
                compatStore = it
                it.initialize()
                it.savePreference("compat.commonUsedVersion", "1.0")
                it.upsertBoard(CompatBoard(compatBoardKey(boardUrl), board.name, boardUrl, boardUrl, 0))
            }
        } else null
        rule.runOnUiThread {
            rule.activity.setContent {
                val compact by store.isCompactThreadHeaderEnabled.collectAsState(false)
                val scope = rememberCoroutineScope()
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalFutachaImageLoader provides images,
                    LocalDensity provides Density(density.density, fontScale)
                ) {
                    MaterialTheme {
                        Box(Modifier.width(320.dp)) {
                            if (compatibility != null) {
                                CompatibilityApp(store = compatibility, stateStore = store, repository = repository,
                                    initialThreadDeepLink = "${boardUrl}res/1001.htm", onExitApplication = {})
                            } else {
                                ThreadScreen(board = board, history = emptyList(), threadId = "1001", threadTitle = title,
                                    initialReplyCount = 1, repository = repository, onBack = {},
                                    preferencesState = ScreenPreferencesState("test", isCompactThreadHeaderEnabled = compact,
                                        threadDisplayMode = if (tree) ThreadDisplayMode.Tree else ThreadDisplayMode.Flat),
                                    preferencesCallbacks = ScreenPreferencesCallbacks(onCompactThreadHeaderChanged = { enabled ->
                                        scope.launch { store.setCompactThreadHeaderEnabled(enabled) }
                                    }))
                            }
                        }
                    }
                }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("HEADER-TEST-OP", substring = true).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        rule.onNodeWithContentDescription(if (compat) "ページ最上部へ" else "最上部").performClick()
        rule.waitForIdle()
    }

    private fun changeInSettings(enabled: Boolean) {
        rule.onAllNodesWithContentDescription("その他").onFirst().performClick()
        rule.onAllNodesWithText("設定").onLast().performClick()
        rule.onNodeWithText("表示").performClick()
        rule.onNodeWithTag("compact-thread-header-switch").performScrollTo().performClick()
        rule.waitUntil(5_000) { runBlocking { store.isCompactThreadHeaderEnabled.first() } == enabled }
        rule.onNodeWithTag("compact-thread-header-switch").assertIsToggleable()
        if (enabled) rule.onNodeWithTag("compact-thread-header-switch").assertIsOn()
        else rule.onNodeWithTag("compact-thread-header-switch").assertIsOff()
        rule.onAllNodesWithContentDescription("戻る").onLast().performClick()
        rule.waitForIdle()
    }

    private fun height(tag: String) = rule.onAllNodesWithTag(tag, useUnmergedTree = true).onLast().fetchSemanticsNode().boundsInRoot.height

    @Test fun flatSettingShrinksTopBarAndPostHeadersImmediatelyAndRestores() = verifySetting(false)
    @Test fun treeSettingShrinksTopBarAndPostHeadersImmediatelyAndRestores() = verifySetting(true)

    private fun verifySetting(tree: Boolean) {
        open(tree)
        val topHeight = height("futacha-thread-top-bar")
        val postHeight = height("futacha-post-header-1001")
        changeInSettings(true)
        val compactTop = height("futacha-thread-top-bar")
        val compactPost = height("futacha-post-header-1001")
        assertTrue("Top bar must become shorter: $topHeight -> $compactTop", compactTop < topHeight)
        assertTrue("Post header must become shorter: $postHeight -> $compactPost", compactPost < postHeight)
        // A long board name can make the normal header taller than 64dp.
        assertTrue(topHeight - compactTop >= 16f * rule.density.density - 1f)
        assertTrue(runBlocking { createAppStateStore(context).isCompactThreadHeaderEnabled.first() })
        assertSingleLineTitle()
        changeInSettings(false)
        assertEquals(topHeight, height("futacha-thread-top-bar"), 1f)
        assertEquals(postHeight, height("futacha-post-header-1001"), 1f)
    }

    private fun assertSingleLineTitle() {
        val nodes = rule.onAllNodes(
            (hasText(title) or hasText(board.name, substring = true)) and hasAnyAncestor(hasTestTag("futacha-thread-top-bar")),
            useUnmergedTree = true
        )
        assertEquals(2, nodes.fetchSemanticsNodes().size)
        nodes.fetchSemanticsNodes().indices.forEach { index ->
            val layouts = mutableListOf<TextLayoutResult>()
            nodes[index].performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            layouts.forEach { assertEquals("Compact title/detail must not wrap", 1, it.lineCount) }
        }
        listOf("戻る", "スレ内検索", "履歴を開く", "その他").forEach {
            rule.onAllNodesWithContentDescription(it).onFirst().assertIsDisplayed()
        }
    }

    @Test fun narrowScreenWithLargeSystemTextKeepsCompactTitleOnOneLine() {
        open(fontScale = 1.5f)
        runBlocking { store.setCompactThreadHeaderEnabled(true) }
        rule.waitForIdle()
        assertSingleLineTitle()
        rule.onNodeWithContentDescription("スレ内検索").performClick()
        rule.onNodeWithContentDescription("検索を閉じる").assertIsDisplayed().performClick()
        rule.waitForIdle()
        assertSingleLineTitle()
    }

    @Test fun flatQuotePreviewUsesCompactPostHeader() = verifyQuote(false)
    @Test fun treeQuotePreviewUsesCompactPostHeader() = verifyQuote(true)

    private fun verifyQuote(tree: Boolean) {
        open(tree)
        rule.onNodeWithText(">>1001", substring = true).performClick()
        val normal = height("futacha-post-header-1001")
        runBlocking { store.setCompactThreadHeaderEnabled(true) }
        rule.waitForIdle()
        assertTrue(height("futacha-post-header-1001") < normal)
        rule.onAllNodesWithText("HEADER-TEST-OP").onLast().assertIsDisplayed()
    }

    @Test fun toshiakiUsesItsOwnHeaderSettingsOnBothGlobalCompactValues() {
        open(compat = true)
        val normal = height("compat-thread-post-1001")
        runBlocking { store.setCompactThreadHeaderEnabled(true) }
        rule.waitForIdle()
        assertEquals(normal, height("compat-thread-post-1001"), 1f)
        rule.onNodeWithTag("futacha-thread-top-bar").assertDoesNotExist()
        rule.onNodeWithTag("compat-thread-post-1001").assertIsDisplayed()
    }
}
