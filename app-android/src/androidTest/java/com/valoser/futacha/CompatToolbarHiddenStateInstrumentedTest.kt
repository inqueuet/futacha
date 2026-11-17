package com.valoser.futacha

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.test.core.app.ApplicationProvider
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.CompatToolbarSurface
import com.valoser.futacha.shared.ui.compat.CompatToolbarEditorScreen
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** 参考アプリ 18-20: a thread toolbar command can be hidden from both the toolbar and the その他 menu. */
class CompatToolbarHiddenStateInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "compat_toolbar_hidden_${System.currentTimeMillis()}.db"
    private lateinit var store: AndroidCompatibilityStore

    @Before fun prepare() {
        runBlocking {
            store = AndroidCompatibilityStore(context, databaseName = databaseName)
            store.initialize()
        }
    }

    @After fun cleanUp() {
        context.deleteDatabase(databaseName)
    }

    private fun show(surface: CompatToolbarSurface) {
        rule.setContent { MaterialTheme { CompatToolbarEditorScreen(surface = surface, store = store, onBack = {}) } }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("compat-toolbar-editor-list").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun close() = runBlocking { store.loadToolbar(CompatToolbarSurface.THREAD).single { it.key == "close" } }

    @Test fun aCommandOffTheToolbarIsHiddenAndTheCheckBringsItBack() {
        show(CompatToolbarSurface.THREAD)
        rule.onNodeWithTag("compat-toolbar-editor-list").performScrollToNode(hasTestTag("compat-toolbar-state-close"))
        // A command on the toolbar has no state button: the check box already says so.
        rule.onAllNodesWithTag("compat-toolbar-state-post").assertCountEquals(0)
        rule.onNodeWithTag("compat-toolbar-state-close").performClick()
        rule.waitUntil(5_000) { close().hidden }
        assertFalse(close().active)
        // Stored as 18-20's value 2 and read back after the store is opened again.
        val reopened = AndroidCompatibilityStore(context, databaseName = databaseName)
        runBlocking { reopened.initialize() }
        assertTrue(runBlocking { reopened.loadToolbar(CompatToolbarSurface.THREAD) }.single { it.key == "close" }.hidden)
        rule.onNodeWithTag("compat-toolbar-toggle-close").performClick()
        rule.waitUntil(5_000) { close().active }
        assertFalse(close().hidden)
        rule.onAllNodesWithTag("compat-toolbar-state-close").assertCountEquals(0)
    }

    @Test fun theCatalogToolbarHasNoHiddenState() {
        show(CompatToolbarSurface.CATALOG)
        rule.onNodeWithTag("compat-toolbar-editor-list").performScrollToNode(hasTestTag("compat-toolbar-toggle-privacy"))
        rule.onAllNodesWithTag("compat-toolbar-state-privacy").assertCountEquals(0)
        assertEquals(false, runBlocking { store.loadToolbar(CompatToolbarSurface.CATALOG) }.any { it.hidden })
    }
}
