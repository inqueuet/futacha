@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.valoser.futacha

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.ai.*
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.state.createAppStateStore
import com.valoser.futacha.shared.ui.board.*
import com.valoser.futacha.shared.ui.compat.CompatibilityApp
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/** Actual screen/list effects and OpenAiService, with deterministic HTTP responses and no live API. */
class AiModerationScrollInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val settings = createAppStateStore(context)
    private val previousModeration = runBlocking { settings.isAiPostFilterEnabled.first() }
    private val previousSummary = runBlocking { settings.isThreadSummaryModeEnabled.first() }
    private val images = ImageLoader(context)
    private val database = "ai_scroll_${System.nanoTime()}.db"
    private var compatStore: AndroidCompatibilityStore? = null
    private var service: OnDeviceAiService? = null
    private val requests = CopyOnWriteArrayList<List<Int>>()
    private val boardUrl = "https://may.2chan.net/b/"
    private val source = ThreadPage("1001", "スクロール判定確認", "END", null, (1..160).map { n ->
        Post("${1000 + n}", n - 1, "としあき", null, "09/29 00:00",
            messageHtml = "POST-${n.toString().padStart(3, '0')}<br>表示周辺の判定確認<br>テスト本文",
            imageUrl = null, thumbnailUrl = null)
    })
    private val repository = object : BoardRepository by FakeBoardRepository() {
        override suspend fun getThread(board: String, threadId: String) = source
        override suspend fun getThreadByUrl(threadUrl: String) = source
        override suspend fun getThreadContent(board: String, threadId: String) = ThreadPageContent(source)
        override suspend fun getThreadContentByUrl(threadUrl: String) = ThreadPageContent(source)
    }

    @After fun close() {
        rule.runOnUiThread { rule.activity.setContent {} }
        service?.close()
        images.shutdown()
        runBlocking {
            settings.setAiPostFilterEnabled(previousModeration)
            settings.setThreadSummaryModeEnabled(previousSummary)
            compatStore?.closeForTest()
        }
        settings.close()
        context.deleteDatabase(database)
    }

    private fun open(compat: Boolean, tree: Boolean) {
        val connection = runBlocking {
            AiConnectionStore(object : AiConnectionStorage {
                private var value: String? = null
                override fun read() = value
                override fun write(value: String) { this.value = value }
            }).also {
                it.load()
                it.save(AiProvider.DEVICE, AiProvider.OPENAI, "gpt-4.1-mini", "test-only-scroll-key")
            }
        }
        val ai = OpenAiService(connection, connection.state.value, HttpClient(MockEngine { request ->
            assertEquals("/v1/moderations", request.url.encodedPath)
            val texts = Json.parseToJsonElement((request.body as TextContent).text).jsonObject["input"]!!.jsonArray
            val numbers = texts.map { Regex("POST-(\\d+)").find(it.jsonPrimitive.content)!!.groupValues[1].toInt() }
            requests += numbers
            delay(80)
            respond(buildJsonObject {
                putJsonArray("results") {
                    numbers.forEach { n -> add(buildJsonObject {
                        putJsonObject("category_scores") {
                            OPENAI_MODERATION_CATEGORIES.keys.forEach { category ->
                                put(category, if (n == 60 && category == "harassment") 0.99 else 0.01)
                            }
                        }
                    }) }
                }
            }.toString())
        })).also { service = it }
        runBlocking {
            settings.setThreadSummaryModeEnabled(false)
            settings.setAiPostFilterEnabled(true)
        }
        val compatibility = if (compat) runBlocking {
            AndroidCompatibilityStore(context, databaseName = database).also {
                compatStore = it
                it.initialize()
                it.savePreference("compat.commonUsedVersion", "999.0")
                it.upsertBoard(CompatBoard(compatBoardKey(boardUrl), "判定確認", boardUrl, boardUrl, 0))
            }
        } else null
        rule.runOnUiThread {
            rule.activity.setContent {
                CompositionLocalProvider(LocalFutachaImageLoader provides images, LocalFutachaAiService provides ai) {
                    MaterialTheme {
                        if (compatibility != null) {
                            CompatibilityApp(store = compatibility, repository = repository, stateStore = settings,
                                initialThreadDeepLink = "${boardUrl}res/1001.htm", onExitApplication = {})
                        } else {
                            ThreadScreen(board = BoardSummary("ai-scroll", "判定確認", "test", boardUrl, ""),
                                history = emptyList(), threadId = "1001", threadTitle = "判定確認", initialReplyCount = 160,
                                repository = repository,
                                preferencesState = ScreenPreferencesState("12.0", isAiPostFilterEnabled = true,
                                    aiAvailability = AiAvailability(true, supportsPostModeration = true, externalModeration = true),
                                    threadDisplayMode = if (tree) ThreadDisplayMode.Tree else ThreadDisplayMode.Flat),
                                onBack = {})
                        }
                    }
                }
            }
        }
    }

    @Test fun futachaFlatScrollRunsModerationWithoutProgressPanel() = verifyScroll(false, false)
    @Test fun futachaTreeScrollRunsModerationWithoutProgressPanel() = verifyScroll(false, true)
    @Test fun compatScrollRunsModerationWithoutProgressPanel() = verifyScroll(true, false)

    private fun verifyScroll(compat: Boolean, tree: Boolean) {
        open(compat, tree)
        val tag = if (compat) "compat-thread-list" else "thread-content-list"
        val list = rule.onNodeWithTag(tag)
        rule.waitUntil(20_000) { requests.isNotEmpty() }
        SystemClock.sleep(700)
        val initial = requests.flatten().toSet()
        assertTrue("Only the initial viewport and neighbors should be sent: $initial", initial.max() < 30)
        assertTrue(initial.size > 1)
        assertNoProgressPanel()
        list.performTouchInput { swipeUp(durationMillis = 400) }
        rule.waitUntil(10_000) { requests.flatten().any { it > initial.max() } }
        SystemClock.sleep(700)
        assertNoProgressPanel()

        // Jump to a previously unseen region, and verify the actual returned HIDE decision reaches the UI.
        list.performScrollToIndex(58)
        rule.waitUntil(10_000) { requests.flatten().contains(60) }
        SystemClock.sleep(1_000)
        assertModeratedPostHidden(compat)
        assertNoProgressPanel()
        screenshot(if (compat) "compat" else if (tree) "tree" else "flat")

        list.performScrollToIndex(150)
        rule.waitUntil(10_000) { requests.flatten().contains(160) }
        SystemClock.sleep(800)
        val beforeReturn = requests.size
        // Recompose the previously hidden row after it has left the viewport entirely.
        // Its neighbor proves this assertion checks the correct region, not an off-screen row.
        repeat(2) {
            list.performScrollToIndex(58)
            SystemClock.sleep(1_000)
            rule.waitForIdle()
            assertModeratedPostHidden(compat)
            assertEquals("Returning to a hidden post must use its cached decision", beforeReturn, requests.size)
            assertNoProgressPanel()
            screenshot(if (compat) "compat-return" else if (tree) "tree-return" else "flat-return")
            list.performScrollToIndex(150)
            SystemClock.sleep(500)
        }
        list.performScrollToIndex(0)
        SystemClock.sleep(1_000)
        rule.waitForIdle()
        assertEquals("Returning to cached posts must not send another request", beforeReturn, requests.size)
        val allSent = requests.flatten()
        assertEquals("Unchanged bodies must not be sent twice", allSent.size, allSent.toSet().size)
        assertTrue("Scrolling must not classify the unvisited middle of the thread", 100 !in allSent)
        assertTrue(requests.all { it.size in 1..32 })
        assertNoProgressPanel()
        println("AI_SCROLL_PROOF compat=$compat tree=$tree requestBatches=$requests")
        println("AI_HIDDEN_CACHE_PROOF compat=$compat tree=$tree returns=2 hidden=true additionalRequests=${requests.size - beforeReturn}")
    }

    private fun assertModeratedPostHidden(compat: Boolean) {
        rule.onNodeWithText("POST-061", substring = true).assertIsDisplayed()
        if (compat) {
            rule.onNodeWithTag("compat-thread-post-1060").assertDoesNotExist()
        } else {
            rule.onNodeWithText("No.1060: OpenAI:", substring = true).assertIsDisplayed()
        }
        rule.onAllNodesWithText("POST-060", substring = true).assertCountEquals(0)
    }

    private fun assertNoProgressPanel() {
        rule.onAllNodesWithText("AI荒らし判定", substring = true).assertCountEquals(0)
        rule.onNodeWithTag("compat-thread-ai-panel").assertDoesNotExist()
        rule.onAllNodesWithText("AIで処理中", substring = true).assertCountEquals(0)
    }

    private fun screenshot(name: String) {
        val folder = context.getExternalFilesDir("ai-scroll-proof")!!
        folder.mkdirs()
        instrumentation.uiAutomation.takeScreenshot().useBitmap { bitmap ->
            File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private inline fun Bitmap.useBitmap(block: (Bitmap) -> Unit) { try { block(this) } finally { recycle() } }
}
