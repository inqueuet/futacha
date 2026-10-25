@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "DEPRECATION")

package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
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
import io.ktor.http.HttpStatusCode
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The 2026-10-14 audit A10/A11 of the ふたばー風 mode: work started from a sheet or a dialog that closes at once must
 * finish after it is gone (つまんね。 registration, the custom font's reset, the preferred file manager). FutaberApp is
 * shown directly on a test store; the board is the bundled mock data with may's address and every request is answered by
 * MockEngine, so nothing reaches a real server.
 */
class FutaberDialogScopeInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var store: AndroidCompatibilityStore
    private lateinit var appState: AppStateStore
    private lateinit var loader: ImageLoader
    private val database = "futaber_dialog_scope_ui_test.db"
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

    private fun waitForText(text: String, timeoutMillis: Long = 20_000) {
        rule.waitUntil(timeoutMillis) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun openSettings() {
        waitForTag("futaber-catalog-item")
        rule.onNodeWithTag("futaber-open-settings").performClick()
        waitForTag("futaber-settings")
    }

    @Test fun theTsumanneRegistrationReportsItsResultAfterTheSheetCloses() {
        val tsumanneRequests = AtomicInteger()
        client = HttpClient(MockEngine { request ->
            if (request.url.host.contains("tsumanne")) {
                tsumanneRequests.incrementAndGet()
                // Long enough for the sheet to be gone before the answer comes.
                delay(1_500)
                respond("busy", HttpStatusCode.ServiceUnavailable)
            } else {
                respond("", HttpStatusCode.NotFound)
            }
        })
        host(mapOf("compat.futaber.ext.archiveMenu" to "ON"), client)
        waitForTag("futaber-catalog-item")
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post")
        rule.onNodeWithTag("futaber-thread-menu").performClick()
        waitForTag("futaber-menu-archive")
        runCatching { rule.onNodeWithTag("futaber-menu-archive").performScrollTo() }
        rule.onNodeWithTag("futaber-menu-archive").performClick()
        waitForTag("futaber-action-archive-tsumanne")
        rule.onNodeWithTag("futaber-action-archive-tsumanne").performClick()
        waitForText("つまんね。に登録しています")
        assertTrue(rule.onAllNodesWithTag("futaber-action-archive-tsumanne").fetchSemanticsNodes().isEmpty())
        // The answer (a failure here, so no browser opens) is reported although the sheet that started it is gone.
        waitForText("つまんね。への登録に失敗しました")
        assertEquals(1, tsumanneRequests.get())
    }

    /** L107: leaving the thread while 「つまんね。に登録」 runs does not cancel it; the result comes as the app's notice. */
    @Test fun theTsumanneRegistrationGoesOnAfterLeavingTheThread() {
        val tsumanneRequests = AtomicInteger()
        client = HttpClient(MockEngine { request ->
            if (request.url.host.contains("tsumanne")) {
                tsumanneRequests.incrementAndGet()
                delay(2_000)
                respond("busy", HttpStatusCode.ServiceUnavailable)
            } else {
                respond("", HttpStatusCode.NotFound)
            }
        })
        host(mapOf("compat.futaber.ext.archiveMenu" to "ON"), client)
        waitForTag("futaber-catalog-item")
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post")
        rule.onNodeWithTag("futaber-thread-menu").performClick()
        waitForTag("futaber-menu-archive")
        runCatching { rule.onNodeWithTag("futaber-menu-archive").performScrollTo() }
        rule.onNodeWithTag("futaber-menu-archive").performClick()
        waitForTag("futaber-action-archive-tsumanne")
        rule.onNodeWithTag("futaber-action-archive-tsumanne").performClick()
        waitForText("つまんね。に登録しています")
        androidx.test.espresso.Espresso.pressBack()
        waitForTag("futaber-catalog-item")
        assertTrue(rule.onAllNodesWithTag("futaber-post").fetchSemanticsNodes().isEmpty())
        waitForText("つまんね。への登録に失敗しました")
        assertEquals(1, tsumanneRequests.get())
    }

    @Test fun theCustomFontResetFinishesAfterTheDialogCloses() {
        val fs = createFileSystem(rule.activity)
        val font = File("/system/fonts").listFiles { file -> file.name.endsWith(".ttf") }!!.first()
        runBlocking {
            fs.createDirectory("private/compat_font").getOrThrow()
            fs.writeBytes("private/compat_font/font.ttf", font.readBytes()).getOrThrow()
        }
        host(mapOf("compat.design.dummyCustomFont" to font.name, "compat.futaber.ext.customFont" to "ON"))
        openSettings()
        rule.onNodeWithTag("futaber-settings-row-extras").performScrollTo().performClick()
        waitForTag("futaber-settings-row-custom-font")
        rule.onNodeWithTag("futaber-settings-row-custom-font").performScrollTo().performClick()
        waitForTag("futaber-custom-font-reset")
        rule.onNodeWithTag("futaber-custom-font-reset").performClick()
        rule.waitUntil(10_000) { runBlocking { store.loadPreference("compat.design.dummyCustomFont") } == "デフォルト" }
        assertFalse(runBlocking { fs.exists("private/compat_font/font.ttf") })
        assertTrue(rule.onAllNodesWithTag("futaber-custom-font-dialog").fetchSemanticsNodes().isEmpty())
    }

    @Test fun clearingThePreferredFileManagerIsStoredAfterTheDialogCloses() {
        runBlocking { appState.setPreferredFileManager("com.example.files", "Example Files") }
        host(emptyMap())
        openSettings()
        rule.onNodeWithTag("futaber-settings-row-preferred-file-manager").performScrollTo().performClick()
        waitForTag("futaber-preferred-file-manager-clear")
        rule.onNodeWithTag("futaber-preferred-file-manager-clear").performClick()
        rule.waitUntil(10_000) {
            runBlocking { appState.getPreferredFileManager().first() }?.packageName.isNullOrBlank()
        }
        assertTrue(rule.onAllNodesWithTag("futaber-preferred-file-manager-dialog").fetchSemanticsNodes().isEmpty())
    }

    @Test fun aFileManagerPickedFromTheListIsStoredAfterTheListCloses() {
        runBlocking { appState.setPreferredFileManager("com.example.files", "Example Files") }
        host(emptyMap())
        openSettings()
        rule.onNodeWithTag("futaber-settings-row-preferred-file-manager").performScrollTo().performClick()
        waitForTag("futaber-preferred-file-manager-change")
        rule.onNodeWithTag("futaber-preferred-file-manager-change").performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText("ファイラーアプリを読み込み中...").fetchSemanticsNodes().isEmpty() &&
                rule.onAllNodesWithText("ファイラーアプリを選択").fetchSemanticsNodes().isNotEmpty()
        }
        val available = com.valoser.futacha.shared.util.getAvailableFileManagers(rule.activity.packageManager)
        org.junit.Assume.assumeTrue("no file manager on this device", available.isNotEmpty())
        val first = available.first()
        rule.onAllNodesWithText(first.label)[0].performClick()
        rule.waitUntil(10_000) {
            runBlocking { appState.getPreferredFileManager().first() }?.packageName == first.packageName
        }
    }
}
