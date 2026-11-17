@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.parser.ThreadHtmlParserCore
import com.valoser.futacha.shared.ui.board.*
import com.valoser.futacha.shared.ui.compat.*
import com.valoser.futacha.shared.ui.ftutter.*
import com.valoser.futacha.shared.ui.futaber.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.io.File

class ThreadFeedbackStuInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private fun boards(n: Int) = (1..n).map { BoardSummary("board-$it", "BOARD-%02d".format(it), "", "https://may.2chan.net/test$it/", "") }
    private fun capture(name: String) {
        rule.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val dir = File(rule.activity.getExternalFilesDir(null), "feedback-stu").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun verifyPlacement(mode: String) {
        var shown by mutableStateOf(boards(1))
        var bottom by mutableStateOf(false)
        var compact by mutableStateOf(true)
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                MaterialTheme { Box(Modifier.fillMaxWidth().then(if (compact) Modifier.height(600.dp) else Modifier.fillMaxHeight()).testTag("board-viewport")) {
                    when (mode) {
                        "futacha" -> BoardManagementBoardList(shown, false, false, false, {},
                            BoardManagementBoardListCallbacks({}, {}, { _, _ -> }, { _, _ -> }, { _, _ -> }),
                            modifier = Modifier.fillMaxSize().navigationBarsPadding().testTag("board-list"), bottomAligned = bottom)
                        "toshiaki" -> CompatibilityProfileTheme(null) {
                            CompatMainScreen(shown.map { CompatBoard(compatBoardKey(it.url), it.name, it.url, it.url, 0) },
                                onOpenDrawer = {}, onCloseDrawer = {}, onOpenSettings = {}, onOpenHelp = {}, onOpenSavedThreads = {},
                                onUpdateBoards = {}, onBoardSelected = {}, onBoardUpsert = {}, onBoardDelete = {}, onBoardsReordered = {},
                                boardsFromBottom = bottom)
                        }
                        "futaber" -> FutaberTheme(FutaberThemeMode.Light) {
                            FutaberBoardDrawer(shown, null, Modifier.fillMaxSize(), {}, { _, _ -> }, {}, { _, _ -> }, boardsFromBottom = bottom)
                        }
                        else -> FtutterTheme(null, FtutterBrightness.LIGHT) {
                            FtutterBoardListScreen(shown, null, {}, {}, {}, null, {}, {}, boardsFromBottom = bottom)
                        }
                    }
                } }
            }
        }
        fun bounds(name: String) = rule.onNodeWithText(name, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val listTag = when (mode) { "futacha" -> "board-list"; "toshiaki" -> "compat-board-list"; "futaber" -> "futaber-board-list"; else -> "ftutter-board-list" }
        for (count in listOf(1, 2, 5)) {
            rule.runOnUiThread { shown = boards(count); bottom = false }
            rule.waitForIdle()
            val top = bounds("BOARD-01").top
            rule.runOnUiThread { bottom = true }; rule.waitForIdle()
            val first = bounds("BOARD-01")
            val last = bounds("BOARD-%02d".format(count))
            if (count <= 2) assertTrue("$mode/$count should move down: $top -> $first", first.top > top + 8f)
            else assertTrue("An overflowing list keeps its first item at the top", first.top >= top - 1f)
            assertTrue(first.top <= last.top)
            val list = rule.onNodeWithTag(listTag).fetchSemanticsNode().boundsInRoot
            assertTrue("$mode last row must remain inside $list: $last", last.bottom <= list.bottom + 1f)
        }
        capture("$mode-five-boards-bottom-large-font")
        rule.runOnUiThread { shown = boards(2); compact = false }; rule.waitForIdle()
        capture("$mode-two-boards-bottom-full-screen")
        rule.runOnUiThread { shown = boards(30) }; rule.waitForIdle()
        rule.onNodeWithTag(listTag).performScrollToNode(hasText("BOARD-30"))
        rule.onNodeWithText("BOARD-30", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag(listTag).performScrollToNode(hasText("BOARD-01"))
        rule.onNodeWithText("BOARD-01", useUnmergedTree = true).assertIsDisplayed()
        rule.runOnUiThread { shown = emptyList() }; rule.waitForIdle()
    }
    @Test fun futachaBoardsKeepTheirOrderAndScrollWhenBottomAligned() = verifyPlacement("futacha")
    @Test fun toshiakiBoardsKeepTheirOrderAndScrollWhenBottomAligned() = verifyPlacement("toshiaki")
    @Test fun futaberBoardsKeepTheirOrderAndScrollWhenBottomAligned() = verifyPlacement("futaber")
    @Test fun ftutterBoardsKeepTheirOrderAndScrollWhenBottomAligned() = verifyPlacement("ftutter")

    @Test fun boardPlacementPreferencePersistsAcrossStoreReopen() {
        val db = "feedback_stu.db"
        rule.activity.deleteDatabase(db)
        var store = AndroidCompatibilityStore(rule.activity, databaseName = db)
        try { runBlocking {
            store.initialize()
            assertFalse(boardsFromBottom(store.preferences.first()))
            store.savePreference(BOARDS_FROM_BOTTOM_KEY, "true"); store.closeForTest()
            store = AndroidCompatibilityStore(rule.activity, databaseName = db); store.initialize()
            assertTrue(boardsFromBottom(store.preferences.first()))
            store.savePreference(BOARDS_FROM_BOTTOM_KEY, "false")
            assertFalse(boardsFromBottom(store.preferences.first()))
        } } finally { runBlocking { store.closeForTest() }; rule.activity.deleteDatabase(db) }
    }
    @Test fun themedCurrentIconHasTransparentMarginAndVisibleSproutCutout() {
        for (id in listOf(R.mipmap.ic_launcher_current, R.mipmap.ic_launcher_current_round)) {
            val icon = rule.activity.getDrawable(id) as AdaptiveIconDrawable
            val mono = requireNotNull(icon.monochrome)
            val bitmap = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888)
            mono.setBounds(0, 0, 432, 432); mono.draw(Canvas(bitmap))
            val pixels = IntArray(432 * 432); bitmap.getPixels(pixels, 0, 432, 0, 0, 432, 432)
            val opaque = pixels.count { AndroidColor.alpha(it) > 0 }
            assertTrue("monochrome must contain a mark", opaque > pixels.size / 10)
            assertTrue("monochrome must not be a solid tile", opaque < pixels.size / 3)
            assertEquals(0, AndroidColor.alpha(bitmap.getPixel(0, 0)))
            for (y in 0 until 432) for (x in 0 until 432) {
                if ((x - 216) * (x - 216) + (y - 216) * (y - 216) > 106 * 106)
                    assertEquals("No colored fragments outside the circular mark at $x,$y", 0, AndroidColor.alpha(bitmap.getPixel(x, y)))
            }
            assertEquals(0, AndroidColor.alpha(bitmap.getPixel(216, 248))) // stem cutout
            assertEquals(255, AndroidColor.alpha(bitmap.getPixel(216, 300))) // circular mark
            val sheet = Bitmap.createBitmap(864, 432, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(sheet)
            for ((i, background) in listOf(0xFFE2F6EB.toInt(), 0xFF18372A.toInt()).withIndex()) {
                val save = canvas.save(); canvas.translate(i * 432f, 0f); canvas.clipRect(0, 0, 432, 432)
                canvas.drawColor(background); mono.setTint(if (i == 0) 0xFF18372A.toInt() else 0xFFE2F6EB.toInt()); mono.draw(canvas)
                canvas.restoreToCount(save)
            }
            val dir = File(rule.activity.getExternalFilesDir(null), "feedback-stu").apply { mkdirs() }
            File(dir, "monochrome-${rule.activity.resources.getResourceEntryName(id)}.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle(); sheet.recycle()
        }
    }
    @Test fun toshiakiNestedQuoteBodyAndInnerAnchorOpenTheirParsedTargets() {
        fun post(id: String, body: String) = Post(id = id, author = null, subject = null, timestamp = "",
            messageHtml = body, imageUrl = null, thumbnailUrl = null)
        val posts = ThreadHtmlParserCore.rebuildReferences(listOf(post("100", "元の本文"),
            post("101", ">元の本文<br>返信本文"), post("102", ">No.101<br>>>No.100<br>>>元の本文<br>>返信本文")))
        val snapshot = posts[2].toCompatPostSnapshot(2)
        var clicked: String? = null
        rule.setContent { MaterialTheme { CompatibilityProfileTheme(null) {
            Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            CompatPostRow(snapshot, ownPostNos = setOf("101"), fontSize = 18, thumbnailSize = 64,
                onQuoteClick = { clicked = it })
            }
        } } }
        val body = snapshot.messageHtml.toCompatPlainText()
        val node = rule.onNodeWithText(body, useUnmergedTree = true)
        fun tapText(text: String) {
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            node.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val box = layouts.single().getBoundingBox(body.indexOf(text) + 2)
            node.performTouchInput { click(box.center) }
            rule.waitForIdle()
        }
        tapText(">>元の本文")
        assertEquals("no:101", clicked)
        tapText(">>No.100")
        assertEquals("no:100", clicked)
        capture("toshiaki-nested-quote-body-and-anchor")
    }

    @Test fun parsedNestedReplyUsesTheSameParentForCountsMarksTreesAndPopups() {
        fun post(id: String, body: String) = Post(id = id, author = null, subject = null, timestamp = "", messageHtml = body, imageUrl = null, thumbnailUrl = null)
        val posts = ThreadHtmlParserCore.rebuildReferences(listOf(post("100", "元の本文"),
            post("101", ">元の本文<br>返信本文"), post("102", ">No.101<br>>>No.100<br>>>元の本文<br>>返信本文")))
        val snapshots = posts.mapIndexed { i, p -> p.toCompatPostSnapshot(i) }
        assertTrue(compatPostQuotesOwnPost(snapshots[2], setOf("101")))
        assertFalse(compatPostQuotesOwnPost(snapshots[2], setOf("100")))
        assertEquals("no:101", compatQuoteQueryForPostLine(snapshots[2], ">>元の本文"))
        assertEquals("no:100", compatQuoteQueryForPostLine(snapshots[2], ">>No.100"))
        assertEquals(listOf("102"), futaberReplyIndex(posts)["101"]?.map { it.id })
        assertEquals(listOf("101"), futaberReplyIndex(posts)["100"]?.map { it.id })
        assertEquals(FtutterPostMark.REPLY_TO_SELF, ftutterPostMark(posts[2], setOf("101"), 0))
        assertEquals(FtutterPostMark.NONE, ftutterPostMark(posts[2], setOf("100"), 0))
        assertEquals(listOf(1), ftutterQuoteTargets(posts, 2))
        val derived = buildThreadPostDerivedData(posts)
        assertEquals(listOf("102"), derived.referencedByMap["101"]?.map { it.id })
        assertEquals(listOf("101"), derived.referencedByMap["100"]?.map { it.id })
        assertEquals(1, ThreadOpeningSummaryTracker(2).accept(posts, setOf("101"))?.repliesToSelf)
        assertEquals(0, ThreadOpeningSummaryTracker(2).accept(posts, setOf("100"))?.repliesToSelf)
        runBlocking {
            assertEquals(listOf(0, 1, 2), buildThreadTreeNodes(posts).map { it.depth })
            assertEquals(listOf(0, 1, 2), buildCompatThreadDisplay(snapshots, true).posts.map { it.position })
        }
    }
}
