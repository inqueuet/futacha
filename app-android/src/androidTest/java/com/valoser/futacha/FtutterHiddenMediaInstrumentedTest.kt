@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.espresso.Espresso
import coil3.ColorImage
import coil3.ImageLoader
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.ftutter.*
import com.valoser.futacha.shared.ui.image.*
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.*
import org.junit.Assert.*

/** Real thread -> gallery/viewer/popup paths; all image requests intercepted and saves recorded only. */
class FtutterHiddenMediaInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val board = BoardSummary("m72", "M72", "test", "https://may.2chan.net/b/", "")
    private val blocked = FtutterNgRule("blocked", FtutterNgType.WORD.stored)
    private var ngRules by mutableStateOf(listOf(blocked))
    private val saved = CopyOnWriteArrayList<String>()
    private val requests = CopyOnWriteArrayList<String>()
    private lateinit var loader: ImageLoader
    private val repo = FakeBoardRepository()
    private fun url(id: String) = "https://may.2chan.net/b/src/m72-$id.png"
    private fun post(id: String, body: String) = Post(id, author = null, subject = null, timestamp = "", messageHtml = body,
        imageUrl = url(id), thumbnailUrl = "https://may.2chan.net/b/thumb/m72-${id}s.jpg", thumbnailWidth = 80, thumbnailHeight = 80)
    private val page = ThreadPage("100", null, null, null, listOf(post("100", "visible-op"),
        post("101", "blocked<br>https://dec.2chan.net/up2/src/fu1234567.png"), post("102", "visible-last")))
    private val preferences = mapOf(
        FtutterKeys.THREAD_TOOLBAR_ORDER to encodeFtutterList(FtutterThreadTool.entries.map { it.stored }),
        FtutterKeys.THREAD_TOOLBAR_ENABLED to encodeFtutterList(FtutterThreadTool.entries.map { (it == FtutterThreadTool.GALLERY || it == FtutterThreadTool.MENU).toString() }),
        FtutterKeys.OP_IMAGE_MAX_HEIGHT to "60", FtutterKeys.RES_IMAGE_MAX_HEIGHT to "60")
    @Before fun prepare() {
        loader = ImageLoader.Builder(rule.activity).memoryCache(null).diskCache(null).components {
            add(Interceptor { chain -> requests += chain.request.data.toString(); SuccessResult(ColorImage(Color.GREEN, 80, 80), chain.request) })
        }.build()
    }
    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }; rule.waitForIdle()
        if (::loader.isInitialized) loader.shutdown()
    }
    private fun open(expectedImages: Int = 2) {
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader,
                LocalHighQualityThumbnailMode provides HighQualityThumbnailMode.OFF) {
                FtutterTheme(null, FtutterBrightness.LIGHT) {
                    FtutterThreadScreen(board, FtutterThreadRef(board.id, "100", "M72"), repo, null,
                        preferences, parseFtutterDesign(preferences), null, emptySet(), null,
                        { _, _ -> }, {}, {}, {}, { _, _, _, _ -> }, {}, {}, {}, {},
                        ngRules = ngRules, initialPage = page, refreshOnOpen = false,
                        mediaTools = FtutterMediaSaveTools("fixture", {}, { saved += it }, { urls, _ -> saved.addAll(urls); FtutterBatchSaveResult(urls.size, 0) }, false))
                }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("ftutter-post-image", useUnmergedTree = true).fetchSemanticsNodes().size == expectedImages }
    }
    private fun awaitTag(tag: String) = rule.waitUntil(10_000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun assertHiddenAbsent(scope: String) {
        rule.onAllNodes(hasContentDescription("No.101") and hasAnyAncestor(hasTestTag(scope)), useUnmergedTree = true).assertCountEquals(0)
    }
    @Test fun gallerySelectionAndSaveExcludeNgAndAnUnhideRestoresItWithoutReopeningTheThread() {
        open()
        rule.onNodeWithTag("ftutter-thread-tool-collection").performClick(); awaitTag("ftutter-gallery-cell")
        assertHiddenAbsent("ftutter-gallery")
        rule.onAllNodesWithTag("ftutter-gallery-cell").assertCountEquals(2)
        rule.onAllNodesWithTag("ftutter-gallery-cell")[0].performTouchInput { longClick() }
        rule.onNodeWithTag("ftutter-gallery-select-all").performClick()
        rule.onNodeWithTag("ftutter-gallery-save").performClick()
        rule.onNodeWithTag("ftutter-gallery-save-ok").performClick()
        rule.waitUntil(5_000) { saved.size == 2 }
        assertEquals(listOf(url("100"), url("102")), saved.toList())
        rule.runOnUiThread { ngRules = emptyList() }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("ftutter-gallery-cell").fetchSemanticsNodes().size >= 3 }
        rule.onAllNodes(hasContentDescription("No.101") and hasAnyAncestor(hasTestTag("ftutter-gallery")), useUnmergedTree = true)
            .assertCountEquals(2) // attachment and inline picture of the same now-visible post
    }
    @Test fun aVisibleThumbnailOpensAViewerWithoutAnyHiddenAttachmentOrInlinePicture() {
        open(); requests.clear()
        rule.onAllNodesWithTag("ftutter-post-image", useUnmergedTree = true)[0].performTouchInput { click() }; awaitTag("ftutter-viewer")
        assertHiddenAbsent("ftutter-viewer-strip")
        rule.onNodeWithTag("ftutter-viewer-save").performClick()
        rule.waitUntil(5_000) { saved.isNotEmpty() }
        assertEquals(listOf(url("100")), saved.toList())
        assertFalse(requests.any { it.contains("m72-101") || it.contains("fu1234567") })
    }
    @Test fun explicitlyRevealingAnNgPopupDoesNotRestoreItsImageToTheViewer() {
        open()
        rule.onNodeWithTag("ftutter-thread-tool-menu").performClick()
        rule.onNodeWithTag("ftutter-thread-menu-ng").performClick(); awaitTag("ftutter-popup")
        rule.onNode(hasTestTag("ftutter-post-image") and hasAnyAncestor(hasTestTag("ftutter-popup")), useUnmergedTree = true)
            .performTouchInput { click() }
        // A negative assertion must also cover the off-main media conversion, not just
        // the synchronous tap. Pump frames while that small local task can complete.
        val until = android.os.SystemClock.uptimeMillis() + 500
        do {
            rule.waitForIdle()
            rule.onNodeWithTag("ftutter-viewer").assertDoesNotExist()
            android.os.SystemClock.sleep(10)
        } while (android.os.SystemClock.uptimeMillis() < until)
        rule.onNodeWithTag("ftutter-popup").assertIsDisplayed()
    }
    @Test fun galleryOpeningKeepsTheOriginalPostIndexAfterHiddenMediaAreRemoved() {
        open()
        rule.onNodeWithTag("ftutter-thread-tool-collection").performClick(); awaitTag("ftutter-gallery-cell")
        rule.onNode(hasTestTag("ftutter-gallery-cell") and hasAnyDescendant(hasContentDescription("No.102")), useUnmergedTree = true)
            .performTouchInput { click(center) }
        awaitTag("ftutter-viewer")
        rule.onNodeWithTag("ftutter-viewer-save").performClick()
        rule.waitUntil(5_000) { saved.isNotEmpty() }
        assertEquals(listOf(url("102")), saved.toList())
        rule.onNode(hasTestTag("ftutter-index-card") and hasAnyDescendant(hasText("2")) and
            hasAnyAncestor(hasTestTag("ftutter-viewer-strip")), useUnmergedTree = true).performClick()
        rule.onNodeWithTag("ftutter-gallery").assertDoesNotExist()
        rule.onNodeWithTag("ftutter-viewer").assertDoesNotExist()
        rule.onNodeWithTag("ftutter-thread").assertIsDisplayed()
        assertTrue(rule.onAllNodes(hasText("visible-last") and hasAnyAncestor(hasTestTag("ftutter-thread-list")), useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty())
    }
    @Test fun newlyHiddenPostsAreRemovedFromAnAlreadyOpenViewer() {
        ngRules = emptyList()
        open(expectedImages = 3)
        rule.onAllNodesWithTag("ftutter-post-image", useUnmergedTree = true)[0].performTouchInput { click() }; awaitTag("ftutter-viewer")
        rule.waitUntil(5_000) {
            rule.onAllNodes(hasContentDescription("No.101") and hasAnyAncestor(hasTestTag("ftutter-viewer-strip")), useUnmergedTree = true)
                .fetchSemanticsNodes().size == 2
        }
        rule.runOnUiThread { ngRules = listOf(blocked) }
        rule.waitUntil(10_000) {
            rule.onAllNodes(hasContentDescription("No.101") and hasAnyAncestor(hasTestTag("ftutter-viewer-strip")), useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        }
        rule.onNodeWithTag("ftutter-viewer-save").performClick()
        rule.waitUntil(5_000) { saved.isNotEmpty() }
        assertEquals(listOf(url("100")), saved.toList())
        rule.runOnUiThread { ngRules = emptyList() }
        rule.waitUntil(10_000) {
            rule.onAllNodes(hasContentDescription("No.101") and hasAnyAncestor(hasTestTag("ftutter-viewer-strip")), useUnmergedTree = true)
                .fetchSemanticsNodes().size == 2
        }
        Espresso.pressBack(); rule.onNodeWithTag("ftutter-thread").assertIsDisplayed()
    }

    @Test fun aSurvivingCurrentImageKeepsItsUrlWhileTheStripShrinksAndExpands() {
        openAllInViewer()
        selectStripPost("102"); saveCurrent(url("102"))
        hideBlockedAndAwaitStrip()
        saveCurrent(url("102")) // Old page 3 is now page 1; retain the same media key.
        restoreBlockedAndAwaitStrip()
        saveCurrent(url("102")) // The same media moves back to page 3.
        selectStripPost("100"); saveCurrent(url("100"))
    }

    @Test fun hidingTheCurrentImageFallsBackToItsNeighborAndRestoringItKeepsSelectionUsable() {
        openAllInViewer()
        selectStripPost("101"); saveCurrent(url("101"))
        hideBlockedAndAwaitStrip()
        saveCurrent(url("102")) // Both attachment and inline image vanished; next surviving slot.
        restoreBlockedAndAwaitStrip()
        saveCurrent(url("102"))
        selectStripPost("101"); saveCurrent(url("101"))
    }

    private fun openAllInViewer() {
        ngRules = emptyList()
        open(expectedImages = 3)
        rule.onAllNodesWithTag("ftutter-post-image", useUnmergedTree = true)[0].performTouchInput { click() }
        awaitTag("ftutter-viewer")
        restoreBlockedAndAwaitStrip()
    }
    private fun selectStripPost(id: String) {
        rule.onNodeWithTag("ftutter-viewer-strip").performScrollToNode(hasContentDescription("No.$id"))
        rule.onAllNodes(hasClickAction() and hasAnyDescendant(hasContentDescription("No.$id")) and
            hasAnyAncestor(hasTestTag("ftutter-viewer-strip")), useUnmergedTree = true)[0].performClick()
        rule.waitForIdle()
    }
    private fun hideBlockedAndAwaitStrip() {
        rule.runOnUiThread { ngRules = listOf(blocked) }
        rule.waitUntil(10_000) {
            rule.onAllNodes(hasContentDescription("No.101") and hasAnyAncestor(hasTestTag("ftutter-viewer-strip")), useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        }
        rule.waitForIdle()
    }
    private fun restoreBlockedAndAwaitStrip() {
        rule.runOnUiThread { ngRules = emptyList() }
        rule.waitUntil(10_000) {
            rule.onAllNodes(hasContentDescription("No.101") and hasAnyAncestor(hasTestTag("ftutter-viewer-strip")), useUnmergedTree = true)
                .fetchSemanticsNodes().size == 2
        }
        rule.waitForIdle()
    }
    private fun saveCurrent(expected: String) {
        val before = saved.size
        rule.onNodeWithTag("ftutter-viewer-save").performClick()
        rule.waitUntil(5_000) { saved.size == before + 1 }
        assertEquals(expected, saved.last())
    }
}
