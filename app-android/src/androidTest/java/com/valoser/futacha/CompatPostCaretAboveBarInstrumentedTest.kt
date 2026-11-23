@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.compat.*
import com.valoser.futacha.shared.util.createFileSystem
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** としあき(仮): with the keyboard up, the caret of a long comment stays above the bottom bar (it hid from line 18). */
class CompatPostCaretAboveBarInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val db = "caret-${System.nanoTime()}.db"
    private val boardUrl = "https://may.2chan.net/b/"
    private val threadUrl = "${boardUrl}res/851.htm"
    private val board = CompatBoard(compatBoardKey(boardUrl), "C", boardUrl, boardUrl, 0)
    private val tab = CompatTab(compatTabKey(threadUrl), threadUrl, threadUrl, board.key, "C", "851", "C",
        insertedAtEpochMillis = 1, contentUpdatedAtEpochMillis = 1)
    private lateinit var storage: AndroidCompatibilityStore

    @After fun cleanup() {
        runBlocking { storage.closeForTest() }
        rule.activity.deleteDatabase(db)
    }

    @Test fun theCaretOfALongCommentStaysAboveTheBar() {
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
        storage = AndroidCompatibilityStore(rule.activity, databaseName = db)
        runBlocking { storage.initialize(); storage.upsertBoard(board); storage.openTab(tab) }
        val fs = createFileSystem(rule.activity)
        rule.setContent { CompatibilityProfileTheme("mono") {
            CompatPostScreen(tab, board, FakeBoardRepository(), store = storage, preferences = emptyMap(), appVersion = "test",
                fileSystem = fs, onToolbarEdit = {}, onPostSent = {}, onOpenHelp = {}, onBack = {})
        } }
        val field = rule.onNodeWithTag("compat-post-comment-field")
        field.performClick()
        Thread.sleep(1_500)
        for (i in 1..30) {
            field.performKeyInput { pressKey(Key.Enter) }
            field.performTextInput("line$i")
            Thread.sleep(300)
            rule.waitForIdle()
            val node = field.fetchSemanticsNode()
            val layout = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layout)
            val l = layout.single()
            val caretBottom = node.positionInRoot.y + l.getLineBottom(l.lineCount - 1)
            val barTop = rule.onNodeWithTag("compat-post-bottom-bar").fetchSemanticsNode().boundsInRoot.top
            assertTrue("line ${i + 1}: caret $caretBottom under bar $barTop", caretBottom <= barTop)
        }
    }
}
