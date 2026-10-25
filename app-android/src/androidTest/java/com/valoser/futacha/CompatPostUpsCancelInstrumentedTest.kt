@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.board.IncomingSharedAttachment
import com.valoser.futacha.shared.ui.compat.*
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.ImageData
import com.valoser.futacha.shared.util.createFileSystem
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** L49: a cancelled あぷ小 pick leaves no あぷ小 mode behind, so a shared image is then attached as it is. */
class CompatPostUpsCancelInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val db = "l49-${System.nanoTime()}.db"
    private val boardUrl = "https://may.2chan.net/b/"
    private val threadUrl = "${boardUrl}res/849.htm"
    private val boardKey = compatBoardKey(boardUrl)
    private val board = CompatBoard(boardKey, "L49", boardUrl, boardUrl, 0)
    private val tab = CompatTab(compatTabKey(threadUrl), threadUrl, threadUrl, boardKey, "L49", "849", "L49",
        insertedAtEpochMillis = 1, contentUpdatedAtEpochMillis = 1)
    private val client = HttpClient(MockEngine { respondOk() })
    private lateinit var storage: AndroidCompatibilityStore
    private lateinit var fs: FileSystem

    @Before fun prepare() {
        storage = AndroidCompatibilityStore(rule.activity, databaseName = db)
        fs = createFileSystem(rule.activity)
        runBlocking {
            storage.initialize(); storage.upsertBoard(board); storage.openTab(tab)
            storage.saveToolbar(CompatToolbarSurface.POST, compatToolbarMaster(CompatToolbarSurface.POST).mapIndexed { index, item ->
                CompatToolbarItem(item.key, index, item.defaultActive || item.key == "sio")
            })
        }
    }

    @After fun cleanup() {
        IncomingSharedAttachment.pending.value?.let(IncomingSharedAttachment::consume)
        rule.runOnUiThread { rule.activity.setContent {} }
        runBlocking { storage.closeForTest() }
        client.close()
        rule.activity.deleteDatabase(db)
    }

    @Test fun aSharedImageAfterACancelledUpsPickIsAttached() {
        rule.setContent { CompatibilityProfileTheme("mono") {
            CompatPostScreen(tab, board, FakeBoardRepository(), store = storage, preferences = emptyMap(), appVersion = "test",
                fileSystem = fs, httpClient = client, onToolbarEdit = {}, onPostSent = {}, onBack = {})
        } }
        rule.waitUntil(10_000) {
            rule.onAllNodesWithTag("compat-post-toolbar-icon-sio", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitForIdle()
        rule.onNodeWithTag("compat-post-toolbar-icon-sio", useUnmergedTree = true).performClick()
        // The system picker comes in front; it is left without choosing anything.
        rule.waitUntil(10_000) { !rule.activity.hasWindowFocus() }
        Thread.sleep(1_000)
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        rule.waitUntil(10_000) { rule.activity.hasWindowFocus() }
        rule.waitForIdle()

        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val png = ByteArrayOutputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); out.toByteArray() }
        bitmap.recycle()
        IncomingSharedAttachment.offer(ImageData(png, "shared.png"))
        rule.waitUntil(5_000) { rule.onAllNodesWithText("共有された画像を添付").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("共有された画像を添付").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("compat-post-attachment-preview").fetchSemanticsNodes().isNotEmpty() }
    }
}
