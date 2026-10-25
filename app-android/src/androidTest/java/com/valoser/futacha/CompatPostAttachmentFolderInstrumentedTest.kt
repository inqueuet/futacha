@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.compat.*
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.ImageData
import com.valoser.futacha.shared.util.createFileSystem
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** L64: removing an attachment on としあき(仮)'s post screen removes its folder too, not only the file. */
class CompatPostAttachmentFolderInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val db = "l64-${System.nanoTime()}.db"
    private val boardUrl = "https://may.2chan.net/b/"
    private val threadUrl = "${boardUrl}res/864.htm"
    private val boardKey = compatBoardKey(boardUrl)
    private val tabKey = compatTabKey(threadUrl)
    private val board = CompatBoard(boardKey, "L64", boardUrl, boardUrl, 0)
    private val tab = CompatTab(tabKey, threadUrl, threadUrl, boardKey, "L64", "864", "L64",
        insertedAtEpochMillis = 1, contentUpdatedAtEpochMillis = 1)
    private lateinit var storage: AndroidCompatibilityStore
    private lateinit var fs: FileSystem
    private lateinit var container: String

    @Before fun prepare() {
        storage = AndroidCompatibilityStore(rule.activity, databaseName = db)
        fs = createFileSystem(rule.activity)
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val png = ByteArrayOutputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); out.toByteArray() }
        bitmap.recycle()
        runBlocking {
            storage.initialize(); storage.upsertBoard(board); storage.openTab(tab)
            val locator = persistCompatPostAttachment(fs, tabKey, ImageData(png, "l64.png")).getOrThrow()
            container = checkNotNull(compatPostAttachmentContainer(locator))
            storage.saveDraft(CompatReplyDraft(tabKey, comment = "keep", attachmentUri = locator, updatedAtEpochMillis = 1))
        }
    }

    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        runBlocking {
            fs.deleteRecursively(container)
            storage.closeForTest()
        }
        rule.activity.deleteDatabase(db)
    }

    @Test fun removingTheAttachmentRemovesItsFolder() {
        rule.setContent { CompatibilityProfileTheme("mono") {
            CompatPostScreen(tab, board, FakeBoardRepository(), store = storage, preferences = emptyMap(), appVersion = "test",
                fileSystem = fs, onToolbarEdit = {}, onPostSent = {}, onBack = {})
        } }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("compat-post-attachment-preview").fetchSemanticsNodes().isNotEmpty() }
        // The button is off while the draft's attachment is read back.
        val removeButton = hasContentDescription("添付削除") and isEnabled()
        rule.waitUntil(10_000) { rule.onAllNodes(removeButton).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(removeButton).performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("compat-post-attachment-preview").fetchSemanticsNodes().isEmpty() }
        rule.waitUntil(10_000) { !runBlocking { fs.exists(container) } }
    }
}
