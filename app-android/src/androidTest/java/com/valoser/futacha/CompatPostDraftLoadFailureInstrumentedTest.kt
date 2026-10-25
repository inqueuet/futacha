@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.compat.*
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.createFileSystem
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * L51 + L149: on a reply screen whose draft could not be read, the notice stays as the app's notices do, what is typed
 * is still saved (an empty form deletes nothing), and the ⋮ "ヘルプ" can be used.
 */
class CompatPostDraftLoadFailureInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val db = "l51-${System.nanoTime()}.db"
    private val boardUrl = "https://may.2chan.net/b/"
    private val threadUrl = "${boardUrl}res/851.htm"
    private val boardKey = compatBoardKey(boardUrl)
    private val board = CompatBoard(boardKey, "L51", boardUrl, boardUrl, 0)
    private val tab = CompatTab(compatTabKey(threadUrl), threadUrl, threadUrl, boardKey, "L51", "851", "L51",
        insertedAtEpochMillis = 1, contentUpdatedAtEpochMillis = 1)
    private lateinit var storage: AndroidCompatibilityStore
    private lateinit var fs: FileSystem

    @Before fun prepare() {
        storage = AndroidCompatibilityStore(rule.activity, databaseName = db)
        fs = createFileSystem(rule.activity)
        runBlocking { storage.initialize(); storage.upsertBoard(board); storage.openTab(tab) }
    }

    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        runBlocking { storage.closeForTest() }
        rule.activity.deleteDatabase(db)
    }

    @Test fun anUnreadableDraftStillKeepsWhatIsTypedAndHelpWorks() {
        val unreadable = object : CompatibilityStore by storage {
            override suspend fun loadDraft(tabKey: String): CompatReplyDraft? = throw IOException("L51 test: unreadable")
        }
        val helpOpened = AtomicBoolean(false)
        rule.setContent { CompatibilityProfileTheme("mono") {
            CompatPostScreen(tab, board, FakeBoardRepository(), store = unreadable, preferences = emptyMap(), appVersion = "test",
                fileSystem = fs, onToolbarEdit = {}, onPostSent = {}, onOpenHelp = { helpOpened.set(true) }, onBack = {})
        } }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("下書きを読み込めませんでした").fetchSemanticsNodes().isNotEmpty() }
        // As the app's own notices: a short one stays 4 seconds (it went after 2.5).
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeBy(3_000)
        rule.onAllNodesWithText("下書きを読み込めませんでした").assertCountEquals(1)
        rule.mainClock.advanceTimeBy(1_500)
        rule.onAllNodesWithText("下書きを読み込めませんでした").assertCountEquals(0)
        rule.mainClock.autoAdvance = true

        // An empty form has deleted nothing; what is typed is saved.
        rule.waitForIdle()
        assertEquals(null, runBlocking { storage.loadDraft(tab.key) })
        rule.onNodeWithTag("compat-post-comment-field").performTextInput("L51 typed")
        rule.waitUntil(5_000) { runBlocking { storage.loadDraft(tab.key) }?.comment == "L51 typed" }

        rule.onAllNodesWithContentDescription("その他").onFirst().performClick()
        rule.onNodeWithText("ヘルプ").assertIsEnabled().performClick()
        rule.waitUntil(5_000) { helpOpened.get() }
        assertTrue(helpOpened.get())
    }
}
