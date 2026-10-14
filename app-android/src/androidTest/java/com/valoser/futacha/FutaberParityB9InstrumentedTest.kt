@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "DEPRECATION")

package com.valoser.futacha

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import coil3.ImageLoader
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.service.AUTO_SAVE_DIRECTORY
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.BaseInMemoryPlatformStateStorage
import com.valoser.futacha.shared.ui.futaber.FutaberApp
import com.valoser.futacha.shared.ui.futaber.FutaberMediaServices
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.util.createFileSystem
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream

/** The bundled mock repository reads this board (the address names example.com); the id is the test's own. */
private const val B9_TUTORIAL_URL = "https://www.example.com/t/futaba.php"
private const val B9_BOARD_ID = "zz-b9-tutorial"

/**
 * ふたばー風モードの機能そろえ B9（カタログの細かな調整・閲覧中スレッドの自動保存コピー）。FutaberApp をテスト用の
 * 保存先（専用のデータベースとメモリ上の AppStateStore）で直接表示する。板・スレッドはチュートリアル板の模擬データ、
 * 画像の取得は MockEngine が小さなJPEGで答え、実サーバー・外部サービスへは何も送らない。自動保存はアプリの
 * 自動保存フォルダ（AUTO_SAVE_DIRECTORY）に、この試験専用の板IDで書き、後片付けで消す。
 */
