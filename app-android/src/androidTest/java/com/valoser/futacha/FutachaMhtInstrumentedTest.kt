@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import coil3.ImageLoader
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.compatTabKey
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.board.FutachaMhtViewer
import com.valoser.futacha.shared.ui.board.FutachaPageSaveDialog
import com.valoser.futacha.shared.ui.board.FutachaSharedFeatures
import com.valoser.futacha.shared.ui.board.FutachaStaticThreadRepository
import com.valoser.futacha.shared.ui.board.LocalFutachaSharedFeatures
import com.valoser.futacha.shared.ui.board.ProvideFutachaSharedFeatures
import com.valoser.futacha.shared.ui.board.ScreenContract
import com.valoser.futacha.shared.ui.board.ScreenHistoryCallbacks
import com.valoser.futacha.shared.ui.board.ScreenPreferencesCallbacks
import com.valoser.futacha.shared.ui.board.ScreenPreferencesState
import com.valoser.futacha.shared.ui.board.ThreadScreenDependencies
import com.valoser.futacha.shared.ui.futaber.mht.FutaberMhtOpened
import com.valoser.futacha.shared.ui.futaber.mht.FutaberMhtThread
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.theme.FutachaTheme
import com.valoser.futacha.shared.model.ThemeMode
import com.valoser.futacha.shared.model.ThemePalette
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** ふたちゃ side of the MHT support: the save choice, and the read-only viewer of an opened file. */
class FutachaMhtInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var store: AndroidCompatibilityStore? = null
    private var loader: ImageLoader? = null
    private val databaseName = "futacha_mht_ui_test.db"

    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        runBlocking { store?.closeForTest() }
        rule.activity.deleteDatabase(databaseName)
        loader?.shutdown()
    }

    private val board = BoardSummary("mht-ui", "確認板", "test", "https://may.2chan.net/b/", "")
    private val page = ThreadPage(
        "555", board.name, null, null,
        listOf(Post("555", 0, null, null, "", messageHtml = "MHTから読んだ本文です", imageUrl = null, thumbnailUrl = null))
    )

    @Test fun anOpenedMhtFileIsAReadOnlyCopyThatWritesNoTabAndNoSnapshot() {
        val storage = AndroidCompatibilityStore(rule.activity, databaseName = databaseName).also { store = it }
        runBlocking { storage.initialize() }
        val images = ImageLoader(rule.activity).also { loader = it }
        val opened = FutaberMhtOpened(
            thread = FutaberMhtThread(
                title = "MHTのスレ", boardName = board.name, boardKey = null, boardUrl = board.url, threadId = "555",
                threadUrl = "https://may.2chan.net/b/res/555.htm", page = page, pictures = emptyMap()
            ),
            page = page
        )
        val contract = ScreenContract(
            history = emptyList(), historyCallbacks = ScreenHistoryCallbacks(),
            preferencesState = ScreenPreferencesState("test", themeMode = ThemeMode.Light, themePalette = ThemePalette.Current),
            preferencesCallbacks = ScreenPreferencesCallbacks()
        )
        val repository = FutachaStaticThreadRepository(FakeBoardRepository(), page)
        rule.runOnUiThread { rule.activity.setContent {
            FutachaTheme(ThemeMode.Light, ThemePalette.Current) { CompositionLocalProvider(LocalFutachaImageLoader provides images) {
                ProvideFutachaSharedFeatures(storage, null, repository, null, null, "test") {
                    FutachaMhtViewer(board, opened, contract, ThreadScreenDependencies(repository = repository), onBack = {})
                }
            } }
        } }
        rule.waitUntil(15_000) { rule.onAllNodesWithText("MHTから読んだ本文です").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("MHTから読んだ本文です").assertIsDisplayed()
        // Give the screen's effects time to run: an ordinary thread would register itself here.
        Thread.sleep(3_000)
        assertTrue("A read-only copy must not register a tab", runBlocking { storage.tabs.first() }.isEmpty())
        val tabKey = compatTabKey("https://may.2chan.net/b/res/555.htm")
        assertNull("A read-only copy must not write a shared snapshot", runBlocking { storage.loadThreadSnapshot(tabKey) })
    }

    @Test fun theSaveFormatDialogOffersMhtOnlyWhereItIsAskedFor() {
        val storage = AndroidCompatibilityStore(rule.activity, databaseName = databaseName).also { store = it }
        runBlocking { storage.initialize() }
        val images = ImageLoader(rule.activity).also { loader = it }
        var chosen: Boolean? = null
        var withMht by androidx.compose.runtime.mutableStateOf(false)
        rule.runOnUiThread { rule.activity.setContent {
            FutachaTheme(ThemeMode.Light, ThemePalette.Current) { CompositionLocalProvider(LocalFutachaImageLoader provides images) {
                ProvideFutachaSharedFeatures(storage, io.ktor.client.HttpClient(io.ktor.client.engine.okhttp.OkHttp), FakeBoardRepository(), com.valoser.futacha.shared.util.createFileSystem(rule.activity), null, "test") {
                    val features = LocalFutachaSharedFeatures.current!!
                    Box { FutachaPageSaveDialog(
                        features, page, "k", board.name, board.url, "題", onDismiss = {},
                        onSaveMht = if (withMht) { full -> chosen = full } else null
                    ) }
                }
            } }
        } }
        // Without the option the dialog is what it always was: the three HTML formats and nothing else.
        rule.waitUntil(10_000) { rule.onAllNodesWithText("HTMLのみ").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(rule.onAllNodesWithText("MHT（1ファイル・サムネイル）").fetchSemanticsNodes().isEmpty())
        rule.runOnUiThread { withMht = true }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("MHT（1ファイル・全画像）").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("MHT（1ファイル・全画像）").performClickCompat()
        assertEquals(true, chosen)
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.performClickCompat() = performClick()
