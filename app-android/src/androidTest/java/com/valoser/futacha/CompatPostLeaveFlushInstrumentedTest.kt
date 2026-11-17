@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.ui.compat.*
import com.valoser.futacha.shared.util.createFileSystem
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** L52: leaving the reply screen right after typing keeps the last input in the draft. */
class CompatPostLeaveFlushInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val db = "l52-${System.nanoTime()}.db"
    private val boardUrl = "https://may.2chan.net/b/"
    private val threadUrl = "${boardUrl}res/852.htm"
    private val boardKey = compatBoardKey(boardUrl)
    private val tabKey = compatTabKey(threadUrl)
    private lateinit var storage: AndroidCompatibilityStore

    @Before fun prepare() {
        storage = AndroidCompatibilityStore(rule.activity, databaseName = db)
        runBlocking {
            storage.initialize()
            storage.savePreference("compat.commonUsedVersion", "9999.0")
            storage.upsertBoard(CompatBoard(boardKey, "L52", boardUrl, boardUrl, 0))
            storage.openTab(CompatTab(tabKey, threadUrl, threadUrl, boardKey, "L52", "852", "L52 thread",
                insertedAtEpochMillis = 852, contentUpdatedAtEpochMillis = 852, snapshotRevision = 852))
            storage.saveThreadSnapshot(CompatThreadSnapshot(tabKey, 852, 852,
                posts = listOf(CompatPostSnapshot(0, "852", timestamp = "", messageHtml = "L52 post"))))
        }
    }

    @After fun cleanup() {
        rule.mainClock.autoAdvance = true
        rule.runOnUiThread { rule.activity.setContent {} }
        runBlocking { storage.closeForTest() }
        rule.activity.deleteDatabase(db)
    }

    @Test fun leavingRightAfterTypingKeepsTheLastInput() {
        val files = createFileSystem(rule.activity)
        rule.setContent { MaterialTheme {
            CompatibilityApp(storage, repository = null, fileSystem = files, initialThreadDeepLink = threadUrl, onExitApplication = {})
        } }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("compat-toolbar-command-post").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("compat-toolbar-command-post").performScrollTo().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("compat-post-comment-field").fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()

        // Typed, then left at once: the 300 ms autosave has not run.
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("compat-post-comment-field").performTextInput("L52 last words")
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        repeat(5) { rule.mainClock.advanceTimeByFrame() }
        rule.mainClock.autoAdvance = true
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("compat-post-comment-field").fetchSemanticsNodes().isEmpty() }

        rule.waitUntil(10_000) { runBlocking { storage.loadDraft(tabKey) }?.comment == "L52 last words" }
    }
}
