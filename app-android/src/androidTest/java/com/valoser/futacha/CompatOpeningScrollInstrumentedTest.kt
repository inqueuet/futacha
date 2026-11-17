@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.espresso.Espresso
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.ui.compat.CompatibilityApp
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*

/** Actual workspace and cached threads; no network, media, or sound. */
class CompatOpeningScrollInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val db = "opening_scroll_${System.nanoTime()}.db"
    private val base = "https://may.2chan.net/b/"
    private val boardKey = compatBoardKey(base)
    private fun tab(number: String) = CompatTab(compatTabKey("${base}res/$number.htm"),
        "${base}res/$number.htm", "${base}res/$number.htm", boardKey, "Opening board", number, "Opening $number",
        replyCount = 120, insertedAtEpochMillis = 1, contentUpdatedAtEpochMillis = 1, snapshotRevision = 1)
    private val a = tab("100")
    private val b = tab("200")
    private lateinit var store: AndroidCompatibilityStore
    @Before fun prepare() {
        store = AndroidCompatibilityStore(rule.activity, databaseName = db)
        runBlocking {
            store.initialize()
            store.upsertBoard(CompatBoard(boardKey, "Opening board", base, base, 0))
            store.savePreference("compat.design.designTabSelectorOpened", "ON")
            for (tab in listOf(b, a)) {
                store.openTab(tab)
                store.saveThreadSnapshot(CompatThreadSnapshot(tab.key, 1, 1, posts = (1..120).map { n ->
                    CompatPostSnapshot(n - 1, "$n", timestamp = "", messageHtml = "${tab.threadNo}-body-$n<br>local line<br>local line")
                }))
            }
        }
        rule.setContent { MaterialTheme { CompatibilityApp(store, repository = null, initialThreadDeepLink = a.canonicalUrl, onExitApplication = {}) } }
        waitBody("100", 1)
    }
    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }; rule.waitForIdle()
        if (::store.isInitialized) runBlocking { store.closeForTest() }
        rule.activity.deleteDatabase(db)
    }
    private fun waitBody(thread: String, number: Int) {
        rule.waitUntil(10_000) { rule.onAllNodesWithText("$thread-body-$number", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
        rule.onNodeWithText("$thread-body-$number", substring = true).assertIsDisplayed()
    }
    private fun select(tab: CompatTab) { rule.onNodeWithTag("compat-selector-tab-${tab.key}").performClick() }
    private fun jumpThenReadMiddle() {
        select(a); waitBody("100", 120)
        rule.onNodeWithTag("compat-thread-list").performScrollToIndex(40)
        waitBody("100", 41)
        rule.waitUntil(10_000) { runBlocking { store.tabs.first() }.first { it.key == a.key }.scrollAnchor.postNo == "41" }
    }
    @Test fun aFirstVisitWithNoPositionStartsAtTheFirstPost() { waitBody("100", 1) }
    @Test fun aConsumedBottomJumpMustNotReplayWhenReturningFromAnotherTab() {
        jumpThenReadMiddle()
        select(b); waitBody("200", 1)
        select(a); waitBody("100", 41)
        rule.onNodeWithText("100-body-120", substring = true).assertDoesNotExist()
    }
    @Test fun aConsumedBottomJumpMustNotReplayAfterSettings() {
        jumpThenReadMiddle()
        val topOverflow = hasContentDescription("その他") and SemanticsMatcher("top overflow") {
            it.boundsInRoot.top >= 0f && it.boundsInRoot.bottom < rule.activity.window.decorView.height / 3f
        }
        rule.onNode(topOverflow).performClick()
        rule.onNodeWithText("設定").performClick()
        rule.waitForIdle(); Espresso.pressBack()
        waitBody("100", 41)
        rule.onNodeWithText("100-body-120", substring = true).assertDoesNotExist()
    }
}
