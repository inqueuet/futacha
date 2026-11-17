@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.compat.*
import com.valoser.futacha.shared.ui.LocalPostingInFlight
import com.valoser.futacha.shared.ui.PostingInFlight
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** L47: a reply being sent when its screen is taken away is sent to the end, and its draft is then cleared. */
class CompatPostSendOutlivesScreenInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val db = "l47-${System.nanoTime()}.db"
    private val boardUrl = "https://may.2chan.net/b/"
    private val threadUrl = "${boardUrl}res/847.htm"
    private val boardKey = compatBoardKey(boardUrl)
    private val board = CompatBoard(boardKey, "L47", boardUrl, boardUrl, 0)
    private val tab = CompatTab(compatTabKey(threadUrl), threadUrl, threadUrl, boardKey, "L47", "847", "L47",
        insertedAtEpochMillis = 1, contentUpdatedAtEpochMillis = 1)
    private val started = CompletableDeferred<Unit>()
    private val release = CompletableDeferred<Unit>()
    private val sent = AtomicInteger(0)
    private lateinit var storage: AndroidCompatibilityStore

    private val repo = object : BoardRepository by FakeBoardRepository() {
        override suspend fun replyToThread(board: String, threadId: String, name: String, email: String, subject: String,
            comment: String, password: String, imageFile: ByteArray?, imageFileName: String?, textOnly: Boolean,
            handwriting: Boolean): String? {
            started.complete(Unit); release.await(); sent.incrementAndGet(); return "8471"
        }
    }

    @Before fun prepare() {
        storage = AndroidCompatibilityStore(rule.activity, databaseName = db)
        runBlocking {
            storage.initialize(); storage.upsertBoard(board); storage.openTab(tab)
            storage.saveDraft(CompatReplyDraft(tab.key, comment = "L47 body", deleteKey = "l47key", updatedAtEpochMillis = 1))
        }
    }

    @After fun cleanup() {
        release.complete(Unit)
        rule.runOnUiThread { rule.activity.setContent {} }
        runBlocking { storage.closeForTest() }
        rule.activity.deleteDatabase(db)
    }

    @Test fun aReplyBeingSentWhenItsScreenGoesIsSentAndItsDraftCleared() {
        val shown = mutableStateOf(true)
        val backs = AtomicInteger(0)
        val postSent = AtomicInteger(0)
        rule.setContent {
            val lasting = rememberCoroutineScope()
            CompatibilityProfileTheme("mono") {
                if (shown.value) CompatPostScreen(tab, board, repo, store = storage,
                    preferences = mapOf("compat.control.controlPostConfirm" to "OFF"), appVersion = "test", fileSystem = null,
                    onToolbarEdit = {}, onPostSent = { postSent.incrementAndGet() }, lastingScope = lasting,
                    onBack = { backs.incrementAndGet() })
            }
        }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("L47 body").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("送信する").performClick()
        rule.waitUntil(10_000) { started.isCompleted }

        // Taken away from outside (not by its own back) while the reply is being sent.
        rule.runOnUiThread { shown.value = false }
        rule.waitForIdle()
        release.complete(Unit)

        rule.waitUntil(10_000) { sent.get() == 1 && runBlocking { storage.loadDraft(tab.key) } == null }
        rule.waitForIdle()
        assertEquals("The reply is told as sent", 1, postSent.get())
        assertEquals("No back from a screen already gone", 0, backs.get())
        assertEquals("l47key", runBlocking { storage.loadPreference(COMPAT_POST_DELETE_KEY_STORAGE_KEY) })
    }

    /**
     * L157 / L168: a send still running after its screen went counts as a post being sent, and the same post screen
     * opened again waits for it instead of offering the draft (which the send may already have posted) again.
     */
    @Test fun aSendThatOutlivesItsScreenStillCountsAndTheReopenedScreenWaitsForIt() {
        val shown = mutableStateOf(true)
        val generation = mutableStateOf(0)
        val inFlight = PostingInFlight()
        rule.setContent {
            val lasting = rememberCoroutineScope()
            CompositionLocalProvider(LocalPostingInFlight provides inFlight) {
                CompatibilityProfileTheme("mono") {
                    if (shown.value) androidx.compose.runtime.key(generation.value) {
                        CompatPostScreen(tab, board, repo, store = storage,
                            preferences = mapOf("compat.control.controlPostConfirm" to "OFF"), appVersion = "test", fileSystem = null,
                            onToolbarEdit = {}, onPostSent = {}, lastingScope = lasting, onBack = {})
                    }
                }
            }
        }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("L47 body").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("送信する").performClick()
        rule.waitUntil(10_000) { started.isCompleted }

        rule.runOnUiThread { shown.value = false }
        rule.waitForIdle()
        assertEquals("The send still running counts", 1, inFlight.count)

        // The same post screen again, while the first send has not ended.
        rule.runOnUiThread { generation.value += 1; shown.value = true }
        rule.waitForIdle()
        Thread.sleep(500)
        rule.waitForIdle()
        org.junit.Assert.assertTrue("The draft being sent is not offered again",
            rule.onAllNodesWithText("L47 body").fetchSemanticsNodes().isEmpty())

        release.complete(Unit)
        rule.waitUntil(10_000) { sent.get() == 1 && inFlight.count == 0 }
        rule.waitForIdle()
        org.junit.Assert.assertTrue("Posted, so its draft is gone from the reopened screen",
            rule.onAllNodesWithText("L47 body").fetchSemanticsNodes().isEmpty())
        assertEquals(1, sent.get())
    }

    /** L157: a send that fails after its screen went is told through the app (it would be lost with the screen). */
    @Test fun aSendFailingAfterItsScreenWentIsToldThroughTheApp() {
        val failing = object : BoardRepository by repo {
            override suspend fun replyToThread(board: String, threadId: String, name: String, email: String, subject: String,
                comment: String, password: String, imageFile: ByteArray?, imageFileName: String?, textOnly: Boolean,
                handwriting: Boolean): String? {
                started.complete(Unit); release.await(); throw java.io.IOException("L157 test: server error")
            }
        }
        val shown = mutableStateOf(true)
        val notices = java.util.concurrent.CopyOnWriteArrayList<String>()
        rule.setContent {
            val lasting = rememberCoroutineScope()
            CompatibilityProfileTheme("mono") {
                if (shown.value) CompatPostScreen(tab, board, failing, store = storage,
                    preferences = mapOf("compat.control.controlPostConfirm" to "OFF"), appVersion = "test", fileSystem = null,
                    onToolbarEdit = {}, onPostSent = {}, lastingScope = lasting, onLastingNotice = { notices += it }, onBack = {})
            }
        }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("L47 body").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("送信する").performClick()
        rule.waitUntil(10_000) { started.isCompleted }
        rule.runOnUiThread { shown.value = false }
        rule.waitForIdle()
        release.complete(Unit)
        rule.waitUntil(10_000) { notices.isNotEmpty() }
        assertEquals("L47 body", runBlocking { storage.loadDraft(tab.key) }?.comment)
    }
}
