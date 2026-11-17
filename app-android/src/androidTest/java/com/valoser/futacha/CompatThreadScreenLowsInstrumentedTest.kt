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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** としあき(仮)'s thread screen: L62 (back in the gallery's save mode) and L55 (the gallery while its list is read). */
class CompatThreadScreenLowsInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val db = "l42-${System.nanoTime()}.db"
    private val boardUrl = "https://may.2chan.net/b/"
    private val threadUrl = "${boardUrl}res/842.htm"
    private val boardKey = compatBoardKey(boardUrl)
    private val tabKey = compatTabKey(threadUrl)
    private lateinit var storage: AndroidCompatibilityStore

    @Before fun prepare() {
        storage = AndroidCompatibilityStore(rule.activity, databaseName = db)
        runBlocking {
            storage.initialize()
            storage.savePreference("compat.commonUsedVersion", "9999.0")
            storage.upsertBoard(CompatBoard(boardKey, "L42", boardUrl, boardUrl, 0))
            storage.openTab(CompatTab(tabKey, threadUrl, threadUrl, boardKey, "L42", "842", "L42 thread",
                insertedAtEpochMillis = 842, contentUpdatedAtEpochMillis = 842, snapshotRevision = 842))
            storage.saveThreadSnapshot(CompatThreadSnapshot(tabKey, 842, 842,
                posts = listOf(CompatPostSnapshot(0, "842", timestamp = "", messageHtml = "L42 post",
                    imageUrl = "${boardUrl}src/1700000000842.jpg", thumbnailUrl = "${boardUrl}thumb/1700000000842s.jpg"))))
        }
    }

    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        runBlocking { storage.closeForTest() }
        rule.activity.deleteDatabase(db)
    }

    /** L62: in the gallery's save mode, the system back leaves the save mode first, as the top-left arrow does. */
    @Test fun backInTheGallerysSaveModeLeavesTheSaveModeFirst() {
        rule.setContent { MaterialTheme {
            CompatibilityApp(storage, repository = null, initialThreadDeepLink = threadUrl, onExitApplication = {})
        } }
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("compat-toolbar-command-gallery").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("compat-toolbar-command-gallery").performScrollTo().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription("一括保存").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("一括保存").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("選択を終了").fetchSemanticsNodes().isNotEmpty() }
        androidx.test.espresso.Espresso.pressBack()
        rule.waitForIdle()
        rule.onAllNodesWithContentDescription("選択を終了").assertCountEquals(0)
        rule.onNodeWithContentDescription("一括保存").assertExists()
    }

    /** L55: while the gallery's list is being read, it does not say that there are no pictures. */
    @Test fun theGalleryDoesNotSayNoPicturesWhileItsListIsRead() {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val hold = java.util.concurrent.atomic.AtomicBoolean(false)
        val slow = object : CompatibilityStore by storage {
            override suspend fun loadThreadSnapshot(tabKey: String): CompatThreadSnapshot? {
                if (hold.get()) gate.await()
                return storage.loadThreadSnapshot(tabKey)
            }
        }
        rule.setContent { MaterialTheme {
            CompatibilityApp(slow, repository = null, initialThreadDeepLink = threadUrl, onExitApplication = {})
        } }
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("compat-toolbar-command-gallery").fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
        hold.set(true)
        rule.onNodeWithTag("compat-toolbar-command-gallery").performScrollTo().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription("一括保存").fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
        rule.onAllNodesWithText("画像はありません").assertCountEquals(0)
        gate.complete(Unit)
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("compat-gallery-empty").fetchSemanticsNodes().isEmpty() }
        rule.onAllNodesWithText("画像はありません").assertCountEquals(0)
    }
}
