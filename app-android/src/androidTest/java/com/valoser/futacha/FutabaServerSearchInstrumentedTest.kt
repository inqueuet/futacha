@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import coil3.ImageLoader
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.AimogeAvailability
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.model.ThreadPageContent
import com.valoser.futacha.shared.network.aimoge.AimogePostSearchRow
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.board.FutabaServerSearchDialog
import com.valoser.futacha.shared.ui.board.LocalThreadSearchJumpState
import com.valoser.futacha.shared.ui.board.ScreenPreferencesState
import com.valoser.futacha.shared.ui.board.ThreadScreen
import com.valoser.futacha.shared.ui.board.ThreadSearchJumpState
import com.valoser.futacha.shared.ui.board.aimogePostSearcher
import com.valoser.futacha.shared.ui.compat.CompatibilityApp
import com.valoser.futacha.shared.ui.ftutter.*
import com.valoser.futacha.shared.ui.futaber.*
import com.valoser.futacha.shared.ui.image.LocalFutachaCatalogImageLoader
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import kotlinx.coroutines.runBlocking
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import org.junit.*
import org.junit.Assert.*

class FutabaServerSearchInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private enum class Mode { FUTACHA, TOSHIAKI, FUTABER, FTUTTER }

    private val database = "response-search-${java.util.UUID.randomUUID()}.db"
    private var store: AndroidCompatibilityStore? = null
    private var loader: ImageLoader? = null
    private val client = HttpClient(MockEngine {
        assertEquals(HttpMethod.Get, it.method)
        respond("""<script>var ret={"res":{"456":{"resto":123,"com":"検索で見つかったレス"}}};</script>""",
            HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html; charset=UTF-8"))
    })
    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        rule.waitForIdle()
        loader?.shutdown()
        runBlocking { store?.closeForTest() }
        rule.activity.deleteDatabase(database)
        client.close()
    }

    @Test fun selectingASearchResultScrollsTheOpenedThreadToThatResponse() {
        assertFutabaResultJumps(Mode.FUTACHA)
    }

    @Test fun toshiakiFutabaResultsJump() = assertFutabaResultJumps(Mode.TOSHIAKI)
    @Test fun futaberFutabaResultsJump() = assertFutabaResultJumps(Mode.FUTABER)
    @Test fun ftutterFutabaResultsJump() = assertFutabaResultJumps(Mode.FTUTTER)

    private fun assertFutabaResultJumps(mode: Mode) {
        val board = BoardSummary("img-b", "img", "", "https://img.2chan.net/b/", "")
        assertResultJumps(board, "123", aimoge = false, mode)
    }

    @Test fun aimogeResultsJumpByDisplayedSequence() = assertAimogeResultJumps(Mode.FUTACHA)
    @Test fun toshiakiAimogeResultsJump() = assertAimogeResultJumps(Mode.TOSHIAKI)
    @Test fun futaberAimogeResultsJump() = assertAimogeResultJumps(Mode.FUTABER)
    @Test fun ftutterAimogeResultsJump() = assertAimogeResultJumps(Mode.FTUTTER)

    private fun assertAimogeResultJumps(mode: Mode) {
        val board = BoardSummary("aimoge-ai2", "あいもげ", "", "https://nijiurachan.net/b/ai2/", "")
        val threadId = "01a1ab6e-57e8-755b-a01c-66792ff13aef"
        val previousEnabled = AimogeAvailability.sitesEnabled
        val previousSearcher = aimogePostSearcher
        AimogeAvailability.sitesEnabled = true
        aimogePostSearcher = { slug, query ->
            assertEquals("ai2", slug)
            assertEquals("検索", query)
            listOf(AimogePostSearchRow(threadId, "${board.url}thread/$threadId", 60, "検索で見つかったレス", null))
        }
        try {
            assertResultJumps(board, threadId, aimoge = true, mode)
        } finally {
            aimogePostSearcher = previousSearcher
            AimogeAvailability.sitesEnabled = previousEnabled
        }
    }

    private fun assertResultJumps(board: BoardSummary, expectedThreadId: String, aimoge: Boolean, mode: Mode) {
        val imageLoader = ImageLoader(rule.activity).also { loader = it }
        val savedPosition = ThreadHistoryEntry(
            expectedThreadId, board.id, "テストスレ", "", board.name, board.url, 1L, 80, lastReadItemIndex = 2
        )
        val targetBody = "表示する検索先の本文"
        val page = ThreadPage(expectedThreadId, "テストスレ", null, null, List(80) { index ->
            Post(
                if (index == 60 && !aimoge) "456" else (123 + index).toString(),
                index, null, null, "",
                messageHtml = if (index == 60) targetBody else "通常のレス $index",
                imageUrl = null, thumbnailUrl = null,
                displayNumber = index.takeIf { aimoge }
            )
        })
        val repository = object : BoardRepository by FakeBoardRepository() {
            override suspend fun getThread(board: String, threadId: String) = page
            override suspend fun getThreadByUrl(threadUrl: String) = page
            override suspend fun getThreadContent(board: String, threadId: String) = ThreadPageContent(page)
            override suspend fun getThreadContentByUrl(threadUrl: String) = ThreadPageContent(page)
        }
        val compatStore = if (mode == Mode.TOSHIAKI) runBlocking {
            AndroidCompatibilityStore(rule.activity, databaseName = database).also {
                store = it
                it.initialize()
                it.savePreference("compat.commonUsedVersion", "1.0")
                it.upsertBoard(CompatBoard(compatBoardKey(board.url), board.name, board.url, board.url, 0))
            }
        } else null
        rule.setContent { MaterialTheme {
            CompositionLocalProvider(
                LocalThreadSearchJumpState provides remember { ThreadSearchJumpState() },
                LocalFutachaImageLoader provides imageLoader,
                LocalFutachaCatalogImageLoader provides imageLoader
            ) {
                var opened by remember { mutableStateOf<String?>(null) }
                val threadUrl = opened
                if (threadUrl == null) {
                    FutabaServerSearchDialog(client, board.url, {}, { hit ->
                        assertEquals(expectedThreadId, hit.id)
                        opened = hit.threadUrl
                    })
                } else when (mode) {
                    Mode.FUTACHA -> ThreadScreen(
                        board = board, history = listOf(savedPosition), threadId = expectedThreadId,
                        threadTitle = "テストスレ", initialReplyCount = 80, repository = repository,
                        preferencesState = ScreenPreferencesState("test"), onBack = {}
                    )
                    Mode.TOSHIAKI -> CompatibilityApp(
                        store = requireNotNull(compatStore), repository = repository,
                        imageLoader = imageLoader, catalogImageLoader = imageLoader,
                        initialThreadDeepLink = threadUrl, onExitApplication = {}
                    )
                    Mode.FUTABER -> FutaberTheme(FutaberThemeMode.Light) {
                        FutaberThreadScreen(
                            board, FutaberThreadRef(board.id, expectedThreadId, "テストスレ", "", 80, resumeIndex = 2),
                            repository, isTabbed = false, onToggleTab = {}, onOpenManage = {}, onOpenPost = {},
                            refreshSignal = 0, quoteMode = false, onQuoteLine = {}, onOpenDrawer = {},
                            drawerGestureEnabled = false, ngWords = emptyList(), ngHeaders = emptyList(),
                            ngRules = emptyList(), onChangeNgWords = {}, onChangeNgHeaders = {},
                            onPageLoaded = {}, onScrollSettled = { _, _, _, _ -> }, onBack = {}
                        )
                    }
                    Mode.FTUTTER -> FtutterTheme(null, FtutterBrightness.LIGHT) {
                        FtutterThreadScreen(
                            board, FtutterThreadRef(board.id, expectedThreadId, "テストスレ"), repository, null,
                            emptyMap(), parseFtutterDesign(emptyMap()), savedPosition, emptySet(), null,
                            { _, _ -> }, {}, {}, {}, { _, _, _, _ -> }, {}, {}, {}, {}
                        )
                    }
                }
            }
        } }
        rule.onNodeWithTag("futaba-server-search-query").performTextInput("検索")
        rule.onNodeWithTag("futaba-server-search-submit").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("検索で見つかったレス").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("検索で見つかったレス").performClick()
        rule.waitUntil(15_000) { rule.onNodeWithText(targetBody, substring = true, useUnmergedTree = true).isDisplayed() }
        rule.onNodeWithText(targetBody, substring = true, useUnmergedTree = true).assertIsDisplayed()
    }
}
