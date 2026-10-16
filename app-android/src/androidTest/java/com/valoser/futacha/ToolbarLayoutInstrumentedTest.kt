@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.futaber.*
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.defaultThreadMenuEntries
import com.valoser.futacha.shared.ui.board.ThreadActionBar
import com.valoser.futacha.shared.ui.compat.CompatToolbarEditorScreen
import com.valoser.futacha.shared.ui.compat.CompatibilityApp
import com.valoser.futacha.shared.ui.ftutter.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Actual toolbar geometry and reachability, including settings saved before the capacity limit. */
class ToolbarLayoutInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var store: AndroidCompatibilityStore? = null
    private var images: ImageLoader? = null
    private val databaseName = "toolbar_layout_test.db"

    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        runBlocking { store?.closeForTest() }
        rule.activity.deleteDatabase(databaseName)
        images?.shutdown()
    }

    private fun saveShot(name: String) {
        rule.waitForIdle()
        val dir = java.io.File(rule.activity.getExternalFilesDir(null), "toolbar-layout-shots").apply { mkdirs() }
        val bitmap = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        java.io.File(dir, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun ftutterExistingAllOnSettingsKeepFortyDpButtonsAndTheLastButtonReachable() {
        var clicked = false
        rule.setContent {
            MaterialTheme {
                Box(Modifier.width(180.dp)) {
                    FtutterBottomToolbar(
                        entries = FTUTTER_THREAD_TOOLBAR_DEFAULT.map { it.copy(enabled = true) },
                        metrics = FtutterToolbarMetrics(leftSpace = 100, rightSpace = 100),
                        isSpace = { it == FtutterThreadTool.SPACE }, isOrder = { false }
                    ) { tool, width ->
                        androidx.compose.material3.IconButton(
                            onClick = { if (tool == FtutterThreadTool.MENU) clicked = true },
                            modifier = Modifier.width(width).testTag("probe-${tool.stored}")
                        ) { Text(tool.stored.take(1)) }
                    }
                }
            }
        }
        val first = rule.onNodeWithTag("probe-drawer").fetchSemanticsNode().size
        assertTrue("button width ${first.width}", first.width / rule.density.density >= 39.9f)
        // Drag on the scroll viewport (not on the navigation-bar inset) to check real touch scrolling.
        repeat(6) { rule.onNode(hasScrollAction()).performTouchInput { swipeLeft() } }
        rule.onNodeWithTag("probe-menu").assertIsDisplayed().performClick()
        rule.runOnIdle { assertTrue(clicked) }
    }

    @Test fun ftutterAllOffAndSpaceOnlyDoNotLeaveAnEmptyToolbarBody() {
        rule.setContent {
            Column(Modifier.width(240.dp)) {
                Box(Modifier.fillMaxWidth().testTag("insets-only").navigationBarsPadding())
                listOf(false, true).forEach { spaceOnly ->
                    FtutterBottomToolbar(
                        entries = FTUTTER_THREAD_TOOLBAR_DEFAULT.map { it.copy(enabled = spaceOnly && it.tool == FtutterThreadTool.SPACE) },
                        metrics = FtutterToolbarMetrics(), isSpace = { it == FtutterThreadTool.SPACE }, isOrder = { false },
                        modifier = Modifier.testTag(if (spaceOnly) "space-only" else "all-off")
                    ) { _, _ -> error("No action should be drawn") }
                }
            }
        }
        val expected = rule.onNodeWithTag("insets-only").fetchSemanticsNode().size.height
        listOf("all-off", "space-only").forEach { tag ->
            assertEquals(tag, expected, rule.onNodeWithTag(tag).fetchSemanticsNode().size.height)
        }
    }

    @Test fun futachaNarrowThreadBarKeepsTheSettingsActionReachable() {
        rule.setContent {
            MaterialTheme {
                Box(Modifier.width(240.dp)) {
                    ThreadActionBar(defaultThreadMenuEntries(), onAction = {}, applyNavigationBarsPadding = false)
                }
            }
        }
        rule.onNodeWithContentDescription("設定").performScrollTo().assertIsDisplayed()
        saveShot("futacha-narrow-thread-bar")
        val width = rule.onNodeWithContentDescription("設定").fetchSemanticsNode().size.width
        assertTrue("settings icon width $width", width / rule.density.density >= 23.9f)
    }

    @Test fun toshiakiAllOnToolbarPreviewKeepsTheLastIconReachable() {
        val storage = AndroidCompatibilityStore(rule.activity, databaseName = databaseName).also { store = it }
        runBlocking {
            storage.initialize()
            storage.saveToolbar(CompatToolbarSurface.THREAD, compatToolbarMaster(CompatToolbarSurface.THREAD).mapIndexed { index, item ->
                CompatToolbarItem(item.key, index, true)
            })
        }
        rule.setContent {
            MaterialTheme {
                Box(Modifier.width(240.dp).fillMaxHeight()) {
                    CompatToolbarEditorScreen(CompatToolbarSurface.THREAD, storage, onBack = {})
                }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("compat-toolbar-preview-active-more", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("compat-toolbar-preview-active-more", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        saveShot("toshiaki-preview-all-on")
        assertEquals(with(rule.density) { 24.dp.roundToPx() }, rule.onNodeWithTag("compat-toolbar-preview-active-more", useUnmergedTree = true).fetchSemanticsNode().size.width)
    }

    @Test fun futaberNarrowCatalogKeepsTheRefreshButtonReachable() {
        val loader = ImageLoader(rule.activity).also { images = it }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader) {
                FutaberTheme(FutaberThemeMode.Light) {
                    Box(Modifier.width(180.dp).fillMaxHeight()) {
                        FutaberCatalogScreen(
                            board = BoardSummary("tutorial", "チュートリアル", "テスト", "https://tutorial.futacha.example/b/", ""),
                            repository = FakeBoardRepository(), displayStyle = FutaberCatalogDisplayStyle.Grid3,
                            sortMode = CatalogMode.Catalog, onDisplayStyleChange = {}, onSortChange = {},
                            onOpenDrawer = {}, onOpenSettings = {}, onOpenManage = {}, onCreateThread = {},
                            refreshSignal = 0, onOpenThread = {}
                        )
                    }
                }
            }
        }
        rule.onNodeWithTag("futaber-refresh").performScrollTo().assertIsDisplayed()
        saveShot("futaber-narrow-catalog")
        val width = rule.onNodeWithTag("futaber-refresh").fetchSemanticsNode().size.width / rule.density.density
        assertTrue("refresh width $width", width >= 47.9f)
    }


    @Test fun toshiakiExistingAllOnThreadKeepsTheOverflowAndEveryCommandReachable() {
        val storage = AndroidCompatibilityStore(rule.activity, databaseName = databaseName).also { store = it }
        val boardUrl = "https://may.2chan.net/b/"
        val threadUrl = "${boardUrl}res/811.htm"
        val boardKey = compatBoardKey(boardUrl)
        val tabKey = compatTabKey(threadUrl)
        runBlocking {
            storage.initialize()
            storage.savePreference("compat.commonUsedVersion", "9999.0")
            storage.upsertBoard(CompatBoard(boardKey, "テスト", boardUrl, boardUrl, 0))
            storage.openTab(CompatTab(tabKey, threadUrl, threadUrl, boardKey, "テスト", "811", "ツールバー確認",
                insertedAtEpochMillis = 811, contentUpdatedAtEpochMillis = 811, snapshotRevision = 811))
            storage.saveThreadSnapshot(CompatThreadSnapshot(tabKey, 811, 811,
                posts = listOf(CompatPostSnapshot(position = 0, postNo = "811", timestamp = "", messageHtml = "模擬データ"))))
            storage.saveToolbar(CompatToolbarSurface.THREAD, compatToolbarMaster(CompatToolbarSurface.THREAD).mapIndexed { index, item ->
                CompatToolbarItem(item.key, index, true)
            })
        }
        val loader = ImageLoader(rule.activity).also { images = it }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader) {
                MaterialTheme {
                    Box(Modifier.width(240.dp).fillMaxHeight()) {
                        CompatibilityApp(store = storage, repository = null, initialThreadDeepLink = threadUrl, onExitApplication = {})
                    }
                }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("compat-thread-post-811", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("compat-toolbar-command-other").performScrollTo().assertIsDisplayed()
        saveShot("toshiaki-thread-all-on")
        assertTrue(rule.onNodeWithTag("compat-toolbar-command-other").fetchSemanticsNode().size.width / rule.density.density >= 39.9f)
        rule.onNodeWithTag("compat-toolbar-command-post").performScrollTo().assertIsDisplayed()
        assertEquals(19, runBlocking { storage.loadToolbar(CompatToolbarSurface.THREAD).count { it.active } })
    }

}
