@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.CompatToolbarSurface
import com.valoser.futacha.shared.ui.compat.CompatToolbarEditorScreen
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** L57: two quick backs on the toolbar editor leave it once, not two screens. */
class CompatToolbarEditorBackInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val db = "l57-${System.nanoTime()}.db"
    private lateinit var storage: AndroidCompatibilityStore

    @Before fun prepare() {
        storage = AndroidCompatibilityStore(rule.activity, databaseName = db)
        runBlocking { storage.initialize() }
    }

    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        runBlocking { storage.closeForTest() }
        rule.activity.deleteDatabase(db)
    }

    @Test fun twoQuickBacksLeaveTheEditorOnce() {
        var backs = 0
        rule.setContent { MaterialTheme {
            CompatToolbarEditorScreen(CompatToolbarSurface.CATALOG, storage, onBack = { backs++ })
        } }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("ツールバー編集").fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
        // Both presses arrive before the first leave has run.
        rule.runOnUiThread {
            rule.activity.onBackPressedDispatcher.onBackPressed()
            rule.activity.onBackPressedDispatcher.onBackPressed()
        }
        rule.waitForIdle()
        assertEquals(1, backs)
    }
}
