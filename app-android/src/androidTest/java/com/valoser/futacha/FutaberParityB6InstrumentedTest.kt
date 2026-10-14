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
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import coil3.ImageLoader
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * ふたばー風モードの機能そろえ B6（メディアのみ保存・カスタムフォント・優先ファイルマネージャー）。FutaberApp をテスト用の
 * 保存先（専用のデータベースとメモリ上の AppStateStore）で直接表示する。板・スレッドは同梱の模擬データ（may板のアドレスで
 * 中身は模擬）、画像の取得は MockEngine が答え、実サーバー・外部サービスへは何も送らない。
 */
class FutaberParityB6InstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var store: AndroidCompatibilityStore
    private lateinit var appState: AppStateStore
    private lateinit var loader: ImageLoader
    private val database = "futaber_b6_ui_test.db"
    private val may = BoardSummary("may", "may/b", "", "https://may.2chan.net/b/futaba.php", "")
    private val repository = FakeBoardRepository()
    private var client: HttpClient? = null

    @Before fun prepare() {
        rule.activity.deleteDatabase(database)
        store = AndroidCompatibilityStore(rule.activity, databaseName = database)
        appState = AppStateStore(BaseInMemoryPlatformStateStorage())
        runBlocking {
            store.initialize()
            appState.setBoards(listOf(may))
            appState.setHistory(emptyList())
        }
        loader = ImageLoader(rule.activity)
    }

    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        loader.shutdown()
        client?.close()
        runBlocking {
            createFileSystem(rule.activity).deleteRecursively("private/compat_font")
            store.closeForTest()
        }
        rule.activity.deleteDatabase(database)
    }

    private fun jpegBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff3366cc.toInt()) }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }

    private fun host(preferences: Map<String, String>, httpClient: HttpClient? = null) {
        runBlocking { store.savePreferences(preferences + ("compat.futaber.lastBoardId" to "may")) }
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
                    appVersion = "test"
                )
            }
        }
    }

    private fun waitForTag(tag: String, timeoutMillis: Long = 20_000) {
        rule.waitUntil(timeoutMillis) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun absent(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()

    private fun openSaveDialog() {
        waitForTag("futaber-catalog-item")
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post")
        rule.onNodeWithTag("futaber-thread-menu").performClick()
        waitForTag("futaber-operation-menu")
        waitForTag("futaber-menu-save")
        runCatching { rule.onNodeWithTag("futaber-menu-save").performScrollTo() }
        rule.onNodeWithTag("futaber-menu-save").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("保存形式").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun byDefaultTheSaveDialogKeepsItsOldListAndTheExtrasAreOff() {
        host(emptyMap(), HttpClient(MockEngine { respond("unused") }).also { client = it })
        openSaveDialog()
        rule.onNodeWithText("HTMLのみ").assertExists()
        assertTrue(rule.onAllNodesWithText("メディアのみ（ZIP）").fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithText("メディアのみ（フォルダ）").fetchSemanticsNodes().isEmpty())
    }

    @Test fun withTheExtensionTheMediaOnlyZipIsWrittenToTheSaveFolder() {
        val bytes = jpegBytes()
        client = HttpClient(MockEngine { respond(bytes, headers = headersOf("Content-Type", "image/jpeg")) })
        val saveDir = File(rule.activity.getExternalFilesDir(null), "futaber-b6-save-${System.nanoTime()}").apply { mkdirs() }
        try {
            // The app's own folder as the save folder, so no folder picker opens.
            host(mapOf("compat.futaber.ext.mediaOnlySave" to "ON", "compat.storage.dummyDownloadDir" to saveDir.absolutePath), client)
            openSaveDialog()
            rule.onNodeWithText("メディアのみ（フォルダ）").assertExists()
            rule.onNodeWithText("メディアのみ（ZIP）").performClick()
            rule.waitUntil(60_000) { rule.onAllNodesWithText("失敗0件", substring = true).fetchSemanticsNodes().isNotEmpty() }
            val zips = saveDir.walkTopDown().filter { it.isFile && it.name.endsWith(".zip") }.toList()
            assertEquals(zips.toString(), 1, zips.size)
            assertTrue(zips.single().length() > 0)
        } finally {
            runBlocking { store.savePreference("compat.storage.dummyDownloadDir", "") }
            saveDir.deleteRecursively()
        }
    }

    @Test fun theCustomFontIsUsedOnlyWithTheExtensionAndTheBarsKeepTheirPlaces() {
        // A system font as the shared custom font (as the shared settings page would have copied it).
        val fs = createFileSystem(rule.activity)
        val font = File("/system/fonts").listFiles { file -> file.name.startsWith("DancingScript") && file.name.endsWith(".ttf") }
            ?.firstOrNull() ?: File("/system/fonts").listFiles { file -> file.name.endsWith(".ttf") }!!.first()
        runBlocking {
            fs.createDirectory("private/compat_font").getOrThrow()
            fs.writeBytes("private/compat_font/font.ttf", font.readBytes()).getOrThrow()
        }
        host(mapOf("compat.design.dummyCustomFont" to font.name))
        waitForTag("futaber-catalog-item")
        fun places() = listOf(
            rule.onAllNodesWithTag("futaber-catalog-item")[0].fetchSemanticsNode().boundsInRoot.top,
            rule.onNodeWithTag("futaber-open-settings").fetchSemanticsNode().boundsInRoot.bottom,
            rule.onNodeWithTag("futaber-manage").fetchSemanticsNode().boundsInRoot.top
        )
        val before = places()
        rule.onNodeWithTag("futaber-open-settings").performClick()
        waitForTag("futaber-settings")
        // The version row shows Latin letters ("test"), which the chosen font draws at another width.
        fun versionWidth(): Float {
            rule.onNodeWithTag("futaber-settings-detail-version").performScrollTo()
            return rule.onNodeWithTag("futaber-settings-detail-version").fetchSemanticsNode().boundsInRoot.width
        }
        val plainWidth = versionWidth()
        rule.onNodeWithTag("futaber-settings-row-extras").performScrollTo().performClick()
        waitForTag("futaber-settings-switch-ext-custom-font")
        assertTrue(absent("futaber-settings-row-custom-font"))
        rule.onNodeWithTag("futaber-settings-switch-ext-custom-font").performScrollTo().performClick()
        rule.waitUntil(5_000) { runBlocking { store.loadPreference("compat.futaber.ext.customFont") } == "ON" }
        waitForTag("futaber-settings-row-custom-font")
        rule.onNodeWithTag("futaber-settings-row-custom-font").performScrollTo().performClick()
        waitForTag("futaber-custom-font-dialog")
        rule.onNodeWithText("キャンセル").performClick()
        rule.onNodeWithTag("futaber-settings-back").performClick()
        rule.waitUntil(10_000) { versionWidth() != plainWidth }
        assertNotEquals(plainWidth, versionWidth())
        rule.onNodeWithTag("futaber-settings-done").performClick()
        waitForTag("futaber-catalog-item")
        rule.waitForIdle()
        // The fixed bars stay where they were: the first thread, the top bar's button and the bottom bar.
        assertEquals(before, places())
    }

    @Test fun thePreferredFileManagerRowOpensTheListOfFileManagers() {
        host(emptyMap())
        waitForTag("futaber-catalog-item")
        rule.onNodeWithTag("futaber-open-settings").performClick()
        waitForTag("futaber-settings")
        rule.onNodeWithTag("futaber-settings-row-preferred-file-manager").performScrollTo()
        // Nothing chosen yet (the value ふたちゃ's settings write): the row says so.
        rule.onNodeWithTag("futaber-settings-detail-preferred-file-manager", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("未設定", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("futaber-settings-row-preferred-file-manager").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("ファイラーアプリを選択").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("キャンセル").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("ファイラーアプリを選択").fetchSemanticsNodes().isEmpty() }
    }
}