class FutaberParityB9InstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var store: AndroidCompatibilityStore
    private lateinit var appState: AppStateStore
    private lateinit var loader: ImageLoader
    private lateinit var autoSaved: SavedThreadRepository
    private val database = "futaber_b9_ui_test.db"
    private val tutorial = BoardSummary(B9_BOARD_ID, "B9チュートリアル", "チュートリアル", B9_TUTORIAL_URL, "チュートリアル")
    private val repository = FakeBoardRepository()
    private var client: HttpClient? = null

    @Before fun prepare() {
        rule.activity.deleteDatabase(database)
        store = AndroidCompatibilityStore(rule.activity, databaseName = database)
        appState = AppStateStore(BaseInMemoryPlatformStateStorage())
        autoSaved = SavedThreadRepository(createFileSystem(rule.activity), baseDirectory = AUTO_SAVE_DIRECTORY)
        runBlocking {
            store.initialize()
            appState.setBoards(listOf(tutorial))
            appState.setHistory(emptyList())
            removeOwnAutoSaves()
        }
        loader = ImageLoader(rule.activity)
    }

    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        loader.shutdown()
        client?.close()
        runBlocking {
            removeOwnAutoSaves()
            store.closeForTest()
        }
        rule.activity.deleteDatabase(database)
    }

    private suspend fun ownAutoSaves() = autoSaved.getAllThreads().filter { it.boardId == B9_BOARD_ID }

    private suspend fun removeOwnAutoSaves() {
        ownAutoSaves().forEach { autoSaved.deleteThread(it.threadId, it.boardId) }
    }

    private fun jpegBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff3366cc.toInt()) }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }

    private fun host(preferences: Map<String, String>) {
        val bytes = jpegBytes()
        val httpClient = HttpClient(MockEngine { respond(bytes, headers = headersOf("Content-Type", "image/jpeg")) }).also { client = it }
        runBlocking { store.savePreferences(preferences + ("compat.futaber.lastBoardId" to B9_BOARD_ID)) }
        val services = FutaberMediaServices(store, httpClient, createFileSystem(rule.activity), null)
        rule.setContent {
            val boards by appState.boards.collectAsState(emptyList())
            val history by appState.history.collectAsState(emptyList())
            val prefs by store.preferences.collectAsState(emptyMap())
            val ngRules by store.ngRules.collectAsState(emptyList())
            CompositionLocalProvider(LocalFutachaImageLoader provides loader) {
                if (boards.isNotEmpty() && prefs["compat.futaber.lastBoardId"] != null) FutaberApp(
                    boards = boards,
                    history = history,
                    repository = repository,
                    stateStore = appState,
                    preferences = prefs,
                    ngRules = ngRules,
                    savePreference = { key, value -> runBlocking { store.savePreference(key, value) } },
                    mediaServices = services,
                    appVersion = "test",
                    autoSavedThreadRepository = autoSaved
                )
            }
        }
    }

    private fun waitForTag(tag: String, timeoutMillis: Long = 20_000) {
        rule.waitUntil(timeoutMillis) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun absent(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()

    private fun firstCellWidth(): Float = rule.onAllNodesWithTag("futaber-catalog-item")[0].fetchSemanticsNode().boundsInRoot.width

    private fun gridWidth(): Float = rule.onNodeWithTag("futaber-catalog-grid").fetchSemanticsNode().boundsInRoot.width

    // ---- C06: the catalog's fine adjustments ----

    @Test fun theColumnsOfTheGridFollowTheSettingOnlyWithTheExtension() {
        host(emptyMap())
        waitForTag("futaber-catalog-grid")
        waitForTag("futaber-catalog-item")
        val grid = gridWidth()
        val before = firstCellWidth()
        // The default style 横4: four cells across.
        assertEquals(grid / 4f, before, grid * 0.03f)

        rule.onNodeWithTag("futaber-open-settings").performClick()
        waitForTag("futaber-settings")
        rule.onNodeWithTag("futaber-settings-row-catalog").performScrollTo().performClick()
        waitForTag("futaber-settings-switch-catalog-scroll-top")
        // Off by default: the catalog page has no adjustments.
        assertTrue(absent("futaber-settings-row-catalog-columns-grid4"))
        rule.onNodeWithTag("futaber-settings-back").performClick()
        rule.onNodeWithTag("futaber-settings-row-extras").performScrollTo().performClick()
        waitForTag("futaber-settings-switch-ext-catalog-fine-tune")
        rule.onNodeWithTag("futaber-settings-switch-ext-catalog-fine-tune").performScrollTo().performClick()
        rule.waitUntil(5_000) { runBlocking { store.loadPreference("compat.futaber.ext.catalogFineTune") } == "ON" }
        rule.onNodeWithTag("futaber-settings-back").performClick()
        rule.onNodeWithTag("futaber-settings-row-catalog").performScrollTo().performClick()
        waitForTag("futaber-settings-row-catalog-columns-grid4")
        rule.onNodeWithTag("futaber-settings-row-catalog-columns-grid4").performScrollTo()
        // 横4 → 2 columns.
        rule.onNodeWithTag("futaber-settings-minus-catalog-columns-grid4", useUnmergedTree = true).performClick()
        rule.waitUntil(5_000) { runBlocking { store.loadPreference("compat.futaber.catalog.gridColumns.grid4") } == "3" }
        rule.onNodeWithTag("futaber-settings-minus-catalog-columns-grid4", useUnmergedTree = true).performClick()
        rule.waitUntil(5_000) { runBlocking { store.loadPreference("compat.futaber.catalog.gridColumns.grid4") } == "2" }
        rule.onNodeWithTag("futaber-settings-row-catalog-columns-grid4").assertExists()
        rule.onNodeWithTag("futaber-settings-back").performClick()
        waitForTag("futaber-settings-done")
        rule.onNodeWithTag("futaber-settings-done").performClick()
        waitForTag("futaber-catalog-item")
        rule.waitUntil(10_000) { firstCellWidth() > before * 1.8f }
        assertEquals(gridWidth() / 2f, firstCellWidth(), gridWidth() * 0.03f)

        // Turning the extension off draws the style's own four columns again (the stored value is kept but not read).
        runBlocking { store.savePreference("compat.futaber.ext.catalogFineTune", "OFF") }
        rule.waitUntil(10_000) { firstCellWidth() < before * 1.2f }
        assertEquals(gridWidth() / 4f, firstCellWidth(), gridWidth() * 0.03f)
    }

    // ---- the auto-saved copy of a viewed thread ----

    private fun openFirstThread(): String {
        waitForTag("futaber-catalog-item")
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post")
        rule.waitUntil(10_000) { runBlocking { appState.history.first() }.any { it.boardId == B9_BOARD_ID } }
        return runBlocking { appState.history.first() }.first { it.boardId == B9_BOARD_ID }.threadId
    }

    @Test fun withTheExtensionTheOpenedThreadIsAutoSavedAndItsHistoryRowMarked() {
        host(mapOf("compat.futaber.ext.autoSaveView" to "ON"))
        val threadId = openFirstThread()
        // The first save waits some 30 seconds after the page is ready (as in ふたちゃ), then writes the index.
        rule.waitUntil(150_000) { runBlocking { ownAutoSaves() }.any { it.threadId == threadId } }
        val saved = runBlocking { ownAutoSaves() }
        assertEquals(saved.toString(), 1, saved.size)
        assertEquals(threadId, saved.single().threadId)
        rule.waitUntil(10_000) {
            runBlocking { appState.history.first() }.firstOrNull { it.boardId == B9_BOARD_ID && it.threadId == threadId }?.hasAutoSave == true
        }
    }

    @Test fun withTheExtensionOffNothingIsAutoSaved() {
        host(emptyMap())
        val threadId = openFirstThread()
        // Longer than the first save waits with the extension on.
        val until = System.currentTimeMillis() + 45_000
        while (System.currentTimeMillis() < until) {
            rule.waitForIdle()
            Thread.sleep(1_000)
        }
        assertTrue(runBlocking { ownAutoSaves() }.isEmpty())
        assertTrue(runBlocking { appState.history.first() }.first { it.boardId == B9_BOARD_ID && it.threadId == threadId }.hasAutoSave.not())
    }
}
