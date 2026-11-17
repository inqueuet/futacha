@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.ui.compat.CompatibilityApp
import com.valoser.futacha.shared.ui.util.ScrollableToolbarRow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Product thread toolbar and shared scroll row geometry; isolated snapshot, no network/audio actions. */
class CompactThreadToolbarInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var store: AndroidCompatibilityStore? = null
    private val database = "compact_thread_toolbar_test.db"
    private val logicalWidthState = mutableFloatStateOf(375f)
    private val fontScale = mutableFloatStateOf(1f)
    private var savedItems: List<CompatToolbarItem> = emptyList()
    private val threadBar = hasTestTag("compat-main-bottom-bar") and
        hasAnyDescendant(hasTestTag("compat-toolbar-command-other"))

    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        runBlocking { store?.closeForTest() }
        rule.activity.deleteDatabase(database)
    }

    private fun showThread(allOn: Boolean = false, order: List<String>? = null) {
        val storage = AndroidCompatibilityStore(rule.activity, databaseName = database).also { store = it }
        val boardUrl = "https://may.2chan.net/b/"
        val threadUrl = "${boardUrl}res/811.htm"
        val boardKey = compatBoardKey(boardUrl)
        val tabKey = compatTabKey(threadUrl)
        val master = compatToolbarMaster(CompatToolbarSurface.THREAD)
        val ordered = if (order == null) master else master.sortedBy { order.indexOf(it.key).takeIf { it >= 0 } ?: (order.size + master.indexOf(it)) }
        savedItems = ordered.mapIndexed { index, item ->
            CompatToolbarItem(item.key, index, if (order != null) item.key in order else allOn || item.defaultActive)
        }
        runBlocking {
            storage.initialize()
            storage.savePreference("compat.commonUsedVersion", "9999.0")
            storage.upsertBoard(CompatBoard(boardKey, "テスト", boardUrl, boardUrl, 0))
            storage.openTab(CompatTab(tabKey, threadUrl, threadUrl, boardKey, "テスト", "811", "toolbar",
                insertedAtEpochMillis = 811, contentUpdatedAtEpochMillis = 811, snapshotRevision = 811))
            storage.saveThreadSnapshot(CompatThreadSnapshot(tabKey, 811, 811,
                posts = listOf(CompatPostSnapshot(0, "811", timestamp = "", messageHtml = "TOOLBAR FIXTURE"))))
            storage.saveToolbar(CompatToolbarSurface.THREAD, savedItems)
        }
        rule.setContent { MaterialTheme {
            // These are logical layout widths; a 411dp case also fits the emulator's physical screen.
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale.floatValue)) {
                Box(Modifier.width(logicalWidthState.floatValue.dp).fillMaxHeight()) {
                    CompatibilityApp(store = storage, repository = null, initialThreadDeepLink = threadUrl, onExitApplication = {})
                }
            }
        } }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("compat-thread-post-811", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun defaultsFitAt375393And411DpWithRegularAndLargeTextAndOtherIsImmediatelyUsable() {
        showThread()
        assertEquals(14, savedItems.count { it.active })
        for (scale in listOf(1f, 1.6f)) for (logicalWidth in listOf(375f, 393f, 411f)) {
            rule.runOnIdle { logicalWidthState.floatValue = logicalWidth; fontScale.floatValue = scale }
            val bar = rule.onNode(threadBar).fetchSemanticsNode().boundsInRoot
            for (key in savedItems.filter { it.active }.map { it.key } + "other") {
                val node = rule.onNodeWithTag("compat-toolbar-command-$key").assertIsDisplayed().fetchSemanticsNode()
                assertTrue("$key is clipped at $logicalWidth dp/$scale", node.boundsInRoot.left >= bar.left - 1 && node.boundsInRoot.right <= bar.right + 1)
                assertTrue("$key width ${node.size.width}", node.size.width >= 24)
            }
            val other = rule.onNodeWithTag("compat-toolbar-command-other").fetchSemanticsNode().boundsInRoot
            assertEquals(bar.right, other.right, 1f)
            assertTrue(other.width in 24f..28f)
            rule.onNodeWithTag("scrollable-toolbar-more-end").assertDoesNotExist()
        }
        openAndCloseOther()
        assertStoredItemsUnchanged()
    }

    @Test fun allOnScrollsWithoutCrushingButtonsAndOtherStaysFixedAtBothEnds() {
        fontScale.floatValue = 1.6f
        showThread(allOn = true)
        val other = rule.onNodeWithTag("compat-toolbar-command-other").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("scrollable-toolbar-more-end").assertIsDisplayed()
        rule.onNodeWithTag("scrollable-toolbar-more-start").assertDoesNotExist()
        openAndCloseOther()
        val scroll = rule.onNode(hasScrollAction() and hasAnyAncestor(threadBar))
        repeat(3) { scroll.performTouchInput { swipeLeft() } }
        val atEnd = rule.onNodeWithTag("compat-toolbar-command-other").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(other, atEnd)
        rule.onNodeWithTag("compat-toolbar-command-autoscroll").assertIsDisplayed()
        rule.onNodeWithTag("scrollable-toolbar-more-start").assertIsDisplayed()
        rule.onNodeWithTag("scrollable-toolbar-more-end").assertDoesNotExist()
        assertTrue(rule.onNodeWithTag("compat-toolbar-command-autoscroll").fetchSemanticsNode().size.width >= 24)
        openAndCloseOther()
        assertStoredItemsUnchanged()
    }

    @Test fun savedReorderingAndDisabledButtonsArePreserved() {
        val order = listOf("gallery", "post", "search", "reload")
        showThread(order = order)
        val lefts = order.map { rule.onNodeWithTag("compat-toolbar-command-$it").assertIsDisplayed().fetchSemanticsNode().boundsInRoot.left }
        assertEquals(lefts.sorted(), lefts)
        rule.onNodeWithTag("compat-toolbar-command-autoscroll").assertDoesNotExist()
        rule.onNodeWithTag("scrollable-toolbar-more-end").assertDoesNotExist()
        openAndCloseOther()
        assertStoredItemsUnchanged()
    }

    @Test fun sharedScrollHintsChangeAtTheEndsAndDoNotEatButtonTaps() {
        val clicks = mutableListOf<Int>()
        rule.setContent { MaterialTheme {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                ScrollableToolbarRow(20, Modifier.width(200.dp).height(40.dp)) {
                    repeat(20) { index ->
                        Box(Modifier.weight(1f).fillMaxHeight().testTag("generic-$index").clickable { clicks.add(index) },
                            contentAlignment = Alignment.Center) { Text("$index") }
                    }
                }
            }
        } }
        rule.onNodeWithTag("scrollable-toolbar-more-end").assertIsDisplayed()
        rule.onNodeWithTag("scrollable-toolbar-more-start").assertDoesNotExist()
        // Tap the portion of the last visible button underneath the end hint.
        rule.onNodeWithTag("generic-4").performTouchInput { click(Offset(width - 2f, height / 2f)) }
        rule.runOnIdle { assertEquals(listOf(4), clicks) }
        repeat(6) { rule.onNode(hasScrollAction()).performTouchInput { swipeLeft() } }
        rule.onNodeWithTag("scrollable-toolbar-more-start").assertIsDisplayed()
        rule.onNodeWithTag("scrollable-toolbar-more-end").assertDoesNotExist()
        rule.onNodeWithTag("generic-19").assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(listOf(4, 19), clicks) }
    }

    @Test fun sharedScrollHintsPreserveTheNaturalToolbarHeightWithoutAnExplicitHeight() {
        rule.setContent { MaterialTheme {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Column(Modifier.fillMaxSize()) {
                    ScrollableToolbarRow(20, Modifier.width(200.dp).testTag("natural-toolbar")) {
                        repeat(20) { index ->
                            Box(Modifier.weight(1f).height(40.dp), contentAlignment = Alignment.Center) {
                                Text("$index")
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(30.dp).testTag("below-toolbar"))
                }
            }
        } }
        // Ensure the overflowing row has composed the hint before inspecting its final size.
        rule.onNodeWithTag("scrollable-toolbar-more-end").assertIsDisplayed()
        val toolbar = rule.onNodeWithTag("natural-toolbar").fetchSemanticsNode().boundsInRoot
        val below = rule.onNodeWithTag("below-toolbar").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(40f, toolbar.height, 1f)
        assertEquals(toolbar.bottom, below.top, 1f)
        assertEquals(40f, rule.onNodeWithTag("scrollable-toolbar-more-end").fetchSemanticsNode().boundsInRoot.height, 1f)
    }

    private fun openAndCloseOther() {
        rule.onNodeWithTag("compat-toolbar-command-other").performClick()
        rule.onNodeWithText("ページを保存", substring = true).assertIsDisplayed()
        Espresso.pressBack()
    }

    private fun assertStoredItemsUnchanged() = runBlocking {
        assertEquals(savedItems, checkNotNull(store).loadToolbar(CompatToolbarSurface.THREAD))
    }
}
