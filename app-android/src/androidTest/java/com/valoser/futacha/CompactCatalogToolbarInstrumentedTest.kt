@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.ui.compat.CompatibilityApp
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** M76: the としあき(仮) catalog bar gets the M47 layout (defaults fit, "その他" pinned right); no network. */
class CompactCatalogToolbarInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var store: AndroidCompatibilityStore? = null
    private val database = "compact_catalog_toolbar_test.db"
    private val logicalWidthState = mutableFloatStateOf(375f)
    private val fontScale = mutableFloatStateOf(1f)
    private var savedItems: List<CompatToolbarItem> = emptyList()
    private val catalogBar = hasTestTag("compat-main-bottom-bar") and
        hasAnyDescendant(hasTestTag("compat-toolbar-command-other"))

    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        runBlocking { store?.closeForTest() }
        rule.activity.deleteDatabase(database)
    }

    private fun showCatalog(allOn: Boolean = false) {
        val storage = AndroidCompatibilityStore(rule.activity, databaseName = database).also { store = it }
        val boardUrl = "https://may.2chan.net/b/"
        val master = compatToolbarMaster(CompatToolbarSurface.CATALOG)
        savedItems = master.mapIndexed { index, item -> CompatToolbarItem(item.key, index, allOn || item.defaultActive) }
        runBlocking {
            storage.initialize()
            storage.savePreference("compat.commonUsedVersion", "9999.0")
            storage.upsertBoard(CompatBoard(compatBoardKey(boardUrl), "テスト", boardUrl, boardUrl, 0))
            storage.saveToolbar(CompatToolbarSurface.CATALOG, savedItems)
        }
        rule.setContent { MaterialTheme {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale.floatValue)) {
                Box(Modifier.width(logicalWidthState.floatValue.dp).fillMaxHeight()) {
                    CompatibilityApp(store = storage, repository = null, initialBoardDeepLink = boardUrl, onExitApplication = {})
                }
            }
        } }
        rule.waitUntil(10_000) { rule.onAllNodes(catalogBar).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun defaultsFitAt375393And411DpAndOtherIsPinnedAtTheRightEdge() {
        showCatalog()
        assertEquals(10, savedItems.count { it.active })
        for (scale in listOf(1f, 1.6f)) for (logicalWidth in listOf(375f, 393f, 411f)) {
            rule.runOnIdle { logicalWidthState.floatValue = logicalWidth; fontScale.floatValue = scale }
            val bar = rule.onNode(catalogBar).fetchSemanticsNode().boundsInRoot
            for (key in savedItems.filter { it.active }.map { it.key } + "other") {
                val node = rule.onNodeWithTag("compat-toolbar-command-$key").assertIsDisplayed().fetchSemanticsNode()
                assertTrue("$key is clipped at $logicalWidth dp/$scale", node.boundsInRoot.left >= bar.left - 1 && node.boundsInRoot.right <= bar.right + 1)
                assertTrue("$key width ${node.size.width}", node.size.width >= 24)
            }
            val other = rule.onNodeWithTag("compat-toolbar-command-other").fetchSemanticsNode().boundsInRoot
            assertEquals(bar.right, other.right, 1f)
            rule.onNodeWithTag("scrollable-toolbar-more-end").assertDoesNotExist()
        }
        assertStoredItemsUnchanged()
    }

    @Test fun allOnScrollsWithAHintWhileOtherStaysReachable() {
        // All 13 catalog commands still fit at 24dp on 375dp phones; a narrower bar must scroll.
        logicalWidthState.floatValue = 300f
        showCatalog(allOn = true)
        val other = rule.onNodeWithTag("compat-toolbar-command-other").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("scrollable-toolbar-more-end").assertIsDisplayed()
        val scroll = rule.onNode(hasScrollAction() and hasAnyAncestor(catalogBar))
        // The scroll range is only a few dp here; scroll by semantics to the last command.
        scroll.performScrollToNode(hasTestTag("compat-toolbar-command-drawer"))
        rule.onNodeWithTag("compat-toolbar-command-drawer").assertIsDisplayed()
        rule.onNodeWithTag("scrollable-toolbar-more-start").assertIsDisplayed()
        assertEquals(other, rule.onNodeWithTag("compat-toolbar-command-other").assertIsDisplayed().fetchSemanticsNode().boundsInRoot)
        assertStoredItemsUnchanged()
    }

    private fun assertStoredItemsUnchanged() = runBlocking {
        assertEquals(savedItems, checkNotNull(store).loadToolbar(CompatToolbarSurface.CATALOG))
    }
}
