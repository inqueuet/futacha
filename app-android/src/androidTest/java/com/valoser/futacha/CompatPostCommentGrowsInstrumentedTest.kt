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

/** The comment box grows with its lines instead of stopping at eight and scrolling inside. */
class CompatPostCommentGrowsInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val db = "grow-${System.nanoTime()}.db"
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

    @Test fun theCommentBoxGrowsPastEightLines() {
        val files = createFileSystem(rule.activity)
        rule.setContent { MaterialTheme {
            CompatibilityApp(storage, repository = null, fileSystem = files, initialThreadDeepLink = threadUrl, onExitApplication = {})
        } }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("compat-toolbar-command-post").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("compat-toolbar-command-post").performScrollTo().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("compat-post-comment-field").fetchSemanticsNodes().isNotEmpty() }
        val field = rule.onNodeWithTag("compat-post-comment-field")
        field.performTextInput("1")
        rule.waitForIdle()
        val oneLine = field.fetchSemanticsNode().size.height
        field.performTextReplacement((1..8).joinToString("\n"))
        rule.waitForIdle()
        val eight = field.fetchSemanticsNode().size.height
        field.performTextReplacement((1..14).joinToString("\n"))
        rule.waitForIdle()
        val fourteen = field.fetchSemanticsNode().size.height
        val perLine = (eight - oneLine) / 7f
        org.junit.Assert.assertTrue("8 lines $eight, 14 lines $fourteen, one line $perLine",
            fourteen - eight > perLine * 5)
    }
}
