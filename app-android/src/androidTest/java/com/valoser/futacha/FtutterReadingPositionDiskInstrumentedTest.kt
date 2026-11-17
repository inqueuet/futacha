@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import android.content.ContextWrapper
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.state.*
import com.valoser.futacha.shared.ui.ftutter.*
import com.valoser.futacha.shared.ui.image.*
import com.valoser.futacha.shared.util.AndroidFileSystem
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import java.io.File
import org.junit.*
import org.junit.Assert.*

/** The optional write/read phases are invoked in separate Android processes by the review verifier. */
class FtutterReadingPositionDiskInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val board = BoardSummary("disk-position", "fixture", "test", "https://may.2chan.net/b/", "")
    private val page = ThreadPage("100", null, null, null, (100..219).map { n ->
        Post("$n", author = null, subject = null, timestamp = "", messageHtml = "disk-body-$n<br>line two<br>line three",
            imageUrl = null, thumbnailUrl = null)
    })
    private val phase get() = InstrumentationRegistry.getArguments().getString("readingDiskPhase") ?: "roundtrip"
    private lateinit var root: File
    private lateinit var loader: ImageLoader
    private lateinit var store: AppStateStore
    private val writes = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val failures = java.util.concurrent.CopyOnWriteArrayList<Throwable>()
    private var shown by mutableStateOf(true)
    private var history by mutableStateOf<ThreadHistoryEntry?>(null)
    private fun newStore(): AppStateStore {
        val isolated = object : ContextWrapper(rule.activity) { override fun getFilesDir(): File = root }
        return AppStateStore(BaseInMemoryPlatformStateStorage(),
            AppStateHistoryFileStore(AndroidFileSystem(isolated), Json { ignoreUnknownKeys = true }, "ReadingDiskTest"))
    }
    @Before fun prepare() {
        root = File(rule.activity.filesDir, "reading-position-process-fixture")
        if (phase != "read") root.deleteRecursively()
        root.mkdirs()
        loader = ImageLoader.Builder(rule.activity).build()
        store = newStore()
    }
    @After fun cleanup() {
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.runOnUiThread { shown = false }; rule.waitForIdle()
        runBlocking { writes.coroutineContext[Job]!!.children.toList().joinAll(); store.close() }
        writes.cancel(); loader.shutdown()
        if (phase != "write") root.deleteRecursively()
    }
    @Test fun backgroundViewportIsWrittenToDiskAndRestoresInANewStoreOrProcess() {
        if (phase == "read") {
            assertNotEquals("Read phase must be a different process", Process.myPid(), File(root, "writer-pid").readText().toInt())
            history = runBlocking { store.readHistory().single() }
            assertEquals("180", history!!.lastReadPostId)
        } else {
            history = ThreadHistoryEntry("100", board.id, "fixture", "", board.name, board.url, lastVisitedEpochMillis = 1, replyCount = 120)
            runBlocking { store.setHistory(listOf(history!!)) }
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader) {
                FtutterTheme(null, FtutterBrightness.LIGHT) {
                    if (shown) FtutterThreadScreen(board, FtutterThreadRef(board.id, "100", "fixture"), FakeBoardRepository(), null,
                        emptyMap(), parseFtutterDesign(emptyMap()), history, emptySet(), null,
                        { _, _ -> }, {}, {}, {}, { index, offset, id, total ->
                            writes.launch {
                                try { store.updateHistoryScrollPositionImmediately(AppStateHistoryScrollUpdateRequest(
                                    "100", index, offset, id, boardId = board.id, title = "fixture", titleImageUrl = "",
                                    boardName = board.name, boardUrl = board.url, replyCount = total)) }
                                catch (failure: Throwable) { failures += failure }
                            }
                        }, {}, {}, {}, {}, initialPage = page, refreshOnOpen = false)
                }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("ftutter-post").fetchSemanticsNodes().isNotEmpty() }
        if (phase == "read") {
            rule.onNode(hasText("disk-body-180", substring = true) and hasTestTag("ftutter-post-body"), useUnmergedTree = true).assertIsDisplayed()
            return
        }
        rule.onNodeWithTag("ftutter-thread-list").performScrollToIndex(81)
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        rule.waitUntil(10_000) { runBlocking { store.readHistory().single().lastReadPostId == "180" } }
        assertTrue(failures.toString(), failures.isEmpty())
        // Read actual files through a new file store before disposing the screen.
        val fresh = newStore()
        val persisted = runBlocking { fresh.readHistory().single().also { fresh.close() } }
        assertEquals("180", persisted.lastReadPostId)
        File(root, "writer-pid").writeText(Process.myPid().toString())
        if (phase == "roundtrip") {
            rule.runOnUiThread { shown = false }; rule.waitForIdle()
            rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            rule.runOnUiThread { history = persisted; shown = true }
            rule.waitForIdle()
            rule.onNode(hasText("disk-body-180", substring = true) and hasTestTag("ftutter-post-body"), useUnmergedTree = true).assertIsDisplayed()
        }
    }
}
